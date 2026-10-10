package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.*
import com.example.data.repository.FinanceRepository
import com.example.util.DateFormatter
import com.example.util.ExportHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

data class TicketLineDraft(
    val product: Product,
    val quantity: Int = 1,
    val unitPriceInput: String = "", // Raw text input
    val subtotalInput: String = ""   // Raw text input (Source of truth)
)

data class UiNotificationMessage(
    val message: String,
    val isError: Boolean = false
)

class FinanceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FinanceRepository(AppDatabase.getDatabase(application))
    private val firestoreRepository = com.example.data.firebase.FirestoreRepository(application)

    private val _authenticatedUser = MutableStateFlow<com.google.firebase.auth.FirebaseUser?>(null)
    val authenticatedUser: StateFlow<com.google.firebase.auth.FirebaseUser?> = _authenticatedUser.asStateFlow()

    // State flows
    val groups: StateFlow<List<Group>> = repository.allGroups
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedGroupId = MutableStateFlow<String?>(null)
    val selectedGroupId: StateFlow<String?> = _selectedGroupId.asStateFlow()

    private val _currentMember = MutableStateFlow<Member?>(null)
    val currentMember: StateFlow<Member?> = _currentMember.asStateFlow()

    private val _selectedMonthKey = MutableStateFlow(DateFormatter.currentMonthKey())
    val selectedMonthKey: StateFlow<String> = _selectedMonthKey.asStateFlow()

    private val _isAccumulatedView = MutableStateFlow(false)
    val isAccumulatedView: StateFlow<Boolean> = _isAccumulatedView.asStateFlow()

    // Notification message
    private val _userMessage = MutableStateFlow<UiNotificationMessage?>(null)
    val userMessage: StateFlow<UiNotificationMessage?> = _userMessage.asStateFlow()

    // Members of current group
    val members: StateFlow<List<Member>> = _selectedGroupId
        .filterNotNull()
        .flatMapLatest { repository.getMembers(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeMembers: StateFlow<List<Member>> = _selectedGroupId
        .filterNotNull()
        .flatMapLatest { repository.getActiveMembers(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Products
    val products: StateFlow<List<Product>> = _selectedGroupId
        .filterNotNull()
        .flatMapLatest { repository.getProducts(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProducts: StateFlow<List<Product>> = _selectedGroupId
        .filterNotNull()
        .flatMapLatest { repository.getActiveProducts(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Movements
    val movements: StateFlow<List<Movement>> = _selectedGroupId
        .filterNotNull()
        .flatMapLatest { repository.getMovements(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Transactions (Room Entity)
    val transactions: StateFlow<List<Transaction>> = _selectedGroupId
        .filterNotNull()
        .flatMapLatest { repository.getTransactionsByGroup(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Transfer accounts
    val transferAccounts: StateFlow<List<TransferAccount>> = _selectedGroupId
        .filterNotNull()
        .flatMapLatest { repository.getTransferAccounts(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Notifications
    val notifications: StateFlow<List<NotificationItem>> = _selectedGroupId
        .filterNotNull()
        .flatMapLatest { repository.getNotifications(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadNotificationsCount: StateFlow<Int> = _selectedGroupId
        .filterNotNull()
        .flatMapLatest { repository.getUnreadNotificationsCount(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Current Month Settlement Summary
    private val _settlementSummary = MutableStateFlow<SettlementSummary?>(null)
    val settlementSummary: StateFlow<SettlementSummary?> = _settlementSummary.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val settlementsHistory: StateFlow<List<Settlement>> = _selectedGroupId
        .filterNotNull()
        .flatMapLatest { repository.getSettlements(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Persistent Ticket Draft State (Preserves work across tabs and connects Catalog -> Register)
    private val _ticketDraftLines = MutableStateFlow<List<MovementLine>>(emptyList())
    val ticketDraftLines: StateFlow<List<MovementLine>> = _ticketDraftLines.asStateFlow()

    private val _draftMovementType = MutableStateFlow(MovementType.VENTA)
    val draftMovementType: StateFlow<MovementType> = _draftMovementType.asStateFlow()

    private val _draftPaymentMethod = MutableStateFlow<PaymentMethod?>(PaymentMethod.EFECTIVO)
    val draftPaymentMethod: StateFlow<PaymentMethod?> = _draftPaymentMethod.asStateFlow()

    private val _draftAccountName = MutableStateFlow("Nequi")
    val draftAccountName: StateFlow<String> = _draftAccountName.asStateFlow()

    private val _draftNote = MutableStateFlow("")
    val draftNote: StateFlow<String> = _draftNote.asStateFlow()

    private val _draftLoanAmount = MutableStateFlow("")
    val draftLoanAmount: StateFlow<String> = _draftLoanAmount.asStateFlow()

    private val _draftDebtorMemberId = MutableStateFlow<String?>(null)
    val draftDebtorMemberId: StateFlow<String?> = _draftDebtorMemberId.asStateFlow()

    init {
        // Al abrir la app no se crea ninguna empresa ni ningun dato de ejemplo.
        // El usuario empieza con la lista vacia y crea su empresa o se une por codigo.
        viewModelScope.launch {
            groups.collectLatest { groupList ->
                if (groupList.isNotEmpty() && _selectedGroupId.value == null) {
                    _selectedGroupId.value = groupList.first().id
                }
            }
        }

        viewModelScope.launch {
            combine(_selectedGroupId.filterNotNull(), _selectedMonthKey, movements, activeMembers) { gId, mKey, _, _ ->
                refreshSettlementSummary(gId, mKey)
            }.collect()
        }
    }

    /**
     * Resuelve la identidad del usuario tras iniciar sesion con Google.
     *
     * - Si ya existia una identidad guardada en este dispositivo, se reutiliza.
     * - Si el usuario pertenece a alguna empresa, se recupera ese miembro.
     * - Si es nuevo, se crea su identidad (sin grupo) y la app queda vacia,
     *   lista para crear su primera empresa o unirse con un codigo.
     */
    fun setAuthenticatedGoogleUser(user: com.google.firebase.auth.FirebaseUser) {
        _authenticatedUser.value = user
        viewModelScope.launch {
            try {
                firestoreRepository.syncUserProfile(user)
            } catch (e: Exception) {
                // Ignorar errores transitorios de red
            }

            val uid = user.uid
            val displayName = user.displayName ?: ""
            val email = user.email ?: ""

            // Identidad guardada previamente en este dispositivo
            val storedId = getApplication<Application>()
                .getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
                .getString(KEY_CURRENT_MEMBER_ID, null)
            if (storedId != null) {
                val stored = repository.getMemberById(storedId)
                if (stored != null && stored.googleUid == uid) {
                    _currentMember.value = stored
                    if (stored.groupId.isNotBlank()) _selectedGroupId.value = stored.groupId
                    return@launch
                }
            }

            // Ya es miembro de alguna empresa
            val existing = repository.getMemberByGoogleUidAnywhere(uid)
            if (existing != null) {
                _currentMember.value = existing
                if (existing.groupId.isNotBlank()) _selectedGroupId.value = existing.groupId
                persistCurrentMemberId(existing.id)
                return@launch
            }

            // Usuario nuevo: identidad sin grupo, app vacia
            val identity = repository.bindAuthenticatedUser(uid, displayName, email)
            _currentMember.value = identity
            persistCurrentMemberId(identity?.id)
        }
    }

    private fun persistCurrentMemberId(memberId: String?) {
        getApplication<Application>()
            .getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CURRENT_MEMBER_ID, memberId)
            .apply()
    }

    fun selectGroup(groupId: String) {
        _selectedGroupId.value = groupId
        viewModelScope.launch {
            val uid = _authenticatedUser.value?.uid
            if (uid == null) {
                _currentMember.value = null
                return@launch
            }
            // Solo se vincula si el usuario pertenece a ese grupo; si es nuevo
            // se crea como SOCIO (nunca sobrescribe al administrador existente).
            val member = repository.linkGoogleAccountToGroup(groupId, uid, "", "")
            _currentMember.value = member
            persistCurrentMemberId(member.id)
        }
    }

    fun switchCurrentMember(member: Member) {
        _currentMember.value = member
        persistCurrentMemberId(member.id)
        showMessage("Usuario actual: ${member.name}")
    }

    fun selectMonth(monthKey: String) {
        _selectedMonthKey.value = monthKey
        _selectedGroupId.value?.let { refreshSettlementSummary(it, monthKey) }
    }

    fun setAccumulatedView(isAccumulated: Boolean) {
        _isAccumulatedView.value = isAccumulated
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    // --- Draft Ticket Methods ---

    fun addTicketDraftLine(line: MovementLine) {
        val current = _ticketDraftLines.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.productId == line.productId }
        if (existingIndex >= 0) {
            val old = current[existingIndex]
            val newQty = old.quantity + line.quantity
            val newSubtotal = old.subtotalCop + line.subtotalCop
            val newUnitPrice = if (newQty > 0) newSubtotal / newQty else old.unitPriceCop
            current[existingIndex] = old.copy(quantity = newQty, subtotalCop = newSubtotal, unitPriceCop = newUnitPrice)
        } else {
            current.add(line)
        }
        _ticketDraftLines.value = current
        showMessage("Producto agregado al ticket: ${line.productName}")
    }

    fun removeTicketDraftLine(line: MovementLine) {
        _ticketDraftLines.value = _ticketDraftLines.value.filter { it != line }
    }

    fun clearTicketDraft() {
        _ticketDraftLines.value = emptyList()
        _draftNote.value = ""
        _draftLoanAmount.value = ""
    }

    fun setDraftMovementType(type: MovementType) {
        _draftMovementType.value = type
    }

    fun setDraftPaymentMethod(method: PaymentMethod?) {
        _draftPaymentMethod.value = method
    }

    fun setDraftAccountName(name: String) {
        _draftAccountName.value = name
    }

    fun setDraftNote(note: String) {
        _draftNote.value = note
    }

    fun setDraftLoanAmount(amount: String) {
        _draftLoanAmount.value = amount
    }

    fun setDraftDebtorMemberId(id: String?) {
        _draftDebtorMemberId.value = id
    }

    fun addProductToDraftAndPrepare(product: Product, quantity: Int = 1, type: MovementType = MovementType.VENTA) {
        val unitPrice = if (product.standardPriceCop > 0) product.standardPriceCop else 10_000L
        val subtotal = unitPrice * quantity
        val line = MovementLine(
            productId = product.id,
            productName = product.name,
            quantity = quantity,
            subtotalCop = subtotal,
            unitPriceCop = unitPrice
        )
        _draftMovementType.value = type
        addTicketDraftLine(line)
    }

    private fun showMessage(msg: String, isError: Boolean = false) {
        _userMessage.value = UiNotificationMessage(msg, isError)
    }

    private fun refreshSettlementSummary(groupId: String, monthKey: String) {
        viewModelScope.launch {
            try {
                val summary = repository.getSettlementSummary(groupId, monthKey)
                _settlementSummary.value = summary
            } catch (e: Exception) {
                // handle error
            }
        }
    }

    // --- Action Methods ---

    fun registerMovement(
        type: MovementType,
        lines: List<MovementLine>,
        totalCop: Long,
        paymentMethod: PaymentMethod,
        transferAccountName: String?,
        occurredAt: Long?,
        debtorMemberId: String?,
        debtorMemberName: String?,
        note: String,
        onSuccess: () -> Unit
    ) {
        val user = _currentMember.value ?: return
        val gId = _selectedGroupId.value ?: return

        viewModelScope.launch {
            val result = repository.registerMovement(
                groupId = gId,
                currentUser = user,
                type = type,
                lines = lines,
                totalCop = totalCop,
                paymentMethod = paymentMethod,
                transferAccountName = transferAccountName,
                occurredAtDate = occurredAt,
                debtorMemberId = debtorMemberId,
                debtorMemberName = debtorMemberName,
                note = note
            )
            result.onSuccess {
                clearTicketDraft()
                setDraftPaymentMethod(PaymentMethod.EFECTIVO)
                _selectedGroupId.value?.let { gid ->
                    refreshSettlementSummary(gid, _selectedMonthKey.value)
                }
                showMessage("Movimiento registrado con éxito")
                onSuccess()
            }.onFailure { err ->
                showMessage(err.message ?: "Error al registrar movimiento", isError = true)
            }
        }
    }

    fun editMovement(
        movementId: String,
        newLines: List<MovementLine>,
        newTotalCop: Long,
        newPaymentMethod: PaymentMethod,
        newAccountName: String?,
        newNote: String,
        onSuccess: () -> Unit
    ) {
        val user = _currentMember.value ?: return
        viewModelScope.launch {
            val result = repository.editMovement(
                movementId = movementId,
                currentUser = user,
                newLines = newLines,
                newTotalCop = newTotalCop,
                newPaymentMethod = newPaymentMethod,
                newAccountName = newAccountName,
                newNote = newNote
            )
            result.onSuccess {
                showMessage("Movimiento corregido con éxito")
                onSuccess()
            }.onFailure { err ->
                showMessage(err.message ?: "Error al corregir movimiento", isError = true)
            }
        }
    }

    fun annulMovement(
        movementId: String,
        reason: String,
        onSuccess: () -> Unit
    ) {
        val user = _currentMember.value ?: return
        viewModelScope.launch {
            val result = repository.annulMovement(
                movementId = movementId,
                currentUser = user,
                reason = reason
            )
            result.onSuccess {
                showMessage("Movimiento anulado")
                onSuccess()
            }.onFailure { err ->
                showMessage(err.message ?: "Error al anular movimiento", isError = true)
            }
        }
    }

    fun closeSettlement() {
        val user = _currentMember.value ?: return
        val gId = _selectedGroupId.value ?: return
        val mKey = _selectedMonthKey.value

        viewModelScope.launch {
            val result = repository.closeSettlement(gId, mKey, user)
            result.onSuccess {
                showMessage("Corte de ${DateFormatter.formatMonthYear(mKey)} cerrado con éxito")
                refreshSettlementSummary(gId, mKey)
            }.onFailure { err ->
                showMessage(err.message ?: "Error al cerrar corte", isError = true)
            }
        }
    }

    fun reopenSettlement() {
        val user = _currentMember.value ?: return
        val gId = _selectedGroupId.value ?: return
        val mKey = _selectedMonthKey.value

        viewModelScope.launch {
            val result = repository.reopenSettlement(gId, mKey, user)
            result.onSuccess {
                showMessage("Corte de ${DateFormatter.formatMonthYear(mKey)} reabierto")
                refreshSettlementSummary(gId, mKey)
            }.onFailure { err ->
                showMessage(err.message ?: "Error al reabrir corte", isError = true)
            }
        }
    }

    fun disburseCurrentPeriod(onSuccess: () -> Unit = {}) {
        val user = _currentMember.value ?: return
        val gId = _selectedGroupId.value ?: return

        viewModelScope.launch {
            val result = repository.disburseCurrentPeriod(gId, user)
            result.onSuccess {
                showMessage("Desembolso realizado con éxito. Cifras congeladas.")
                refreshSettlementSummary(gId, _selectedMonthKey.value)
                onSuccess()
            }.onFailure { err ->
                showMessage(err.message ?: "Error al realizar desembolso", isError = true)
            }
        }
    }

    fun startNewPeriod(onSuccess: () -> Unit = {}) {
        val user = _currentMember.value ?: return
        val gId = _selectedGroupId.value ?: return

        viewModelScope.launch {
            val result = repository.startNewPeriod(gId, user)
            result.onSuccess {
                showMessage("Nuevo período iniciado con la fecha actual.")
                refreshSettlementSummary(gId, _selectedMonthKey.value)
                onSuccess()
            }.onFailure { err ->
                showMessage(err.message ?: "Error al iniciar nuevo período", isError = true)
            }
        }
    }

    fun setGroupSettlementMode(mode: String) {
        val gId = _selectedGroupId.value ?: return
        viewModelScope.launch {
            repository.updateGroupSettlementMode(gId, mode)
            showMessage(if (mode == "DESEMBOLSO") "Modo de cuentas cambiado a: Por Desembolsos" else "Modo de cuentas cambiado a: Mensual")
            refreshSettlementSummary(gId, _selectedMonthKey.value)
        }
    }

    fun addProduct(name: String, category: String, photoUri: String, standardPriceCop: Long = 0L, onSuccess: () -> Unit) {
        val user = _currentMember.value
        if (user?.role != Role.ADMIN) {
            showMessage("Solo el administrador puede agregar productos al catálogo", isError = true)
            return
        }
        val gId = _selectedGroupId.value ?: return
        viewModelScope.launch {
            val result = repository.addProduct(gId, name, category, photoUri, standardPriceCop)
            result.onSuccess {
                showMessage("Producto '${it.name}' agregado al catálogo")
                onSuccess()
            }.onFailure { err ->
                showMessage(err.message ?: "Error al agregar producto", isError = true)
            }
        }
    }

    fun quickChangePaymentMethod(
        movementId: String,
        newMethod: PaymentMethod,
        newAccountName: String?,
        onSuccess: () -> Unit = {}
    ) {
        val user = _currentMember.value ?: return
        viewModelScope.launch {
            val result = repository.quickChangePaymentMethod(movementId, newMethod, newAccountName, user)
            result.onSuccess {
                showMessage("Medio de pago cambiado a ${if (newMethod == PaymentMethod.EFECTIVO) "Efectivo" else "Transferencia (${newAccountName ?: "General"})"}")
                onSuccess()
            }.onFailure { err ->
                showMessage(err.message ?: "Error al cambiar medio de pago", isError = true)
            }
        }
    }

    fun createGroup(name: String, creatorName: String, creatorEmail: String, settlementMode: String = "DESEMBOLSO", onDone: (Group) -> Unit) {
        viewModelScope.launch {
            val uid = _authenticatedUser.value?.uid ?: ""
            val result = repository.createGroup(name, creatorName, creatorEmail, settlementMode, uid)
            result.onSuccess { newGroup ->
                _selectedGroupId.value = newGroup.id
                // El creador queda vinculado como Admin de la nueva empresa
                val creator = repository.getMemberByGoogleUidInGroup(uid, newGroup.id)
                    ?: repository.linkGoogleAccountToGroup(newGroup.id, uid, creatorName, creatorEmail)
                _currentMember.value = creator
                persistCurrentMemberId(creator.id)
                showMessage("Empresa '${newGroup.name}' creada exitosamente")
                onDone(newGroup)
            }.onFailure { err ->
                showMessage(err.message ?: "Error al crear empresa", isError = true)
            }
        }
    }

    fun renameGroup(groupId: String, newName: String, onSuccess: () -> Unit = {}) {
        val user = _currentMember.value ?: return
        viewModelScope.launch {
            val result = repository.renameGroup(groupId, newName, user)
            result.onSuccess {
                showMessage("Empresa renombrada a '$newName'")
                onSuccess()
            }.onFailure { err ->
                showMessage(err.message ?: "Error al renombrar", isError = true)
            }
        }
    }

    fun deleteGroup(groupId: String, onSuccess: () -> Unit = {}) {
        val user = _currentMember.value ?: return
        if (user.role != Role.ADMIN) {
            showMessage("Solo el administrador puede eliminar la empresa", isError = true)
            return
        }
        viewModelScope.launch {
            val result = repository.deleteGroup(groupId, user)
            result.onSuccess {
                showMessage("Empresa eliminada")
                val remaining = groups.value.filter { it.id != groupId }
                if (remaining.isNotEmpty()) {
                    selectGroup(remaining.first().id)
                } else {
                    // Ya no queda ninguna empresa: la app vuelve al estado inicial
                    _selectedGroupId.value = null
                    // Se recupera la identidad de la cuenta (sin grupo asociado)
                    val uid = _authenticatedUser.value?.uid
                    if (uid != null) {
                        _currentMember.value = repository.bindAuthenticatedUser(uid, user.name, user.email)
                    }
                }
                onSuccess()
            }.onFailure { err ->
                showMessage(err.message ?: "Error al eliminar empresa", isError = true)
            }
        }
    }

    fun archiveProduct(productId: String) {
        val user = _currentMember.value ?: return
        if (user.role != Role.ADMIN) {
            showMessage("Solo el administrador puede archivar productos", isError = true)
            return
        }
        viewModelScope.launch {
            val result = repository.archiveProduct(productId, user)
            result.onSuccess {
                showMessage("Producto archivado")
            }.onFailure { err ->
                showMessage(err.message ?: "Error al archivar", isError = true)
            }
        }
    }

    fun createInvitation(role: Role, hoursValid: Int, maxUses: Int, onCreated: (Invitation) -> Unit) {
        val user = _currentMember.value ?: return
        val gId = _selectedGroupId.value ?: return
        viewModelScope.launch {
            val result = repository.createInvitation(gId, user, role, hoursValid, maxUses)
            result.onSuccess {
                showMessage("Invitación creada: Código ${it.code}")
                onCreated(it)
            }.onFailure { err ->
                showMessage(err.message ?: "Error al crear invitación", isError = true)
            }
        }
    }

    fun useInvitation(code: String, name: String, email: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val uid = _authenticatedUser.value?.uid ?: ""
            val result = repository.useInvitation(code, name, email, uid)
            result.onSuccess { newMember ->
                _selectedGroupId.value = newMember.groupId
                _currentMember.value = newMember
                persistCurrentMemberId(newMember.id)
                showMessage("¡Bienvenido al grupo, ${newMember.name}!")
                onSuccess()
            }.onFailure { err ->
                showMessage(err.message ?: "Error con la invitación", isError = true)
            }
        }
    }

    fun updateMemberRole(memberId: String, newRole: Role) {
        val user = _currentMember.value ?: return
        viewModelScope.launch {
            val result = repository.updateMemberRole(memberId, newRole, user)
            result.onSuccess {
                showMessage("Rol actualizado")
            }.onFailure { err ->
                showMessage(err.message ?: "Error al cambiar rol", isError = true)
            }
        }
    }

    fun deactivateMember(memberId: String) {
        val user = _currentMember.value ?: return
        viewModelScope.launch {
            val result = repository.deactivateMember(memberId, user)
            result.onSuccess {
                showMessage("Socio retirado")
            }.onFailure { err ->
                showMessage(err.message ?: "Error al retirar socio", isError = true)
            }
        }
    }

    fun updateMemberShare(memberId: String, newShareCentecimas: Int) {
        val user = _currentMember.value ?: return
        viewModelScope.launch {
            val result = repository.updateMemberShare(memberId, newShareCentecimas, user)
            result.onSuccess {
                showMessage("Porcentaje actualizado")
            }.onFailure { err ->
                showMessage(err.message ?: "Error al actualizar porcentaje", isError = true)
            }
        }
    }

    fun addTransferAccount(name: String, onSuccess: () -> Unit) {
        val user = _currentMember.value ?: return
        val gId = _selectedGroupId.value ?: return
        viewModelScope.launch {
            val result = repository.addTransferAccount(gId, name, user)
            result.onSuccess {
                showMessage("Cuenta '$name' agregada")
                onSuccess()
            }.onFailure { err ->
                showMessage(err.message ?: "Error al agregar cuenta", isError = true)
            }
        }
    }

    fun markNotificationsRead() {
        val gId = _selectedGroupId.value ?: return
        viewModelScope.launch {
            repository.markAllNotificationsRead(gId)
        }
    }

    fun getAuditsForMovement(movementId: String): Flow<List<MovementAudit>> {
        return repository.getAuditsForMovement(movementId)
    }

    fun exportPdfReport(onReady: (File) -> Unit) {
        val gName = groups.value.firstOrNull { it.id == _selectedGroupId.value }?.name ?: "Negocio"
        val summary = _settlementSummary.value ?: return
        val currentMonthMovements = movements.value.filter { DateFormatter.toMonthKey(it.occurredAt) == summary.monthKey }
        val file = ExportHelper.createPdfReport(getApplication(), gName, summary, currentMonthMovements)
        if (file != null) {
            onReady(file)
        } else {
            showMessage("Error al generar PDF", isError = true)
        }
    }

    fun exportCsv(onReady: (String) -> Unit) {
        val gName = groups.value.firstOrNull { it.id == _selectedGroupId.value }?.name ?: "Negocio"
        val summary = _settlementSummary.value ?: return
        val currentMonthMovements = movements.value.filter { DateFormatter.toMonthKey(it.occurredAt) == summary.monthKey }
        val csv = ExportHelper.generateCsv(gName, summary, currentMonthMovements)
        onReady(csv)
    }

    fun shareWhatsAppSummary(onReady: (String) -> Unit) {
        val gName = groups.value.firstOrNull { it.id == _selectedGroupId.value }?.name ?: "Negocio"
        val summary = _settlementSummary.value ?: return
        val text = ExportHelper.generateWhatsAppSummary(gName, summary)
        onReady(text)
    }

    companion object {
        private const val PREFS_NAME = "sociofinanzas_prefs"
        private const val KEY_CURRENT_MEMBER_ID = "current_member_id"
    }
}
