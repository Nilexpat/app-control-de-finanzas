package com.example.data.repository

import com.example.data.db.AppDatabase
import com.example.data.model.*
import com.example.util.DateFormatter
import com.example.util.JsonHelper
import com.example.util.SettlementCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.security.SecureRandom
import java.util.UUID

class FinanceRepository(private val db: AppDatabase) {

    private val groupDao = db.groupDao()
    private val memberDao = db.memberDao()
    private val productDao = db.productDao()
    private val movementDao = db.movementDao()
    private val movementAuditDao = db.movementAuditDao()
    private val transferAccountDao = db.transferAccountDao()
    private val invitationDao = db.invitationDao()
    private val settlementDao = db.settlementDao()
    private val notificationDao = db.notificationDao()
    private val transactionDao = db.transactionDao()

    val allGroups: Flow<List<Group>> = groupDao.getAllGroups()
    val allTransactions: Flow<List<Transaction>> = transactionDao.getAllTransactions()

    fun getTransactionsByGroup(groupId: String): Flow<List<Transaction>> = transactionDao.getTransactionsByGroup(groupId)
    fun getTransactionsByType(type: TransactionType): Flow<List<Transaction>> = transactionDao.getTransactionsByType(type)
    fun getSales(groupId: String): Flow<List<Transaction>> = transactionDao.getSalesByGroup(groupId)
    fun getPurchases(groupId: String): Flow<List<Transaction>> = transactionDao.getPurchasesByGroup(groupId)
    fun getLoans(groupId: String): Flow<List<Transaction>> = transactionDao.getLoansByGroup(groupId)
    suspend fun insertTransaction(transaction: Transaction) = transactionDao.insertTransaction(transaction)
    suspend fun updateTransaction(transaction: Transaction) = transactionDao.updateTransaction(transaction)
    suspend fun deleteTransaction(id: String) = transactionDao.deleteTransactionById(id)

    fun getMembers(groupId: String): Flow<List<Member>> = memberDao.getMembersByGroup(groupId)
    fun getActiveMembers(groupId: String): Flow<List<Member>> = memberDao.getActiveMembersByGroup(groupId)

    fun getProducts(groupId: String): Flow<List<Product>> = productDao.getProductsByGroup(groupId)
    fun getActiveProducts(groupId: String): Flow<List<Product>> = productDao.getActiveProductsByGroup(groupId)

    fun getMovements(groupId: String): Flow<List<Movement>> = movementDao.getMovementsByGroup(groupId)
    fun getAuditsForMovement(movementId: String): Flow<List<MovementAudit>> = movementAuditDao.getAuditsForMovement(movementId)

    fun getTransferAccounts(groupId: String): Flow<List<TransferAccount>> = transferAccountDao.getActiveAccountsByGroup(groupId)
    fun getInvitations(groupId: String): Flow<List<Invitation>> = invitationDao.getInvitationsByGroup(groupId)

    fun getSettlements(groupId: String): Flow<List<Settlement>> = settlementDao.getSettlementsByGroup(groupId)
    fun getNotifications(groupId: String): Flow<List<NotificationItem>> = notificationDao.getNotificationsByGroup(groupId)
    fun getUnreadNotificationsCount(groupId: String): Flow<Int> = notificationDao.getUnreadCount(groupId)

    suspend fun markAllNotificationsRead(groupId: String) {
        notificationDao.markAllAsRead(groupId)
    }

    // --- Validation and Business Rules ---

