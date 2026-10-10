package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class Role {
    ADMIN,
    SOCIO
}

enum class PaymentMethod {
    EFECTIVO,
    TRANSFERENCIA
}

enum class MovementType {
    VENTA,
    COMPRA,
    PRESTAMO,
    ABONO
}

enum class TransactionType {
    VENTA,
    COMPRA,
    PRESTAMO
}

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val groupId: String = "",
    val type: TransactionType = TransactionType.VENTA,
    val amount: Double = 0.0,
    val description: String = "",
    val date: Long = System.currentTimeMillis(),
    val category: String = "General",
    val paymentMethod: PaymentMethod = PaymentMethod.EFECTIVO,
    val customerOrDebtor: String? = null,
    val interestRate: Double = 0.0,
    val installments: Int = 1,
    val dueDate: Long? = null,
    val note: String = "",
    val isCompleted: Boolean = true
)

@Entity(tableName = "groups")
data class Group(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val currency: String = "COP",
    val settlementMode: String = "DESEMBOLSO", // "DESEMBOLSO" or "MENSUAL"
    val lastDisbursementAt: Long = 0L,
    val currentPeriodStartAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "members")
data class Member(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val groupId: String,
    val name: String,
    val email: String,
    val role: Role,
    val active: Boolean = true,
    val sharePercentCentecimas: Int = 4000, // 40.00%
    val initialDebtCop: Long = 0L,
    // UID de la cuenta de Google vinculada a este miembro. Vacio si aun no
    // ha iniciado sesion. Es lo que permite reconocer al mismo usuario entre
    // dispositivos sin sobrescribir a otros socios.
    val googleUid: String = "",
    val notifySales: Boolean = true,
    val notifyLoans: Boolean = true
)

@Entity(tableName = "products")
data class Product(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val groupId: String,
    val name: String,
    val category: String = "General",
    val photoUri: String = "",
    val standardPriceCop: Long = 0L,
    val isArchived: Boolean = false
)

data class MovementLine(
    val productId: String,
    val productName: String,
    val quantity: Int,
    val subtotalCop: Long, // Source of truth
    val unitPriceCop: Long  // Recalculated from subtotalCop / quantity
)

@Entity(tableName = "movements")
data class Movement(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val orderCode: String = "", // Unique readable code like #V-1042
    val groupId: String,
    val type: MovementType,
    val linesJson: String = "[]",
    val totalCop: Long,
    val paymentMethod: PaymentMethod,
    val transferAccountName: String? = null,
    val occurredAt: Long,
    val recordedAt: Long = System.currentTimeMillis(),
    val registeredByUserId: String,
    val registeredByName: String,
    val debtorMemberId: String? = null,
    val debtorMemberName: String? = null,
    val note: String = "",
    val isAnnulled: Boolean = false,
    val annulReason: String? = null,
    val isEdited: Boolean = false,
    val isOfflineRecorded: Boolean = false
)

@Entity(tableName = "movement_audits")
data class MovementAudit(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val movementId: String,
    val modifiedByUserId: String,
    val modifiedByName: String,
    val modifiedAt: Long = System.currentTimeMillis(),
    val description: String,
    val oldValue: String = "",
    val newValue: String = ""
)

@Entity(tableName = "transfer_accounts")
data class TransferAccount(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val groupId: String,
    val name: String,
    val isActive: Boolean = true
)

@Entity(tableName = "invitations")
data class Invitation(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val groupId: String,
    val code: String, // 8 chars, no 0, O, 1, I
    val role: Role = Role.SOCIO,
    val expiresAt: Long,
    val maxUses: Int = 1,
    val usedCount: Int = 0,
    val isRevoked: Boolean = false
)

@Entity(tableName = "settlements")
data class Settlement(
    @PrimaryKey val id: String, // groupId_yyyy-MM
    val groupId: String,
    val monthKey: String, // yyyy-MM
    val isClosed: Boolean = false,
    val closedAt: Long? = null,
    val closedByUserId: String? = null,
    val closedByName: String? = null,
    val frozenSnapshotJson: String? = null,
    val periodStartAt: Long = 0L,
    val periodEndAt: Long = 0L
)

@Entity(tableName = "notifications")
data class NotificationItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val groupId: String,
    val title: String,
    val message: String,
    val movementId: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)
