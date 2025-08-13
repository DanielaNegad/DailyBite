package com.example.dailybite.data.post

import com.example.dailybite.data.local.PostDao
import com.example.dailybite.data.local.toEntity
import com.example.dailybite.data.local.toItem
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PostRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val postDao: PostDao
) {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** יצירת פוסט חדש: העלאת תמונה ל-Storage, כתיבה ל-Firestore, והכנסה ל-ROOM */
    suspend fun createPost(
        ownerUid: String,
        mealType: String,
        description: String,
        imageBytes: ByteArray
    ): Result<String> = runCatching {
        val postId = firestore.collection("posts").document().id
        val path = "posts/$ownerUid/$postId.jpg"
        val storageRef = storage.reference.child(path)

        // העלאת תמונה
        val uploadResult = storageRef.putBytes(imageBytes).await()
        uploadResult.metadata ?: error("העלאת התמונה נכשלה")

        val now = System.currentTimeMillis()
        val data = mapOf(
            "id" to postId,
            "ownerUid" to ownerUid,
            "mealType" to mealType,
            "description" to description,
            "imageStoragePath" to path,
            "createdAt" to now,
            "updatedAt" to now,
            "likesCount" to 0
        )

        // כתיבה למסמך הפוסט
        firestore.collection("posts").document(postId).set(data).await()

        // עדכון מקומי מיידי (כדי שה-UI יתעדכן גם לפני listener)
        val local = PostItem(
            id = postId,
            ownerUid = ownerUid,
            mealType = mealType,
            description = description,
            imageStoragePath = path,
            createdAt = now,
            likesCount = 0
        )
        postDao.upsertAll(listOf(local.toEntity()))
        postId
    }

    /** עדכון פרטי פוסט + רענון מקומי */
    suspend fun updatePost(
        postId: String,
        mealType: String,
        description: String,
        imageStoragePath: String,
        newImageBytes: ByteArray?
    ): Result<Unit> = runCatching {
        if (newImageBytes != null && imageStoragePath.isNotEmpty()) {
            storage.reference.child(imageStoragePath).putBytes(newImageBytes).await()
        }
        val updatedAt = System.currentTimeMillis()
        firestore.collection("posts").document(postId).update(
            mapOf(
                "mealType" to mealType,
                "description" to description,
                "updatedAt" to updatedAt
            )
        ).await()

        // עדכון מקומי
        postDao.updatePostDetails(postId, mealType, description, updatedAt)
    }

    /** מחיקת פוסט (כולל תמונה אם קיימת) + מחיקה מקומית */
    suspend fun deletePost(postId: String, imageStoragePath: String): Result<Unit> = runCatching {
        if (imageStoragePath.isNotEmpty()) {
            storage.reference.child(imageStoragePath).delete().await()
        }
        firestore.collection("posts").document(postId).delete().await()
        postDao.deleteById(postId)
    }

    /** לייק + כתיבת "feedback" מסוג like (לצורך היסטוריה/אנליטיקות) */
    suspend fun like(postId: String, userUid: String): Result<Unit> = runCatching {
        firestore.collection("posts").document(postId)
            .update("likesCount", FieldValue.increment(1))
            .await()

        val fidRef = firestore.collection("feedback")
            .document(postId).collection("items").document()
        val payload = mapOf(
            "id" to fidRef.id,
            "postId" to postId,
            "authorUid" to userUid,
            "type" to "like",
            "createdAt" to System.currentTimeMillis()
        )
        fidRef.set(payload).await()
    }

    /** הוספת תגובה */
    suspend fun addComment(postId: String, userUid: String, text: String): Result<Unit> =
        runCatching {
            val ref = firestore.collection("feedback")
                .document(postId).collection("items").document()
            val payload = mapOf(
                "id" to ref.id,
                "postId" to postId,
                "authorUid" to userUid,
                "type" to "comment",
                "text" to text,
                "createdAt" to System.currentTimeMillis()
            )
            ref.set(payload).await()
        }



    /** פיד כללי מקומי (Room) */
    fun feedLocalFlow(): Flow<List<PostItem>> =
        postDao.feedFlow().map { rows -> rows.map { it.toItem() } }

    /** הפוסטים של משתמש מסוים (Room) */
    fun myPostsLocalFlow(ownerUid: String): Flow<List<PostItem>> =
        postDao.myPostsFlow(ownerUid).map { rows -> rows.map { it.toItem() } }

    /** סנכרון פיד כללי מהענן אל Room */
    fun startFeedSync(limit: Long = 100): ListenerRegistration =
        firestore.collection("posts")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                val items = snap.documents.mapNotNull { doc ->
                    val id = doc.getString("id") ?: doc.id
                    val ownerUid = doc.getString("ownerUid") ?: return@mapNotNull null
                    val mealType = doc.getString("mealType") ?: ""
                    val description = doc.getString("description") ?: ""
                    val path = doc.getString("imageStoragePath") ?: ""
                    val createdAt = doc.getLong("createdAt") ?: 0L
                    val likesCount = (doc.getLong("likesCount") ?: 0L).toInt()
                    PostItem(id, ownerUid, mealType, description, path, createdAt, likesCount)
                }
                ioScope.launch { postDao.upsertAll(items.map { it.toEntity() }) }
            }

    /** סנכרון הפוסטים של משתמש מסוים מהענן אל Room */
    fun startMyPostsSync(ownerUid: String): ListenerRegistration =
        firestore.collection("posts")
            .whereEqualTo("ownerUid", ownerUid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                val items = snap.documents.mapNotNull { doc ->
                    val id = doc.getString("id") ?: doc.id
                    val realOwner = doc.getString("ownerUid") ?: ownerUid
                    val mealType = doc.getString("mealType") ?: ""
                    val description = doc.getString("description") ?: ""
                    val path = doc.getString("imageStoragePath") ?: ""
                    val createdAt = doc.getLong("createdAt") ?: 0L
                    val likesCount = (doc.getLong("likesCount") ?: 0L).toInt()
                    PostItem(id, realOwner, mealType, description, path, createdAt, likesCount)
                }
                ioScope.launch { postDao.upsertAll(items.map { it.toEntity() }) }
            }

    /** ניקוי טבלאות מקומיות */
    suspend fun clearLocal() {
        postDao.clearAll()
    }

    /** עוטף listener של שאילתה ל-Flow וממפה מסמכים לפריטים */
    private inline fun <T> Query.snapshotsAsFlow(
        crossinline mapDoc: (com.google.firebase.firestore.DocumentSnapshot) -> T?
    ): Flow<List<T>> = callbackFlow {
        val reg = addSnapshotListener { snap, err ->
            if (err != null || snap == null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            trySend(snap.documents.mapNotNull(mapDoc))
        }
        awaitClose { reg.remove() }
    }
}