    suspend fun registerMovement(
        groupId: String,
        currentUser: Member,
        type: MovementType,
        lines: List<MovementLine>,
        totalCop: Long,
        paymentMethod: PaymentMethod,
        transferAccountName: String?,
        occurredAtDate: Long?, // null for Socio -> forced to System.currentTimeMillis()
        debtorMemberId: String?,
        debtorMemberName: String?,
        note: String
    ): Result<Movement> {
        // Rule: Payment method is mandatory
        // Rule: Price/amount must be > 0
        if (totalCop <= 0) {
            return Result.failure(IllegalArgumentException("El monto debe ser mayor que cero"))
        }

        // Rule: For Socio, date is strictly current time
        val finalOccurredAt = if (currentUser.role == Role.SOCIO) {
            System.currentTimeMillis()
        } else {
            val chosen = occurredAtDate ?: System.currentTimeMillis()
            // Admin cannot pick future date
            if (chosen > System.currentTimeMillis() + 300_000L) { // 5 min tolerance
                return Result.failure(IllegalArgumentException("No se permiten fechas futuras"))
            }
            chosen
        }

        // Rule: Check if movement date falls into a closed settlement
        val monthKey = DateFormatter.toMonthKey(finalOccurredAt)
        val settlement = settlementDao.getSettlementByMonth(groupId, monthKey)
        if (settlement != null && settlement.isClosed) {
            return Result.failure(IllegalStateException("No se pueden registrar movimientos en un corte cerrado ($monthKey)"))
        }

        // Rule: For loans, Socio can only register for themselves
        val finalDebtorId = if (type == MovementType.PRESTAMO || type == MovementType.ABONO) {
            if (currentUser.role == Role.SOCIO) {
                currentUser.id
            } else {
                debtorMemberId ?: currentUser.id
            }
        } else null

        val finalDebtorName = if (type == MovementType.PRESTAMO || type == MovementType.ABONO) {
            if (currentUser.role == Role.SOCIO) {
                currentUser.name
            } else {
                debtorMemberName ?: currentUser.name
            }
        } else null

        val prefix = when (type) {
            MovementType.VENTA -> "V"
            MovementType.COMPRA -> "C"
            MovementType.PRESTAMO -> "P"
            MovementType.ABONO -> "A"
        }
        val orderCode = "#$prefix-${(1000..9999).random()}"

        val movement = Movement(
            groupId = groupId,
            orderCode = orderCode,
            type = type,
            linesJson = JsonHelper.serializeLines(lines),
            totalCop = totalCop,
            paymentMethod = paymentMethod,
            transferAccountName = if (paymentMethod == PaymentMethod.TRANSFERENCIA) transferAccountName else null,
            occurredAt = finalOccurredAt,
            recordedAt = System.currentTimeMillis(),
            registeredByUserId = currentUser.id,
            registeredByName = currentUser.name,
            debtorMemberId = finalDebtorId,
            debtorMemberName = finalDebtorName,
            note = note,
            isAnnulled = false,
            isEdited = false,
            isOfflineRecorded = false
        )

        movementDao.insertMovement(movement)

        // Store corresponding Transaction entity for sales, purchases, and loans
        val transType = when (type) {
            MovementType.VENTA -> TransactionType.VENTA
            MovementType.COMPRA -> TransactionType.COMPRA
            MovementType.PRESTAMO -> TransactionType.PRESTAMO
            MovementType.ABONO -> TransactionType.VENTA
        }
        val transaction = Transaction(
            id = movement.id,
            groupId = groupId,
            type = transType,
            amount = totalCop.toDouble(),
            description = note.ifBlank { "${transType.name} - ${movement.orderCode}" },
            date = finalOccurredAt,
            paymentMethod = paymentMethod,
            customerOrDebtor = finalDebtorName,
            note = note
        )
        transactionDao.insertTransaction(transaction)

        // Notification logic
        val activeMembers = memberDao.getActiveMembersByGroup(groupId).first()
        val formattedAmount = com.example.util.CurrencyFormatter.formatCop(totalCop)
        val methodLabel = if (paymentMethod == PaymentMethod.EFECTIVO) "Efectivo" else "Transferencia (${transferAccountName ?: "General"})"

        when (type) {
            MovementType.VENTA -> {
                val detailStr = if (lines.isNotEmpty()) " · ${lines.size} producto(s)" else ""
                val notif = NotificationItem(
                    groupId = groupId,
                    title = "Nueva venta de ${currentUser.name}",
                    message = "${currentUser.name} registró venta de $formattedAmount$detailStr · $methodLabel",
                    movementId = movement.id,
                    timestamp = System.currentTimeMillis()
                )
                notificationDao.insertNotification(notif)
            }
            MovementType.COMPRA -> {
                val notif = NotificationItem(
                    groupId = groupId,
                    title = "Nueva compra registrada",
                    message = "${currentUser.name} registró compra por $formattedAmount · $methodLabel",
                    movementId = movement.id,
                    timestamp = System.currentTimeMillis()
                )
                notificationDao.insertNotification(notif)
            }
            MovementType.PRESTAMO -> {
                val notif = NotificationItem(
                    groupId = groupId,
                    title = "Préstamo registrado",
                    message = "${finalDebtorName ?: currentUser.name} retiró $formattedAmount como préstamo · $methodLabel",
                    movementId = movement.id,
                    timestamp = System.currentTimeMillis()
                )
                notificationDao.insertNotification(notif)
            }
            MovementType.ABONO -> {
                val notif = NotificationItem(
                    groupId = groupId,
                    title = "Abono a deuda",
                    message = "${finalDebtorName ?: currentUser.name} abonó $formattedAmount a su deuda · $methodLabel",
                    movementId = movement.id,
                    timestamp = System.currentTimeMillis()
                )
                notificationDao.insertNotification(notif)
            }
        }

        return Result.success(movement)
    }

    suspend fun editMovement(
        movementId: String,
        currentUser: Member,
        newLines: List<MovementLine>,
        newTotalCop: Long,
        newPaymentMethod: PaymentMethod,
        newAccountName: String?,
        newNote: String
    ): Result<Movement> {
        val existing = movementDao.getMovementById(movementId)
            ?: return Result.failure(IllegalArgumentException("Movimiento no encontrado"))

        // Check closed settlement
        val monthKey = DateFormatter.toMonthKey(existing.occurredAt)
        val settlement = settlementDao.getSettlementByMonth(existing.groupId, monthKey)
        if (settlement != null && settlement.isClosed) {
            return Result.failure(IllegalStateException("No se puede editar un movimiento de un corte cerrado"))
        }

        // Check 24-hour rule
        val elapsed = System.currentTimeMillis() - existing.recordedAt
        val isWithin24Hours = elapsed <= 24 * 60 * 60 * 1000L

        if (currentUser.role == Role.SOCIO) {
            if (existing.registeredByUserId != currentUser.id) {
                return Result.failure(IllegalAccessException("Solo puedes editar los movimientos que tú mismo registraste"))
            }
            if (!isWithin24Hours) {
                return Result.failure(IllegalAccessException("El plazo de 24 horas para corregir este movimiento ya expiró"))
            }
        }

        // Build audit entries
        val audits = mutableListOf<MovementAudit>()
        if (existing.totalCop != newTotalCop) {
            audits.add(
                MovementAudit(
                    movementId = movementId,
                    modifiedByUserId = currentUser.id,
                    modifiedByName = currentUser.name,
                    description = "Monto modificado",
                    oldValue = com.example.util.CurrencyFormatter.formatCop(existing.totalCop),
                    newValue = com.example.util.CurrencyFormatter.formatCop(newTotalCop)
                )
            )
        }
        if (existing.paymentMethod != newPaymentMethod || existing.transferAccountName != newAccountName) {
            audits.add(
                MovementAudit(
                    movementId = movementId,
                    modifiedByUserId = currentUser.id,
                    modifiedByName = currentUser.name,
                    description = "Medio de pago modificado",
                    oldValue = "${existing.paymentMethod} (${existing.transferAccountName ?: ""})",
                    newValue = "$newPaymentMethod (${newAccountName ?: ""})"
                )
            )
        }
        if (existing.note != newNote) {
            audits.add(
                MovementAudit(
                    movementId = movementId,
                    modifiedByUserId = currentUser.id,
                    modifiedByName = currentUser.name,
                    description = "Nota modificada",
                    oldValue = existing.note,
                    newValue = newNote
                )
            )
        }

        for (a in audits) {
            movementAuditDao.insertAudit(a)
        }

        // Notify Admins if Socio reduced a loan
        if (existing.type == MovementType.PRESTAMO && currentUser.role == Role.SOCIO) {
            val alertNotif = NotificationItem(
                groupId = existing.groupId,
                title = "Alerta: Préstamo modificado por socio",
                message = "${currentUser.name} corrigió un préstamo de ${com.example.util.CurrencyFormatter.formatCop(existing.totalCop)} a ${com.example.util.CurrencyFormatter.formatCop(newTotalCop)}",
                movementId = movementId,
                timestamp = System.currentTimeMillis()
            )
            notificationDao.insertNotification(alertNotif)
        }

        val updated = existing.copy(
            linesJson = JsonHelper.serializeLines(newLines),
            totalCop = newTotalCop,
            paymentMethod = newPaymentMethod,
            transferAccountName = if (newPaymentMethod == PaymentMethod.TRANSFERENCIA) newAccountName else null,
            note = newNote,
            isEdited = true
        )
        movementDao.updateMovement(updated)

        return Result.success(updated)
    }

