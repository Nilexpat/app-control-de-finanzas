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
        settlementMode: String = "DESEMBOLSO",
        creatorGoogleUid: String = ""
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
            initialDebtCop = 0L,
            googleUid = creatorGoogleUid
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
        // Se permite eliminar la ultima empresa: la app queda vacia y el usuario
        // puede crear una nueva. Los movimientos de esa empresa se borran con ella.
        groupDao.deleteMembersByGroup(groupId)
        groupDao.deleteProductsByGroup(groupId)
        groupDao.deleteMovementsByGroup(groupId)
        groupDao.deleteAccountsByGroup(groupId)
        groupDao.deleteSettlementsByGroup(groupId)
        groupDao.deleteNotificationsByGroup(groupId)
        groupDao.deleteMovementAuditsByGroup(groupId)
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

    suspend fun getMemberById(memberId: String): Member? = memberDao.getMemberById(memberId)

    suspend fun getMemberByGoogleUidAnywhere(googleUid: String): Member? =
        memberDao.getMemberByGoogleUidAnywhere(googleUid)

    /** Ficha de identidad sin grupo (groupId vacio) de esa cuenta de Google. */
    suspend fun getProfileMember(googleUid: String): Member? =
        memberDao.getProfileMemberByGoogleUid(googleUid)

    suspend fun getMemberByGoogleUidInGroup(googleUid: String, groupId: String): Member? =
        memberDao.getMemberByGoogleUidInGroup(googleUid, groupId)

    suspend fun useInvitation(
        code: String,
        userName: String,
        userEmail: String,
        googleUid: String = ""
    ): Result<Member> {
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

        // Si esta cuenta ya pertenece al grupo, se devuelve su ficha existente
        if (googleUid.isNotBlank()) {
            memberDao.getMemberByGoogleUidInGroup(googleUid, inv.groupId)?.let { return Result.success(it) }
        }

        // El nombre es opcional: si no se indica, se usa el correo o un generico
        val resolvedName = userName.ifBlank {
            userEmail.substringBefore("@").ifBlank { "Socio" }
                .replaceFirstChar { it.uppercase() }
        }
        val resolvedEmail = userEmail.ifBlank { "socio@google.com" }

        val newMember = Member(
            groupId = inv.groupId,
            name = resolvedName,
            email = resolvedEmail,
            role = inv.role,
            active = true,
            sharePercentCentecimas = 2000, // 20% por defecto
            googleUid = googleUid
        )
        memberDao.insertMember(newMember)

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


    /**
     * Vincula una cuenta de Google con el miembro correcto DENTRO de un grupo.
     *
     * Reglas (en orden):
     *  1. Si ya existe un miembro con ese googleUid en el grupo -> ese es el usuario.
     *  2. Si no, y existe un miembro sin googleUid con el mismo correo -> se vincula.
     *  3. Si no, y el grupo no tiene ningun Admin -> este usuario pasa a ser Admin.
     *  4. En cualquier otro caso -> se crea un nuevo SOCIO.
     *
     * Nunca sobrescribe el nombre ni el correo de otro miembro.
     */
    suspend fun linkGoogleAccountToGroup(
        groupId: String,
        googleUid: String,
        displayName: String,
        email: String
    ): Member {
        val members = memberDao.getMembersByGroup(groupId).first()
        val finalName = if (displayName.isNotBlank()) displayName
            else email.substringBefore("@").replaceFirstChar { it.uppercase() }
        val finalEmail = email.ifBlank { "usuario@google.com" }

        // 1. Ya vinculado por UID
        members.firstOrNull { it.googleUid.isNotBlank() && it.googleUid == googleUid }
            ?.let { return it }

        // 2. Mismo correo, aun sin vincular (migracion de datos antiguos)
        members.firstOrNull { it.googleUid.isBlank()
            && it.email.isNotBlank()
            && it.email.equals(email, ignoreCase = true) }
            ?.let { matched ->
                val updated = matched.copy(googleUid = googleUid)
                memberDao.updateMember(updated)
                return updated
            }

        // 3. Grupo sin administrador -> el primero en entrar administra
        if (members.none { it.role == Role.ADMIN }) {
            val newAdmin = Member(
                id = "user_" + UUID.randomUUID().toString().take(8),
                groupId = groupId,
                name = finalName,
                email = finalEmail,
                role = Role.ADMIN,
                active = true,
                sharePercentCentecimas = 5000,
                initialDebtCop = 0L,
                googleUid = googleUid
            )
            memberDao.insertMember(newAdmin)
            return newAdmin
        }

        // 4. Nuevo socio
        val newMember = Member(
            id = "user_" + UUID.randomUUID().toString().take(8),
            groupId = groupId,
            name = finalName,
            email = finalEmail,
            role = Role.SOCIO,
            active = true,
            sharePercentCentecimas = 0,
            initialDebtCop = 0L,
            googleUid = googleUid
        )
        memberDao.insertMember(newMember)
        return newMember
    }

    /**
     * Resuelve la identidad global de la cuenta de Google (sin grupo asociado).
     * Devuelve null si el usuario aun no pertenece a ninguna empresa.
     */
    suspend fun bindAuthenticatedUser(
        googleUid: String,
        displayName: String,
        email: String
    ): Member? {
        // Buscar una fila perfil (groupId vacio) ya creada para este UID
        val profile = memberDao.getProfileMemberByGoogleUid(googleUid)
        if (profile != null) return profile

        // Si existe como miembro de algun grupo, ya tiene identidad: no creamos perfil
        val inGroup = memberDao.getMemberByGoogleUidAnywhere(googleUid)
        if (inGroup != null) return inGroup

        // Crear fila perfil para que el usuario pueda navegar y crear su empresa
        val finalName = if (displayName.isNotBlank()) displayName
            else email.substringBefore("@").replaceFirstChar { it.uppercase() }
        val created = Member(
            id = "profile_" + googleUid,
            groupId = "",
            name = finalName,
            email = email.ifBlank { "usuario@google.com" },
            role = Role.ADMIN,
            active = true,
            sharePercentCentecimas = 0,
            initialDebtCop = 0L,
            googleUid = googleUid
        )
        memberDao.insertMember(created)
        return created
    }
}
