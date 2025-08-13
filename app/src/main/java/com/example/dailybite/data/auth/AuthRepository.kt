package com.example.dailybite.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    fun currentUidOrNull(): String? = auth.currentUser?.uid
    fun isLoggedIn(): Boolean = auth.currentUser != null

    /** שינויי התחברות בזמן אמת (אופציונלי לשימוש במסכים) */
    val authState: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }


    suspend fun signInEmail(email: String, password: String): Result<String> = runCatching {
        val res = auth.signInWithEmailAndPassword(email.trim(), password).await()
        val uid = res.user?.uid ?: error("Email sign-in failed")
        ensureUserDoc(uid, res.user?.displayName)
        uid
    }

    suspend fun signUpEmail(email: String, password: String): Result<String> = runCatching {
        val res = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val uid = res.user?.uid ?: error("Email sign-up failed")
        createUserProfile(uid, res.user?.displayName)
        uid
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    /** יוצר מסמך משתמש כברירת מחדל */
    private suspend fun createUserProfile(uid: String, displayName: String?) {
        val userDoc = mapOf(
            "uid" to uid,
            "name" to (displayName ?: ""),
            "profileImagePath" to "",
            "createdAt" to System.currentTimeMillis()
        )
        firestore.collection("users").document(uid).set(userDoc).await()
    }

    /** אם אין מסמך משתמש – ניצור */
    private suspend fun ensureUserDoc(uid: String, displayName: String?) {
        val doc = firestore.collection("users").document(uid).get().await()
        if (!doc.exists()) createUserProfile(uid, displayName)
    }

    fun signOut() {
        auth.signOut()
    }
}
