package com.example.data.firebase

import android.util.Log
import com.example.R
import com.example.data.model.Group
import com.example.data.model.Invitation
import com.example.data.model.Member
import com.example.data.model.Movement
import com.example.data.model.Product
import com.example.data.model.Settlement
import com.example.data.model.TransferAccount
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Cliente de Firestore: TODO el acceso a la nube pasa por aqui.
 *
 * Estructura en Firestore:
 *   users/{uid}                                  -> perfil
 *   invitations/{invitationId}                   -> coleccion global, permite
 *                                                   unirse por codigo desde otro
 *                                                   dispositivo
 *   groups/{groupId}                             -> incluye memberUids (array)
 *     members/{memberId}
 *     movements/{movementId}
 *     products/{productId}
 *     transferAccounts/{accountId}
 *     settlements/{settlementId}
 *
 * `memberUids` se mantiene duplicado dentro del grupo para que un dispositivo
 * nuevo pueda averiguar a que empresas pertenece con UNA sola consulta, sin
 * necesidad de leer todas las subcolecciones.
 */
class FirestoreRepository(
    private val db: FirebaseFirestore,
    private val authProvider: () -> FirebaseUser?
) {
    constructor(
        context: android.content.Context,
        authProvider: () -> FirebaseUser?
    ) : this(
        firestoreFor(context),
        authProvider
    )

    companion object {
        private const val TAG = "FirestoreRepository"

        /**
         * Obtiene la instancia de Firestore segun el id configurado en
         * `firebase_applet_config.xml`.
         *
         * El proyecto original de Google AI Studio usa una base de datos con
         * nombre personalizado. Un proyecto de Firebase propio solo tiene la
         * base `(default)`. Para no tener que tocar codigo al cambiar de
         * proyecto, aqui se aceptan los valores "(default)" y "" como peticion
         * de usar la base por defecto.
         */
        private fun firestoreFor(context: android.content.Context): FirebaseFirestore {
            val configured = context.applicationContext
                .getString(R.string.firestore_database_id)
                .trim()
            return if (configured.isEmpty() || configured.equals("(default)", ignoreCase = true)) {
                FirebaseFirestore.getInstance()
            } else {
                FirebaseFirestore.getInstance(configured)
            }
        }
    }

    private val groupsCol = db.collection("groups")
    private val invitationsCol = db.collection("invitations")
    private val usersCol = db.collection("users")

    private fun uid(): String? = authProvider()?.uid

    // ---------------------------------------------------------------- perfil

    suspend fun syncUserProfile(user: FirebaseUser): Result<Unit> = runCatching {
        usersCol.document(user.uid).set(
            mapOf(
                "uid" to user.uid,
                "email" to (user.email ?: ""),
                "displayName" to (user.displayName ?: ""),
                "photoUrl" to (user.photoUrl?.toString() ?: ""),
                "lastLogin" to System.currentTimeMillis()
            ),
            SetOptions.merge()
        ).await()
    }.onFailure { Log.w(TAG, "syncUserProfile: ${it.message}") }

    /** UIDs de usuario de todas las empresas a las que pertenece esta cuenta. */
    suspend fun fetchGroupIdsForUser(userId: String): Result<List<String>> = runCatching {
        groupsCol.whereArrayContains("memberUids", userId).get().await()
            .documents
            .mapNotNull { it.getString("id") ?: it.id }
    }.onFailure { Log.w(TAG, "fetchGroupIdsForUser: ${it.message}") }

    // ------------------------------------------------------------ escrituras

    suspend fun upsertGroup(group: Group, memberUids: List<String>): Result<Unit> = runCatching {
        val data = group.toFirestore().toMutableMap()
        data["memberUids"] = memberUids
        data["ownerId"] = memberUids.firstOrNull() ?: uid() ?: ""
        groupsCol.document(group.id).set(data, SetOptions.merge()).await()
    }.onFailure { Log.w(TAG, "upsertGroup(${group.id}): ${it.message}") }

    suspend fun upsertMember(member: Member): Result<Unit> = runCatching {
        groupsCol.document(member.groupId)
            .collection("members").document(member.id)
            .set(member.toFirestore(), SetOptions.merge()).await()
    }.onFailure { Log.w(TAG, "upsertMember(${member.id}): ${it.message}") }

    suspend fun upsertMovement(movement: Movement): Result<Unit> = runCatching {
        groupsCol.document(movement.groupId)
            .collection("movements").document(movement.id)
            .set(movement.toFirestore(), SetOptions.merge()).await()
    }.onFailure { Log.w(TAG, "upsertMovement(${movement.id}): ${it.message}") }

    suspend fun upsertProduct(product: Product): Result<Unit> = runCatching {
        groupsCol.document(product.groupId)
            .collection("products").document(product.id)
            .set(product.toFirestore(), SetOptions.merge()).await()
    }.onFailure { Log.w(TAG, "upsertProduct(${product.id}): ${it.message}") }

    suspend fun upsertTransferAccount(account: TransferAccount): Result<Unit> = runCatching {
        groupsCol.document(account.groupId)
            .collection("transferAccounts").document(account.id)
            .set(account.toFirestore(), SetOptions.merge()).await()
    }.onFailure { Log.w(TAG, "upsertTransferAccount(${account.id}): ${it.message}") }

    suspend fun upsertSettlement(settlement: Settlement): Result<Unit> = runCatching {
        groupsCol.document(settlement.groupId)
            .collection("settlements").document(settlement.id)
            .set(settlement.toFirestore(), SetOptions.merge()).await()
    }.onFailure { Log.w(TAG, "upsertSettlement(${settlement.id}): ${it.message}") }

    suspend fun upsertInvitation(invitation: Invitation): Result<Unit> = runCatching {
        invitationsCol.document(invitation.id)
            .set(invitation.toFirestore(), SetOptions.merge()).await()
    }.onFailure { Log.w(TAG, "upsertInvitation(${invitation.id}): ${it.message}") }

    /** Anade un UID a la lista de miembros del grupo (arrayUnion es idempotente). */
    suspend fun addMemberUid(groupId: String, userId: String): Result<Unit> = runCatching {
        groupsCol.document(groupId).update("memberUids", FieldValue.arrayUnion(userId)).await()
    }.onFailure { Log.w(TAG, "addMemberUid($groupId): ${it.message}") }

    // -------------------------------------------------------------- borrados

    suspend fun deleteGroupTree(groupId: String): Result<Unit> = runCatching {
        val groupRef = groupsCol.document(groupId)
        listOf("members", "movements", "products", "transferAccounts", "settlements")
            .forEach { sub ->
                groupRef.collection(sub).get().await().documents.forEach { it.reference.delete().await() }
            }
        groupRef.delete().await()
    }.onFailure { Log.w(TAG, "deleteGroupTree($groupId): ${it.message}") }

    suspend fun deleteInvitationsForGroup(groupId: String): Result<Unit> = runCatching {
        invitationsCol.whereEqualTo("groupId", groupId).get().await()
            .documents.forEach { it.reference.delete().await() }
    }.onFailure { Log.w(TAG, "deleteInvitationsForGroup: ${it.message}") }

    // -------------------------------------------------------------- lecturas

    suspend fun fetchGroup(groupId: String): Result<Group?> = runCatching {
        groupsCol.document(groupId).get().await()
            .takeIf { it.exists() }
            ?.data
            ?.toGroup()
    }.onFailure { Log.w(TAG, "fetchGroup($groupId): ${it.message}") }

    suspend fun fetchMembers(groupId: String): Result<List<Member>> =
        fetchSubcollection(groupId, "members") { it.toMember() }

    suspend fun fetchMovements(groupId: String): Result<List<Movement>> =
        fetchSubcollection(groupId, "movements") { it.toMovement() }

    suspend fun fetchProducts(groupId: String): Result<List<Product>> =
        fetchSubcollection(groupId, "products") { it.toProduct() }

    suspend fun fetchTransferAccounts(groupId: String): Result<List<TransferAccount>> =
        fetchSubcollection(groupId, "transferAccounts") { it.toTransferAccount() }

    suspend fun fetchSettlements(groupId: String): Result<List<Settlement>> =
        fetchSubcollection(groupId, "settlements") { it.toSettlement() }

    private suspend fun <T> fetchSubcollection(
        groupId: String,
        name: String,
        map: (Map<String, Any?>) -> T
    ): Result<List<T>> = runCatching {
        groupsCol.document(groupId).collection(name).get().await()
            .documents
            .mapNotNull { snap -> snap.data?.let(map) }
    }.onFailure { Log.w(TAG, "fetch $name de $groupId: ${it.message}") }

    /**
     * Busca una invitacion por su codigo en la coleccion global.
     * Esto es lo que permite unirse desde OTRO dispositivo: el movil nuevo no
     * tiene la invitacion en su base local, asi que la busca en la nube.
     */
    suspend fun findInvitationByCode(code: String): Result<Invitation?> = runCatching {
        invitationsCol
            .whereEqualTo("code", code.trim().uppercase())
            .limit(1)
            .get().await()
            .documents
            .firstOrNull()
            ?.data
            ?.toInvitation()
    }.onFailure { Log.w(TAG, "findInvitationByCode: ${it.message}") }

    suspend fun markInvitationUsed(invitationId: String, newUsedCount: Int): Result<Unit> = runCatching {
        invitationsCol.document(invitationId)
            .set(mapOf("usedCount" to newUsedCount), SetOptions.merge()).await()
    }.onFailure { Log.w(TAG, "markInvitationUsed: ${it.message}") }

    // ------------------------------------------------------- tiempo real

    /**
     * Escucha los cambios de una subcoleccion del grupo.
     * El callback recibe el mapa del documento ya convertido a entidad.
     */
    fun listenSubcollection(
        groupId: String,
        name: String,
        onChange: (List<Map<String, Any?>>) -> Unit
    ): ListenerRegistration = groupsCol.document(groupId).collection(name)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "listener $name de $groupId: ${error.message}")
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener
            onChange(snapshot.documents.mapNotNull { it.data })
        }

    /** Escucha el documento del grupo (nombre, modo de reparto, etc.). */
    fun listenGroup(groupId: String, onChange: (Map<String, Any?>) -> Unit): ListenerRegistration =
        groupsCol.document(groupId).addSnapshotListener { snapshot: DocumentSnapshot?, error ->
            if (error != null) {
                Log.w(TAG, "listener grupo $groupId: ${error.message}")
                return@addSnapshotListener
            }
            snapshot?.data?.let(onChange)
        }

    /** Escucha las empresas a las que pertenece el usuario (para descubrir grupos nuevos). */
    fun listenUserGroups(userId: String, onChange: (List<String>) -> Unit): ListenerRegistration =
        groupsCol.whereArrayContains("memberUids", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "listener grupos de $userId: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                onChange(snapshot.documents.mapNotNull { it.getString("id") ?: it.id })
            }

    /** Escucha invitaciones nuevas para un grupo (para que el admin vea el uso). */
    fun listenInvitations(groupId: String, onChange: (List<Invitation>) -> Unit): ListenerRegistration =
        invitationsCol.whereEqualTo("groupId", groupId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "listener invitaciones de $groupId: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                onChange(snapshot.documents.mapNotNull { it.data?.toInvitation() })
            }
}
