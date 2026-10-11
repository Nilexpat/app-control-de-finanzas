package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.*
import com.example.ui.components.PaymentMethodSelector
import com.example.ui.theme.*
import com.example.ui.viewmodel.FinanceViewModel
import com.example.util.CurrencyFormatter
import com.example.util.DateFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterMovementScreen(
    viewModel: FinanceViewModel,
    initialType: MovementType = MovementType.VENTA,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val selectedGroupId by viewModel.selectedGroupId.collectAsStateWithLifecycle()
    val activeProducts by viewModel.activeProducts.collectAsStateWithLifecycle()
    val activeMembers by viewModel.activeMembers.collectAsStateWithLifecycle()
    val transferAccounts by viewModel.transferAccounts.collectAsStateWithLifecycle()
    val currentMember by viewModel.currentMember.collectAsStateWithLifecycle()

    val activeGroup = groups.firstOrNull { it.id == selectedGroupId }
    val activeGroupName = activeGroup?.name ?: "SocioFinanzas"

    val ticketLines by viewModel.ticketDraftLines.collectAsStateWithLifecycle()
    val selectedType by viewModel.draftMovementType.collectAsStateWithLifecycle()
    val selectedPaymentMethod by viewModel.draftPaymentMethod.collectAsStateWithLifecycle()
    val selectedAccountName by viewModel.draftAccountName.collectAsStateWithLifecycle()
    val noteText by viewModel.draftNote.collectAsStateWithLifecycle()
    val loanAmountInput by viewModel.draftLoanAmount.collectAsStateWithLifecycle()
    val selectedDebtorMemberId by viewModel.draftDebtorMemberId.collectAsStateWithLifecycle()

    val selectedDebtorMember = activeMembers.firstOrNull { it.id == selectedDebtorMemberId } ?: currentMember

    LaunchedEffect(initialType) {
        if (ticketLines.isEmpty()) {
            viewModel.setDraftMovementType(initialType)
        }
    }

    // Product entry bottom sheet / dialog
    var productForEntry by remember { mutableStateOf<Product?>(null) }
    var catalogSearch by remember { mutableStateOf("") }

    // Admin date picker state
    var adminPastDateMillis by remember { mutableStateOf<Long?>(null) }
    var showDatePickerDialog by remember { mutableStateOf(false) }

    val isAdmin = currentMember?.role == Role.ADMIN

    val totalTicketCop = remember(ticketLines) {
        ticketLines.sumOf { it.subtotalCop }
    }

    val finalTotalCop = when (selectedType) {
        MovementType.VENTA, MovementType.COMPRA -> totalTicketCop
        MovementType.PRESTAMO, MovementType.ABONO -> CurrencyFormatter.parseCopInput(loanAmountInput)
    }

    val canSave = remember(selectedType, finalTotalCop, selectedPaymentMethod, noteText) {
        // El nombre del cliente o nota es obligatorio: identifica el movimiento
        finalTotalCop > 0 && selectedPaymentMethod != null && noteText.isNotBlank()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Registrar Movimiento", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(activeGroupName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onFinish) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        // El Scaffold reserva espacio para el teclado (ademas de las barras del
        // sistema). Sin esto, al escribir el teclado tapaba el campo.
        contentWindowInsets = WindowInsets.safeDrawing,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Type Selector Buttons
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Venta (+)
                    val isVenta = selectedType == MovementType.VENTA
                    Button(
                        onClick = { viewModel.setDraftMovementType(MovementType.VENTA) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isVenta) BrandLime else BrandSurface,
                            contentColor = if (isVenta) BrandDark else BrandTextSecondary
                        ),
                        border = if (isVenta) null else BorderStroke(1.dp, BrandBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("type_btn_venta")
                    ) {
                        Icon(Icons.Default.TrendingUp, contentDescription = "Venta", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }

                    // Compra (−)
                    val isCompra = selectedType == MovementType.COMPRA
                    Button(
                        onClick = { viewModel.setDraftMovementType(MovementType.COMPRA) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCompra) BrandDark else BrandSurface,
                            contentColor = if (isCompra) Color.White else BrandTextSecondary
                        ),
                        border = if (isCompra) null else BorderStroke(1.dp, BrandBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("type_btn_compra")
                    ) {
                        Icon(Icons.Default.TrendingDown, contentDescription = "Compra", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("−", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }

                    // Préstamo (⇄)
                    val isPrestamo = selectedType == MovementType.PRESTAMO
                    Button(
                        onClick = { viewModel.setDraftMovementType(MovementType.PRESTAMO) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPrestamo) FinanceOrangeBg else BrandSurface,
                            contentColor = if (isPrestamo) FinanceOrange else BrandTextSecondary
                        ),
                        border = if (isPrestamo) null else BorderStroke(1.dp, BrandBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("type_btn_prestamo")
                    ) {
                        Icon(Icons.Default.Payments, contentDescription = "Préstamo", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("⇄", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }

                    // Abono (↩)
                    val isAbono = selectedType == MovementType.ABONO
                    Button(
                        onClick = { viewModel.setDraftMovementType(MovementType.ABONO) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAbono) BrandBlue else BrandSurface,
                            contentColor = if (isAbono) Color.White else BrandTextSecondary
                        ),
                        border = if (isAbono) null else BorderStroke(1.dp, BrandBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("type_btn_abono")
                    ) {
                        Icon(Icons.Default.Savings, contentDescription = "Abono", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("↩", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            // Flow for Venta and Compra (Catalog Products)
            if (selectedType == MovementType.VENTA || selectedType == MovementType.COMPRA) {
                item {
                    OutlinedTextField(
                        value = catalogSearch,
                        onValueChange = { catalogSearch = it },
                        placeholder = { Text("Buscar en catálogo...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("catalog_search_input")
                    )
                }

                // Catalog products grid
                val filteredProducts = activeProducts.filter {
                    catalogSearch.isBlank() || it.name.contains(catalogSearch, ignoreCase = true)
                }

                item {
                    if (filteredProducts.isEmpty()) {
                        Text(
                            text = "No se encontraron productos. Puedes crearlos en la pestaña 'Catálogo'.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        // Product chips / tiles
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            filteredProducts.forEach { product ->
                                Card(
                                    onClick = { productForEntry = product },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("product_tile_${product.id}"),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Inventory2,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = product.name,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = product.category,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        FilledTonalButton(
                                            onClick = { productForEntry = product },
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.testTag("add_product_btn_${product.id}")
                                        ) {
                                            Text(
                                                text = "+",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Ticket Section - only shown when items are added!
                if (ticketLines.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Ticket (${ticketLines.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = { viewModel.clearTicketDraft() },
                                    contentPadding = PaddingValues(horizontal = 6.dp)
                                ) {
                                    Text("Vaciar", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Total: ${CurrencyFormatter.formatCop(totalTicketCop)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedType == MovementType.VENTA) FinanceGreen else FinanceRed
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    items(ticketLines) { line ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = line.productName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${line.quantity} un. x ${CurrencyFormatter.formatCop(line.unitPriceCop)} = ${CurrencyFormatter.formatCop(line.subtotalCop)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.removeTicketDraftLine(line) },
                                    modifier = Modifier.testTag("remove_line_btn")
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            // Flow for Préstamo and Abono (Cash out / Cash in without products)
            if (selectedType == MovementType.PRESTAMO || selectedType == MovementType.ABONO) {
                item {
                    Text(
                        text = if (selectedType == MovementType.PRESTAMO) "Monto del Préstamo (Retiro de plata) *" else "Monto del Abono (Devolución a deuda) *",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = loanAmountInput,
                        onValueChange = { input ->
                            val clean = input.filter { it.isDigit() }
                            val num = clean.toLongOrNull() ?: 0L
                            viewModel.setDraftLoanAmount(if (num > 0) CurrencyFormatter.formatRawThousands(num) else "")
                        },
                        placeholder = { Text("0") },
                        prefix = { Text("$ ", fontWeight = FontWeight.Bold) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("loan_amount_input")
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Partner selection
                    Text(
                        text = "Socio Destinatario / Deudor *",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    if (!isAdmin) {
                        // Socios can only register loans for themselves!
                        Text(
                            text = "Registrado a nombre de: ${currentMember?.name} (los socios solo pueden solicitar préstamos a su propio nombre)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        // Admin can pick any partner
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            activeMembers.forEach { member ->
                                val isSelected = selectedDebtorMember?.id == member.id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.setDraftDebtorMemberId(member.id) },
                                    label = { Text(member.name) },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    modifier = Modifier.testTag("debtor_chip_${member.id}")
                                )
                            }
                        }
                    }
                }
            }

            // Payment method selector - MANDATORY on all movements!
            item {
                Spacer(modifier = Modifier.height(8.dp))
                PaymentMethodSelector(
                    selectedMethod = selectedPaymentMethod,
                    selectedAccount = selectedAccountName,
                    availableAccounts = transferAccounts,
                    onMethodSelected = { viewModel.setDraftPaymentMethod(it) },
                    onAccountSelected = { viewModel.setDraftAccountName(it) }
                )
            }

            // Date & Time info
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Fecha y Hora del Movimiento:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val dateToShow = adminPastDateMillis ?: System.currentTimeMillis()
                        Text(
                            text = DateFormatter.formatFullDateTime(dateToShow),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (!isAdmin) {
                            Text(
                                text = "Automática (hora actual, fija para Socios)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (isAdmin) {
                        TextButton(onClick = { showDatePickerDialog = true }) {
                            Text("Cambiar fecha")
                        }
                    }
                }
            }

            // Nombre del cliente o nota. Es obligatorio: identifica el movimiento
            // y es lo que se muestra en el historial.
            item {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { viewModel.setDraftNote(it) },
                    label = { Text("Nombre del cliente o nota *") },
                    placeholder = { Text("Ej. Juan Pérez, pago parcial") },
                    isError = noteText.isBlank(),
                    supportingText = {
                        if (noteText.isBlank()) {
                            Text("Escribe a quién corresponde este movimiento")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_input")
                )
            }

            // Save Button
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        val method = selectedPaymentMethod ?: return@Button
                        viewModel.registerMovement(
                            type = selectedType,
                            lines = ticketLines.toList(),
                            totalCop = finalTotalCop,
                            paymentMethod = method,
                            transferAccountName = if (method == PaymentMethod.TRANSFERENCIA) selectedAccountName else null,
                            occurredAt = if (isAdmin) adminPastDateMillis else null,
                            debtorMemberId = selectedDebtorMember?.id,
                            debtorMemberName = selectedDebtorMember?.name,
                            note = noteText
                        ) {
                            onFinish()
                        }
                    },
                    enabled = canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("save_movement_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when (selectedType) {
                            MovementType.VENTA -> FinanceGreen
                            MovementType.COMPRA -> FinanceRed
                            MovementType.PRESTAMO -> FinanceOrange
                            MovementType.ABONO -> FinanceTeal
                        }
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Guardar ${selectedType.name} (${CurrencyFormatter.formatCop(finalTotalCop)})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Product entry Dialog (Quantity stepper, unit price <-> subtotal two-way link)
    productForEntry?.let { product ->
        ProductLineEntryDialog(
            product = product,
            onDismiss = { productForEntry = null },
            onAddLine = { line ->
                viewModel.addTicketDraftLine(line)
                productForEntry = null
            }
        )
    }

    // Admin Date picker dialog
    if (showDatePickerDialog) {
        AlertDialog(
            onDismissRequest = { showDatePickerDialog = false },
            title = { Text("Fecha del Movimiento (Admin)") },
            text = {
                Column {
                    Text("Como Administrador puedes seleccionar una fecha pasada (nunca futura):")
                    Spacer(modifier = Modifier.height(12.dp))
                    // Quick past options
                    val now = System.currentTimeMillis()
                    Button(
                        onClick = {
                            adminPastDateMillis = now
                            showDatePickerDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Hoy (Hora actual)")
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = {
                            adminPastDateMillis = now - 24 * 3600_000L
                            showDatePickerDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Ayer")
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = {
                            adminPastDateMillis = now - 48 * 3600_000L
                            showDatePickerDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Hace 2 días")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun ProductLineEntryDialog(
    product: Product,
    onDismiss: () -> Unit,
    onAddLine: (MovementLine) -> Unit
) {
    var quantity by remember { mutableIntStateOf(1) }
    // Pre-populate with standard reference price if configured, or empty
    var unitPriceText by remember {
        mutableStateOf(if (product.standardPriceCop > 0) product.standardPriceCop.toString() else "")
    }
    var subtotalText by remember {
        mutableStateOf(if (product.standardPriceCop > 0) product.standardPriceCop.toString() else "")
    }

    // Synchronize two-way linkage between Unit Price and Subtotal
    // Subtotal is source of truth!
    fun onUnitPriceChanged(newText: String) {
        val clean = newText.filter { it.isDigit() }
        unitPriceText = clean
        val uPrice = clean.toLongOrNull() ?: 0L
        val sub = uPrice * quantity
        subtotalText = if (sub > 0) sub.toString() else ""
    }

    fun onSubtotalChanged(newText: String) {
        val clean = newText.filter { it.isDigit() }
        subtotalText = clean
        val sub = clean.toLongOrNull() ?: 0L
        val uPrice = if (quantity > 0) sub / quantity else 0L
        unitPriceText = if (uPrice > 0) uPrice.toString() else ""
    }

    fun onQuantityChanged(newQty: Int) {
        if (newQty < 1) return
        quantity = newQty
        val uPrice = unitPriceText.toLongOrNull() ?: 0L
        val sub = uPrice * quantity
        subtotalText = if (sub > 0) sub.toString() else ""
    }

    val parsedSubtotal = subtotalText.toLongOrNull() ?: 0L
    val parsedUnitPrice = unitPriceText.toLongOrNull() ?: 0L
    val isValid = parsedSubtotal > 0 && parsedUnitPrice > 0 && quantity >= 1

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(product.name, fontWeight = FontWeight.Bold)
                Text(
                    text = product.category,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Quantity Stepper
                Text(
                    text = "Cantidad *",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalIconButton(
                        onClick = { onQuantityChanged(quantity - 1) },
                        enabled = quantity > 1,
                        modifier = Modifier.testTag("qty_minus_btn")
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Menos")
                    }
                    Text(
                        text = quantity.toString(),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .testTag("qty_display_text")
                    )
                    FilledTonalIconButton(
                        onClick = { onQuantityChanged(quantity + 1) },
                        modifier = Modifier.testTag("qty_plus_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Más")
                    }
                }

                // Unit Price input (starts empty, mandatory > 0)
                OutlinedTextField(
                    value = if (unitPriceText.isNotBlank()) CurrencyFormatter.formatRawThousands(unitPriceText.toLongOrNull() ?: 0L) else "",
                    onValueChange = { onUnitPriceChanged(it) },
                    label = { Text("Precio Unitario COP *") },
                    placeholder = { Text("Ej. 25.000") },
                    prefix = { Text("$ ", fontWeight = FontWeight.Bold) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("unit_price_input")
                )

                // Subtotal input (Source of truth)
                OutlinedTextField(
                    value = if (subtotalText.isNotBlank()) CurrencyFormatter.formatRawThousands(subtotalText.toLongOrNull() ?: 0L) else "",
                    onValueChange = { onSubtotalChanged(it) },
                    label = { Text("Subtotal de la Línea (Fuente de verdad) *") },
                    prefix = { Text("$ ", fontWeight = FontWeight.Bold) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("subtotal_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAddLine(
                        MovementLine(
                            productId = product.id,
                            productName = product.name,
                            quantity = quantity,
                            subtotalCop = parsedSubtotal,
                            unitPriceCop = parsedUnitPrice
                        )
                    )
                },
                enabled = isValid,
                modifier = Modifier.testTag("add_line_confirm_btn")
            ) {
                Text("Agregar al Ticket")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
