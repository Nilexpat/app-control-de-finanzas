package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Movement
import com.example.data.model.Product
import com.example.ui.theme.*
import com.example.util.SalesPatterns
import java.util.Locale

/**
 * Cuando se vende: tabla de dias x horas y curva de ventas a lo largo del dia.
 *
 * Se calcula a partir de los movimientos que ya hay en el movil, asi que se
 * actualiza sola en cuanto se registra o anula una venta. Las ventas anuladas
 * no cuentan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesPatternsCard(
    movements: List<Movement>,
    products: List<Product>,
    modifier: Modifier = Modifier
) {
    // Rango hacia atras; null = todo el historial
    var ultimosDias by remember { mutableStateOf<Int?>(30) }
    // De lunes a viernes por defecto
    var diasElegidos by remember { mutableStateOf(setOf(0, 1, 2, 3, 4)) }
    var productoId by remember { mutableStateOf<String?>(null) }
    var menuProductos by remember { mutableStateOf(false) }

    val patron = remember(movements, ultimosDias, productoId) {
        SalesPatterns.calcular(movements, ultimosDias, productoId)
    }
    val dias = (0..6).filter { it in diasElegidos }
    val porHora = remember(patron, diasElegidos) { patron.porHora(diasElegidos) }
    val total = porHora.sum()
    val unidad = if (productoId == null) "ventas" else "unidades"

    // Horas a mostrar: de la primera a la ultima con ventas (o una jornada tipica)
    val horasConVentas = (0 until 24).filter { porHora[it] > 0 }
    val horaInicio = (horasConVentas.minOrNull() ?: 8).coerceAtMost(8)
    val horaFin = (horasConVentas.maxOrNull() ?: 20).coerceAtLeast(20)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("sales_patterns_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("¿Cuándo se vende más?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            // --- Filtros ---
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(7 to "7 días", 15 to "15 días", 30 to "30 días", 90 to "90 días", null to "Todo")
                    .forEach { (valor, etiqueta) ->
                        FilterChip(
                            selected = ultimosDias == valor,
                            onClick = { ultimosDias = valor },
                            label = { Text(etiqueta) }
                        )
                    }
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SalesPatterns.INICIALES_DIA.forEachIndexed { dia, inicial ->
                    FilterChip(
                        selected = dia in diasElegidos,
                        onClick = {
                            // Siempre queda al menos un dia elegido
                            val nuevos = if (dia in diasElegidos) diasElegidos - dia else diasElegidos + dia
                            if (nuevos.isNotEmpty()) diasElegidos = nuevos
                        },
                        label = { Text(inicial) }
                    )
                }
            }

            Box {
                OutlinedButton(onClick = { menuProductos = true }) {
                    Text(
                        products.firstOrNull { it.id == productoId }?.name ?: "Todos los productos",
                        maxLines = 1
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = menuProductos, onDismissRequest = { menuProductos = false }) {
                    DropdownMenuItem(
                        text = { Text("Todos los productos") },
                        onClick = { productoId = null; menuProductos = false }
                    )
                    products.sortedBy { it.name.lowercase() }.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.name) },
                            onClick = { productoId = p.id; menuProductos = false }
                        )
                    }
                }
            }

            if (total == 0) {
                Text(
                    "Aún no hay $unidad en este rango y estos días.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BrandTextSecondary
                )
            } else {
                SalesPatternsDetalle(
                    patron = patron,
                    dias = dias,
                    porHora = porHora,
                    total = total,
                    unidad = unidad,
                    horaInicio = horaInicio,
                    horaFin = horaFin
                )
            }
        }
    }
}

/** Resumen, curva y tabla, cuando hay ventas que mostrar. */
@Composable
private fun SalesPatternsDetalle(
    patron: com.example.util.PatronVentas,
    dias: List<Int>,
    porHora: IntArray,
    total: Int,
    unidad: String,
    horaInicio: Int,
    horaFin: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // --- Resumen ---
        val mejorDia = dias.maxByOrNull { patron.promedioDia(it) }
        val horaPico = (0 until 24).maxByOrNull { porHora[it] }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ResumenDato(
                titulo = "Mejor día",
                valor = mejorDia?.let { SalesPatterns.NOMBRES_DIA[it] } ?: "-",
                detalle = mejorDia?.let { "${formatoDecimal(patron.promedioDia(it))} $unidad de media" } ?: "",
                modifier = Modifier.weight(1f)
            )
            ResumenDato(
                titulo = "Hora pico",
                valor = horaPico?.let { "%02d:00 – %02d:00".format(it, it + 1) } ?: "-",
                detalle = "$total $unidad en total",
                modifier = Modifier.weight(1f)
            )
        }

        // --- Curva a lo largo del dia ---
        Text(
            "Ventas a lo largo del día",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        CurvaPorHora(porHora = porHora, horaInicio = horaInicio, horaFin = horaFin, horaPico = horaPico)
        Text(
            "Barras: $unidad en cada hora. Línea: tendencia suavizada (campana de Gauss sobre cada hora).",
            style = MaterialTheme.typography.bodySmall,
            color = BrandTextSecondary
        )

        // --- Tabla dia x hora ---
        Text(
            "Tabla por día y hora",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        TablaDiaHora(
            dias = dias,
            horas = (horaInicio..horaFin).toList(),
            valor = { d, h -> patron.conteo[d][h] },
            promedio = { d -> patron.promedioDia(d) }
        )
        Text(
            "Cada casilla suma las $unidad de ese día de la semana a esa hora. " +
                "\"Media\" es lo que se vende un ${SalesPatterns.NOMBRES_DIA[dias.first()].lowercase()} típico del rango.",
            style = MaterialTheme.typography.bodySmall,
            color = BrandTextSecondary
        )
    }
}

