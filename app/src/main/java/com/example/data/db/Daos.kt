package com.example.data.db

import androidx.room.*
import com.example.data.model.*
import com.example.data.model.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups ORDER BY createdAt ASC")
    fun getAllGroups(): Flow<List<Group>>

    @Query("SELECT * FROM groups WHERE id = :id LIMIT 1")
    suspend fun getGroupById(id: String): Group?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: Group)

    @Update
    suspend fun updateGroup(group: Group)

    @Query("UPDATE groups SET settlementMode = :mode WHERE id = :groupId")
    suspend fun updateSettlementMode(groupId: String, mode: String)

    @Query("UPDATE groups SET lastDisbursementAt = :disbursementTime WHERE id = :groupId")
    suspend fun updateLastDisbursementAt(groupId: String, disbursementTime: Long)

    @Query("UPDATE groups SET currentPeriodStartAt = :newPeriodStart WHERE id = :groupId")
    suspend fun updateCurrentPeriodStart(groupId: String, newPeriodStart: Long)

    @Query("DELETE FROM groups WHERE id = :groupId")
    suspend fun deleteGroupById(groupId: String)

    @Query("DELETE FROM members WHERE groupId = :groupId")
    suspend fun deleteMembersByGroup(groupId: String)

    @Query("DELETE FROM products WHERE groupId = :groupId")
    suspend fun deleteProductsByGroup(groupId: String)

    @Query("DELETE FROM movements WHERE groupId = :groupId")
    suspend fun deleteMovementsByGroup(groupId: String)

    @Query("DELETE FROM transfer_accounts WHERE groupId = :groupId")
    suspend fun deleteAccountsByGroup(groupId: String)

    @Query("DELETE FROM settlements WHERE groupId = :groupId")
    suspend fun deleteSettlementsByGroup(groupId: String)

    @Query("DELETE FROM notifications WHERE groupId = :groupId")
    suspend fun deleteNotificationsByGroup(groupId: String)

    @Query("DELETE FROM movement_audits WHERE movementId IN (SELECT id FROM movements WHERE groupId = :groupId)")
    suspend fun deleteMovementAuditsByGroup(groupId: String)
}

@Dao
interface MemberDao {
    @Query("SELECT * FROM members WHERE groupId = :groupId ORDER BY role ASC, name ASC")
    fun getMembersByGroup(groupId: String): Flow<List<Member>>

    @Query("SELECT * FROM members WHERE groupId = :groupId AND active = 1 ORDER BY role ASC, name ASC")
    fun getActiveMembersByGroup(groupId: String): Flow<List<Member>>

    @Query("SELECT * FROM members WHERE id = :memberId LIMIT 1")
    suspend fun getMemberById(memberId: String): Member?

    @Query("SELECT * FROM members WHERE googleUid = :googleUid AND groupId = '' LIMIT 1")
    suspend fun getProfileMemberByGoogleUid(googleUid: String): Member?

    @Query("SELECT * FROM members WHERE googleUid = :googleUid LIMIT 1")
    suspend fun getMemberByGoogleUidAnywhere(googleUid: String): Member?

    @Query("SELECT * FROM members WHERE googleUid = :googleUid AND groupId = :groupId LIMIT 1")
    suspend fun getMemberByGoogleUidInGroup(googleUid: String, groupId: String): Member?

    @Query("SELECT COUNT(*) FROM members WHERE googleUid = :googleUid AND groupId = :groupId")
    suspend fun countMembersWithGoogleUidInGroup(googleUid: String, groupId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: Member)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<Member>)

    @Update
    suspend fun updateMember(member: Member)
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE groupId = :groupId ORDER BY name ASC")
    fun getProductsByGroup(groupId: String): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE groupId = :groupId AND isArchived = 0 ORDER BY name ASC")
    fun getActiveProductsByGroup(groupId: String): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE id = :productId LIMIT 1")
    suspend fun getProductById(productId: String): Product?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: Product)

    @Update
    suspend fun updateProduct(product: Product)
}

@Dao
interface MovementDao {
    @Query("SELECT * FROM movements WHERE groupId = :groupId ORDER BY occurredAt DESC")
    fun getMovementsByGroup(groupId: String): Flow<List<Movement>>

    @Query("SELECT * FROM movements WHERE groupId = :groupId AND occurredAt >= :startTime AND occurredAt <= :endTime ORDER BY occurredAt DESC")
    fun getMovementsByDateRange(groupId: String, startTime: Long, endTime: Long): Flow<List<Movement>>