    suspend fun annulMovement(
        movementId: String,
        currentUser: Member,
        reason: String
    ): Result<Movement> {
        if (reason.isBlank()) {
            return Result.failure(IllegalArgumentException("Debes indicar un motivo de anulación"))
        }

        val existing = movementDao.getMovementById(movementId)
            ?: return Result.failure(IllegalArgumentException("Movimiento no encontrado"))

        // Check closed settlement
        val monthKey = DateFormatter.toMonthKey(existing.occurredAt)
        val settlement = settlementDao.getSettlementByMonth(existing.groupId, monthKey)
        if (settlement != null && settlement.isClosed) {
            return Result.failure(IllegalStateException("No se puede anular un movimiento de un corte cerrado"))
        }

        // Check 24-hour rule for Socio
        val elapsed = System.currentTimeMillis() - existing.recordedAt
        val isWithin24Hours = elapsed <= 24 * 60 * 60 * 1000L

        if (currentUser.role == Role.SOCIO) {
            if (existing.registeredByUserId != currentUser.id) {
                return Result.failure(IllegalAccessException("Solo puedes anular los movimientos que tú mismo registraste"))
            }
            if (!isWithin24Hours) {
                return Result.failure(IllegalAccessException("El plazo de 24 horas para anular este movimiento ya expiró"))
            }
        }

        val audit = MovementAudit(
            movementId = movementId,
            modifiedByUserId = currentUser.id,
            modifiedByName = currentUser.name,
            description = "Movimiento anulado",
            oldValue = "Activo",
            newValue = "Anulado: $reason"
        )
        movementAuditDao.insertAudit(audit)

        val updated = existing.copy(
            isAnnulled = true,
            annulReason = reason
        )
        movementDao.updateMovement(updated)

        // Notification for annulled sale or loan
        val notif = NotificationItem(
            groupId = existing.groupId,
            title = "Movimiento anulado por ${currentUser.name}",
            message = "Se anuló ${existing.type}: ${com.example.util.CurrencyFormatter.formatCop(existing.totalCop)} (Motivo: $reason)",
            movementId = movementId,
            timestamp = System.currentTimeMillis()
        )
        notificationDao.insertNotification(notif)

        return Result.success(updated)
    }

    // --- Settlements / Cortes ---

    suspend fun getSettlementSummary(groupId: String, monthKey: String): SettlementSummary {
        val group = groupDao.getGroupById(groupId)
        val isDisbursementMode = group?.settlementMode == "DESEMBOLSO"

        if (isDisbursementMode && group != null) {
            val periodStart = if (group.currentPeriodStartAt > 0) group.currentPeriodStartAt else group.createdAt
            val isDisbursed = group.lastDisbursementAt >= periodStart && group.lastDisbursementAt > 0
            val periodEnd = if (isDisbursed) group.lastDisbursementAt else System.currentTimeMillis()

            val allMovements = movementDao.getMovementsByGroup(groupId).first()
            val members = memberDao.getActiveMembersByGroup(groupId).first()

            val periodMovements = allMovements.filter {
                if (isDisbursed) {
                    it.occurredAt in periodStart..periodEnd
                } else {
                    it.occurredAt >= periodStart
                }
            }

            val partnersShareSum = members.sumOf { it.sharePercentCentecimas }
            val companyShare = (10000 - partnersShareSum).coerceAtLeast(0)

            return SettlementCalculator.calculateSettlement(
                monthKey = monthKey,
                movements = periodMovements,
                members = members,
                companyShareCentecimas = companyShare,
                isClosed = isDisbursed,
                closedAt = if (isDisbursed) group.lastDisbursementAt else null,
                closedByName = if (isDisbursed) "Administrador" else null,
                periodStartAt = periodStart,
                periodEndAt = periodEnd,
                isDisbursementMode = true,
                canCreateNewPeriod = isDisbursed
            )
        }

        val settlement = settlementDao.getSettlementByMonth(groupId, monthKey)
        if (settlement != null && settlement.isClosed && !settlement.frozenSnapshotJson.isNullOrBlank()) {
            val frozen = JsonHelper.deserializeSettlementSummary(settlement.frozenSnapshotJson)
            if (frozen != null) return frozen
        }

        // Dynamic calculation
        val allMovements = movementDao.getMovementsByGroup(groupId).first()
        val members = memberDao.getActiveMembersByGroup(groupId).first()

        val monthMovements = allMovements.filter {
            DateFormatter.toMonthKey(it.occurredAt) == monthKey
        }

        // Calculate company share: 10000 - sum(partner shares)
        val partnersShareSum = members.sumOf { it.sharePercentCentecimas }
        val companyShare = (10000 - partnersShareSum).coerceAtLeast(0)

        return SettlementCalculator.calculateSettlement(
            monthKey = monthKey,
            movements = monthMovements,
            members = members,
            companyShareCentecimas = companyShare,
            isClosed = settlement?.isClosed ?: false,
            closedAt = settlement?.closedAt,
            closedByName = settlement?.closedByName,
            isDisbursementMode = false,
            canCreateNewPeriod = false
        )
    }

