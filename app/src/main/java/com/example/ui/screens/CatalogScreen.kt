package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.MovementType
import com.example.data.model.Product
import com.example.data.model.Role
import com.example.ui.components.CompanyProfileHeaderTitle
import com.example.ui.components.CompanySwitcherDialog
import com.example.ui.components.CreateCompanyDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.FinanceViewModel
import com.example.util.CurrencyFormatter

// Available visual icons/presets for products
val PRESET_PRODUCT_ICONS = listOf(
    "cafe" to Icons.Default.LocalCafe,
    "comida" to Icons.Default.Fastfood,
    "bebida" to Icons.Default.LocalBar,
    "panaderia" to Icons.Default.BakeryDining,
    "restaurante" to Icons.Default.Restaurant,
    "tienda" to Icons.Default.Storefront,
    "postre" to Icons.Default.Icecream,
    "caja" to Icons.Default.Inventory2
)

fun getIconForPhotoUri(uri: String): ImageVector {
    return PRESET_PRODUCT_ICONS.firstOrNull { it.first == uri }?.second ?: Icons.Default.Fastfood
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    viewModel: FinanceViewModel,
    onNavigateToRegisterWithType: (MovementType) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val selectedGroupId by viewModel.selectedGroupId.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val currentMember by viewModel.currentMember.collectAsStateWithLifecycle()

    val activeGroup = groups.firstOrNull { it.id == selectedGroupId }
    val activeGroupName = activeGroup?.name ?: "SocioFinanzas"

    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var showArchived by remember { mutableStateOf(false) }
    var showCompanyPicker by remember { mutableStateOf(false) }
    var showCreateCompanyDialog by remember { mutableStateOf(false) }

    val isAdmin = currentMember?.role == Role.ADMIN

    if (!isAdmin) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Acceso restringido",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Acceso restringido",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Solo el usuario con rol de Administrador puede acceder y gestionar el catálogo de productos.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
        return
    }

    val displayedProducts = remember(products, searchQuery, showArchived) {
        products.filter { p ->
            val matchArchived = if (showArchived) true else !p.isArchived
            val matchSearch = searchQuery.isBlank() || p.name.contains(searchQuery, ignoreCase = true) || p.category.contains(searchQuery, ignoreCase = true)
            matchArchived && matchSearch
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    CompanyProfileHeaderTitle(
                        companyName = activeGroupName,
                        subtitle = "Catálogo de Productos",
                        onClick = { showCompanyPicker = true },
                        modifier = Modifier.testTag("catalog_company_switcher")
                    )
                },
                actions = {
                    IconButton(
                        onClick = { showArchived = !showArchived },
                        modifier = Modifier.testTag("toggle_archived_btn")
                    ) {
                        Icon(
                            imageVector = if (showArchived) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showArchived) "Ocultar archivados" else "Ver archivados"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            if (isAdmin) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = Color(0xFF2563EB),
                    contentColor = Color.White,
                    modifier = Modifier.testTag("fab_add_product")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Nuevo Producto")
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Buscar producto en catálogo...") },
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
                    .padding(vertical = 8.dp)
                    .testTag("catalog_search_bar")
            )

            // Info note
            Text(
                text = "Catálogo propio de esta empresa. Los precios son sugeridos y se pueden ajustar en cada venta o compra.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (displayedProducts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isBlank()) "Aún no hay productos en esta empresa. Agrega el primero con el botón '+'" else "No se encontraron productos",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(displayedProducts, key = { it.id }) { product ->
                        ProductGridCard(
                            product = product,
                            isAdmin = isAdmin,
                            onSell = {
                                viewModel.addProductToDraftAndPrepare(product, 1, MovementType.VENTA)
                                onNavigateToRegisterWithType(MovementType.VENTA)
                            },
                            onBuy = {
                                viewModel.addProductToDraftAndPrepare(product, 1, MovementType.COMPRA)
                                onNavigateToRegisterWithType(MovementType.COMPRA)
                            },
                            onArchive = { viewModel.archiveProduct(product.id) },
                            onUnarchive = { viewModel.unarchiveProduct(product.id) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(72.dp))
                    }
                }
            }
        }
    }

    // Add Product Dialog
    if (showAddDialog) {
        AddProductDialog(
            existingProducts = products,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, category, photoUri, standardPrice ->
                viewModel.addProduct(name, category, photoUri, standardPrice) {
                    showAddDialog = false
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
}

@Composable
fun ProductGridCard(
    product: Product,
    isAdmin: Boolean,
    onSell: () -> Unit = {},
    onBuy: () -> Unit = {},
    onArchive: () -> Unit,
    onUnarchive: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("product_card_${product.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (product.isArchived) BrandSurfaceMuted.copy(alpha = 0.5f) else BrandSurface
        ),
        border = BorderStroke(1.dp, BrandBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Photo / Icon box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFEFF6FF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getIconForPhotoUri(product.photoUri),
                    contentDescription = product.name,
                    tint = Color(0xFF2563EB),
                    modifier = Modifier.size(46.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = product.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = product.category,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFF1E3A8A)
            )

            // Suggested Standard Price
            if (product.standardPriceCop > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Ref: ${CurrencyFormatter.formatCop(product.standardPriceCop)}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF166534)
                )
            }

            if (product.isArchived) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFFEE2E2)
                ) {
                    Text(
                        text = "Archivado",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFB91C1C),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (isAdmin && !product.isArchived) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onArchive,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2563EB),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .testTag("archive_btn_${product.id}"),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Archivar", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
                }
            }

            // Un producto archivado se puede devolver al catalogo
            if (isAdmin && product.isArchived) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onUnarchive,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF2563EB)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .testTag("unarchive_btn_${product.id}"),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Unarchive, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color(0xFF2563EB))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Desarchivar", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF2563EB))
                }
            }
        }
    }
}

