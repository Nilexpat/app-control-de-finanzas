package com.example.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object CurrencyFormatter {
    private val bogotaLocale = Locale("es", "CO")
    
    // Colombian Peso format: thousands separator dot, no decimals ($1.250.000)
    private val symbols = DecimalFormatSymbols(bogotaLocale).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }
    private val copFormat = DecimalFormat("$#,##0", symbols)
    private val rawNumberFormat = DecimalFormat("#,##0", symbols)

    fun formatCop(amount: Long): String {
        return copFormat.format(amount)
    }

    fun formatRawThousands(amount: Long): String {
        return rawNumberFormat.format(amount)
    }

    /**
     * Parses clean Long from user input string (ignores dots, spaces, dollar signs).
     * Returns 0L if empty or invalid. Never uses floating point.
     */
    fun parseCopInput(input: String): Long {
        val digitsOnly = input.filter { it.isDigit() }
        if (digitsOnly.isEmpty()) return 0L
        return try {
            digitsOnly.toLong()
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Format basis points (centésimas) into percentage string (e.g. 3333 -> "33,33 %", 4000 -> "40,00 %")
     */
    fun formatPercentage(centecimas: Int): String {
        val integerPart = centecimas / 100
        val decimalPart = centecimas % 100
        return String.format(Locale.US, "%d,%02d %%", integerPart, decimalPart)
    }

    /**
     * Parse percentage input string (e.g. "33.33" or "33,33") to centésimas integer (3333).
     */
    fun parsePercentageToCentecimas(input: String): Int {
        val cleaned = input.trim().replace("%", "").replace(",", ".").trim()
        val parts = cleaned.split(".")
        if (parts.isEmpty()) return 0
        val integerPart = parts[0].toIntOrNull() ?: 0
        val decimalPart = if (parts.size > 1) {
            val dec = parts[1].take(2).padEnd(2, '0')
            dec.toIntOrNull() ?: 0
        } else {
            0
        }
        return (integerPart * 100 + decimalPart).coerceIn(0, 10000)
    }
}

object DateFormatter {
    val BOGOTA_ZONE: ZoneId = ZoneId.of("America/Bogota")

    private val fullDateTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM/yyyy h:mm a", Locale("es", "CO"))

    private val shortDateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale("es", "CO"))

    private val monthYearFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMMM yyyy", Locale("es", "CO"))

    private val monthKeyFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM", Locale.US)

    fun formatFullDateTime(epochMillis: Long): String {
        val instant = Instant.ofEpochMilli(epochMillis)
        val zonedDateTime = instant.atZone(BOGOTA_ZONE)
        return fullDateTimeFormatter.format(zonedDateTime)
    }

    fun formatShortDate(epochMillis: Long): String {
        val instant = Instant.ofEpochMilli(epochMillis)
        val zonedDateTime = instant.atZone(BOGOTA_ZONE)
        return shortDateFormatter.format(zonedDateTime)
    }

    fun formatMonthYear(monthKey: String): String {
        // monthKey is "yyyy-MM", e.g. "2026-10"
        return try {
            val parts = monthKey.split("-")
            val year = parts[0].toInt()
            val month = parts[1].toInt()
            val date = LocalDate.of(year, month, 1)
            val formatted = date.format(monthYearFormatter)
            formatted.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("es", "CO")) else it.toString() }
        } catch (_: Exception) {
            monthKey
        }
    }

    fun currentMonthKey(): String {
        val now = LocalDateTime.now(BOGOTA_ZONE)
        return monthKeyFormatter.format(now)
    }

    fun toMonthKey(epochMillis: Long): String {
        val instant = Instant.ofEpochMilli(epochMillis)
        val zonedDateTime = instant.atZone(BOGOTA_ZONE)
        return monthKeyFormatter.format(zonedDateTime)
    }

    fun previousMonthKey(monthKey: String): String {
        return try {
            val parts = monthKey.split("-")
            val date = LocalDate.of(parts[0].toInt(), parts[1].toInt(), 1).minusMonths(1)
            monthKeyFormatter.format(date)
        } catch (_: Exception) {
            monthKey
        }
    }

    /** Medianoche (hora de Bogota) del primer dia del mes "yyyy-MM". */
    fun monthStartMillis(monthKey: String): Long {
        return try {
            val parts = monthKey.split("-")
            LocalDate.of(parts[0].toInt(), parts[1].toInt(), 1)
                .atStartOfDay(BOGOTA_ZONE).toInstant().toEpochMilli()
        } catch (_: Exception) {
            0L
        }
    }

    fun nextMonthKey(monthKey: String): String {
        return try {
            val parts = monthKey.split("-")
            val date = LocalDate.of(parts[0].toInt(), parts[1].toInt(), 1).plusMonths(1)
            monthKeyFormatter.format(date)
        } catch (_: Exception) {
            monthKey
        }
    }
}
