package com.example.data.firebase

import com.example.data.model.Group
import com.example.data.model.Invitation
import com.example.data.model.Member
import com.example.data.model.Movement
import com.example.data.model.MovementType
import com.example.data.model.PaymentMethod
import com.example.data.model.Product
import com.example.data.model.Role
import com.example.data.model.Settlement
import com.example.data.model.TransferAccount

/**
 * Conversion entre las entidades de Room y los documentos de Firestore.
 *
 * Reglas de diseno:
 *  - Las fechas se guardan como epoch en milisegundos (Long), no como Timestamp,
 *    para que el viaje de ida y vuelta no pierda precision.
 *  - `updatedAt` es la marca de resolucion de conflictos: gana el cambio mas
 *    reciente (last-write-wins). Se pone en cada escritura local.
 *  - Las lecturas usan accesores tolerantes: si un campo falta o viene de otro
 *    tipo, se usa un valor por defecto en lugar de lanzar una excepcion.
 */

// --- Lectura tolerante de documentos ---

internal fun Map<String, Any?>.str(key: String, def: String = ""): String =
    (this[key] as? String) ?: def

internal fun Map<String, Any?>.long(key: String, def: Long = 0L): Long =
    (this[key] as? Number)?.toLong() ?: def

internal fun Map<String, Any?>.int(key: String, def: Int = 0): Int =
    (this[key] as? Number)?.toInt() ?: def

internal fun Map<String, Any?>.double(key: String, def: Double = 0.0): Double =
    (this[key] as? Number)?.toDouble() ?: def

internal fun Map<String, Any?>.bool(key: String, def: Boolean = false): Boolean =
    (this[key] as? Boolean) ?: def

// `enumValueOf<T>()` es inline y exige un tipo reified, que no se puede usar
// dentro de una funcion normal. Se usa java.lang.Enum.valueOf con la clase
// explicita, que si funciona en tiempo de ejecucion.
internal fun <T : Enum<T>> Map<String, Any?>.enum(key: String, type: Class<T>, def: T): T {
    val raw = this[key] as? String ?: return def
    return runCatching { java.lang.Enum.valueOf(type, raw) }.getOrDefault(def)
}

// --- Group ---

internal fun Group.toFirestore(): Map<String, Any?> = mapOf(
    "id" to id,
    "name" to name,
    "createdAt" to createdAt,
    "currency" to currency,
    "settlementMode" to settlementMode,
    "lastDisbursementAt" to lastDisbursementAt,
    "currentPeriodStartAt" to currentPeriodStartAt,
    "updatedAt" to System.currentTimeMillis()
)

internal fun Map<String, Any?>.toGroup(): Group = Group(
    id = str("id"),
    name = str("name"),
    createdAt = long("createdAt"),
    currency = str("currency", "COP"),
    settlementMode = str("settlementMode", "DESEMBOLSO"),
    lastDisbursementAt = long("lastDisbursementAt"),
    currentPeriodStartAt = long("currentPeriodStartAt")
)

// --- Member ---

internal fun Member.toFirestore(): Map<String, Any?> = mapOf(
    "id" to id,
    "groupId" to groupId,
    "name" to name,
    "email" to email,
    "role" to role.name,
    "active" to active,
    "sharePercentCentecimas" to sharePercentCentecimas,
    "initialDebtCop" to initialDebtCop,
    "googleUid" to googleUid,
    "updatedAt" to System.currentTimeMillis()
)

internal fun Map<String, Any?>.toMember(): Member = Member(
    id = str("id"),
    groupId = str("groupId"),
    name = str("name"),
    email = str("email"),
    role = enum("role", Role::class.java, Role.SOCIO),
    active = bool("active", true),
    sharePercentCentecimas = int("sharePercentCentecimas", 4000),
    initialDebtCop = long("initialDebtCop"),
    googleUid = str("googleUid")
)

// --- Product ---

internal fun Product.toFirestore(): Map<String, Any?> = mapOf(
    "id" to id,
    "groupId" to groupId,
    "name" to name,
    "category" to category,
    "photoUri" to photoUri,
    "standardPriceCop" to standardPriceCop,
    "isArchived" to isArchived,
    "updatedAt" to System.currentTimeMillis()
)

internal fun Map<String, Any?>.toProduct(): Product = Product(
    id = str("id"),
    groupId = str("groupId"),
    name = str("name"),
    category = str("category", "General"),
    photoUri = str("photoUri"),
    standardPriceCop = long("standardPriceCop"),
    isArchived = bool("isArchived")
)

