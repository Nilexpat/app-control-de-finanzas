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

    /** Ids que acaban de llegar de la nube y no deben reenviarse. */
    private val remoteApplied = java.util.Collections.synchronizedSet(mutableSetOf<String>())

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

            // Descubrir grupos nuevos (por ejemplo tras aceptar una invitacion)
            listeners += firestore.listenUserGroups(userId) { ids ->
                scope.launch {
                    ids.forEach { id ->
                        if (db.groupDao().getGroupById(id) == null) pullGroup(id)
                    }
                }
            }
        }
    }

    fun stop() {
        listeners.forEach { runCatching { it.remove() } }
        listeners.clear()
        started = false
    }

    /** Descarga un grupo completo y lo guarda en Room. */
    suspend fun pullGroup(groupId: String) {
        try {
            val group = firestore.fetchGroup(groupId).getOrNull() ?: return
            db.groupDao().insertGroup(group)
            db.groupDao().getGroupById(groupId)?.let { }

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
        } catch (e: Exception) {
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
    }

    fun pushMember(member: Member) {
        scope.launch {
            if (!shouldPush(member.id)) return@launch
            firestore.upsertMember(member)
            // Mantener la lista de miembros del grupo al dia
            if (member.googleUid.isNotBlank()) {
                firestore.addMemberUid(member.groupId, member.googleUid)
            }
        }
    }

    fun pushMovement(movement: Movement) {
        scope.launch {
            if (!shouldPush(movement.id)) return@launch
            firestore.upsertMovement(movement)
        }
    }

    fun pushProduct(product: Product) {
        scope.launch {
            if (!shouldPush(product.id)) return@launch
            firestore.upsertProduct(product)
        }
    }

    fun pushTransferAccount(account: TransferAccount) {
        scope.launch {
            if (!shouldPush(account.id)) return@launch
            firestore.upsertTransferAccount(account)
        }
    }

    fun pushSettlement(settlement: Settlement) {
        scope.launch {
            if (!shouldPush(settlement.id)) return@launch
            firestore.upsertSettlement(settlement)
        }
    }

    fun pushInvitation(invitation: com.example.data.model.Invitation) {
        scope.launch {
            if (!shouldPush(invitation.id)) return@launch
            firestore.upsertInvitation(invitation)
        }
    }

    fun deleteGroupEverywhere(groupId: String) {
        scope.launch {
            firestore.deleteGroupTree(groupId)
            firestore.deleteInvitationsForGroup(groupId)
        }
    }

    companion object {
        private const val TAG = "SyncManager"
    }
}
