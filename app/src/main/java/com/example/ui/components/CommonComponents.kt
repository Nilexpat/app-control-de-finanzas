package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.theme.*
import com.example.util.CurrencyFormatter
import com.example.util.DateFormatter
import com.example.util.JsonHelper

val SummaryCardGreenBg = Color(0xFFEEF8DB)
val SummaryCardGreenBorder = Color(0xFFC7E7A6)

@Composable
fun SummaryMetricCard(
    title: String,
    amountCop: Long,
    icon: ImageVector,
    color: Color,
    bgColor: Color,
    modifier: Modifier = Modifier,
    prefix: String = "",
    containerBgColor: Color = SummaryCardGreenBg,
    containerBorderColor: Color = SummaryCardGreenBorder
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerBgColor
        ),
        border = BorderStroke(1.dp, containerBorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF0F172A),
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = color,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "$prefix${CurrencyFormatter.formatCop(amountCop)}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = when (title) {
                    "Compras" -> Color(0xFF991B1B)
                    "Préstamos" -> Color(0xFF92400E)
                    else -> if (amountCop < 0) Color(0xFF991B1B) else Color(0xFF166534)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun PaymentMethodSelector(
    selectedMethod: PaymentMethod?,
    selectedAccount: String?,
    availableAccounts: List<TransferAccount>,
    onMethodSelected: (PaymentMethod) -> Unit,
    onAccountSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Medio de Pago *",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Indica dónde está o de dónde sale la plata (obligatorio)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Cash button
            val isCash = selectedMethod == PaymentMethod.EFECTIVO
            OutlinedCard(
                onClick = { onMethodSelected(PaymentMethod.EFECTIVO) },
                modifier = Modifier
                    .weight(1f)
                    .height(68.dp)
                    .testTag("payment_method_cash"),
                shape = RoundedCornerShape(12.dp),
                border = CardDefaults.outlinedCardBorder().copy(
                    width = if (isCash) 2.dp else 1.dp,
                    brush = androidx.compose.ui.graphics.SolidColor(if (isCash) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                ),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = if (isCash) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Payments,
                        contentDescription = "Efectivo",
                        tint = if (isCash) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Efectivo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isCash) FontWeight.Bold else FontWeight.Medium,
                        color = if (isCash) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Transfer button
            val isTransfer = selectedMethod == PaymentMethod.TRANSFERENCIA
            OutlinedCard(
                onClick = { onMethodSelected(PaymentMethod.TRANSFERENCIA) },
                modifier = Modifier
                    .weight(1f)
                    .height(68.dp)
                    .testTag("payment_method_transfer"),
                shape = RoundedCornerShape(12.dp),
                border = CardDefaults.outlinedCardBorder().copy(
                    width = if (isTransfer) 2.dp else 1.dp,
                    brush = androidx.compose.ui.graphics.SolidColor(if (isTransfer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                ),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = if (isTransfer) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalance,
                        contentDescription = "Transferencia",
                        tint = if (isTransfer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Transferencia",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isTransfer) FontWeight.Bold else FontWeight.Medium,
                        color = if (isTransfer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Dropdown or chips for transfer account if Transfer selected
        if (selectedMethod == PaymentMethod.TRANSFERENCIA) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Cuenta o app receptora / pagadora:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))

            val accountList = if (availableAccounts.isNotEmpty()) availableAccounts.map { it.name } else listOf("Nequi", "Daviplata", "Bancolombia", "Otra")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                accountList.forEach { acc ->
                    val isAccSelected = selectedAccount == acc
                    FilterChip(
                        selected = isAccSelected,
                        onClick = { onAccountSelected(acc) },
                        label = { Text(acc, style = MaterialTheme.typography.bodySmall) },
                        leadingIcon = if (isAccSelected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        } else null,
                        modifier = Modifier.testTag("account_chip_$acc")
                    )
                }
            }
        }
    }
}

@Composable
fun MovementItemRow(
    movement: Movement,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val typeColor = when (movement.type) {
        MovementType.VENTA -> FinanceGreen
        MovementType.COMPRA -> FinanceRed
        MovementType.PRESTAMO -> FinanceOrange
        MovementType.ABONO -> FinanceTeal
    }

    val typeIcon = when (movement.type) {
        MovementType.VENTA -> Icons.Default.TrendingUp
        MovementType.COMPRA -> Icons.Default.TrendingDown
        MovementType.PRESTAMO -> Icons.Default.Handshake
        MovementType.ABONO -> Icons.Default.PriceCheck
    }

    val prefix = when (movement.type) {
        MovementType.VENTA -> "+"
        MovementType.COMPRA -> "−"
        MovementType.PRESTAMO -> "−"
        MovementType.ABONO -> "+"
    }

    // Titulo: "Cliente · Tipo". El monto ya se muestra a la derecha de la fila,
    // asi que aqui no se repite para no duplicarlo. El detalle de los productos
    // se ve al abrir el movimiento.
    val lineas = JsonHelper.deserializeLines(movement.linesJson)
    val nombreCliente = movement.debtorMemberName?.takeIf { it.isNotBlank() }
        ?: movement.note.takeIf { it.isNotBlank() }
        ?: movement.registeredByName
    val tipoTexto = when (movement.type) {
        MovementType.VENTA -> "Venta"
        MovementType.COMPRA -> "Compra"
        MovementType.PRESTAMO -> "Préstamo"
        MovementType.ABONO -> "Abono"
    }
    val titleText = "$nombreCliente · $tipoTexto"

    // Numero de productos como linea secundaria breve
    val subtitleText = if (lineas.isNotEmpty()) {
        if (lineas.size == 1) "1 producto" else "${lineas.size} productos"
    } else {
        null
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("movement_item_${movement.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (movement.isAnnulled) Color(0xFFF3F4F6) else Color(0xFFF1F8E8)
        ),
        border = BorderStroke(1.dp, if (movement.isAnnulled) BrandBorder else Color(0xFFD6EBB8)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        when (movement.type) {
                            MovementType.VENTA -> Color(0xFFD4F73C)
                            MovementType.COMPRA -> Color(0xFFFEE2E2)
                            MovementType.PRESTAMO -> Color(0xFFFEF3C7)
                            MovementType.ABONO -> Color(0xFFDBEAFE)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = typeIcon,
                    contentDescription = movement.type.name,
                    tint = when (movement.type) {
                        MovementType.VENTA -> Color(0xFF166534)
                        MovementType.COMPRA -> Color(0xFF991B1B)
                        MovementType.PRESTAMO -> Color(0xFF92400E)
                        MovementType.ABONO -> Color(0xFF1E40AF)
                    },
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (movement.orderCode.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFE2E8F0),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                text = movement.orderCode,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF1E3A8A),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            textDecoration = if (movement.isAnnulled) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        color = if (movement.isAnnulled) Color(0xFF6B7280) else Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = movement.registeredByName,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF1E293B),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = " • ${DateFormatter.formatShortDate(movement.occurredAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF0F172A),
                        fontWeight = FontWeight.Bold
                    )
                    if (movement.isEdited) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "• Editado",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF1D4ED8)
                        )
                    }
                    if (movement.isAnnulled) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "• Anulado",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFDC2626)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right side: Amount and payment badge
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$prefix${CurrencyFormatter.formatCop(movement.totalCop)}",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (movement.isAnnulled) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (movement.isAnnulled) Color(0xFF6B7280) else when (movement.type) {
                        MovementType.VENTA -> Color(0xFF166534)
                        MovementType.COMPRA -> Color(0xFF991B1B)
                        MovementType.PRESTAMO -> Color(0xFF92400E)
                        MovementType.ABONO -> Color(0xFF1E40AF)
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (movement.paymentMethod == PaymentMethod.EFECTIVO) Icons.Default.Payments else Icons.Default.AccountBalance,
                        contentDescription = movement.paymentMethod.name,
                        tint = Color(0xFF334155),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (movement.paymentMethod == PaymentMethod.EFECTIVO) "Efectivo" else (movement.transferAccountName ?: "Transf."),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = Color(0xFF334155)
                    )
                }
            }
        }
    }
}

