package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.example.data.db.AppDatabase
import com.example.data.model.Group
import com.example.data.model.Member
import com.example.data.model.Movement
import com.example.data.model.Product
import com.example.data.model.Settlement
import com.example.data.model.TransferAccount
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Orquesta la sincronizacion entre Firestore (nube compartida) y Room (local).
 *
 * Direccion de los datos:
 *   - SUBIDA: cuando el usuario escribe algo, FinanceRepository avisa aqui y se
 *     envia a Firestore. Room sigue siendo la fuente de verdad local, asi que la
 *     app funciona igual sin conexion (Firestore encola la escritura).
 *   - BAJADA: listeners en tiempo real sobre las subcolecciones del grupo. Cada
 *     documento que llega se escribe en Room, y los Flows de Room refrescan la UI
 *     solos. No hace falta tocar las pantallas.
 *
 * Las lecturas remotas se aplican directamente a Room y solo las escrituras
 * explicitas del repositorio se suben a Firestore. Los cambios locales de un
 * mismo registro prevalecen al sincronizar para no restaurar versiones antiguas.
 *
 */
class SyncManager(
    context: Context,
    private val db: AppDatabase,
    private val firestore: FirestoreRepository,
    private val currentUserIdProvider: () -> String?
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Escuchas agrupadas por empresa, para poder quitar solo las de una empresa
    // concreta cuando alguien la abandona o le retiran el acceso.
    private val listenersPorGrupo = mutableMapOf<String, MutableList<ListenerRegistration>>()
    private val listenersGlobales = mutableListOf<ListenerRegistration>()

    /**
     * Ultimo fallo de sincronizacion, para poder mostrarlo en la app.
     * Antes todos los errores se guardaban solo en el registro y pasaban
     * desapercibidos: la app parecia funcionar mientras no sincronizaba nada.
     */
    data class SyncError(val message: String, val at: Long = System.currentTimeMillis())

    private val _lastError = kotlinx.coroutines.flow.MutableStateFlow<SyncError?>(null)
    val lastError: kotlinx.coroutines.flow.StateFlow<SyncError?> = _lastError

    /** Marca de la ultima sincronizacion correcta. */
    private val _lastSuccessAt = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val lastSuccessAt: kotlinx.coroutines.flow.StateFlow<Long> = _lastSuccessAt

    private fun recordError(where: String, message: String?) {
        val text = message ?: "error desconocido"
        Log.w(TAG, "$where: $text")
        _lastError.value = SyncError("No se pudo sincronizar ($where): $text")
    }

    private fun recordSuccess() {
        _lastSuccessAt.value = System.currentTimeMillis()
    }

    /** Traduce los errores mas habituales de Firestore a algo entendible. */
    private fun friendly(where: String, raw: String?): String {
        val msg = raw ?: return "No se pudo sincronizar ($where)"
        return when {
            msg.contains("PERMISSION_DENIED", ignoreCase = true) ||
                msg.contains("Missing or insufficient permissions", ignoreCase = true) ->
                "Firebase rechazó la operación ($where). Revisa las reglas de seguridad."

            msg.contains("FAILED_PRECONDITION", ignoreCase = true) ||
                msg.contains("index", ignoreCase = true) ->
                "Falta un índice en Firestore ($where). Abre la consola de Firebase y créalo."

            msg.contains("UNAVAILABLE", ignoreCase = true) ||
                msg.contains("network", ignoreCase = true) ->
                "Sin conexión ($where). Se reintentará automáticamente."

            else -> "No se pudo sincronizar ($where): $msg"
        }
    }

    /**
     * Momento de la ultima escritura local de cada cosa que el usuario cambia
     * desde la app (movimientos y socios), identificada por su id.
     *
     * Proteccion contra datos obsoletos. La causa de que una anulacion "reviviera"
     * o de que un socio desvinculado reapareciera era esta secuencia:
     *
     *   1. Anulas el movimiento -> se guarda en local, pero la nube aun tiene la
     *      version activa.
     *   2. Esa version activa vuelve (por la escucha o al abrir la app) y pisa la
     *      anulacion.
     *
     * Con esta marca, se ignora lo que llega de la nube para ese id durante un
     * margen suficientemente amplio. Es un margen generoso a proposito: cerrar y
     * abrir la aplicacion tarda menos que ese tiempo, que era el caso reportado.
     * Si el cambio lo hizo el OTRO movil, tambien se respeta mientras el margen
     * este activo en este; para dos personas editando el mismo registro a la vez
     * es un caso muy improbable.
     */
    private val escrituraLocal = java.util.Collections.synchronizedMap(mutableMapOf<String, Long>())

    private val ventanaProteccionMs = 15 * 60_000L

    private fun anotarEscrituraLocal(id: String) {
        escrituraLocal[id] = System.currentTimeMillis()
    }

    private fun llegaDemasiadoPronto(id: String): Boolean {
        val cuando = escrituraLocal[id] ?: return false
        return System.currentTimeMillis() - cuando < ventanaProteccionMs
    }

    /**
     * Cola de subidas: las escrituras salen hacia Firestore en el MISMO orden
     * en que el repositorio las pide.
     *
     * Hace falta porque las reglas de seguridad solo aceptan contenido de una
     * empresa que ya existe y de la que eres miembro. Al crear una empresa, el
     * grupo tiene que salir antes que la ficha del administrador y las cuentas;
     * si cada subida fuera en su propia corrutina, el orden seria aleatorio y
     * Firebase rechazaria las que se adelantaran.
     *
     * Solo se ordena el ENVIO, no se espera la confirmacion de cada una: asi,
     * sin conexion, todas quedan encoladas en Firestore igual que antes.
     */
    private val colaSubidas = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (tarea in colaSubidas) {
                runCatching { tarea() }.onFailure { Log.w(TAG, "subida: ${it.message}") }
            }
        }
    }

    /** Ejecuta la tarea cuando hayan salido todas las subidas anteriores. */
    private fun encolar(tarea: suspend () -> Unit) {
        colaSubidas.trySend(tarea)
    }

    /**
     * Lanza la escritura sin esperar su confirmacion. UNDISPATCHED hace que la
     * llamada a Firestore se ejecute aqui mismo, antes de pasar a la siguiente
     * tarea de la cola; lo unico que queda en segundo plano es la espera.
     */
    private fun emitir(escritura: suspend () -> Unit) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { escritura() }
    }

    private fun subir(escritura: suspend () -> Unit) = encolar { emitir(escritura) }

    /**
     * Grupos a los que ya estamos suscritos. Evita volver a descargar y a
     * enganchar escuchas duplicadas, pero garantiza que un grupo NUEVO
     * (por ejemplo tras aceptar una invitacion) se descargue entero.
     */
    private val watching = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private var started = false

    /**
     * Arranca la sincronizacion: descubre los grupos del usuario, baja sus datos
     * y se suscribe a los cambios en tiempo real.
     */
    fun start(userId: String) {
        if (started) return
        started = true
        scope.launch {
            try {
                val groupIds = firestore.fetchGroupIdsForUser(userId).getOrElse { emptyList() }
                Log.d(TAG, "Grupos remotos del usuario: ${groupIds.size}")
                groupIds.forEach { pullGroup(it) }
            } catch (e: Exception) {
                Log.w(TAG, "start: ${e.message}")
            }

            // Descubrir grupos nuevos (por ejemplo tras aceptar una invitacion).
            // Se comprueba `watching` y no la base local: al unirse por codigo ya
            // puede existir una ficha de miembro sin que el grupo este descargado.
            listenersGlobales += firestore.listenUserGroups(userId) { ids, confirmadoPorServidor ->
                scope.launch {
                    ids.forEach { id ->
                        if (!watching.contains(id)) pullGroup(id)
                    }
                    // Empresas que seguia y en las que el servidor ya no me
                    // lista: me retiraron o sali. Las reglas me cortan la
                    // lectura del grupo, asi que esta es la senal fiable. Solo
                    // se actua con respuesta del servidor: la cache puede venir
                    // incompleta y no debe hacer desaparecer empresas.
                    if (confirmadoPorServidor) {
                        synchronized(watching) { watching.toList() }
                            .filter { it !in ids }
                            .forEach { perderAcceso(it) }
                    }
                }
            }
        }
    }

    fun stop() {
        listenersPorGrupo.values.flatten().forEach { runCatching { it.remove() } }
        listenersPorGrupo.clear()
        listenersGlobales.forEach { runCatching { it.remove() } }
        listenersGlobales.clear()
        watching.clear()
        started = false
    }

    /** Descarga un grupo completo y lo guarda en Room. Es idempotente. */
    suspend fun pullGroup(groupId: String) {
        // Evita descargas y escuchas duplicadas si varios avisos coinciden
        if (!watching.add(groupId)) return
        try {
            val group = firestore.fetchGroup(groupId).getOrNull()
            if (group == null) {
                // El grupo aun no esta en la nube: se reintentara en el proximo aviso
                watching.remove(groupId)
                Log.w(TAG, "pullGroup($groupId): el grupo no existe todavia en la nube")
                return
            }
            db.groupDao().insertGroup(group)

            // Guardar tambien la lista de UIDs con acceso a la empresa: es lo que
            // permite que cada cuenta vea solo sus empresas en local.
            firestore.fetchGroupMemberUids(groupId).getOrNull()?.let { uids ->
                if (uids.isNotEmpty()) {
                    db.groupDao().getGroupById(groupId)?.let { local ->
                        db.groupDao().insertGroup(local.copy(memberUids = uids))
                    }
                }
            }

            // AL BAJAR, se ignora lo que el usuario acaba de cambiar en local.
            // Sin esta proteccion, la copia antigua de la nube pisaba la
            // anulacion de un movimiento o resucitaba a un socio desvinculado
            // cada vez que se abria la aplicacion.
            firestore.fetchMembers(groupId).getOrNull()?.forEach { m ->
                if (llegaDemasiadoPronto(m.id)) return@forEach
                db.memberDao().insertMember(m)
            }
            firestore.fetchMovements(groupId).getOrNull()?.forEach { m ->
                if (llegaDemasiadoPronto(m.id)) return@forEach
                db.movementDao().insertMovement(m)
            }
            firestore.fetchProducts(groupId).getOrNull()?.forEach { p ->
                if (llegaDemasiadoPronto(p.id)) return@forEach
                db.productDao().insertProduct(p)
            }
            firestore.fetchTransferAccounts(groupId).getOrNull()?.forEach { a ->
                if (llegaDemasiadoPronto(a.id)) return@forEach
                db.transferAccountDao().insertAccount(a)
            }
            firestore.fetchSettlements(groupId).getOrNull()?.forEach { s ->
                if (llegaDemasiadoPronto(s.id)) return@forEach
                db.settlementDao().insertSettlement(s)
            }
            attachListeners(groupId)
            Log.d(TAG, "pullGroup($groupId): descarga completa")
        } catch (e: Exception) {
            watching.remove(groupId)
            Log.w(TAG, "pullGroup($groupId): ${e.message}")
        }
    }

    private fun attachListeners(groupId: String) {
        listenersPorGrupo.getOrPut(groupId) { mutableListOf() } += firestore.listenGroup(groupId) { data ->
            scope.launch {
                val remoto = data.toGroup()
                val yo = currentUserIdProvider()
                // Si ya no figuro como miembro (me retiraron o sali), la empresa
                // desaparece tambien en este dispositivo. Cubre el caso de que el
                // movil estuviera cerrado en el momento de la retirada.
                if (yo != null && remoto.memberUids.isNotEmpty() && !remoto.memberUids.contains(yo)) {
                    perderAcceso(groupId)
                    return@launch
                }
                runCatching { db.groupDao().insertGroup(remoto) }
            }
        }
        listenersPorGrupo.getOrPut(groupId) { mutableListOf() } += firestore.listenSubcollection(groupId, "members") { docs ->
            scope.launch {
                docs.forEach { d ->
                    val m = d.toMember()
                    // Ignora la copia antigua de un socio recien desvinculado:
                    // es lo que impedia que reapareciera al reabrir la app
                    if (llegaDemasiadoPronto(m.id)) return@forEach
                    runCatching { db.memberDao().insertMember(m) }
                }
            }
        }
        listenersPorGrupo.getOrPut(groupId) { mutableListOf() } += firestore.listenSubcollection(groupId, "movements") { docs ->
            scope.launch {
                docs.forEach { d ->
                    val m = d.toMovement()
                    // Si acaba de escribirse en local (anulacion, edicion), se
                    // ignora la copia remota: puede ser anterior al cambio.
                    if (llegaDemasiadoPronto(m.id)) return@forEach
                    runCatching { db.movementDao().insertMovement(m) }
                }
            }
        }
        listenersPorGrupo.getOrPut(groupId) { mutableListOf() } += firestore.listenSubcollection(groupId, "products") { docs ->
            scope.launch {
                docs.forEach { d ->
                    val p = d.toProduct()
                    if (llegaDemasiadoPronto(p.id)) return@forEach
                    runCatching { db.productDao().insertProduct(p) }
                }
            }
        }
        listenersPorGrupo.getOrPut(groupId) { mutableListOf() } += firestore.listenSubcollection(groupId, "transferAccounts") { docs ->
            scope.launch {
                docs.forEach { d ->
                    val a = d.toTransferAccount()
                    if (llegaDemasiadoPronto(a.id)) return@forEach
                    runCatching { db.transferAccountDao().insertAccount(a) }
                }
            }
        }
        listenersPorGrupo.getOrPut(groupId) { mutableListOf() } += firestore.listenSubcollection(groupId, "settlements") { docs ->
            scope.launch {
                docs.forEach { d ->
                    val s = d.toSettlement()
                    if (llegaDemasiadoPronto(s.id)) return@forEach
                    runCatching { db.settlementDao().insertSettlement(s) }
                }
            }
        }
    }

    // ------------------------------------------------------------- subidas

    /** Sube el grupo y su lista de UIDs de miembro. */
    fun pushGroup(group: Group) {
        encolar {
            // Solo los socios ACTIVOS tienen acceso. Al desactivar a alguien, su UID
            // desaparece de esta lista y deja de recibir la empresa en su movil.
            val members = runCatching { db.memberDao().getActiveMembersByGroup(group.id).first() }
                .getOrElse { emptyList() }
            val memberUids = members.mapNotNull { it.googleUid.ifBlank { null } }
            val currentUid = currentUserIdProvider()
            val uids = if (currentUid != null && currentUid !in memberUids) memberUids + currentUid else memberUids
            emitir {
                firestore.upsertGroup(group, uids)
                    .onSuccess { recordSuccess() }
                    .onFailure { recordError("empresa", friendly("empresa", it.message)) }
            }
        }
    }

    /**
     * El servidor ya no me reconoce como miembro de esta empresa: desaparece
     * de este dispositivo y se deja de sincronizar.
     */
    private suspend fun perderAcceso(groupId: String) {
        runCatching { db.groupDao().deleteGroupById(groupId) }
        stopWatching(groupId)
        Log.d(TAG, "Salida detectada en $groupId: se deja de sincronizar")
    }

    /**
     * Deja de sincronizar una empresa en este dispositivo.
     *
     * Se usa cuando el usuario la abandona o le retiran el acceso: los datos
     * historicos se conservan en local (sirven de evidencia), pero la empresa
     * deja de aparecer y deja de recibir cambios.
     */
    fun stopWatching(groupId: String) {
        watching.remove(groupId)
        listenersPorGrupo.remove(groupId)?.forEach { reg ->
            runCatching { reg.remove() }
        }
    }

    fun pushMember(member: Member) {
        anotarEscrituraLocal(member.id)
        subir {
            firestore.upsertMember(member)
                .onSuccess { recordSuccess() }
                .onFailure { recordError("socio", friendly("socio", it.message)) }
            // Mantener la lista de miembros del grupo al dia
            if (member.active && member.googleUid.isNotBlank()) {
                firestore.addMemberUid(member.groupId, member.googleUid)
                    .onFailure { recordError("lista de socios", friendly("lista de socios", it.message)) }
            }
        }
    }

    fun pushMovement(movement: Movement) {
        anotarEscrituraLocal(movement.id)
        subir {
            firestore.upsertMovement(movement)
                .onSuccess { recordSuccess() }
                .onFailure { recordError("movimiento", friendly("movimiento", it.message)) }
        }
    }

    fun pushProduct(product: Product) {
        anotarEscrituraLocal(product.id)
        subir {
            firestore.upsertProduct(product)
                .onSuccess { recordSuccess() }
                .onFailure { recordError("producto", friendly("producto", it.message)) }
        }
    }

    fun pushTransferAccount(account: TransferAccount) {
        anotarEscrituraLocal(account.id)
        subir {
            firestore.upsertTransferAccount(account)
                .onSuccess { recordSuccess() }
                .onFailure { recordError("cuenta", friendly("cuenta", it.message)) }
        }
    }

    fun pushSettlement(settlement: Settlement) {
        anotarEscrituraLocal(settlement.id)
        subir {
            firestore.upsertSettlement(settlement)
                .onSuccess { recordSuccess() }
                .onFailure { recordError("corte", friendly("corte", it.message)) }
        }
    }

    fun pushInvitation(invitation: com.example.data.model.Invitation) {
        subir {
            firestore.upsertInvitation(invitation)
                .onSuccess { recordSuccess() }
                .onFailure { recordError("invitación", friendly("invitación", it.message)) }
        }
    }

    /** Elimina una cuenta de transferencia tambien en la nube. */
    fun deleteTransferAccount(groupId: String, accountId: String) {
        subir {
            firestore.deleteTransferAccount(groupId, accountId)
                .onSuccess { recordSuccess() }
                .onFailure { recordError("cuenta", friendly("cuenta", it.message)) }
        }
    }

    fun deleteGroupEverywhere(groupId: String) {
        subir {
            // Primero las invitaciones: las reglas solo dejan borrarlas a un
            // miembro, y eso deja de poder comprobarse al borrar el grupo.
            firestore.deleteInvitationsForGroup(groupId)
                .onFailure { recordError("borrado de invitaciones", friendly("borrado", it.message)) }
            firestore.deleteGroupTree(groupId)
                .onFailure { recordError("borrado de empresa", friendly("borrado", it.message)) }
        }
    }

    /**
     * Sincroniza en Firestore la lista de UIDs con acceso a la empresa.
     * Se llama cuando alguien crea una empresa, o cuando un socio sale o es
     * retirado. Aceptar una invitacion NO pasa por aqui: esa union la hace
     * FirestoreRepository.joinWithInvitation en una sola escritura atomica.
     */
    fun pushMembership(groupId: String, memberUids: List<String>) {
        subir {
            firestore.setGroupMemberUids(groupId, memberUids)
                .onSuccess { recordSuccess() }
                .onFailure { recordError("miembros de la empresa", friendly("miembros", it.message)) }
        }
    }

    /** Limpia el ultimo error (cuando la app ya lo ha mostrado). */
    fun clearError() {
        _lastError.value = null
    }

    companion object {
        private const val TAG = "SyncManager"
    }
}
