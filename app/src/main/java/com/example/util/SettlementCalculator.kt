package com.example.util

import com.example.data.model.*

object SettlementCalculator {

    /**
     * Deuda con la que cada socio ENTRA a un corte.
     *
     * Un prestamo es una deuda personal de quien lo pidio, no una perdida de la
     * empresa: sigue debiendose hasta que se paga, sea con abonos o con la parte
     * de utilidades que se le descuenta en un reparto. Por eso la deuda pasa de
     * un corte al siguiente:
     *
     *   deuda inicial + prestamos anteriores - abonos anteriores
     *   - lo que ya se le desconto en cortes cerrados anteriores
     *
     * @param movementsBefore movimientos de ANTES del corte (los anulados se ignoran).
     * @param discountsApplied descuentos por deuda ya aplicados, por socio.
     */
    fun carriedDebts(
        members: List<Member>,
        movementsBefore: List<Movement>,
        discountsApplied: Map<String, Long>
    ): Map<String, Long> {
        val balance = mutableMapOf<String, Long>()
        for (m in movementsBefore) {
            if (m.isAnnulled) continue
            val debtorId = m.debtorMemberId ?: m.registeredByUserId
            when (m.type) {
                MovementType.PRESTAMO -> balance[debtorId] = (balance[debtorId] ?: 0L) + m.totalCop
                MovementType.ABONO -> balance[debtorId] = (balance[debtorId] ?: 0L) - m.totalCop
                else -> Unit
            }
        }
        return members.associate { member ->
            val debt = member.initialDebtCop +
                (balance[member.id] ?: 0L) -
                (discountsApplied[member.id] ?: 0L)
            member.id to debt.coerceAtLeast(0L)
        }
    }