// --- Movement ---

internal fun Movement.toFirestore(): Map<String, Any?> = mapOf(
    "id" to id,
    "groupId" to groupId,
    "orderCode" to orderCode,
    "type" to type.name,
    "linesJson" to linesJson,
    "totalCop" to totalCop,
    "paymentMethod" to paymentMethod.name,
    "transferAccountName" to transferAccountName,
    "occurredAt" to occurredAt,
    "recordedAt" to recordedAt,
    "registeredByUserId" to registeredByUserId,
    "registeredByName" to registeredByName,
    "debtorMemberId" to debtorMemberId,
    "debtorMemberName" to debtorMemberName,
    "note" to note,
    "isAnnulled" to isAnnulled,
    "annulReason" to annulReason,
    "isEdited" to isEdited,
    "isOfflineRecorded" to isOfflineRecorded,
    "updatedAt" to System.currentTimeMillis()
)

internal fun Map<String, Any?>.toMovement(): Movement = Movement(
    id = str("id"),
    groupId = str("groupId"),
    orderCode = str("orderCode"),
    type = enum("type", MovementType::class.java, MovementType.VENTA),
    linesJson = str("linesJson", "[]"),
    totalCop = long("totalCop"),
    paymentMethod = enum("paymentMethod", PaymentMethod::class.java, PaymentMethod.EFECTIVO),
    transferAccountName = this["transferAccountName"] as? String,
    occurredAt = long("occurredAt"),
    recordedAt = long("recordedAt"),
    registeredByUserId = str("registeredByUserId"),
    registeredByName = str("registeredByName"),
    debtorMemberId = this["debtorMemberId"] as? String,
    debtorMemberName = this["debtorMemberName"] as? String,
    note = str("note"),
    isAnnulled = bool("isAnnulled"),
    annulReason = this["annulReason"] as? String,
    isEdited = bool("isEdited"),
    isOfflineRecorded = bool("isOfflineRecorded")
)

// --- TransferAccount ---

internal fun TransferAccount.toFirestore(): Map<String, Any?> = mapOf(
    "id" to id,
    "groupId" to groupId,
    "name" to name,
    "isActive" to isActive,
    "updatedAt" to System.currentTimeMillis()
)

internal fun Map<String, Any?>.toTransferAccount(): TransferAccount = TransferAccount(
    id = str("id"),
    groupId = str("groupId"),
    name = str("name"),
    isActive = bool("isActive", true)
)

// --- Invitation ---

internal fun Invitation.toFirestore(): Map<String, Any?> = mapOf(
    "id" to id,
    "groupId" to groupId,
    "code" to code,
    "role" to role.name,
    "expiresAt" to expiresAt,
    "maxUses" to maxUses,
    "usedCount" to usedCount,
    "isRevoked" to isRevoked,
    "updatedAt" to System.currentTimeMillis()
)

internal fun Map<String, Any?>.toInvitation(): Invitation = Invitation(
    id = str("id"),
    groupId = str("groupId"),
    code = str("code"),
    role = enum("role", Role::class.java, Role.SOCIO),
    expiresAt = long("expiresAt"),
    maxUses = int("maxUses", 1),
    usedCount = int("usedCount"),
    isRevoked = bool("isRevoked")
)

// --- Settlement ---

internal fun Settlement.toFirestore(): Map<String, Any?> = mapOf(
    "id" to id,
    "groupId" to groupId,
    "monthKey" to monthKey,
    "isClosed" to isClosed,
    "closedAt" to closedAt,
    "closedByUserId" to closedByUserId,
    "closedByName" to closedByName,
    "frozenSnapshotJson" to frozenSnapshotJson,
    "periodStartAt" to periodStartAt,
    "periodEndAt" to periodEndAt,
    "updatedAt" to System.currentTimeMillis()
)

internal fun Map<String, Any?>.toSettlement(): Settlement = Settlement(
    id = str("id"),
    groupId = str("groupId"),
    monthKey = str("monthKey"),
    isClosed = bool("isClosed"),
    closedAt = (this["closedAt"] as? Number)?.toLong(),
    closedByUserId = this["closedByUserId"] as? String,
    closedByName = this["closedByName"] as? String,
    frozenSnapshotJson = this["frozenSnapshotJson"] as? String,
    periodStartAt = long("periodStartAt"),
    periodEndAt = long("periodEndAt")
)