    suspend fun closeSettlement(
        groupId: String,
        monthKey: String,
        currentUser: Member
    ): Result<Settlement> {
        if (currentUser.role != Role.ADMIN) {
            return Result.failure(IllegalAccessException("Solo un Admin puede cerrar el corte"))
        }

        val members = memberDao.getActiveMembersByGroup(groupId).first()
        val partnersShareSum = members.sumOf { it.sharePercentCentecimas }
        val companyShare = 10000 - partnersShareSum

        // Validate percentage sum exactly equals 100.00% (10000 centésimas)
        if (partnersShareSum + companyShare != 10000 || companyShare < 0) {
            return Result.failure(IllegalArgumentException("Los porcentajes deben sumar exactamente 100,00 %"))
        }

        val summary = getSettlementSummary(groupId, monthKey)
        val frozenSummary = summary.copy(
            isClosed = true,
            closedAt = System.currentTimeMillis(),
            closedByName = currentUser.name
        )
        val snapshotJson = JsonHelper.serializeSettlementSummary(frozenSummary)

        val settlementId = "${groupId}_$monthKey"
        val settlement = Settlement(
            id = settlementId,
            groupId = groupId,
            monthKey = monthKey,
            isClosed = true,
            closedAt = System.currentTimeMillis(),
            closedByUserId = currentUser.id,
            closedByName = currentUser.name,
            frozenSnapshotJson = snapshotJson
        )

        settlementDao.insertSettlement(settlement)

        val notif = NotificationItem(
            groupId = groupId,
            title = "Corte cerrado: ${DateFormatter.formatMonthYear(monthKey)}",
            message = "${currentUser.name} cerró el corte. Las cifras quedan congeladas.",
            timestamp = System.currentTimeMillis()
        )
        notificationDao.insertNotification(notif)

        return Result.success(settlement)
    }

    suspend fun reopenSettlement(
        groupId: String,
        monthKey: String,
        currentUser: Member
    ): Result<Settlement> {
        if (currentUser.role != Role.ADMIN) {
            return Result.failure(IllegalAccessException("Solo un Admin puede reabrir cortes"))
        }

        // Only the latest closed settlement can be reopened
        val latest = settlementDao.getLatestClosedSettlement(groupId)
        if (latest == null || latest.monthKey != monthKey) {
            return Result.failure(IllegalStateException("Solo se puede reabrir el último corte cerrado"))
        }

        val updated = latest.copy(
            isClosed = false,
            closedAt = null,
            closedByUserId = null,
            closedByName = null,
            frozenSnapshotJson = null
        )
        settlementDao.updateSettlement(updated)

        val notif = NotificationItem(
            groupId = groupId,
            title = "Corte reabierto: ${DateFormatter.formatMonthYear(monthKey)}",
            message = "${currentUser.name} reabrió el corte para correcciones.",
            timestamp = System.currentTimeMillis()
        )
        notificationDao.insertNotification(notif)

        return Result.success(updated)
    }

    suspend fun disburseCurrentPeriod(
        groupId: String,
        currentUser: Member
    ): Result<Settlement> {
        if (currentUser.role != Role.ADMIN) {
            return Result.failure(IllegalAccessException("Solo un Admin puede realizar el desembolso"))
        }
        val group = groupDao.getGroupById(groupId)
            ?: return Result.failure(IllegalArgumentException("Empresa no encontrada"))

        val members = memberDao.getActiveMembersByGroup(groupId).first()
        val partnersShareSum = members.sumOf { it.sharePercentCentecimas }
        val companyShare = (10000 - partnersShareSum).coerceAtLeast(0)

        val periodStart = if (group.currentPeriodStartAt > 0) group.currentPeriodStartAt else group.createdAt
        val now = System.currentTimeMillis()

        val allMovements = movementDao.getMovementsByGroup(groupId).first()
        val periodMovements = allMovements.filter { it.occurredAt >= periodStart && it.occurredAt <= now }

        val summary = SettlementCalculator.calculateSettlement(
            monthKey = "DESEMBOLSO_${now}",
            movements = periodMovements,
            members = members,
            companyShareCentecimas = companyShare,
            isClosed = true,
            closedAt = now,
            closedByName = currentUser.name,
            periodStartAt = periodStart,
            periodEndAt = now,
            isDisbursementMode = true,
            canCreateNewPeriod = true
        )

        val snapshotJson = JsonHelper.serializeSettlementSummary(summary)
        val settlementId = "${groupId}_disburse_${now}"
        val settlement = Settlement(
            id = settlementId,
            groupId = groupId,
            monthKey = "DESEMBOLSO_${now}",
            isClosed = true,
            closedAt = now,
            closedByUserId = currentUser.id,
            closedByName = currentUser.name,
            frozenSnapshotJson = snapshotJson,
            periodStartAt = periodStart,
            periodEndAt = now
        )
        settlementDao.insertSettlement(settlement)

        // Mark group last disbursement
        groupDao.updateLastDisbursementAt(groupId, now)

        val notif = NotificationItem(
            groupId = groupId,
            title = "Desembolso realizado",
            message = "${currentUser.name} ejecutó el desembolso del período. Cifras congeladas para este pago.",
            timestamp = now
        )
        notificationDao.insertNotification(notif)

        return Result.success(settlement)
    }

    suspend fun startNewPeriod(
        groupId: String,
        currentUser: Member
    ): Result<Unit> {
        if (currentUser.role != Role.ADMIN) {
            return Result.failure(IllegalAccessException("Solo un Admin puede iniciar un nuevo período"))
        }
        val now = System.currentTimeMillis()
        groupDao.updateCurrentPeriodStart(groupId, now)

        val notif = NotificationItem(
            groupId = groupId,
            title = "Nuevo período iniciado",
            message = "${currentUser.name} inició un nuevo período financiero.",
            timestamp = now
        )
        notificationDao.insertNotification(notif)

        return Result.success(Unit)
    }

    suspend fun updateGroupSettlementMode(groupId: String, mode: String) {
        groupDao.updateSettlementMode(groupId, mode)
    }