@Composable
private fun ResumenDato(titulo: String, valor: String, detalle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(BrandBlueLight)
            .padding(10.dp)
    ) {
        Text(titulo, style = MaterialTheme.typography.labelSmall, color = BrandTextSecondary)
        Text(valor, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = BrandTextPrimary)
        if (detalle.isNotBlank()) {
            Text(detalle, style = MaterialTheme.typography.bodySmall, color = BrandTextSecondary)
        }
    }
}

@Composable
private fun CurvaPorHora(porHora: IntArray, horaInicio: Int, horaFin: Int, horaPico: Int?) {
    val medidor = rememberTextMeasurer()
    val curva = remember(porHora) { SalesPatterns.curvaSuavizada(porHora) }
    val estiloEje = TextStyle(fontSize = 10.sp, color = BrandTextSecondary)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
    ) {
        val margenEje = 18.dp.toPx()
        val base = size.height - margenEje
        val alto = base - 6.dp.toPx()
        val numHoras = horaFin - horaInicio + 1
        val anchoHora = size.width / numHoras
        val hueco = (anchoHora * 0.15f).coerceAtMost(4.dp.toPx())

        val visibles = curva.filter { it.first >= horaInicio && it.first <= horaFin + 1 }
        val maximo = maxOf(
            (horaInicio..horaFin).maxOf { porHora[it] }.toFloat(),
            visibles.maxOfOrNull { it.second } ?: 0f,
            1f
        )

        // Barras por hora
        for (h in horaInicio..horaFin) {
            val v = porHora[h]
            if (v == 0) continue
            val altoBarra = v / maximo * alto
            drawRect(
                color = if (h == horaPico) BrandBlue.copy(alpha = 0.55f) else BrandBlue.copy(alpha = 0.22f),
                topLeft = Offset((h - horaInicio) * anchoHora + hueco, base - altoBarra),
                size = Size(anchoHora - 2 * hueco, altoBarra)
            )
        }

        // Curva suavizada
        val trazo = Path()
        visibles.forEachIndexed { i, (x, y) ->
            val px = (x - horaInicio) * anchoHora
            val py = base - y / maximo * alto
            if (i == 0) trazo.moveTo(px, py) else trazo.lineTo(px, py)
        }
        drawPath(
            path = trazo,
            color = BrandBlue,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Eje horizontal con las horas
        drawLine(BrandBorder, Offset(0f, base), Offset(size.width, base), strokeWidth = 1.dp.toPx())
        val cadaCuantas = if (numHoras > 12) 3 else 2
        for (h in horaInicio..horaFin + 1 step cadaCuantas) {
            val texto = "${h}h"
            val medida = medidor.measure(texto, estiloEje)
            val x = ((h - horaInicio) * anchoHora - medida.size.width / 2f)
                .coerceIn(0f, size.width - medida.size.width)
            drawText(medidor, texto, topLeft = Offset(x, base + 3.dp.toPx()), style = estiloEje)
        }
    }
}

@Composable
private fun TablaDiaHora(
    dias: List<Int>,
    horas: List<Int>,
    valor: (Int, Int) -> Int,
    promedio: (Int) -> Double
) {
    val maximo = dias.maxOf { d -> horas.maxOf { h -> valor(d, h) } }.coerceAtLeast(1)
    val anchoDia = 44.dp
    val anchoCelda = 34.dp
    val anchoMedia = 48.dp
    val estiloCabecera = MaterialTheme.typography.labelSmall

    Column(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .testTag("sales_patterns_table"),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Spacer(Modifier.width(anchoDia))
            horas.forEach { h ->
                Text(
                    "${h}h",
                    modifier = Modifier.width(anchoCelda),
                    style = estiloCabecera,
                    color = BrandTextSecondary,
                    textAlign = TextAlign.Center
                )
            }
            Text(
                "Media",
                modifier = Modifier.width(anchoMedia),
                style = estiloCabecera,
                color = BrandTextSecondary,
                textAlign = TextAlign.Center
            )
        }
        dias.forEach { d ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    SalesPatterns.ABREVIATURAS_DIA[d],
                    modifier = Modifier.width(anchoDia),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                horas.forEach { h ->
                    val v = valor(d, h)
                    val intensidad = v.toFloat() / maximo
                    val fondo = if (v == 0) BrandSurfaceMuted else lerp(BrandBlueLight, BrandBlue, intensidad)
                    Box(
                        modifier = Modifier
                            .size(anchoCelda, 28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(fondo),
                        contentAlignment = Alignment.Center
                    ) {
                        if (v > 0) {
                            Text(
                                v.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (intensidad > 0.55f) Color.White else BrandTextPrimary
                            )
                        }
                    }
                }
                Text(
                    formatoDecimal(promedio(d)),
                    modifier = Modifier.width(anchoMedia),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun formatoDecimal(valor: Double): String =
    String.format(Locale("es", "CO"), "%.1f", valor)
