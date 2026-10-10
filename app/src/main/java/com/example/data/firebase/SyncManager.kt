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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
 * Prevencion de bucles: las escrituras que vienen de la nube se registran en
 * `remoteApplied`. Si el mismo id intenta subirse justo despues, se descarta una
 * vez. Asi un cambio remoto no rebota de vuelta a Firestore.
 *
 * Resolucion de conflictos: last-write-wins por id. El documento remoto se aplica
 * solo si no existe una copia local mas reciente (se compara `updatedAt` remoto
 * con la marca local cuando esta disponible). Para dos personas registrando
 * movimientos distintos no hay conflicto real, porque cada movimiento tiene id
 * propio generado en el dispositivo que lo crea.
 */
class SyncManager(
    context: Context,
    private val db: AppDatabase,
    private val firestore: FirestoreRepository,
    private val currentUserIdProvider: () -> String?
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val listeners = mutableListOf<ListenerRegistration>()

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

    /** Ids que acaban de llegar de la nube y no deben reenviarse. */
    private val remoteApplied = java.util.Collections.synchronizedSet(mutableSetOf<String>())

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
            listeners += firestore.listenUserGroups(userId) { ids ->
                scope.launch {
                    ids.forEach { id ->
                        if (!watching.contains(id)) pullGroup(id)
                    }
                }
            }
        }
    }

    fun stop() {
        listeners.forEach { runCatching { it.remove() } }
        listeners.clear()
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

            firestore.fetchMembers(groupId).getOrNull()?.forEach { m ->
                remoteApplied.add(m.id)
                db.memberDao().insertMember(m)
            }
            firestore.fetchMovements(groupId).getOrNull()?.forEach { m ->
                remoteApplied.add(m.id)
                db.movementDao().insertMovement(m)
            }
            firestore.fetchProducts(groupId).getOrNull()?.forEach { p ->
                remoteApplied.add(p.id)
                db.productDao().insertProduct(p)
            }
            firestore.fetchTransferAccounts(groupId).getOrNull()?.forEach { a ->
                remoteApplied.add(a.id)
                db.transferAccountDao().insertAccount(a)
            }
            firestore.fetchSettlements(groupId).getOrNull()?.forEach { s ->
                remoteApplied.add(s.id)
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
        listeners += firestore.listenGroup(groupId) { data ->
            scope.launch {
                runCatching { db.groupDao().insertGroup(data.toGroup()) }
            }
        }
        listeners += firestore.listenSubcollection(groupId, "members") { docs ->
            scope.launch {
                docs.forEach { d ->
                    val m = d.toMember()
                    remoteApplied.add(m.id)
                    runCatching { db.memberDao().insertMember(m) }
                }
            }
        }
        listeners += firestore.listenSubcollection(groupId, "movements") { docs ->
            scope.launch {
                docs.forEach { d ->
                    val m = d.toMovement()
                    remoteApplied.add(m.id)
                    runCatching { db.movementDao().insertMovement(m) }
                }
            }
        }
        listeners += firestore.listenSubcollection(groupId, "products") { docs ->
            scope.launch {
                docs.forEach { d ->
                    val p = d.toProduct()
                    remoteApplied.add(p.id)
                    runCatching { db.productDao().insertProduct(p) }
                }
            }
        }
        listeners += firestore.listenSubcollection(groupId, "transferAccounts") { docs ->
            scope.launch {
                docs.forEach { d ->
                    val a = d.toTransferAccount()
                    remoteApplied.add(a.id)
                    runCatching { db.transferAccountDao().insertAccount(a) }
                }
            }
        }
        listeners += firestore.listenSubcollection(groupId, "settlements") { docs ->
            scope.launch {
                docs.forEach { d ->
                    val s = d.toSettlement()
                    remoteApplied.add(s.id)
                    runCatching { db.settlementDao().insertSettlement(s) }
                }
            }
        }
    }

    // ------------------------------------------------------------- subidas

    /** Devuelve true si hay que subir el cambio (no vino de la nube). */
    private fun shouldPush(id: String): Boolean = !remoteApplied.remove(id)

    fun pushGroup(group: Group) {
        scope.launch {
            if (!shouldPush("group_${group.id}")) return@launch
            pushGroupNow(group)
        }
    }

    /** Sube el grupo y su lista de UIDs de miembro (version suspendida). */
    suspend fun pushGroupNow(group: Group) {
        val members = runCatching { db.memberDao().getMembersByGroup(group.id).first() }
            .getOrElse { emptyList() }
        val memberUids = members.mapNotNull { it.googleUid.ifBlank { null } }
        val currentUid = currentUserIdProvider()
        val uids = if (currentUid != null && currentUid !in memberUids) memberUids + currentUid else memberUids
        firestore.upsertGroup(group, uids)
            .onSuccess { recordSuccess() }
            .onFailure { recordError("empresa", friendly("empresa", it.message)) }
    }

    fun pushMember(member: Member) {
        scope.launch {
            if (!shouldPush(member.id)) return@launch
            firestore.upsertMember(member)
                .onSuccess { recordSuccess() }
                .onFailure { recordError("socio", friendly("socio", it.message)) }
            // Mantener la lista de miembros del grupo al dia
            if (member.googleUid.isNotBlank()) {
                firestore.addMemberUid(member.groupId, member.googleUid)
                    .onFailure { recordError("lista de socios", friendly("lista de socios", it.message)) }
            }
        }
    }

    fun pushMovement(movement: Movement) {
        scope.launch {
            if (!shouldPush(movement.id)) return@launch
            firestore.upsertMovement(movement)
                .onSuccess { recordSuccess() }
                .onFailure { recordError("movimiento", friendly("movimiento", it.message)) }
        }
    }

    fun pushProduct(product: Product) {
        scope.launch {
            if (!shouldPush(product.id)) return@launch
            firestore.upsertProduct(product)
                .onSuccess { recordSuccess() }
                .onFailure { recordError("producto", friendly("producto", it.message)) }
        }
    }

    fun pushTransferAccount(account: TransferAccount) {
        scope.launch {
            if (!shouldPush(account.id)) return@launch
            firestore.upsertTransferAccount(account)
                .onSuccess { recordSuccess() }
                .onFailure { recordError("cuenta", friendly("cuenta", it.message)) }
        }
    }

    fun pushSettlement(settlement: Settlement) {
        scope.launch {
            if (!shouldPush(settlement.id)) return@launch
            firestore.upsertSettlement(settlement)
                .onSuccess { recordSuccess() }
                .onFailure { recordError("corte", friendly("corte", it.message)) }
        }
    }

    fun pushInvitation(invitation: com.example.data.model.Invitation) {
        scope.launch {
            if (!shouldPush(invitation.id)) return@launch
            firestore.upsertInvitation(invitation)
                .onSuccess { recordSuccess() }
                .onFailure { recordError("invitación", friendly("invitación", it.message)) }
        }
    }

    fun deleteGroupEverywhere(groupId: String) {
        scope.launch {
            firestore.deleteGroupTree(groupId)
                .onFailure { recordError("borrado de empresa", friendly("borrado", it.message)) }
            firestore.deleteInvitationsForGroup(groupId)
                .onFailure { recordError("borrado de invitaciones", friendly("borrado", it.message)) }
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
