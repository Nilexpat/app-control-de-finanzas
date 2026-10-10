package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.Group
import com.example.data.model.Member
import com.example.data.model.Movement
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class FirestoreRepository(
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    suspend fun syncUserProfile(user: FirebaseUser): Result<Unit> {
        return try {
            val userDocRef = db.collection("users").document(user.uid)
            val data = mapOf(
                "uid" to user.uid,
                "email" to (user.email ?: ""),
                "displayName" to (user.displayName ?: ""),
                "photoUrl" to (user.photoUrl?.toString() ?: ""),
                "lastLogin" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            userDocRef.set(data, SetOptions.merge()).await()
            Log.d(TAG, "User profile synced in Firestore for ${user.uid}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Error syncing user profile to Firestore: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun syncGroup(group: Group, ownerUserId: String): Result<Unit> {
        return try {
            val groupDocRef = db.collection("groups").document(group.id)
            val data = mapOf(
                "id" to group.id,
                "name" to group.name,
                "ownerId" to ownerUserId,
                "createdAt" to FieldValue.serverTimestamp()
            )
            groupDocRef.set(data, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Error syncing group to Firestore: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun syncMember(member: Member): Result<Unit> {
        return try {
            val memberDocRef = db.collection("groups").document(member.groupId)
                .collection("members").document(member.id)
            val data = mapOf(
                "id" to member.id,
                "groupId" to member.groupId,
                "name" to member.name,
                "email" to member.email,
                "role" to member.role.name,
                "active" to member.active,
                "sharePercentCentecimas" to member.sharePercentCentecimas,
                "initialDebtCop" to member.initialDebtCop,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            memberDocRef.set(data, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Error syncing member to Firestore: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun syncMovement(movement: Movement, currentUserId: String): Result<Unit> {
        return try {
            val movDocRef = db.collection("groups").document(movement.groupId)
                .collection("movements").document(movement.id)
            val data = mapOf(
                "id" to movement.id,
                "groupId" to movement.groupId,
                "type" to movement.type.name,
                "amountCop" to movement.totalCop,
                "concept" to movement.note,
                "paymentMethod" to movement.paymentMethod.name,
                "createdByUserId" to currentUserId,
                "createdAt" to FieldValue.serverTimestamp()
            )
            movDocRef.set(data, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Error syncing movement to Firestore: ${e.message}")
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "FirestoreRepository"
    }
}
