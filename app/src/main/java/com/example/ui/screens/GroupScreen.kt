package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Member
import com.example.data.model.Role
import com.example.ui.components.CompanyProfileHeaderTitle
import com.example.ui.components.CompanySwitcherDialog
import com.example.ui.components.CreateCompanyDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.FinanceViewModel
import com.example.util.CurrencyFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScreen(
    viewModel: FinanceViewModel,
    modifier: Modifier = Modifier,
    onSignOut: (() -> Unit)? = null
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val selectedGroupId by viewModel.selectedGroupId.collectAsStateWithLifecycle()
    val currentMember by viewModel.currentMember.collectAsStateWithLifecycle()
    val activeMembers by viewModel.activeMembers.collectAsStateWithLifecycle()
    val transferAccounts by viewModel.transferAccounts.collectAsStateWithLifecycle()
    val settlementSummary by viewModel.settlementSummary.collectAsStateWithLifecycle()

    var showInviteDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showAddAccountDialog by remember { mutableStateOf(false) }
    var generatedInviteCode by remember { mutableStateOf<String?>(null) }
    var showShareConfigDialog by remember { mutableStateOf(false) }
    var showCompanyPicker by remember { mutableStateOf(false) }
    var showCreateCompanyDialog by remember { mutableStateOf(false) }
    var showRenameCompanyDialog by remember { mutableStateOf(false) }
    var showDeleteCompanyDialog by remember { mutableStateOf(false) }
    var showLeaveGroupDialog by remember { mutableStateOf(false) }
    var memberToRemove by remember { mutableStateOf<Member?>(null) }
    // Estado del dialogo "unirse con codigo", elevado aqui para poder
    // pre-rellenar el nombre desde la cuenta de Google autenticada.
    var inputCode by remember { mutableStateOf("") }
    var inputName by remember { mutableStateOf("") }
    var selectedMemberForDetail by remember { mutableStateOf<Member?>(null) }

    val isAdmin = currentMember?.role == Role.ADMIN
    val activeGroup = groups.firstOrNull { it.id == selectedGroupId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    CompanyProfileHeaderTitle(
                        companyName = activeGroup?.name ?: "SocioFinanzas",
                        subtitle = "Grupo y Socios",
                        onClick = { showCompanyPicker = true },
                        modifier = Modifier.testTag("group_company_switcher")
                    )
                },
                actions = {
                    if (isAdmin && activeGroup != null) {
                        IconButton(
                            onClick = { showRenameCompanyDialog = true },
                            modifier = Modifier.testTag("rename_group_btn")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Renombrar empresa")
                        }
                        IconButton(
                            onClick = { showDeleteCompanyDialog = true },
                            modifier = Modifier.testTag("delete_group_btn")
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Eliminar empresa",
                                tint = FinanceRed
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
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
            // Estado inicial: aun no hay ninguna empresa
            if (groups.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("empty_companies_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = BrandLimeLight),
                        border = BorderStroke(1.dp, BrandLime)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Todavía no tienes ninguna empresa",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = BrandDark
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Crea tu primera empresa para empezar a registrar ventas, " +
                                    "compras y préstamos. También puedes unirte a la de un socio con un código.",
                                style = MaterialTheme.typography.bodySmall,
                                color = BrandTextSecondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { showCreateCompanyDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("create_first_company_btn")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Crear mi primera empresa")
                            }
                        }
                    }
                }
            }

            // Google Account Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("google_account_info_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BrandSurface),
                    border = BorderStroke(1.dp, BrandBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(BrandLime),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = (currentMember?.name?.take(1) ?: "G").uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = BrandDark
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = currentMember?.name ?: "Usuario Google",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandDark
                                )
                                Text(
                                    text = currentMember?.email?.takeIf { it.isNotBlank() } ?: "Cuenta de Google Gmail",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BrandTextSecondary
                                )
                                Text(
                                    text = "Sesión activa • Administrador",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF166534),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        if (onSignOut != null) {
                            OutlinedButton(
                                onClick = onSignOut,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = FinanceRed),
                                border = BorderStroke(1.dp, FinanceRed.copy(alpha = 0.5f)),
                                modifier = Modifier.testTag("btn_sign_out_group")
                            ) {
                                Text("Cerrar sesión", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            // Salir de la empresa (solo si hay una seleccionada)
            if (activeGroup != null) {
                item {
                    OutlinedButton(
                        onClick = { showLeaveGroupDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = FinanceRed),
                        border = BorderStroke(1.dp, FinanceRed.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("leave_group_btn")
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Abandonar esta empresa", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Group Header Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E40AF)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeGroup?.name ?: "Grupo de Finanzas",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Moneda: Peso Colombiano (COP) • Zona: America/Bogota",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Groups,
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // Members section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Saldos de Socios y Empresa",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (isAdmin) {
                        IconButton(
                            onClick = { showInviteDialog = true },
                            modifier = Modifier.testTag("create_invite_btn")
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "Invitar socio")
                        }
                    }
                }
            }

            // Card for Empresa (Fondo Común y Liquidez)
            item {
                val totalLiquidCash = settlementSummary?.moneyLocations?.filter { !it.isSubRow }?.sumOf { it.netCop } ?: 0L

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("company_financial_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1D4ED8)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storefront,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = activeGroup?.name ?: "La Empresa",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color.White.copy(alpha = 0.25f)
                                ) {
                                    Text(
                                        text = "EMPRESA",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = if (totalLiquidCash >= 0) "+ ${CurrencyFormatter.formatCop(totalLiquidCash)}" else "- ${CurrencyFormatter.formatCop(kotlin.math.abs(totalLiquidCash))}",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFD4F73C),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // Partners financial status items
            items(activeMembers, key = { it.id }) { member ->
                val distRow = settlementSummary?.distributions?.firstOrNull { it.memberId == member.id }
                val shareAmount = distRow?.shareAmountCop ?: 0L
                val debtAmount = distRow?.debtCop ?: member.initialDebtCop
                val netBalance = shareAmount - debtAmount

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedMemberForDetail = member }
                        .testTag("member_card_${member.id}"),
                    shape = RoundedCornerShape(12.dp),
                    border = if (member.id == currentMember?.id) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            netBalance > 0 -> BrandLime
                                            netBalance < 0 -> FinanceRedBg
                                            else -> BrandSurfaceMuted
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = member.name.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = when {
                                        netBalance > 0 -> BrandDark
                                        netBalance < 0 -> FinanceRed
                                        else -> BrandTextSecondary
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = member.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = when {
                                        netBalance > 0 -> BrandLime
                                        netBalance < 0 -> FinanceRedBg
                                        else -> BrandSurfaceMuted
                                    }
                                ) {
                                    Text(
                                        text = when {
                                            netBalance > 0 -> "A FAVOR"
                                            netBalance < 0 -> "DEBE"
                                            else -> "AL DÍA"
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            netBalance > 0 -> BrandDark
                                            netBalance < 0 -> FinanceRed
                                            else -> BrandTextSecondary
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = when {
                                netBalance > 0 -> "+ ${CurrencyFormatter.formatCop(netBalance)}"
                                netBalance < 0 -> "- ${CurrencyFormatter.formatCop(kotlin.math.abs(netBalance))}"
                                else -> "$0 COP"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                netBalance > 0 -> BrandDark
                                netBalance < 0 -> FinanceRed
                                else -> BrandTextSecondary
                            },
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // Share percentages configuration section (Admin)
            if (isAdmin) {
                item {
                    Text(
                        text = "Configuración del Reparto",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            val partnersSum = activeMembers.sumOf { it.sharePercentCentecimas }
                            val companyShare = 10000 - partnersSum

                            Text(
                                text = "Empresa (Fondo común): ${CurrencyFormatter.formatPercentage(companyShare)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            activeMembers.forEach { m ->
                                Text(
                                    text = "• ${m.name}: ${CurrencyFormatter.formatPercentage(m.sharePercentCentecimas)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { showShareConfigDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("edit_shares_btn")
                            ) {
                                Text("Ajustar Porcentajes")
                            }
                        }
                    }
                }
            }

            // Join with invitation code
            item {
                OutlinedButton(
                    onClick = {
                        // El nombre se toma de la cuenta de Google ya autenticada
                        if (inputName.isBlank()) {
                            inputName = currentMember?.name
                                ?: currentMember?.email?.substringBefore("@")
                                ?: ""
                        }
                        showJoinDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("join_invite_code_btn")
                ) {
                    Icon(Icons.Default.Key, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unirse a un Grupo con Código de Invitación")
                }
            }

            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    // Invite code generation dialog (Admin)
    if (showInviteDialog) {
        var inviteRole by remember { mutableStateOf(Role.SOCIO) }
        var inviteHours by remember { mutableIntStateOf(48) }
        var inviteUses by remember { mutableIntStateOf(1) }

        AlertDialog(
            onDismissRequest = {
                showInviteDialog = false
                generatedInviteCode = null
            },
            title = { Text("Generar Invitación") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (generatedInviteCode == null) {
                        Text(
                            text = "Genera un código de 8 caracteres (sin 0, O, 1, I) para compartir por WhatsApp:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Rol a otorgar: ", style = MaterialTheme.typography.bodyMedium)
                            FilterChip(
                                selected = inviteRole == Role.SOCIO,
                                onClick = { inviteRole = Role.SOCIO },
                                label = { Text("Socio") }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            FilterChip(
                                selected = inviteRole == Role.ADMIN,
                                onClick = { inviteRole = Role.ADMIN },
                                label = { Text("Admin") }
                            )
                        }
                    } else {
                        // Display generated code
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "CÓDIGO DE INVITACIÓN",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = generatedInviteCode ?: "",
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Válido por 48 horas (1 uso)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (generatedInviteCode == null) {
                    Button(
                        onClick = {
                            viewModel.createInvitation(inviteRole, inviteHours, inviteUses) { inv ->
                                generatedInviteCode = inv.code
                            }
                        },
                        modifier = Modifier.testTag("confirm_create_invite_btn")
                    ) {
                        Text("Generar Código")
                    }
                } else {
                    Button(
                        onClick = {
                            showInviteDialog = false
                            generatedInviteCode = null
                        }
                    ) {
                        Text("Listo")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showInviteDialog = false
                        generatedInviteCode = null
                    }
                ) {
                    Text("Cerrar")
                }
            }
        )
    }

    // Join with code dialog
    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            title = { Text("Unirse con Código") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = inputCode,
                        onValueChange = { inputCode = it.uppercase() },
                        label = { Text("Código de 8 caracteres *") },
                        placeholder = { Text("Ej. 9X7K2M4P") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("join_code_input")
                    )
                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        label = { Text("Tu Nombre Visible") },
                        placeholder = { Text("Ej. Daniel") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("join_name_input")
                    )
                    Text(
                        text = "No hace falta correo: se usará la cuenta de Google con la que has iniciado sesión.",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandTextSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // IMPORTANTE: el nombre y el correo deben salir de la CUENTA
                        // con la que se ha iniciado sesion, no del perfil local
                        // (que puede ser el de quien creo la invitacion).
                        val correo = googleAccountEmail
                        val nombre = inputName.ifBlank {
                            googleAccountName.ifBlank { correo.substringBefore("@") }.ifBlank { "Socio" }
                        }
                        viewModel.useInvitation(inputCode, nombre, correo) {
                            showJoinDialog = false
                        }
                    },
                    enabled = inputCode.isNotBlank(),
                    modifier = Modifier.testTag("confirm_join_btn")
                ) {
                    Text("Unirse al Grupo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Add transfer account dialog (Admin)
    if (showAddAccountDialog) {
        var accountNameInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddAccountDialog = false },
            title = { Text("Agregar Cuenta de Transferencia") },
            text = {
                OutlinedTextField(
                    value = accountNameInput,
                    onValueChange = { accountNameInput = it },
                    label = { Text("Nombre de la Cuenta o App *") },
                    placeholder = { Text("Ej. Dale, Lulo Bank, BBVA") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addTransferAccount(accountNameInput) {
                            showAddAccountDialog = false
                        }
                    },
                    enabled = accountNameInput.isNotBlank()
                ) {
                    Text("Agregar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddAccountDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Adjust shares dialog
    if (showShareConfigDialog) {
        AdjustSharesDialog(
            members = activeMembers,
            onDismiss = { showShareConfigDialog = false },
            onSave = { memberId, newCentecimas ->
                viewModel.updateMemberShare(memberId, newCentecimas)
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

    if (showRenameCompanyDialog && activeGroup != null) {
        var renameText by remember { mutableStateOf(activeGroup.name) }
        AlertDialog(
            onDismissRequest = { showRenameCompanyDialog = false },
            title = { Text("Renombrar Empresa") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Nombre de la Empresa") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("rename_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameText.isNotBlank()) {
                            viewModel.renameGroup(activeGroup.id, renameText) {
                                showRenameCompanyDialog = false
                            }
                        }
                    },
                    enabled = renameText.isNotBlank(),
                    modifier = Modifier.testTag("confirm_rename_btn")
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameCompanyDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showDeleteCompanyDialog && activeGroup != null) {
        AlertDialog(
            onDismissRequest = { showDeleteCompanyDialog = false },
            title = { Text("Eliminar empresa") },
            text = {
                Text(
                    "¿Seguro que quieres eliminar '${activeGroup.name}'?\n\n" +
                    "Se borrarán TODOS sus datos: movimientos, socios, catálogo, " +
                    "cuentas y cortes. Esta acción no se puede deshacer."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteGroup(activeGroup.id) {
                            showDeleteCompanyDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FinanceRed),
                    modifier = Modifier.testTag("confirm_delete_group_btn")
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteCompanyDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showLeaveGroupDialog && activeGroup != null) {
        AlertDialog(
            onDismissRequest = { showLeaveGroupDialog = false },
            title = { Text("Abandonar empresa") },
            text = {
                Text(
                    "¿Seguro que quieres salir de '${activeGroup.name}'?\n\n" +
                    "Dejarás de tener acceso a ella, pero tu historial de movimientos " +
                    "se conserva como evidencia. El administrador seguirá viéndola."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLeaveGroupDialog = false
                        viewModel.leaveCurrentGroup()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FinanceRed),
                    modifier = Modifier.testTag("confirm_leave_group_btn")
                ) {
                    Text("Salir")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveGroupDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    selectedMemberForDetail?.let { member ->
        val distRow = settlementSummary?.distributions?.firstOrNull { it.memberId == member.id }
        val shareAmount = distRow?.shareAmountCop ?: 0L
        val debtAmount = distRow?.debtCop ?: member.initialDebtCop
        val netBalance = shareAmount - debtAmount

        AlertDialog(
            onDismissRequest = { selectedMemberForDetail = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(BrandLime),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = member.name.take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            color = BrandDark
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(member.name, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (member.role == Role.ADMIN) "Administrador" else "Socio Colaborador",
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandTextSecondary
                        )
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Correo: ${member.email.ifBlank { "No registrado" }}", style = MaterialTheme.typography.bodyMedium)
                    Text("Participación en utilidades: ${member.sharePercentCentecimas / 100}%", style = MaterialTheme.typography.bodyMedium)
                    Text("Ganancia neta calculada: ${com.example.util.CurrencyFormatter.formatCop(shareAmount)}", style = MaterialTheme.typography.bodyMedium)
                    Text("Deuda o adelantos: ${com.example.util.CurrencyFormatter.formatCop(debtAmount)}", style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "Saldo final a entregar: ${com.example.util.CurrencyFormatter.formatCop(netBalance)}",
                        fontWeight = FontWeight.Bold,
                        color = if (netBalance >= 0) Color(0xFF166534) else FinanceRed
                    )

                    // Acciones de administracion sobre este socio.
                    // Solo el Admin, y nunca sobre su propia ficha.
                    if (isAdmin && member.id != currentMember?.id && member.active) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        Text(
                            text = "Administrar socio",
                            style = MaterialTheme.typography.labelMedium,
                            color = BrandTextSecondary
                        )

                        // Cambiar entre Admin y Socio
                        OutlinedButton(
                            onClick = {
                                val nuevoRol = if (member.role == Role.ADMIN) Role.SOCIO else Role.ADMIN
                                viewModel.updateMemberRole(member.id, nuevoRol)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("toggle_member_role_btn")
                        ) {
                            Icon(
                                if (member.role == Role.ADMIN) Icons.Default.PersonOutline else Icons.Default.AdminPanelSettings,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (member.role == Role.ADMIN) "Quitar privilegios de Admin"
                                else "Nombrar administrador"
                            )
                        }

                        // Desvincular del grupo conservando su historial
                        OutlinedButton(
                            onClick = {
                                selectedMemberForDetail = null
                                memberToRemove = member
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = FinanceRed),
                            border = BorderStroke(1.dp, FinanceRed.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("remove_member_btn")
                        ) {
                            Icon(Icons.Default.PersonRemove, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Desvincular de la empresa")
                        }

                        Text(
                            text = "Al desvincular, el socio pierde el acceso pero su historial de " +
                                "movimientos se conserva como evidencia.",
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandTextSecondary
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { selectedMemberForDetail = null },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandDark, contentColor = Color.White)
                ) {
                    Text("Cerrar")
                }
            }
        )
    }

    // Confirmacion de desvinculacion de un socio
    memberToRemove?.let { objetivo ->
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            title = { Text("Desvincular socio") },
            text = {
                Text(
                    "¿Seguro que quieres desvincular a '${objetivo.name}' de esta empresa?\n\n" +
                    "Perderá el acceso y dejará de ver la empresa en su móvil, pero TODOS " +
                    "sus movimientos se conservarán como evidencia y seguirán apareciendo " +
                    "en los cortes y en el reparto."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deactivateMember(objetivo.id)
                        memberToRemove = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FinanceRed),
                    modifier = Modifier.testTag("confirm_remove_member_btn")
                ) {
                    Text("Desvincular")
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToRemove = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun AdjustSharesDialog(
    members: List<Member>,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit
) {
    val shareInputs = remember {
        mutableStateMapOf<String, String>().apply {
            members.forEach { put(it.id, (it.sharePercentCentecimas / 100).toString()) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajustar Porcentajes de Socios") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "El porcentaje restante se asigna a la Empresa. El total debe ser 100,00 %.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                members.forEach { m ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(m.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = shareInputs[m.id] ?: "",
                            onValueChange = { shareInputs[m.id] = it.filter { c -> c.isDigit() } },
                            modifier = Modifier.width(90.dp),
                            suffix = { Text("%") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    members.forEach { m ->
                        val pct = (shareInputs[m.id]?.toIntOrNull() ?: 0) * 100
                        onSave(m.id, pct)
                    }
                    onDismiss()
                }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
