package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.auth.AuthRepository
import com.example.data.model.MovementType
import com.example.ui.components.NovedadesDialog
import com.example.ui.screens.*
import com.example.ui.theme.SocioFinanzasTheme
import com.example.ui.viewmodel.FinanceViewModel

enum class MainNavTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    INICIO("Inicio", Icons.Filled.Home, Icons.Outlined.Home, "nav_inicio"),
    REGISTRAR("Registrar", Icons.Filled.AddCircle, Icons.Outlined.AddCircleOutline, "nav_registrar"),
    CATALOGO("Catálogo", Icons.Filled.Storefront, Icons.Outlined.Storefront, "nav_catalogo"),
    CUENTAS("Cuentas", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet, "nav_cuentas"),
    GRUPO("Grupo", Icons.Filled.Groups, Icons.Outlined.Groups, "nav_grupo")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SocioFinanzasTheme {
                val authRepository = remember { AuthRepository() }
                val currentUser by authRepository.authStateFlow().collectAsStateWithLifecycle(initialValue = authRepository.currentUser)

                if (currentUser == null) {
                    LoginScreen(
                        onSignInWithGoogle = {
                            authRepository.signInWithGoogle(this@MainActivity).map { }
                        }
                    )
                } else {
                    val viewModel: FinanceViewModel = viewModel()
                    var currentTab by remember { mutableStateOf(MainNavTab.INICIO) }
                    var showNovedadesDialog by remember { mutableStateOf(false) }
                    var selectedRegisterType by remember { mutableStateOf(MovementType.VENTA) }

                    val snackbarHostState = remember { SnackbarHostState() }
                    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
                    val currentMember by viewModel.currentMember.collectAsStateWithLifecycle()
                    val isAdmin = currentMember?.role == com.example.data.model.Role.ADMIN

                    LaunchedEffect(currentUser) {
                        currentUser?.let { user ->
                            viewModel.setAuthenticatedGoogleUser(user)
                        }
                    }

                    val visibleTabs = remember(isAdmin) {
                        if (isAdmin) {
                            MainNavTab.values().toList()
                        } else {
                            listOf(MainNavTab.INICIO, MainNavTab.REGISTRAR, MainNavTab.CUENTAS, MainNavTab.GRUPO)
                        }
                    }

                    LaunchedEffect(isAdmin) {
                        if (!isAdmin && currentTab == MainNavTab.CATALOGO) {
                            currentTab = MainNavTab.INICIO
                        }
                    }

                    // BackHandler returns to Home if on another tab
                    if (currentTab != MainNavTab.INICIO) {
                        BackHandler {
                            currentTab = MainNavTab.INICIO
                        }
                    }

                    LaunchedEffect(userMessage) {
                        userMessage?.let { msg ->
                            snackbarHostState.showSnackbar(
                                message = msg.message,
                                duration = SnackbarDuration.Short
                            )
                            viewModel.clearUserMessage()
                        }
                    }

                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        bottomBar = {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 0.dp,
                                modifier = Modifier.testTag("bottom_nav_bar")
                            ) {
                                visibleTabs.forEach { tab ->
                                    val selected = currentTab == tab
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { currentTab = tab },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.title
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = com.example.ui.theme.BrandDark,
                                            indicatorColor = com.example.ui.theme.BrandLime,
                                            unselectedIconColor = com.example.ui.theme.BrandTextSecondary
                                        ),
                                        alwaysShowLabel = false,
                                        modifier = Modifier.testTag(tab.testTag)
                                    )
                                }
                            }
                        },
                        contentWindowInsets = WindowInsets.safeDrawing,
                        modifier = Modifier.fillMaxSize()
                    ) { innerPadding ->
                        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                            when (currentTab) {
                                MainNavTab.INICIO -> {
                                    DashboardScreen(
                                        viewModel = viewModel,
                                        onNavigateToRegisterWithType = { type ->
                                            selectedRegisterType = type
                                            currentTab = MainNavTab.REGISTRAR
                                        },
                                        onOpenNovedades = { showNovedadesDialog = true }
                                    )
                                }
                                MainNavTab.REGISTRAR -> {
                                    RegisterMovementScreen(
                                        viewModel = viewModel,
                                        initialType = selectedRegisterType,
                                        onFinish = { currentTab = MainNavTab.INICIO }
                                    )
                                }
                                MainNavTab.CATALOGO -> {
                                    if (isAdmin) {
                                        CatalogScreen(
                                            viewModel = viewModel,
                                            onNavigateToRegisterWithType = { type ->
                                                selectedRegisterType = type
                                                currentTab = MainNavTab.REGISTRAR
                                            }
                                        )
                                    } else {
                                        LaunchedEffect(Unit) {
                                            currentTab = MainNavTab.INICIO
                                        }
                                    }
                                }
                                MainNavTab.CUENTAS -> {
                                    SettlementScreen(viewModel = viewModel)
                                }
                                MainNavTab.GRUPO -> {
                                    GroupScreen(
                                        viewModel = viewModel,
                                        onSignOut = { authRepository.signOut() }
                                    )
                                }
                            }
                        }
                    }

                    if (showNovedadesDialog) {
                        NovedadesDialog(
                            viewModel = viewModel,
                            onDismiss = { showNovedadesDialog = false }
                        )
                    }
                }
            }
        }
    }
}