    /**
     * Calculates the entire settlement for a set of movements and member configurations.
     * All numbers are Long integers (COP pesos).
     */
    fun calculateSettlement(
        monthKey: String,
        movements: List<Movement>,
        members: List<Member>,
        companyShareCentecimas: Int,
        isClosed: Boolean = false,
        closedAt: Long? = null,
        closedByName: String? = null,
        historicalDebtsByMember: Map<String, Long> = emptyMap(),
        periodStartAt: Long = 0L,
        periodEndAt: Long = 0L,
        isDisbursementMode: Boolean = false,
        canCreateNewPeriod: Boolean = false
    ): SettlementSummary {
        // Filter out annulled movements
        val activeMovements = movements.filter { !it.isAnnulled }

        var totalSales = 0L
        var totalPurchases = 0L
        var totalLoans = 0L
        var totalAbonos = 0L

        // Breakdown by payment method
        var cashSales = 0L
        var cashPurchases = 0L
        var cashLoans = 0L
        var cashAbonos = 0L

        var transferSales = 0L
        var transferPurchases = 0L
        var transferLoans = 0L
        var transferAbonos = 0L

        val subAccountMap = mutableMapOf<String, MutableMap<MovementType, Long>>()

        // Loans and abonos grouped by member
        val loansByMember = mutableMapOf<String, Long>()
        val abonosByMember = mutableMapOf<String, Long>()

        for (m in activeMovements) {
            when (m.type) {
                MovementType.VENTA -> {
                    totalSales += m.totalCop
                    if (m.paymentMethod == PaymentMethod.EFECTIVO) {
                        cashSales += m.totalCop
                    } else {
                        transferSales += m.totalCop
                        val acc = m.transferAccountName?.takeIf { it.isNotBlank() } ?: "General"
                        val map = subAccountMap.getOrPut(acc) { mutableMapOf() }
                        map[MovementType.VENTA] = (map[MovementType.VENTA] ?: 0L) + m.totalCop
                    }
                }
                MovementType.COMPRA -> {
                    totalPurchases += m.totalCop
                    if (m.paymentMethod == PaymentMethod.EFECTIVO) {
                        cashPurchases += m.totalCop
                    } else {
                        transferPurchases += m.totalCop
                        val acc = m.transferAccountName?.takeIf { it.isNotBlank() } ?: "General"
                        val map = subAccountMap.getOrPut(acc) { mutableMapOf() }
                        map[MovementType.COMPRA] = (map[MovementType.COMPRA] ?: 0L) + m.totalCop
                    }
                }
                MovementType.PRESTAMO -> {
                    totalLoans += m.totalCop
                    val debtorId = m.debtorMemberId ?: m.registeredByUserId
                    loansByMember[debtorId] = (loansByMember[debtorId] ?: 0L) + m.totalCop

                    if (m.paymentMethod == PaymentMethod.EFECTIVO) {
                        cashLoans += m.totalCop
                    } else {
                        transferLoans += m.totalCop
                        val acc = m.transferAccountName?.takeIf { it.isNotBlank() } ?: "General"
                        val map = subAccountMap.getOrPut(acc) { mutableMapOf() }
                        map[MovementType.PRESTAMO] = (map[MovementType.PRESTAMO] ?: 0L) + m.totalCop
                    }
                }
                MovementType.ABONO -> {
                    totalAbonos += m.totalCop
                    val debtorId = m.debtorMemberId ?: m.registeredByUserId
                    abonosByMember[debtorId] = (abonosByMember[debtorId] ?: 0L) + m.totalCop

                    if (m.paymentMethod == PaymentMethod.EFECTIVO) {
                        cashAbonos += m.totalCop
                    } else {
                        transferAbonos += m.totalCop
                        val acc = m.transferAccountName?.takeIf { it.isNotBlank() } ?: "General"
                        val map = subAccountMap.getOrPut(acc) { mutableMapOf() }
                        map[MovementType.ABONO] = (map[MovementType.ABONO] ?: 0L) + m.totalCop
                    }
                }
            }
        }

        // 1. U = ventas − compras del corte. Si U <= 0, distributable es 0
        val utility = totalSales - totalPurchases
        val distributable = if (utility > 0) utility else 0L

        // 2. Distributions
        // Calculate raw parts
        val distributionRows = mutableListOf<PartnerDistributionRow>()

        // Company first
        var sumOfParts = 0L
        val activePartners = members.filter { it.active }

        // Company initial share
        val companyRawPart = if (distributable > 0) {
            (distributable * companyShareCentecimas) / 10000L
        } else {
            0L
        }
        sumOfParts += companyRawPart

        // Partner shares
        val partnerRawParts = mutableListOf<Long>()
        for (p in activePartners) {
            val part = if (distributable > 0) {
                (distributable * p.sharePercentCentecimas) / 10000L
            } else {
                0L
            }
            partnerRawParts.add(part)
            sumOfParts += part
        }

        // Remainder goes to Empresa so parts strictly sum to distributable
        val remainder = if (distributable > 0) (distributable - sumOfParts) else 0L
        val companyFinalPart = companyRawPart + remainder

        // Add company row
        distributionRows.add(
            PartnerDistributionRow(
                recipientName = "Empresa",
                isCompany = true,
                memberId = null,
                percentageCentecimas = companyShareCentecimas,
                shareAmountCop = companyFinalPart,
                debtCop = 0L,
                discountCop = 0L,
                finalPaymentCop = companyFinalPart,
                remainingDebtCop = 0L
            )
        )

        // Add partner rows
        for (i in activePartners.indices) {
            val partner = activePartners[i]
            val share = partnerRawParts[i]

            // deuda_i = deuda arrastrada del corte anterior + préstamos del corte - abonos del corte
            val carriedDebt = historicalDebtsByMember[partner.id] ?: partner.initialDebtCop
            val currentLoans = loansByMember[partner.id] ?: 0L
            val currentAbonos = abonosByMember[partner.id] ?: 0L
            val totalDebt = (carriedDebt + currentLoans - currentAbonos).coerceAtLeast(0L)

            val discount = if (distributable > 0) minOf(share, totalDebt) else 0L
            val finalPayment = share - discount
            val remainingDebt = totalDebt - discount

            distributionRows.add(
                PartnerDistributionRow(
                    recipientName = partner.name,
                    isCompany = false,
                    memberId = partner.id,
                    percentageCentecimas = partner.sharePercentCentecimas,
                    shareAmountCop = share,
                    debtCop = totalDebt,
                    discountCop = discount,
                    finalPaymentCop = finalPayment,
                    remainingDebtCop = remainingDebt
                )
            )
        }

        // 3. Dónde está la plata
        val moneyLocations = mutableListOf<MoneyLocationRow>()

        // Efectivo
        val cashNet = cashSales - cashPurchases - cashLoans + cashAbonos
        moneyLocations.add(
            MoneyLocationRow(
                name = "Efectivo",
                isSubRow = false,
                paymentMethod = PaymentMethod.EFECTIVO,
                salesCop = cashSales,
                purchasesCop = cashPurchases,
                loansCop = cashLoans,
                abonosCop = cashAbonos,
                netCop = cashNet
            )
        )

        // Transferencia total
        val transferNet = transferSales - transferPurchases - transferLoans + transferAbonos
        moneyLocations.add(
            MoneyLocationRow(
                name = "Transferencia",
                isSubRow = false,
                paymentMethod = PaymentMethod.TRANSFERENCIA,
                salesCop = transferSales,
                purchasesCop = transferPurchases,
                loansCop = transferLoans,
                abonosCop = transferAbonos,
                netCop = transferNet
            )
        )

        // Breakdown by sub-accounts
        for ((accName, amounts) in subAccountMap.toSortedMap()) {
            val sSales = amounts[MovementType.VENTA] ?: 0L
            val sPurchases = amounts[MovementType.COMPRA] ?: 0L
            val sLoans = amounts[MovementType.PRESTAMO] ?: 0L
            val sAbonos = amounts[MovementType.ABONO] ?: 0L
            val sNet = sSales - sPurchases - sLoans + sAbonos

            moneyLocations.add(
                MoneyLocationRow(
                    name = "↳ $accName",
                    isSubRow = true,
                    paymentMethod = PaymentMethod.TRANSFERENCIA,
                    salesCop = sSales,
                    purchasesCop = sPurchases,
                    loansCop = sLoans,
                    abonosCop = sAbonos,
                    netCop = sNet
                )
            )
        }

        return SettlementSummary(
            monthKey = monthKey,
            monthTitle = if (isDisbursementMode) {
                if (periodStartAt > 0) "Período desde ${DateFormatter.formatShortDate(periodStartAt)}" else "Período Actual"
            } else {
                DateFormatter.formatMonthYear(monthKey)
            },
            isClosed = isClosed,
            closedAt = closedAt,
            closedByName = closedByName,
            totalSalesCop = totalSales,
            totalPurchasesCop = totalPurchases,
            utilityCop = utility,
            distributableUtilityCop = distributable,
            totalLoansCop = totalLoans,
            totalAbonosCop = totalAbonos,
            distributions = distributionRows,
            moneyLocations = moneyLocations,
            movementsCount = activeMovements.size,
            periodStartAt = periodStartAt,
            periodEndAt = periodEndAt,
            isDisbursementMode = isDisbursementMode,
            canCreateNewPeriod = canCreateNewPeriod
        )
    }
}
