package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.*
import com.example.ui.components.CompanySwitcherDialog
import com.example.ui.components.CreateCompanyDialog
import com.example.ui.components.MovementDetailDialog
import com.example.ui.components.MovementItemRow
import com.example.ui.components.SummaryMetricCard
import com.example.ui.theme.*
import com.example.ui.viewmodel.FinanceViewModel
import com.example.util.DateFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: FinanceViewModel,
    onNavigateToRegisterWithType: (MovementType) -> Unit,
    onOpenNovedades: () -> Unit,
    modifier: Modifier = Modifier
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val selectedGroupId by viewModel.selectedGroupId.collectAsStateWithLifecycle()
    val currentMember by viewModel.currentMember.collectAsStateWithLifecycle()
    val activeMembers by viewModel.activeMembers.collectAsStateWithLifecycle()
    val summary by viewModel.settlementSummary.collectAsStateWithLifecycle()
    val movements by viewModel.movements.collectAsStateWithLifecycle()
    val unreadCount by viewModel.unreadNotificationsCount.collectAsStateWithLifecycle()

    var selectedMovementForDetail by remember { mutableStateOf<Movement?>(null) }
    var filterType by remember { mutableStateOf<MovementType?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showCompanyPicker by remember { mutableStateOf(false) }
    var showCreateCompanyDialog by remember { mutableStateOf(false) }

    // Floating Vertical Speed Dial Action Board state
    var isFabExpanded by remember { mutableStateOf(false) }

    val activeGroup = groups.firstOrNull { it.id == selectedGroupId }
    val activeGroupName = activeGroup?.name ?: "SocioFinanzas"

    val filteredMovements = remember(movements, filterType, searchQuery) {
        movements.filter { m ->
            val matchType = filterType == null || m.type == filterType
            val matchQuery = searchQuery.isBlank() ||
                    m.registeredByName.contains(searchQuery, ignoreCase = true) ||
                    m.note.contains(searchQuery, ignoreCase = true) ||
                    m.orderCode.contains(searchQuery, ignoreCase = true) ||
                    (m.debtorMemberName?.contains(searchQuery, ignoreCase = true) == true) ||
                    m.linesJson.contains(searchQuery, ignoreCase = true)
            matchType && matchQuery
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        // Instagram-style company switchable title
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showCompanyPicker = true }
                                .padding(vertical = 2.dp, horizontal = 2.dp)
                                .testTag("company_selector_header")
                        ) {
                            Text(
                                text = activeGroupName,
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

                        // User profile Google account badge
                        currentMember?.let { member ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .padding(vertical = 1.dp)
                                    .testTag("persona_badge")
                            ) {
                                Text(
                                    text = "${member.name} (${if (member.role == Role.ADMIN) "Admin" else "Socio"})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                },
                actions = {
                    // Novedades Bell with badge
                    IconButton(
                        onClick = onOpenNovedades,
                        modifier = Modifier.testTag("novedades_bell_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (unreadCount > 0) {
                                    Badge { Text(unreadCount.toString()) }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = "Novedades")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            // Speed Dial: Vertical Floating Action Board
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AnimatedVisibility(
                    visible = isFabExpanded,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        // Venta Action
                        ExtendedFloatingActionButton(
                            onClick = {
                                isFabExpanded = false
                                onNavigateToRegisterWithType(MovementType.VENTA)
                            },
                            icon = { Icon(Icons.Default.TrendingUp, contentDescription = null, tint = BrandDark) },
                            text = { Text("Registrar Venta (+)", fontWeight = FontWeight.Bold) },
                            containerColor = BrandLime,
                            contentColor = BrandDark,
                            modifier = Modifier.testTag("speed_dial_venta")
                        )

                        // Compra Action
                        ExtendedFloatingActionButton(
                            onClick = {
                                isFabExpanded = false
                                onNavigateToRegisterWithType(MovementType.COMPRA)
                            },
                            icon = { Icon(Icons.Default.TrendingDown, contentDescription = null, tint = Color.White) },
                            text = { Text("Registrar Compra (−)", fontWeight = FontWeight.Bold) },
                            containerColor = BrandDark,
                            contentColor = Color.White,
                            modifier = Modifier.testTag("speed_dial_compra")
                        )

                        // Préstamo Action
                        ExtendedFloatingActionButton(
                            onClick = {
                                isFabExpanded = false
                                onNavigateToRegisterWithType(MovementType.PRESTAMO)
                            },
                            icon = { Icon(Icons.Default.Handshake, contentDescription = null, tint = BrandDark) },
                            text = { Text("Registrar Préstamo", fontWeight = FontWeight.Bold) },
                            containerColor = BrandSurfaceMuted,
                            contentColor = BrandDark,
                            modifier = Modifier.testTag("speed_dial_prestamo")
                        )

                        // Nueva Empresa Action
                        ExtendedFloatingActionButton(
                            onClick = {
                                isFabExpanded = false
                                showCreateCompanyDialog = true
                            },
                            icon = { Icon(Icons.Default.AddBusiness, contentDescription = null, tint = Color.White) },
                            text = { Text("Crear Empresa", fontWeight = FontWeight.Bold) },
                            containerColor = BrandBlue,
                            contentColor = Color.White,
                            modifier = Modifier.testTag("speed_dial_empresa")
                        )
                    }
                }

                // Main Toggle FAB
                FloatingActionButton(
                    onClick = { isFabExpanded = !isFabExpanded },
                    containerColor = if (isFabExpanded) BrandSurfaceMuted else BrandDark,
                    contentColor = if (isFabExpanded) BrandTextPrimary else BrandLime,
                    modifier = Modifier.testTag("fab_register_movement")
                ) {
                    Icon(
                        imageVector = if (isFabExpanded) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = if (isFabExpanded) "Cerrar menú" else "Acciones rápidas"
                    )
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Month banner
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Mes Actual: ${summary?.monthTitle ?: DateFormatter.formatMonthYear(DateFormatter.currentMonthKey())}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827)
                    )
                    if (summary?.isClosed == true) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = "Corte Cerrado",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Summary Cards 2x2 Grid
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SummaryMetricCard(
                            title = "Capital / Ventas",
                            amountCop = summary?.totalSalesCop ?: 0L,
                            icon = Icons.Default.TrendingUp,
                            color = FinanceGreen,
                            bgColor = FinanceGreenBg,
                            prefix = "+",
                            modifier = Modifier.weight(1f)
                        )
                        SummaryMetricCard(
                            title = "Compras",
                            amountCop = summary?.totalPurchasesCop ?: 0L,
                            icon = Icons.Default.TrendingDown,
                            color = FinanceRed,
                            bgColor = FinanceRedBg,
                            prefix = "−",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SummaryMetricCard(
                            title = "Utilidad Neta",
                            amountCop = summary?.utilityCop ?: 0L,
                            icon = Icons.Default.ShowChart,
                            color = if ((summary?.utilityCop ?: 0L) >= 0) Color(0xFF15803D) else Color(0xFFB91C1C),
                            bgColor = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryMetricCard(
                            title = "Préstamos",
                            amountCop = summary?.totalLoansCop ?: 0L,
                            icon = Icons.Default.Handshake,
                            color = FinanceOrange,
                            bgColor = FinanceOrangeBg,
                            prefix = "−",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Section: Movimientos Recientes
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Movimientos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Search bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar por código, producto, socio...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                            }
                        }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("movement_search_input")
                )
            }

            // Filter chips
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filterType == null,
                        onClick = { filterType = null },
                        label = { Text("Todos") }
                    )
                    FilterChip(
                        selected = filterType == MovementType.VENTA,
                        onClick = { filterType = if (filterType == MovementType.VENTA) null else MovementType.VENTA },
                        label = { Text("Ventas (+)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BrandLime,
                            selectedLabelColor = BrandDark
                        )
                    )
                    FilterChip(
                        selected = filterType == MovementType.COMPRA,
                        onClick = { filterType = if (filterType == MovementType.COMPRA) null else MovementType.COMPRA },
                        label = { Text("Compras (−)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BrandDark,
                            selectedLabelColor = Color.White
                        )
                    )
                    FilterChip(
                        selected = filterType == MovementType.PRESTAMO,
                        onClick = { filterType = if (filterType == MovementType.PRESTAMO) null else MovementType.PRESTAMO },
                        label = { Text("Préstamos") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BrandSurfaceMuted,
                            selectedLabelColor = BrandTextPrimary
                        )
                    )
                }
            }

            // List of movements
            if (filteredMovements.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.ReceiptLong,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Aún no hay movimientos registrados en $activeGroupName",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Toca el botón '+' para abrir las opciones rápidas",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredMovements, key = { it.id }) { movement ->
                    MovementItemRow(
                        movement = movement,
                        onClick = { selectedMovementForDetail = movement }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    // Detail Dialog
    selectedMovementForDetail?.let { movement ->
        MovementDetailDialog(
            movement = movement,
            viewModel = viewModel,
            onDismiss = { selectedMovementForDetail = null }
        )
    }

    // Instagram-style Company Switcher Dialog
    if (showCompanyPicker) {
        CompanySwitcherDialog(
            groups = groups,
            selectedGroupId = selectedGroupId,
            onSelectGroup = { viewModel.selectGroup(it) },
            onOpenCreateCompany = { showCreateCompanyDialog = true },
            onDismiss = { showCompanyPicker = false }
        )
    }

    // Create Company Dialog
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
}