    @Query("SELECT * FROM movements WHERE id = :movementId LIMIT 1")
    suspend fun getMovementById(movementId: String): Movement?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovement(movement: Movement)

    @Update
    suspend fun updateMovement(movement: Movement)
}

@Dao
interface MovementAuditDao {
    @Query("SELECT * FROM movement_audits WHERE movementId = :movementId ORDER BY modifiedAt DESC")
    fun getAuditsForMovement(movementId: String): Flow<List<MovementAudit>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudit(audit: MovementAudit)
}

@Dao
interface TransferAccountDao {
    @Query("SELECT * FROM transfer_accounts WHERE groupId = :groupId AND isActive = 1 ORDER BY name ASC")
    fun getActiveAccountsByGroup(groupId: String): Flow<List<TransferAccount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: TransferAccount)

    @Update
    suspend fun updateAccount(account: TransferAccount)
}

@Dao
interface InvitationDao {
    @Query("SELECT * FROM invitations WHERE groupId = :groupId ORDER BY expiresAt DESC")
    fun getInvitationsByGroup(groupId: String): Flow<List<Invitation>>

    @Query("SELECT * FROM invitations WHERE code = :code LIMIT 1")
    suspend fun getInvitationByCode(code: String): Invitation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvitation(invitation: Invitation)

    @Update
    suspend fun updateInvitation(invitation: Invitation)
}

@Dao
interface SettlementDao {
    @Query("SELECT * FROM settlements WHERE groupId = :groupId ORDER BY monthKey DESC")
    fun getSettlementsByGroup(groupId: String): Flow<List<Settlement>>

    @Query("SELECT * FROM settlements WHERE id = :id LIMIT 1")
    suspend fun getSettlementById(id: String): Settlement?

    @Query("SELECT * FROM settlements WHERE groupId = :groupId AND monthKey = :monthKey LIMIT 1")
    suspend fun getSettlementByMonth(groupId: String, monthKey: String): Settlement?

    @Query("SELECT * FROM settlements WHERE groupId = :groupId AND isClosed = 1 ORDER BY monthKey DESC LIMIT 1")
    suspend fun getLatestClosedSettlement(groupId: String): Settlement?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettlement(settlement: Settlement)

    @Update
    suspend fun updateSettlement(settlement: Settlement)
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications WHERE groupId = :groupId ORDER BY timestamp DESC")
    fun getNotificationsByGroup(groupId: String): Flow<List<NotificationItem>>

    @Query("SELECT COUNT(*) FROM notifications WHERE groupId = :groupId AND isRead = 0")
    fun getUnreadCount(groupId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationItem)

    @Query("UPDATE notifications SET isRead = 1 WHERE groupId = :groupId")
    suspend fun markAllAsRead(groupId: String)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: String): Transaction?

    @Query("SELECT * FROM transactions WHERE groupId = :groupId ORDER BY date DESC")
    fun getTransactionsByGroup(groupId: String): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE type = :type ORDER BY date DESC")
    fun getTransactionsByType(type: TransactionType): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE groupId = :groupId AND type = :type ORDER BY date DESC")
    fun getTransactionsByGroupAndType(groupId: String, type: TransactionType): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE type = 'VENTA' ORDER BY date DESC")
    fun getSales(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE type = 'COMPRA' ORDER BY date DESC")
    fun getPurchases(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE type = 'PRESTAMO' ORDER BY date DESC")
    fun getLoans(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE groupId = :groupId AND type = 'VENTA' ORDER BY date DESC")
    fun getSalesByGroup(groupId: String): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE groupId = :groupId AND type = 'COMPRA' ORDER BY date DESC")
    fun getPurchasesByGroup(groupId: String): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE groupId = :groupId AND type = 'PRESTAMO' ORDER BY date DESC")
    fun getLoansByGroup(groupId: String): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE date >= :startTime AND date <= :endTime ORDER BY date DESC")
    fun getTransactionsByDateRange(startTime: Long, endTime: Long): Flow<List<Transaction>>

    @Query("SELECT SUM(amount) FROM transactions WHERE type = :type")
    fun getTotalAmountByType(type: TransactionType): Flow<Double?>

    @Query("SELECT SUM(amount) FROM transactions WHERE groupId = :groupId AND type = :type")
    fun getTotalAmountByGroupAndType(groupId: String, type: TransactionType): Flow<Double?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<Transaction>)

    @Update
    suspend fun updateTransaction(transaction: Transaction)

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: String)

    @Query("DELETE FROM transactions WHERE groupId = :groupId")
    suspend fun deleteTransactionsByGroup(groupId: String)

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()
}
