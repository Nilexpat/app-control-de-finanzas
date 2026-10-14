package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Role
import com.example.data.model.Settlement
import com.example.ui.components.CompanyProfileHeaderTitle
import com.example.ui.components.CompanySwitcherDialog
import com.example.ui.components.CreateCompanyDialog
import com.example.ui.components.SalesPatternsCard
import com.example.ui.theme.*
import com.example.ui.viewmodel.FinanceViewModel
import com.example.util.CurrencyFormatter
import com.example.util.DateFormatter
import com.example.util.ExportHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettlementScreen(
    viewModel: FinanceViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val selectedGroupId by viewModel.selectedGroupId.collectAsStateWithLifecycle()
    val currentMember by viewModel.currentMember.collectAsStateWithLifecycle()
    val summary by viewModel.settlementSummary.collectAsStateWithLifecycle()
    val selectedMonthKey by viewModel.selectedMonthKey.collectAsStateWithLifecycle()
    val isAccumulated by viewModel.isAccumulatedView.collectAsStateWithLifecycle()
    val settlementsHistory by viewModel.settlementsHistory.collectAsStateWithLifecycle()
    val movements by viewModel.movements.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()

    val activeGroup = groups.firstOrNull { it.id == selectedGroupId }
    val activeGroupName = activeGroup?.name ?: "SocioFinanzas"

    var showCloseConfirmDialog by remember { mutableStateOf(false) }
    var showReopenConfirmDialog by remember { mutableStateOf(false) }
    var showDisburseConfirmDialog by remember { mutableStateOf(false) }
    var showCompanyPicker by remember { mutableStateOf(false) }
    var showCreateCompanyDialog by remember { mutableStateOf(false) }
    var showExtractsHistoryDialog by remember { mutableStateOf(false) }

    val isAdmin = currentMember?.role == Role.ADMIN

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    CompanyProfileHeaderTitle(
                        companyName = activeGroupName,
                        subtitle = "Historial de Extractos",
                        onClick = { showExtractsHistoryDialog = true },
                        modifier = Modifier.testTag("settlement_company_switcher")
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.exportPdfReport { file ->
                                ExportHelper.shareFile(context, file, "application/pdf", "Reporte de Corte Financiero")
                            }
                        },
                        modifier = Modifier.testTag("export_pdf_top_btn")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Exportar PDF")
                    }
                    IconButton(
                        onClick = {
                            viewModel.shareWhatsAppSummary { summaryText ->
                                ExportHelper.shareText(context, summaryText, "Resumen Financiero WhatsApp")
                            }
                        },
                        modifier = Modifier.testTag("share_whatsapp_top_btn")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Compartir por WhatsApp")
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Month / Period Selector & View Toggle
            item {
                val isDisbursementMode = activeGroup?.settlementMode == "DESEMBOLSO"
                val isClosed = summary?.isClosed == true

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        if (isDisbursementMode) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isClosed) "Corte Desembolsado" else "Período en Curso",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val startEpoch = if ((summary?.periodStartAt ?: 0L) > 0) summary!!.periodStartAt else (activeGroup?.currentPeriodStartAt ?: activeGroup?.createdAt ?: System.currentTimeMillis())
                                    Text(
                                        text = "Inicio: ${DateFormatter.formatShortDate(startEpoch)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (isAdmin) {
                                    AssistChip(
                                        onClick = { viewModel.setGroupSettlementMode("MENSUAL") },
                                        label = { Text("Cambiar a Mensual", style = MaterialTheme.typography.labelSmall) },
                                        leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { viewModel.selectMonth(DateFormatter.previousMonthKey(selectedMonthKey)) }
                                    ) {
                                        Icon(Icons.Default.ChevronLeft, contentDescription = "Mes anterior")
                                    }
                                    Text(
                                        text = DateFormatter.formatMonthYear(selectedMonthKey),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(
                                        onClick = { viewModel.selectMonth(DateFormatter.nextMonthKey(selectedMonthKey)) }
                                    ) {
                                        Icon(Icons.Default.ChevronRight, contentDescription = "Mes siguiente")
                                    }
                                }

                                if (isAdmin) {
                                    AssistChip(
                                        onClick = { viewModel.setGroupSettlementMode("DESEMBOLSO") },
                                        label = { Text("Por Desembolsos", style = MaterialTheme.typography.labelSmall) },
                                        leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Status pill & Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isClosed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = if (isDisbursementMode) {
                                        if (isClosed) "Desembolso Realizado" else "Pendiente de Desembolso"
                                    } else {
                                        if (isClosed) "Corte Cerrado" else "Corte Abierto"
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isClosed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            // Toggle Este corte / Acumulado
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isAccumulated) "Acumulado" else "Solo este corte",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Switch(
                                    checked = isAccumulated,
                                    onCheckedChange = { viewModel.setAccumulatedView(it) },
                                    modifier = Modifier.scale(0.8f)
                                )
                            }
                        }
                    }
                }
            }

            // Summary Totals
            item {
                Text(
                    text = "Totales del Período",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Capital total (Ventas):", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "+${CurrencyFormatter.formatCop(summary?.totalSalesCop ?: 0L)}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = FinanceGreen
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Compras realizadas:", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "−${CurrencyFormatter.formatCop(summary?.totalPurchasesCop ?: 0L)}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = FinanceRed
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Utilidad del corte (Ventas − Compras):", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            val util = summary?.utilityCop ?: 0L
                            Text(
                                CurrencyFormatter.formatCop(util),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (util >= 0) MaterialTheme.colorScheme.primary else FinanceRed
                            )
                        }

                        if ((summary?.utilityCop ?: 0L) <= 0) {
                            Text(
                                text = "Como la utilidad es menor o igual a $0, no hay monto a repartir en este corte.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Monto neto a Repartir (U):", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    CurrencyFormatter.formatCop(summary?.distributableUtilityCop ?: 0L),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // DÓNDE ESTÁ LA PLATA TABLE
            item {
                Text(
                    text = "Dónde está la plata (por medio de pago)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "* Movimientos registrados. No incluye los pagos del reparto.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        // Scrollable table horizontally if needed
                        val scrollState = rememberScrollState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(scrollState),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column {
                                // Header row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Text("Medio / Cuenta", modifier = Modifier.width(130.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text("Ventas (+)", modifier = Modifier.width(90.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text("Compras (−)", modifier = Modifier.width(90.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text("Préstamos (−)", modifier = Modifier.width(95.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text("Neto", modifier = Modifier.width(95.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                summary?.moneyLocations?.forEach { loc ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = loc.name,
                                            modifier = Modifier.width(130.dp),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (loc.isSubRow) FontWeight.Normal else FontWeight.Bold
                                        )
                                        Text(
                                            text = CurrencyFormatter.formatCop(loc.salesCop),
                                            modifier = Modifier.width(90.dp),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = FinanceGreen
                                        )
                                        Text(
                                            text = CurrencyFormatter.formatCop(loc.purchasesCop),
                                            modifier = Modifier.width(90.dp),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = FinanceRed
                                        )
                                        Text(
                                            text = CurrencyFormatter.formatCop(loc.loansCop),
                                            modifier = Modifier.width(95.dp),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = FinanceOrange
                                        )
                                        Text(
                                            text = CurrencyFormatter.formatCop(loc.netCop),
                                            modifier = Modifier.width(95.dp),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // TABLA DE REPARTO DE GANANCIAS
            item {
                Text(
                    text = "Tabla de Reparto de Ganancias",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        val scrollState = rememberScrollState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(scrollState),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column {
                                // Header row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Text("Destinatario", modifier = Modifier.width(130.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text("%", modifier = Modifier.width(55.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text("Parte", modifier = Modifier.width(85.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text("Deuda", modifier = Modifier.width(85.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text("Descuento", modifier = Modifier.width(85.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text("PAGO FINAL", modifier = Modifier.width(95.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text("Deuda Queda", modifier = Modifier.width(90.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                summary?.distributions?.forEach { d ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = d.recipientName,
                                            modifier = Modifier.width(130.dp),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (d.isCompany) FontWeight.Bold else FontWeight.SemiBold
                                        )
                                        Text(text = d.percentageFormatted, modifier = Modifier.width(55.dp), style = MaterialTheme.typography.bodySmall)
                                        Text(text = CurrencyFormatter.formatCop(d.shareAmountCop), modifier = Modifier.width(85.dp), style = MaterialTheme.typography.bodySmall)
                                        Text(text = CurrencyFormatter.formatCop(d.debtCop), modifier = Modifier.width(85.dp), style = MaterialTheme.typography.bodySmall)
                                        Text(text = CurrencyFormatter.formatCop(d.discountCop), modifier = Modifier.width(85.dp), style = MaterialTheme.typography.bodySmall, color = FinanceRed)
                                        Text(
                                            text = CurrencyFormatter.formatCop(d.finalPaymentCop),
                                            modifier = Modifier.width(95.dp),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = CurrencyFormatter.formatCop(d.remainingDebtCop),
                                            modifier = Modifier.width(90.dp),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (d.remainingDebtCop > 0) FinanceOrange else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Close / Reopen / Disburse / New Period Settlement Buttons (Admin)
            if (isAdmin) {
                item {
                    val isDisbursementMode = activeGroup?.settlementMode == "DESEMBOLSO"
                    val isClosed = summary?.isClosed == true

                    if (isDisbursementMode) {
                        if (!isClosed) {
                            Button(
                                onClick = { showDisburseConfirmDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("disburse_button")
                            ) {
                                Icon(Icons.Default.Payments, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Desembolsar / Realizar Pago", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = { viewModel.startNewPeriod() },
                                colors = ButtonDefaults.buttonColors(containerColor = FinanceGreen),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("start_new_period_button")
                            ) {
                                Icon(Icons.Default.AddCircle, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Crear Nuevo Período", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        // Mensual mode
                        if (!isClosed) {
                            Button(
                                onClick = { showCloseConfirmDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("close_settlement_button")
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Cerrar Corte Mensual (Congelar Cifras)")
                            }
                        } else {
                            OutlinedButton(
                                onClick = { showReopenConfirmDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("reopen_settlement_button")
                            ) {
                                Icon(Icons.Default.LockOpen, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Reabrir Corte para Correcciones (Admin)")
                            }
                        }
                    }
                }
            }

            // CUANDO SE VENDE MAS: dias, horas y curva de ventas
            item {
                SalesPatternsCard(movements = movements, products = products)
            }

            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    // Confirmation dialog for disbursement
    if (showDisburseConfirmDialog) {
        val startEpoch = summary?.periodStartAt ?: activeGroup?.currentPeriodStartAt ?: activeGroup?.createdAt ?: System.currentTimeMillis()
        AlertDialog(
            onDismissRequest = { showDisburseConfirmDialog = false },
            title = { Text("Confirmar Desembolso y Pago") },
            text = {
                Text("Al desembolsar se congelarán las cifras calculadas desde ${DateFormatter.formatShortDate(startEpoch)}. Después de confirmar, el botón cambiará a 'Crear nuevo período' para iniciar el siguiente ciclo.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.disburseCurrentPeriod()
                        showDisburseConfirmDialog = false
                    },
                    modifier = Modifier.testTag("confirm_disburse_btn")
                ) {
                    Text("Confirmar Desembolso")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisburseConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Confirmation dialog for closing cut (Mensual)
    if (showCloseConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showCloseConfirmDialog = false },
            title = { Text("Cerrar Corte de ${DateFormatter.formatMonthYear(selectedMonthKey)}") },
            text = {
                Text("Al cerrar el corte se guardará una copia congelada con los totales, reparto y medios de pago. No se podrán crear ni editar movimientos dentro de este mes.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.closeSettlement()
                        showCloseConfirmDialog = false
                    },
                    modifier = Modifier.testTag("confirm_close_cut_btn")
                ) {
                    Text("Confirmar Cierre")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloseConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Confirmation dialog for reopening cut
    if (showReopenConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showReopenConfirmDialog = false },
            title = { Text("Reabrir Corte") },
            text = {
                Text("¿Deseas reabrir el último corte cerrado? Las cifras volverán a recalcularse con los movimientos activos.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.reopenSettlement()
                        showReopenConfirmDialog = false
                    },
                    modifier = Modifier.testTag("confirm_reopen_cut_btn")
                ) {
                    Text("Reabrir Corte")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReopenConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showCompanyPicker) {
        CompanySwitcherDialog(
            groups = groups,
            selectedGroupId = selectedGroupId,
            onSelectGroup = { viewModel.selectGroup(it) },
            onOpenCreateCompany = { showCreateCompanyDialog = true },
            onDismiss = { showCompanyPicker = false }
        )
    }

    if (showCreateCompanyDialog) {
        CreateCompanyDialog(
            initialAdminName = currentMember?.name ?: "Administrador",
            onDismiss = { showCreateCompanyDialog = false },
            onCreate = { newName, creatorName, mode ->
                viewModel.createGroup(newName, creatorName, "", mode) {
                    showCreateCompanyDialog = false
                }
            }
        )
    }

    if (showExtractsHistoryDialog) {
        ExtractsHistoryDialog(
            groupName = activeGroupName,
            settlementMode = activeGroup?.settlementMode ?: "DESEMBOLSO",
            currentPeriodStartAt = (summary?.periodStartAt ?: 0L).takeIf { it > 0 }
                ?: (activeGroup?.currentPeriodStartAt ?: activeGroup?.createdAt ?: System.currentTimeMillis()),
            settlements = settlementsHistory,
            currentSelectedMonthKey = selectedMonthKey,
            onSelectPeriod = { mKey ->
                viewModel.selectMonth(mKey)
                showExtractsHistoryDialog = false
            },
            onOpenCompanyPicker = {
                showExtractsHistoryDialog = false
                showCompanyPicker = true
            },
            onDismiss = { showExtractsHistoryDialog = false }
        )
    }
}

@Composable
fun ExtractsHistoryDialog(
    groupName: String,
    settlementMode: String,
    currentPeriodStartAt: Long,
    settlements: List<Settlement>,
    currentSelectedMonthKey: String,
    onSelectPeriod: (String) -> Unit,
    onOpenCompanyPicker: () -> Unit,
    onDismiss: () -> Unit
) {
    val isDisbursementMode = settlementMode == "DESEMBOLSO"
    val currentKey = DateFormatter.currentMonthKey()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(BrandLime),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ReceiptLong,
                        contentDescription = null,
                        tint = BrandDark,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Historial de Extractos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = groupName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Item 1: Período Actual / Abierto
                item {
                    val isCurrentSelected = currentSelectedMonthKey == currentKey
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectPeriod(currentKey)
                            }
                            .testTag("extract_current_period"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrentSelected) BrandDarkCard else MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, if (isCurrentSelected) BrandLime else BrandBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isDisbursementMode) "Período en Curso" else "Mes Actual (${DateFormatter.formatMonthYear(currentKey)})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCurrentSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Desde ${DateFormatter.formatShortDate(currentPeriodStartAt)} hasta Hoy",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isCurrentSelected) Color(0xFF9CA3AF) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = BrandLime
                            ) {
                                Text(
                                    text = "EN CURSO",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandDark
                                )
                            }
                        }
                    }
                }

                // Section header: Extractos / Cortes Cerrados
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Extractos y Cortes Anteriores",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (settlements.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No hay desembolsos ni extractos cerrados registrados aún.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(settlements, key = { it.id }) { settlement ->
                        val isSelected = currentSelectedMonthKey == settlement.monthKey
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectPeriod(settlement.monthKey)
                                }
                                .testTag("extract_item_${settlement.id}"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) BrandDarkCard else MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(1.dp, if (isSelected) BrandLime else BrandBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (settlement.periodStartAt > 0) {
                                            "Corte ${DateFormatter.formatShortDate(settlement.periodStartAt)} − ${DateFormatter.formatShortDate(settlement.periodEndAt.takeIf { it > 0 } ?: (settlement.closedAt ?: 0L))}"
                                        } else {
                                            DateFormatter.formatMonthYear(settlement.monthKey)
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (settlement.closedAt != null) {
                                            "Cerrado: ${DateFormatter.formatShortDate(settlement.closedAt)} por ${settlement.closedByName ?: "Admin"}"
                                        } else {
                                            "Período guardado"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) Color(0xFF9CA3AF) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) BrandLime else BrandSurfaceMuted
                                ) {
                                    Text(
                                        text = if (settlement.isClosed) "DESEMBOLSADO" else "GUARDADO",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) BrandDark else BrandTextPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = BrandDark, contentColor = Color.White)
            ) {
                Text("Cerrar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onOpenCompanyPicker
            ) {
                Text("Cambiar de empresa")
            }
        }
    )
}

private fun Modifier.scale(scale: Float): Modifier = this
