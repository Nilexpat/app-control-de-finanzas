package com.example.util

import com.example.data.model.MovementLine
import com.example.data.model.PartnerDistributionRow
import com.example.data.model.MoneyLocationRow
import com.example.data.model.SettlementSummary
import org.json.JSONArray
import org.json.JSONObject

object JsonHelper {

    fun serializeLines(lines: List<MovementLine>): String {
        val array = JSONArray()
        for (line in lines) {
            val obj = JSONObject()
            obj.put("productId", line.productId)
            obj.put("productName", line.productName)
            obj.put("quantity", line.quantity)
            obj.put("subtotalCop", line.subtotalCop)
            obj.put("unitPriceCop", line.unitPriceCop)
            array.put(obj)
        }
        return array.toString()
    }

    fun deserializeLines(json: String?): List<MovementLine> {
        if (json.isNullOrBlank()) return emptyList()
        val list = mutableListOf<MovementLine>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val line = MovementLine(
                    productId = obj.optString("productId"),
                    productName = obj.optString("productName"),
                    quantity = obj.optInt("quantity", 1),
                    subtotalCop = obj.optLong("subtotalCop", 0L),
                    unitPriceCop = obj.optLong("unitPriceCop", 0L)
                )
                list.add(line)
            }
        } catch (_: Exception) {
            // ignore
        }
        return list
    }

    fun serializeSettlementSummary(summary: SettlementSummary): String {
        val root = JSONObject()
        root.put("monthKey", summary.monthKey)
        root.put("monthTitle", summary.monthTitle)
        root.put("isClosed", summary.isClosed)
        root.put("closedAt", summary.closedAt ?: 0L)
        root.put("closedByName", summary.closedByName ?: "")
        root.put("totalSalesCop", summary.totalSalesCop)
        root.put("totalPurchasesCop", summary.totalPurchasesCop)
        root.put("utilityCop", summary.utilityCop)
        root.put("distributableUtilityCop", summary.distributableUtilityCop)
        root.put("totalLoansCop", summary.totalLoansCop)
        root.put("totalAbonosCop", summary.totalAbonosCop)
        root.put("movementsCount", summary.movementsCount)

        val distArray = JSONArray()
        for (d in summary.distributions) {
            val obj = JSONObject()
            obj.put("recipientName", d.recipientName)
            obj.put("isCompany", d.isCompany)
            obj.put("memberId", d.memberId ?: "")
            obj.put("percentageCentecimas", d.percentageCentecimas)
            obj.put("shareAmountCop", d.shareAmountCop)
            obj.put("debtCop", d.debtCop)
            obj.put("discountCop", d.discountCop)
            obj.put("finalPaymentCop", d.finalPaymentCop)
            obj.put("remainingDebtCop", d.remainingDebtCop)
            distArray.put(obj)
        }
        root.put("distributions", distArray)

        val locArray = JSONArray()
        for (m in summary.moneyLocations) {
            val obj = JSONObject()
            obj.put("name", m.name)
            obj.put("isSubRow", m.isSubRow)
            obj.put("salesCop", m.salesCop)
            obj.put("purchasesCop", m.purchasesCop)
            obj.put("loansCop", m.loansCop)
            obj.put("abonosCop", m.abonosCop)
            obj.put("netCop", m.netCop)
            locArray.put(obj)
        }
        root.put("moneyLocations", locArray)

        return root.toString()
    }

    fun deserializeSettlementSummary(json: String?): SettlementSummary? {
        if (json.isNullOrBlank()) return null
        return try {
            val root = JSONObject(json)
            val monthKey = root.optString("monthKey")
            val monthTitle = root.optString("monthTitle")
            val isClosed = root.optBoolean("isClosed")
            val closedAt = if (root.has("closedAt") && root.getLong("closedAt") > 0) root.getLong("closedAt") else null
            val closedByName = root.optString("closedByName").takeIf { it.isNotEmpty() }
            val totalSalesCop = root.optLong("totalSalesCop")
            val totalPurchasesCop = root.optLong("totalPurchasesCop")
            val utilityCop = root.optLong("utilityCop")
            val distributableUtilityCop = root.optLong("distributableUtilityCop")
            val totalLoansCop = root.optLong("totalLoansCop")
            val totalAbonosCop = root.optLong("totalAbonosCop")
            val movementsCount = root.optInt("movementsCount")

            val distributions = mutableListOf<PartnerDistributionRow>()
            val distArray = root.optJSONArray("distributions")
            if (distArray != null) {
                for (i in 0 until distArray.length()) {
                    val obj = distArray.getJSONObject(i)
                    distributions.add(
                        PartnerDistributionRow(
                            recipientName = obj.optString("recipientName"),
                            isCompany = obj.optBoolean("isCompany"),
                            memberId = obj.optString("memberId").takeIf { it.isNotEmpty() },
                            percentageCentecimas = obj.optInt("percentageCentecimas"),
                            shareAmountCop = obj.optLong("shareAmountCop"),
                            debtCop = obj.optLong("debtCop"),
                            discountCop = obj.optLong("discountCop"),
                            finalPaymentCop = obj.optLong("finalPaymentCop"),
                            remainingDebtCop = obj.optLong("remainingDebtCop")
                        )
                    )
                }
            }

            val moneyLocations = mutableListOf<MoneyLocationRow>()
            val locArray = root.optJSONArray("moneyLocations")
            if (locArray != null) {
                for (i in 0 until locArray.length()) {
                    val obj = locArray.getJSONObject(i)
                    moneyLocations.add(
                        MoneyLocationRow(
                            name = obj.optString("name"),
                            isSubRow = obj.optBoolean("isSubRow"),
                            salesCop = obj.optLong("salesCop"),
                            purchasesCop = obj.optLong("purchasesCop"),
                            loansCop = obj.optLong("loansCop"),
                            abonosCop = obj.optLong("abonosCop"),
                            netCop = obj.optLong("netCop")
                        )
                    )
                }
            }

            SettlementSummary(
                monthKey = monthKey,
                monthTitle = monthTitle,
                isClosed = isClosed,
                closedAt = closedAt,
                closedByName = closedByName,
                totalSalesCop = totalSalesCop,
                totalPurchasesCop = totalPurchasesCop,
                utilityCop = utilityCop,
                distributableUtilityCop = distributableUtilityCop,
                totalLoansCop = totalLoansCop,
                totalAbonosCop = totalAbonosCop,
                distributions = distributions,
                moneyLocations = moneyLocations,
                movementsCount = movementsCount
            )
        } catch (_: Exception) {
            null
        }
    }
}