    // --- Quick Change Payment Method (Admin) ---
    suspend fun quickChangePaymentMethod(
        movementId: String,
        newMethod: PaymentMethod,
        newAccountName: String?,
        currentUser: Member
    ): Result<Movement> {
        val existing = movementDao.getMovementById(movementId)
            ?: return Result.failure(IllegalArgumentException("Movimiento no encontrado"))

        // Check closed settlement
        val monthKey = DateFormatter.toMonthKey(existing.occurredAt)
        val settlement = settlementDao.getSettlementByMonth(existing.groupId, monthKey)
        if (settlement != null && settlement.isClosed) {
            return Result.failure(IllegalStateException("No se puede modificar un movimiento de un corte cerrado"))
        }

        if (currentUser.role != Role.ADMIN) {
            return Result.failure(IllegalAccessException("Solo un Administrador puede cambiar el medio de pago del movimiento"))
        }

        val oldLabel = if (existing.paymentMethod == PaymentMethod.EFECTIVO) "Efectivo" else "Transferencia (${existing.transferAccountName ?: "General"})"
        val newLabel = if (newMethod == PaymentMethod.EFECTIVO) "Efectivo" else "Transferencia (${newAccountName ?: "General"})"

        val audit = MovementAudit(
            movementId = movementId,
            modifiedByUserId = currentUser.id,
            modifiedByName = currentUser.name,
            description = "Cambio de medio de pago",
            oldValue = oldLabel,
            newValue = newLabel
        )
        movementAuditDao.insertAudit(audit)

        val updated = existing.copy(
            paymentMethod = newMethod,
            transferAccountName = if (newMethod == PaymentMethod.TRANSFERENCIA) newAccountName else null,
            isEdited = true
        )
        movementDao.updateMovement(updated)

        // Notification for payment method change
        val notif = NotificationItem(
            groupId = existing.groupId,
            title = "Cambio de medio de pago",
            message = "En pedido ${existing.orderCode}: se cambió el pago de $oldLabel a $newLabel por ${currentUser.name}.",
            movementId = movementId,
            timestamp = System.currentTimeMillis()
        )
        notificationDao.insertNotification(notif)

        return Result.success(updated)
    }

    // --- Products & Catalog ---

