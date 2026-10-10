package com.example.data.model

import com.example.util.CurrencyFormatter

data class PartnerDistributionRow(
    val recipientName: String,
    val isCompany: Boolean,
    val memberId: String? = null,
    val percentageCentecimas: Int,
    val percentageFormatted: String = CurrencyFormatter.formatPercentage(percentageCentecimas),
    val shareAmountCop: Long,      // parte_i = floor(U * p_i / 10000) (+ residuo for company)
    val debtCop: Long,             // deuda arrastrada + préstamos - abonos
    val discountCop: Long,         // min(shareAmountCop, debtCop)
    val finalPaymentCop: Long,     // shareAmountCop - discountCop
    val remainingDebtCop: Long     // debtCop - discountCop (passes to next cut)
)

data class MoneyLocationRow(
    val name: String,
    val isSubRow: Boolean = false,
    val paymentMethod: PaymentMethod? = null,
    val salesCop: Long = 0L,
    val purchasesCop: Long = 0L,
    val loansCop: Long = 0L,
    val abonosCop: Long = 0L,
    val netCop: Long = 0L          // sales - purchases - loans + abonos
)

data class SettlementSummary(
    val monthKey: String,
    val monthTitle: String,
    val isClosed: Boolean,
    val closedAt: Long? = null,
    val closedByName: String? = null,
    val totalSalesCop: Long,
    val totalPurchasesCop: Long,
    val utilityCop: Long,                 // Ventas - Compras
    val distributableUtilityCop: Long,    // Max(0, utilityCop)
    val totalLoansCop: Long,
    val totalAbonosCop: Long,
    val distributions: List<PartnerDistributionRow>,
    val moneyLocations: List<MoneyLocationRow>,
    val movementsCount: Int,
    val periodStartAt: Long = 0L,
    val periodEndAt: Long = 0L,
    val isDisbursementMode: Boolean = false,
    val canCreateNewPeriod: Boolean = false
)