@Composable
fun AddProductDialog(
    existingProducts: List<Product>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, category: String, photoUri: String, standardPrice: Long) -> Unit
) {
    var nameText by remember { mutableStateOf("") }
    var categoryText by remember { mutableStateOf("Comidas / Bebidas") }
    var standardPriceInput by remember { mutableStateOf("") }
    var selectedIconTag by remember { mutableStateOf("comida") }
    var selectedCustomPhotoUri by remember { mutableStateOf<String?>(null) }

    // Android Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedCustomPhotoUri = uri.toString()
            selectedIconTag = "custom"
        }
    }

    val isDuplicate = remember(nameText, existingProducts) {
        nameText.isNotBlank() && existingProducts.any { it.name.trim().equals(nameText.trim(), ignoreCase = true) }
    }

    val isValid = nameText.isNotBlank() && !isDuplicate

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crear Producto en Catálogo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Name
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text("Nombre del Producto *") },
                    placeholder = { Text("Ej. Café Especial o Empanada") },
                    isError = isDuplicate,
                    supportingText = if (isDuplicate) {
                        { Text("Ya existe un producto con este nombre", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_product_name_input")
                )

                // Category
                OutlinedTextField(
                    value = categoryText,
                    onValueChange = { categoryText = it },
                    label = { Text("Categoría") },
                    placeholder = { Text("Ej. Bebidas, Panadería, Insumos") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_product_category_input")
                )

                // Standard price (Reference)
                OutlinedTextField(
                    value = if (standardPriceInput.isNotBlank()) CurrencyFormatter.formatRawThousands(standardPriceInput.toLongOrNull() ?: 0L) else "",
                    onValueChange = { standardPriceInput = it.filter { c -> c.isDigit() } },
                    label = { Text("Precio Estándar / Referencia (Opcional)") },
                    placeholder = { Text("Ej. 15.000") },
                    prefix = { Text("$ ", fontWeight = FontWeight.Bold) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    supportingText = { Text("Se sugerirá al registrar pero podrás editarlo siempre.") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_product_price_input")
                )

                // Image / Icon selection
                Text(
                    text = "Foto o Ícono del Producto:",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PRESET_PRODUCT_ICONS.take(5).forEach { (tag, icon) ->
                        val isSelected = selectedIconTag == tag
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable {
                                    selectedIconTag = tag
                                    selectedCustomPhotoUri = null
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = tag,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Gallery photo picker button
                    IconButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                    ) {
                        Icon(
                            Icons.Default.AddPhotoAlternate,
                            contentDescription = "Elegir foto de galería",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalPhoto = selectedCustomPhotoUri ?: selectedIconTag
                    val price = standardPriceInput.toLongOrNull() ?: 0L
                    onConfirm(nameText.trim(), categoryText.trim(), finalPhoto, price)
                },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2563EB),
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("confirm_create_product_btn")
            ) {
                Text("Crear Producto", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