    suspend fun addProduct(
        groupId: String,
        name: String,
        category: String,
        photoUri: String,
        standardPriceCop: Long = 0L
    ): Result<Product> {
        if (name.isBlank()) {
            return Result.failure(IllegalArgumentException("El nombre del producto es obligatorio"))
        }
        val existing = productDao.getActiveProductsByGroup(groupId).first()
        if (existing.any { it.name.trim().equals(name.trim(), ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("Ya existe un producto con el nombre '$name'"))
        }

        val product = Product(
            groupId = groupId,
            name = name.trim(),
            category = category.ifBlank { "General" },
            photoUri = photoUri,
            standardPriceCop = standardPriceCop,
            isArchived = false
        )
        productDao.insertProduct(product)
        return Result.success(product)
    }

    // --- Create New Group / Empresa ---

    suspend fun createGroup(
        name: String,
        creatorName: String,
        creatorEmail: String,
        settlementMode: String = "DESEMBOLSO"
    ): Result<Group> {
        if (name.isBlank()) {
            return Result.failure(IllegalArgumentException("El nombre de la empresa es obligatorio"))
        }
        val newGroupId = "group_" + UUID.randomUUID().toString().take(8)
        val now = System.currentTimeMillis()
        val group = Group(
            id = newGroupId,
            name = name.trim(),
            createdAt = now,
            settlementMode = settlementMode,
            lastDisbursementAt = 0L,
            currentPeriodStartAt = now
        )
        groupDao.insertGroup(group)

        // Creator is Admin
        val adminMember = Member(
            id = "user_" + UUID.randomUUID().toString().take(8),
            groupId = newGroupId,
            name = creatorName.ifBlank { "Administrador" },
            email = creatorEmail.ifBlank { "admin@$name.com" },
            role = Role.ADMIN,
            active = true,
            sharePercentCentecimas = 5000,
            initialDebtCop = 0L
        )
        memberDao.insertMember(adminMember)

        // Add standard transfer accounts
        val defaultAccounts = listOf("Nequi", "Daviplata", "Bancolombia", "Otra")
        for (acc in defaultAccounts) {
            transferAccountDao.insertAccount(TransferAccount(groupId = newGroupId, name = acc))
        }

        // Notification
        notificationDao.insertNotification(
            NotificationItem(
                groupId = newGroupId,
                title = "Empresa creada",
                message = "Se ha configurado la empresa '$name'. ¡Comienza agregando socios o productos al catálogo!",
                timestamp = System.currentTimeMillis()
            )
        )

        return Result.success(group)
    }

    suspend fun renameGroup(groupId: String, newName: String, currentUser: Member): Result<Unit> {
        if (newName.isBlank()) return Result.failure(IllegalArgumentException("El nombre no puede estar vacío"))
        if (currentUser.role != Role.ADMIN) return Result.failure(IllegalAccessException("Solo un Admin puede renombrar la empresa"))
        val group = groupDao.getGroupById(groupId) ?: return Result.failure(IllegalArgumentException("Empresa no encontrada"))
        groupDao.updateGroup(group.copy(name = newName.trim()))
        return Result.success(Unit)
    }

    suspend fun deleteGroup(groupId: String, currentUser: Member): Result<Unit> {
        if (currentUser.role != Role.ADMIN) return Result.failure(IllegalAccessException("Solo un Admin puede eliminar la empresa"))
        val allGroups = groupDao.getAllGroups().first()
        if (allGroups.size <= 1) return Result.failure(IllegalStateException("No puedes eliminar la única empresa activa"))
        groupDao.deleteMembersByGroup(groupId)
        groupDao.deleteProductsByGroup(groupId)
        groupDao.deleteMovementsByGroup(groupId)
        groupDao.deleteAccountsByGroup(groupId)
        groupDao.deleteSettlementsByGroup(groupId)
        groupDao.deleteNotificationsByGroup(groupId)
        transactionDao.deleteTransactionsByGroup(groupId)
        groupDao.deleteGroupById(groupId)
        return Result.success(Unit)
    }

    suspend fun archiveProduct(productId: String, currentUser: Member): Result<Unit> {
        if (currentUser.role != Role.ADMIN) {
            return Result.failure(IllegalAccessException("Solo un Admin puede archivar productos"))
        }
        val p = productDao.getProductById(productId) ?: return Result.failure(IllegalArgumentException("Producto no encontrado"))
        productDao.updateProduct(p.copy(isArchived = true))
        return Result.success(Unit)
    }

    // --- Members & Groups ---

    suspend fun updateMemberRole(memberId: String, newRole: Role, currentUser: Member): Result<Unit> {
        if (currentUser.role != Role.ADMIN) {
            return Result.failure(IllegalAccessException("Solo un Admin puede cambiar roles"))
        }
        val target = memberDao.getMemberById(memberId) ?: return Result.failure(IllegalArgumentException("Miembro no encontrado"))
        if (target.role == Role.ADMIN && newRole == Role.SOCIO) {
            // Check if last Admin
            val all = memberDao.getActiveMembersByGroup(target.groupId).first()
            val admins = all.filter { it.role == Role.ADMIN }
            if (admins.size <= 1) {
                return Result.failure(IllegalStateException("El último Admin no puede ser degradado sin nombrar antes a otro Admin"))
            }
        }
        memberDao.updateMember(target.copy(role = newRole))
        return Result.success(Unit)
    }

    suspend fun deactivateMember(memberId: String, currentUser: Member): Result<Unit> {
        if (currentUser.role != Role.ADMIN) {
            return Result.failure(IllegalAccessException("Solo un Admin puede retirar socios"))
        }
        val target = memberDao.getMemberById(memberId) ?: return Result.failure(IllegalArgumentException("Miembro no encontrado"))
        if (target.role == Role.ADMIN) {
            val all = memberDao.getActiveMembersByGroup(target.groupId).first()
            val admins = all.filter { it.role == Role.ADMIN }
            if (admins.size <= 1) {
                return Result.failure(IllegalStateException("El último Admin no puede retirarse sin nombrar antes a otro Admin"))
            }
        }
        memberDao.updateMember(target.copy(active = false))
        return Result.success(Unit)
    }

    suspend fun updateMemberShare(memberId: String, newShareCentecimas: Int, currentUser: Member): Result<Unit> {
        if (currentUser.role != Role.ADMIN) {
            return Result.failure(IllegalAccessException("Solo un Admin puede configurar los porcentajes"))
        }
        val target = memberDao.getMemberById(memberId) ?: return Result.failure(IllegalArgumentException("Miembro no encontrado"))
        memberDao.updateMember(target.copy(sharePercentCentecimas = newShareCentecimas))
        return Result.success(Unit)
    }

    // --- Invitations ---

    suspend fun createInvitation(
        groupId: String,
        currentUser: Member,
        role: Role = Role.SOCIO,
        hoursValid: Int = 48,
        maxUses: Int = 1
    ): Result<Invitation> {
        if (currentUser.role != Role.ADMIN) {
            return Result.failure(IllegalAccessException("Solo un Admin puede generar invitaciones"))
        }
        val code = generateInviteCode()
        val invitation = Invitation(
            groupId = groupId,
            code = code,
            role = role,
            expiresAt = System.currentTimeMillis() + (hoursValid * 3600_000L),
            maxUses = maxUses,
            usedCount = 0,
            isRevoked = false
        )
        invitationDao.insertInvitation(invitation)
        return Result.success(invitation)
    }

    suspend fun useInvitation(code: String, userName: String, userEmail: String): Result<Member> {
        val inv = invitationDao.getInvitationByCode(code.trim().uppercase())
            ?: return Result.failure(IllegalArgumentException("Código de invitación no válido"))

        if (inv.isRevoked) {
            return Result.failure(IllegalStateException("Esta invitación ha sido revocada"))
        }
        if (System.currentTimeMillis() > inv.expiresAt) {
            return Result.failure(IllegalStateException("Esta invitación ya caducó"))
        }
        if (inv.usedCount >= inv.maxUses) {
            return Result.failure(IllegalStateException("Esta invitación ya fue utilizada"))
        }

        // Add member
        val newMember = Member(
            groupId = inv.groupId,
            name = userName,
            email = userEmail,
            role = inv.role,
            active = true,
            sharePercentCentecimas = 2000 // default 20%
        )
        memberDao.insertMember(newMember)

        // Increment usage
        invitationDao.updateInvitation(inv.copy(usedCount = inv.usedCount + 1))
        return Result.success(newMember)
    }

    private fun generateInviteCode(): String {
        val chars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ" // excludes 0, O, 1, I
        val random = SecureRandom()
        return (1..8).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }

    // --- Accounts ---

    suspend fun addTransferAccount(groupId: String, name: String, currentUser: Member): Result<TransferAccount> {
        if (currentUser.role != Role.ADMIN) {
            return Result.failure(IllegalAccessException("Solo un Admin puede administrar cuentas"))
        }
        val acc = TransferAccount(groupId = groupId, name = name.trim())
        transferAccountDao.insertAccount(acc)
        return Result.success(acc)
    }

    // --- Seed Initial Demo Data ---
    suspend fun seedInitialDataIfEmpty() {
        val now = System.currentTimeMillis()

        // --- Empresa 1: Café & Empanadas La Esquina ---
        val groupId = "group_cafe_esquina"
        if (groupDao.getGroupById(groupId) == null || productDao.getProductsByGroup(groupId).first().isEmpty()) {
            val group = Group(
                id = groupId,
                name = "Café & Empanadas La Esquina",
                createdAt = now - 30L * 24 * 3600 * 1000L
            )
            groupDao.insertGroup(group)

            // Members
            // Andrés (Admin) 40%, Carlos (Socio) 40%, Valentina (Socio) 0%, Empresa 20%
            val andres = Member(
                id = "user_andres_admin",
                groupId = groupId,
                name = "Andrés",
                email = "andres@laesquina.co",
                role = Role.ADMIN,
                active = true,
                sharePercentCentecimas = 4000,
                initialDebtCop = 0L
            )
            val carlos = Member(
                id = "user_carlos_socio",
                groupId = groupId,
                name = "Carlos",
                email = "carlos@laesquina.co",
                role = Role.SOCIO,
                active = true,
                sharePercentCentecimas = 4000,
                initialDebtCop = 500_000L // Carried debt of $500.000
            )
            val valentina = Member(
                id = "user_valentina_socio",
                groupId = groupId,
                name = "Valentina",
                email = "valentina@laesquina.co",
                role = Role.SOCIO,
                active = true,
                sharePercentCentecimas = 0,
                initialDebtCop = 0L
            )
            memberDao.insertMembers(listOf(andres, carlos, valentina))

            // Accounts
            val accounts = listOf("Nequi", "Daviplata", "Bancolombia", "Otra")
            for (acc in accounts) {
                transferAccountDao.insertAccount(TransferAccount(groupId = groupId, name = acc))
            }

            // Products
            val products = listOf(
                Product(id = "p_cafe", groupId = groupId, name = "Café Especial (Libra)", category = "Bebidas", photoUri = "cafe", standardPriceCop = 30_000L),
                Product(id = "p_empanada", groupId = groupId, name = "Empanada Tradicional", category = "Comidas", photoUri = "comida", standardPriceCop = 4_000L),
                Product(id = "p_jugo", groupId = groupId, name = "Jugo Natural 16oz", category = "Bebidas", photoUri = "bebida", standardPriceCop = 7_000L),
                Product(id = "p_sandwich", groupId = groupId, name = "Sandwich Gourmet", category = "Comidas", photoUri = "panaderia", standardPriceCop = 18_000L)
            )
            for (p in products) {
                productDao.insertProduct(p)
            }

            // Seed movements matching Test Case 1 in current month:
            val saleLine1 = MovementLine("p_cafe", "Café Especial (Libra)", 100, 3_000_000L, 30_000L)
            val saleLine2 = MovementLine("p_empanada", "Empanada Tradicional", 500, 2_000_000L, 4_000L)
            val sale = Movement(
                groupId = groupId,
                orderCode = "#V-1001",
                type = MovementType.VENTA,
                linesJson = JsonHelper.serializeLines(listOf(saleLine1, saleLine2)),
                totalCop = 5_000_000L,
                paymentMethod = PaymentMethod.EFECTIVO,
                occurredAt = now - 3600_000L * 4,
                recordedAt = now - 3600_000L * 4,
                registeredByUserId = andres.id,
                registeredByName = andres.name,
                note = "Ventas de eventos y salón"
            )
            movementDao.insertMovement(sale)

            val purchaseLine = MovementLine("p_cafe", "Café Especial (Materia prima)", 200, 2_000_000L, 10_000L)
            val purchase = Movement(
                groupId = groupId,
                orderCode = "#C-1002",
                type = MovementType.COMPRA,
                linesJson = JsonHelper.serializeLines(listOf(purchaseLine)),
                totalCop = 2_000_000L,
                paymentMethod = PaymentMethod.TRANSFERENCIA,
                transferAccountName = "Bancolombia",
                occurredAt = now - 3600_000L * 3,
                recordedAt = now - 3600_000L * 3,
                registeredByUserId = andres.id,
                registeredByName = andres.name,
                note = "Compra de bultos de café verde"
            )
            movementDao.insertMovement(purchase)

            val loan = Movement(
                groupId = groupId,
                orderCode = "#P-1003",
                type = MovementType.PRESTAMO,
                linesJson = "[]",
                totalCop = 1_000_000L,
                paymentMethod = PaymentMethod.TRANSFERENCIA,
                transferAccountName = "Nequi",
                occurredAt = now - 3600_000L * 2,
                recordedAt = now - 3600_000L * 2,
                registeredByUserId = carlos.id,
                registeredByName = carlos.name,
                debtorMemberId = carlos.id,
                debtorMemberName = carlos.name,
                note = "Retiro para gastos personales"
            )
            movementDao.insertMovement(loan)

            // Seed initial transactions for Room Database (Ventas, Compras, Préstamos)
            transactionDao.insertTransaction(
                Transaction(
                    id = sale.id,
                    groupId = groupId,
                    type = TransactionType.VENTA,
                    amount = 5_000_000.0,
                    description = "Ventas de eventos y salón",
                    date = sale.occurredAt,
                    paymentMethod = PaymentMethod.EFECTIVO
                )
            )
            transactionDao.insertTransaction(
                Transaction(
                    id = purchase.id,
                    groupId = groupId,
                    type = TransactionType.COMPRA,
                    amount = 2_000_000.0,
                    description = "Compra de bultos de café verde",
                    date = purchase.occurredAt,
                    paymentMethod = PaymentMethod.TRANSFERENCIA
                )
            )
            transactionDao.insertTransaction(
                Transaction(
                    id = loan.id,
                    groupId = groupId,
                    type = TransactionType.PRESTAMO,
                    amount = 1_000_000.0,
                    description = "Retiro para gastos personales",
                    date = loan.occurredAt,
                    paymentMethod = PaymentMethod.TRANSFERENCIA,
                    customerOrDebtor = carlos.name
                )
            )

            // Notification for Group 1
            notificationDao.insertNotification(
                NotificationItem(
                    groupId = groupId,
                    title = "Bienvenido a SocioFinanzas",
                    message = "Cuentas claras para socios de negocio en pesos colombianos (COP).",
                    timestamp = now
                )
            )
        }

        // --- Empresa 2: Panadería & Pastelería San Juan ---
        val group2Id = "group_panaderia_sanjuan"
        if (groupDao.getGroupById(group2Id) == null || productDao.getProductsByGroup(group2Id).first().isEmpty()) {
            val group2 = Group(
                id = group2Id,
                name = "Panadería & Pastelería San Juan",
                createdAt = now - 20L * 24 * 3600 * 1000L
            )
            groupDao.insertGroup(group2)

            val andres2 = Member(
                id = "user_andres_admin_g2",
                groupId = group2Id,
                name = "Andrés",
                email = "andres@sanjuan.co",
                role = Role.ADMIN,
                active = true,
                sharePercentCentecimas = 5000,
                initialDebtCop = 0L
            )
            val mateo = Member(
                id = "user_mateo_socio_g2",
                groupId = group2Id,
                name = "Mateo",
                email = "mateo@sanjuan.co",
                role = Role.SOCIO,
                active = true,
                sharePercentCentecimas = 3000,
                initialDebtCop = 0L
            )
            val lucia = Member(
                id = "user_lucia_socio_g2",
                groupId = group2Id,
                name = "Lucía",
                email = "lucia@sanjuan.co",
                role = Role.SOCIO,
                active = true,
                sharePercentCentecimas = 2000,
                initialDebtCop = 0L
            )
            memberDao.insertMembers(listOf(andres2, mateo, lucia))

            for (acc in listOf("Bancolombia", "Nequi", "Daviplata", "Caja Efectivo")) {
                transferAccountDao.insertAccount(TransferAccount(groupId = group2Id, name = acc))
            }

            val products2 = listOf(
                Product(id = "p_pan_artesanal", groupId = group2Id, name = "Pan Artesanal de Masa Madre", category = "Panadería", photoUri = "panaderia", standardPriceCop = 6_500L),
                Product(id = "p_croissant", groupId = group2Id, name = "Croissant de Mantequilla", category = "Panadería", photoUri = "panaderia", standardPriceCop = 5_000L),
                Product(id = "p_torta_choco", groupId = group2Id, name = "Torta de Chocolate 8 Porciones", category = "Pastelería", photoUri = "postre", standardPriceCop = 42_000L),
                Product(id = "p_latte", groupId = group2Id, name = "Café Latte Vainilla", category = "Bebidas", photoUri = "cafe", standardPriceCop = 8_500L)
            )
            for (p in products2) {
                productDao.insertProduct(p)
            }

            val sale2Line = MovementLine("p_pan_artesanal", "Pan Artesanal de Masa Madre", 200, 1_300_000L, 6_500L)
            val sale2 = Movement(
                groupId = group2Id,
                orderCode = "#V-2001",
                type = MovementType.VENTA,
                linesJson = JsonHelper.serializeLines(listOf(sale2Line)),
                totalCop = 1_300_000L,
                paymentMethod = PaymentMethod.TRANSFERENCIA,
                transferAccountName = "Bancolombia",
                occurredAt = now - 3600_000L * 6,
                recordedAt = now - 3600_000L * 6,
                registeredByUserId = andres2.id,
                registeredByName = andres2.name,
                note = "Despacho a cafeterías aliadas"
            )
            movementDao.insertMovement(sale2)

            val purchase2Line = MovementLine("p_harina", "Bulto de Harina de Trigo Especial", 10, 450_000L, 45_000L)
            val purchase2 = Movement(
                groupId = group2Id,
                orderCode = "#C-2002",
                type = MovementType.COMPRA,
                linesJson = JsonHelper.serializeLines(listOf(purchase2Line)),
                totalCop = 450_000L,
                paymentMethod = PaymentMethod.EFECTIVO,
                occurredAt = now - 3600_000L * 5,
                recordedAt = now - 3600_000L * 5,
                registeredByUserId = andres2.id,
                registeredByName = andres2.name,
                note = "Insumos molino central"
            )
            movementDao.insertMovement(purchase2)

            val loan2 = Movement(
                groupId = group2Id,
                orderCode = "#P-2003",
                type = MovementType.PRESTAMO,
                linesJson = "[]",
                totalCop = 200_000L,
                paymentMethod = PaymentMethod.TRANSFERENCIA,
                transferAccountName = "Nequi",
                occurredAt = now - 3600_000L * 2,
                recordedAt = now - 3600_000L * 2,
                registeredByUserId = mateo.id,
                registeredByName = mateo.name,
                debtorMemberId = mateo.id,
                debtorMemberName = mateo.name,
                note = "Adelanto personal Mateo"
            )
            movementDao.insertMovement(loan2)

            transactionDao.insertTransaction(
                Transaction(
                    id = sale2.id,
                    groupId = group2Id,
                    type = TransactionType.VENTA,
                    amount = 1_300_000.0,
                    description = "Despacho a cafeterías aliadas",
                    date = sale2.occurredAt,
                    paymentMethod = PaymentMethod.TRANSFERENCIA
                )
            )
            transactionDao.insertTransaction(
                Transaction(
                    id = purchase2.id,
                    groupId = group2Id,
                    type = TransactionType.COMPRA,
                    amount = 450_000.0,
                    description = "Insumos molino central",
                    date = purchase2.occurredAt,
                    paymentMethod = PaymentMethod.EFECTIVO
                )
            )
            transactionDao.insertTransaction(
                Transaction(
                    id = loan2.id,
                    groupId = group2Id,
                    type = TransactionType.PRESTAMO,
                    amount = 200_000.0,
                    description = "Adelanto personal Mateo",
                    date = loan2.occurredAt,
                    paymentMethod = PaymentMethod.TRANSFERENCIA,
                    customerOrDebtor = mateo.name
                )
            )

            notificationDao.insertNotification(
                NotificationItem(
                    groupId = group2Id,
                    title = "Panadería San Juan lista",
                    message = "Perfil de empresa activo con su propio catálogo y finanzas.",
                    timestamp = now
                )
            )
        }
    }

    suspend fun updateAdminGoogleAccount(groupId: String, googleUid: String, displayName: String, email: String): Member {
        val members = memberDao.getMembersByGroup(groupId).first()
        val currentAdmin = members.firstOrNull { it.role == Role.ADMIN }
        val finalName = if (displayName.isNotBlank()) displayName else (email.substringBefore("@").replaceFirstChar { it.uppercase() })
        val finalEmail = if (email.isNotBlank()) email else "admin@google.com"

        return if (currentAdmin != null) {
            val updated = currentAdmin.copy(
                name = finalName,
                email = finalEmail
            )
            memberDao.updateMember(updated)
            updated
        } else {
            val newMember = Member(
                id = "user_google_$googleUid",
                groupId = groupId,
                name = finalName,
                email = finalEmail,
                role = Role.ADMIN,
                active = true,
                sharePercentCentecimas = 4000,
                initialDebtCop = 0L
            )
            memberDao.insertMember(newMember)
            newMember
        }
    }
}