@Composable
fun CompanyProfileHeaderTitle(
    companyName: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp, horizontal = 2.dp)
            .testTag("company_selector_header")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = companyName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Cambiar de empresa",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun CompanySwitcherDialog(
    groups: List<Group>,
    selectedGroupId: String?,
    onSelectGroup: (String) -> Unit,
    onOpenCreateCompany: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Store, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Tus Empresas y Negocios")
            }
        },
        text = {
            Column {
                Text(
                    text = "Elige la empresa activa. El catálogo, las finanzas y los socios cambian según la empresa:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                groups.forEach { group ->
                    val isSelected = group.id == selectedGroupId
                    Card(
                        onClick = {
                            onSelectGroup(group.id)
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Business,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = group.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Activa",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        onDismiss()
                        onOpenCreateCompany()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("add_company_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Crear Nueva Empresa")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
fun CreateCompanyDialog(
    initialAdminName: String,
    onDismiss: () -> Unit,
    onCreate: (name: String, adminName: String, settlementMode: String) -> Unit
) {
    var newCompanyName by remember { mutableStateOf("") }
    var creatorName by remember { mutableStateOf(initialAdminName.ifBlank { "Administrador" }) }
    var selectedMode by remember { mutableStateOf("DESEMBOLSO") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crear Nueva Empresa") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Configura un nuevo negocio independiente con su propio catálogo, socios y cortes:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = newCompanyName,
                    onValueChange = { newCompanyName = it },
                    label = { Text("Nombre de la Empresa *") },
                    placeholder = { Text("Ej. Panadería San Juan") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_company_name_input")
                )
                OutlinedTextField(
                    value = creatorName,
                    onValueChange = { creatorName = it },
                    label = { Text("Tu Nombre como Admin") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Tipo de Cuentas / Extractos:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedMode == "DESEMBOLSO",
                        onClick = { selectedMode = "DESEMBOLSO" },
                        label = { Text("Por Desembolsos", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedMode == "MENSUAL",
                        onClick = { selectedMode = "MENSUAL" },
                        label = { Text("Mensual", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = if (selectedMode == "DESEMBOLSO") "Cortes activados manualmente al desembolsar dinero (ideal para negocios no lineales)." else "Cortes fijos por mes calendario.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newCompanyName.isNotBlank()) {
                        onCreate(newCompanyName.trim(), creatorName.trim(), selectedMode)
                    }
                },
                enabled = newCompanyName.isNotBlank(),
                modifier = Modifier.testTag("confirm_create_company_btn")
            ) {
                Text("Crear Empresa")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
