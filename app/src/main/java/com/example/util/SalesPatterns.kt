package com.example.util

import com.example.data.model.Movement
import com.example.data.model.MovementType
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Patron de ventas por dia de la semana y hora del dia.
 *
 * Los dias van de lunes (0) a domingo (6). `conteo[dia][hora]` es el numero de
 * ventas de ese dia a esa hora, o de unidades vendidas si se filtra por un
 * producto concreto.
 */
class PatronVentas(
    val conteo: Array<IntArray>,
    /** Cuantas veces cae cada dia de la semana dentro del rango, para promediar. */
    val diasEnRango: IntArray
) {
    fun totalDia(dia: Int): Int = conteo[dia].sum()

    /** Ventas medias de ese dia de la semana (por ejemplo, un viernes cualquiera). */
    fun promedioDia(dia: Int): Double =
        if (diasEnRango[dia] == 0) 0.0 else totalDia(dia).toDouble() / diasEnRango[dia]

    /** Ventas por hora sumando solo los dias indicados. */
    fun porHora(dias: Set<Int>): IntArray = IntArray(24) { h -> dias.sumOf { d -> conteo[d][h] } }
}

object SalesPatterns {
    val NOMBRES_DIA = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
    val ABREVIATURAS_DIA = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
    val INICIALES_DIA = listOf("L", "M", "X", "J", "V", "S", "D")

    /** Lunes = 0 ... domingo = 6 (Calendar empieza en domingo = 1). */
    private fun diaSemana(cal: Calendar): Int = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7

    private fun inicioDelDia(cal: Calendar) {
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
    }

    /**
     * Calcula el patron de las ventas no anuladas.
     *
     * @param ultimosDias rango hacia atras contando hoy; null = desde la primera venta.
     * @param productoId si no es null, se cuentan las unidades de ese producto
     *        en lugar del numero de ventas.
     */
    fun calcular(
        movimientos: List<Movement>,
        ultimosDias: Int?,
        productoId: String?,
        ahora: Long = System.currentTimeMillis()
    ): PatronVentas {
        val ventas = movimientos.filter { it.type == MovementType.VENTA && !it.isAnnulled }
        val cal = Calendar.getInstance()

        // Inicio del rango a medianoche, para que cada dia cuente completo
        cal.timeInMillis = if (ultimosDias == null) {
            ventas.minOfOrNull { it.occurredAt } ?: ahora
        } else {
            ahora
        }
        inicioDelDia(cal)
        if (ultimosDias != null) cal.add(Calendar.DAY_OF_YEAR, -(ultimosDias - 1))
        val inicio = cal.timeInMillis

        val conteo = Array(7) { IntArray(24) }
        for (m in ventas) {
            if (m.occurredAt < inicio || m.occurredAt > ahora) continue
            val cantidad = if (productoId == null) {
                1
            } else {
                JsonHelper.deserializeLines(m.linesJson)
                    .filter { it.productId == productoId }
                    .sumOf { it.quantity }
            }
            if (cantidad <= 0) continue
            cal.timeInMillis = m.occurredAt
            conteo[diaSemana(cal)][cal.get(Calendar.HOUR_OF_DAY)] += cantidad
        }

        // Cuantos lunes, martes... hay en el rango
        val diasEnRango = IntArray(7)
        cal.timeInMillis = inicio
        var vueltas = 0
        while (cal.timeInMillis <= ahora && vueltas < 20_000) {
            diasEnRango[diaSemana(cal)]++
            cal.add(Calendar.DAY_OF_YEAR, 1)
            vueltas++
        }
        return PatronVentas(conteo, diasEnRango)
    }

    /**
     * Curva suave de ventas a lo largo del dia.
     *
     * Cada hora reparte sus ventas en una campana de Gauss centrada en esa hora
     * (suavizado con nucleo gaussiano). A diferencia de ajustar UNA sola
     * campana, asi se ven bien los negocios con dos picos (mediodia y tarde).
     * La curva queda en la misma escala que las barras por hora.
     *
     * @return puntos (hora con decimales 0..24, valor).
     */
    fun curvaSuavizada(
        porHora: IntArray,
        sigmaHoras: Double = 1.0,
        pasosPorHora: Int = 4
    ): List<Pair<Float, Float>> {
        val factor = 1.0 / (sigmaHoras * sqrt(2 * PI))
        return (0..24 * pasosPorHora).map { i ->
            val x = i.toDouble() / pasosPorHora
            var y = 0.0
            for (h in 0 until 24) {
                if (porHora[h] == 0) continue
                val z = (x - (h + 0.5)) / sigmaHoras
                y += porHora[h] * factor * exp(-0.5 * z * z)
            }
            x.toFloat() to y.toFloat()
        }
    }
}
