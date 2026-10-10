package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.FinanceViewModel
import com.example.util.CurrencyFormatter
import com.example.util.DateFormatter
import com.example.util.JsonHelper

@Composable
fun MovementDetailDialog(
    movement: Movement,
    viewModel: FinanceViewModel,
    onDismiss: () -> Unit
) {
    val currentUser by viewModel.currentMember.collectAsStateWithLifecycle()
    val audits by viewModel.getAuditsForMovement(movement.id).collectAsStateWithLifecycle(emptyList())
    val transferAccounts by viewModel.transferAccounts.collectAsStateWithLifecycle()

    var showAnnulConfirmDialog by remember { mutableStateOf(false) }
    var annulReasonText by remember { mutableStateOf("") }
    var showEditDialog by remember { mutableStateOf(false) }
    var showQuickChangePaymentDialog by remember { mutableStateOf(false) }
    var quickTransferAccount by remember { mutableStateOf(transferAccounts.firstOrNull()?.name ?: "Bancolombia") }

    // 24-hour rule check
    val elapsedMillis = System.currentTimeMillis() - movement.recordedAt
    val isWithin24Hours = elapsedMillis <= 24 * 60 * 60 * 1000L

    val isAuthor = currentUser?.id == movement.registeredByUserId
    val isAdmin = currentUser?.role == Role.ADMIN

    // Can edit if: not annulled AND (isAdmin OR (isAuthor && isWithin24Hours))
    val canModify = !movement.isAnnulled && (isAdmin || (isAuthor && isWithin24Hours))

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = when (movement.type) {
                                        MovementType.VENTA -> "Detalle de Venta"
                                        MovementType.COMPRA -> "Detalle de Compra"
                                        MovementType.PRESTAMO -> "Detalle de Préstamo"
                                        MovementType.ABONO -> "Detalle de Abono"
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                if (movement.orderCode.isNotBlank()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = movement.orderCode,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                            Text(
                                text = DateFormatter.formatFullDateTime(movement.occurredAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Big amount banner
                    val typeColor = when (movement.type) {
                        MovementType.VENTA -> FinanceGreen
                        MovementType.COMPRA -> FinanceRed
                        MovementType.PRESTAMO -> FinanceOrange
                        MovementType.ABONO -> FinanceTeal
                    }
                    val prefix = when (movement.type) {
                        MovementType.VENTA -> "+"
                        MovementType.COMPRA -> "−"
                        MovementType.PRESTAMO -> "−"
                        MovementType.ABONO -> "+"
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = typeColor.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "$prefix${CurrencyFormatter.formatCop(movement.totalCop)}",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    textDecoration = if (movement.isAnnulled) TextDecoration.LineThrough else TextDecoration.None
                                ),
                                color = if (movement.isAnnulled) MaterialTheme.colorScheme.onSurfaceVariant else typeColor
                            )
                            if (movement.isAnnulled) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "ANULADO: ${movement.annulReason ?: "Sin motivo"}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Payment method & info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Medio de Pago:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (movement.paymentMethod == PaymentMethod.EFECTIVO) Icons.Default.Payments else Icons.Default.AccountBalance,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (movement.paymentMethod == PaymentMethod.EFECTIVO) "Efectivo" else "Transferencia (${movement.transferAccountName ?: "General"})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (isAdmin && !movement.isAnnulled) {
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = { showQuickChangePaymentDialog = true },
                                    modifier = Modifier.size(28.dp).testTag("quick_change_payment_btn")
                                ) {
                                    Icon(Icons.Default.SwapHoriz, contentDescription = "Cambiar medio de pago", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Registrado por:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = movement.registeredByName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (movement.type == MovementType.PRESTAMO || movement.type == MovementType.ABONO) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Socio:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = movement.debtorMemberName ?: movement.registeredByName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (movement.note.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Nota / Cliente: ${movement.note}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Products table if applicable
                    val lines = JsonHelper.deserializeLines(movement.linesJson)
                    if (lines.isNotEmpty()) {
                        Text(
                            text = "Productos en el Ticket",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                val lines = JsonHelper.deserializeLines(movement.linesJson)
                items(lines) { line ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = line.productName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${line.quantity} un. x ${CurrencyFormatter.formatCop(line.unitPriceCop)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = CurrencyFormatter.formatCop(line.subtotalCop),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Audit history section
                item {
                    if (audits.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Historial de Cambios",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                items(audits) { audit ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = audit.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = DateFormatter.formatShortDate(audit.modifiedAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "Por ${audit.modifiedByName}: ${audit.oldValue} ➔ ${audit.newValue}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Actions: Edit / Annul
                item {
                    Spacer(modifier = Modifier.height(20.dp))

                    if (canModify) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showEditDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("edit_movement_button")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Corregir")
                            }

                            Button(
                                onClick = { showAnnulConfirmDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("annul_movement_button")
                            ) {
                                Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Anular")
                            }
                        }
                    } else if (movement.isAnnulled) {
                        Text(
                            text = "Este movimiento ya fue anulado y no se puede modificar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        // Explanation of why it cannot be edited
                        val reasonText = if (!isWithin24Hours && !isAdmin) {
                            "El plazo de 24 horas para corregir o anular este movimiento expiró. Solo un Admin puede modificarlo."
                        } else if (!isAuthor && !isAdmin) {
                            "Solo el autor que registró el movimiento o un Admin pueden corregirlo."
                        } else {
                            "No se puede modificar este movimiento."
                        }
                        Text(
                            text = reasonText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Annul confirmation dialog
    if (showAnnulConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showAnnulConfirmDialog = false },
            title = { Text("Anular Movimiento") },
            text = {
                Column {
                    Text("Los movimientos no se eliminan. Quedará registro de anulación y motivo.")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = annulReasonText,
                        onValueChange = { annulReasonText = it },
                        label = { Text("Motivo de la anulación *") },
                        placeholder = { Text("Ej. Error en digitación, venta cancelada") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("annul_reason_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.annulMovement(movement.id, annulReasonText) {
                            showAnnulConfirmDialog = false
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = annulReasonText.isNotBlank(),
                    modifier = Modifier.testTag("confirm_annul_button")
                ) {
                    Text("Confirmar Anulación")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAnnulConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Quick change payment method dialog for Admin
    if (showQuickChangePaymentDialog) {
        val targetNewMethod = if (movement.paymentMethod == PaymentMethod.EFECTIVO) PaymentMethod.TRANSFERENCIA else PaymentMethod.EFECTIVO
        AlertDialog(
            onDismissRequest = { showQuickChangePaymentDialog = false },
            title = { Text("Cambiar Medio de Pago (Admin)") },
            text = {
                Column {
                    Text("Se registrará en el historial de cambios y notificará a los socios el ajuste de efectivo a transferencia o viceversa.")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Cambiar a: ${if (targetNewMethod == PaymentMethod.EFECTIVO) "Efectivo" else "Transferencia"}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (targetNewMethod == PaymentMethod.TRANSFERENCIA) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Selecciona la cuenta o app bancaria:", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val accs = if (transferAccounts.isNotEmpty()) transferAccounts.map { it.name } else listOf("Bancolombia", "Nequi", "Daviplata", "Otra")
                            accs.forEach { acc ->
                                FilterChip(
                                    selected = quickTransferAccount == acc,
                                    onClick = { quickTransferAccount = acc },
                                    label = { Text(acc, style = MaterialTheme.typography.bodySmall) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.quickChangePaymentMethod(
                            movementId = movement.id,
                            newMethod = targetNewMethod,
                            newAccountName = if (targetNewMethod == PaymentMethod.TRANSFERENCIA) quickTransferAccount else null
                        ) {
                            showQuickChangePaymentDialog = false
                            onDismiss()
                        }
                    }
                ) {
                    Text("Confirmar Cambio")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickChangePaymentDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Edit dialog
    if (showEditDialog) {
        MovementEditDialog(
            movement = movement,
            availableAccounts = transferAccounts,
            onDismiss = { showEditDialog = false },
            onSave = { newLines, newTotal, newMethod, newAccount, newNote ->
                viewModel.editMovement(
                    movementId = movement.id,
                    newLines = newLines,
                    newTotalCop = newTotal,
                    newPaymentMethod = newMethod,
                    newAccountName = newAccount,
                    newNote = newNote
                ) {
                    showEditDialog = false
                    onDismiss()
                }
            }
        )
    }
}

@Composable
fun MovementEditDialog(
    movement: Movement,
    availableAccounts: List<TransferAccount>,
    onDismiss: () -> Unit,
    onSave: (List<MovementLine>, Long, PaymentMethod, String?, String) -> Unit
) {
    val initialLines = remember { JsonHelper.deserializeLines(movement.linesJson) }
    var noteText by remember { mutableStateOf(movement.note) }
    var selectedMethod by remember { mutableStateOf(movement.paymentMethod) }
    var selectedAccount by remember { mutableStateOf(movement.transferAccountName ?: "Nequi") }
    var loanAmountInput by remember { mutableStateOf(movement.totalCop.toString()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Corregir Movimiento",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "La fecha y autor no se pueden cambiar. Quedará registro en la auditoría.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (movement.type == MovementType.PRESTAMO || movement.type == MovementType.ABONO || initialLines.isEmpty()) {
                    OutlinedTextField(
                        value = loanAmountInput,
                        onValueChange = { loanAmountInput = it.filter { c -> c.isDigit() } },
                        label = { Text("Nuevo Monto COP *") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_amount_input"),
                        leadingIcon = { Text("$", fontWeight = FontWeight.Bold) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Payment method selector
                PaymentMethodSelector(
                    selectedMethod = selectedMethod,
                    selectedAccount = selectedAccount,
                    availableAccounts = availableAccounts,
                    onMethodSelected = { selectedMethod = it },
                    onAccountSelected = { selectedAccount = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Nota / Observación") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val total = if (initialLines.isNotEmpty()) {
                                initialLines.sumOf { it.subtotalCop }
                            } else {
                                loanAmountInput.toLongOrNull() ?: movement.totalCop
                            }
                            onSave(
                                initialLines,
                                total,
                                selectedMethod,
                                if (selectedMethod == PaymentMethod.TRANSFERENCIA) selectedAccount else null,
                                noteText
                            )
                        },
                        modifier = Modifier.testTag("save_edit_button")
                    ) {
                        Text("Guardar Corrección")
                    }
                }
            }
        }
    }
}
