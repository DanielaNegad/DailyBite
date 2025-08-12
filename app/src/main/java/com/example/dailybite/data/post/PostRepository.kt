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

    suspend fun createPost(
        ownerUid: String,
        mealType: String,
        description: String,
        imageBytes: ByteArray
    ): Result<String> = runCatching {
        val postId = firestore.collection("posts").document().id
        val path = "posts/$ownerUid/$postId.jpg"
        storage.reference.child(path).putBytes(imageBytes).await()

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

        firestore.collection("posts").document(postId).set(data).await()
        postId
    }

    suspend fun updatePost(
        postId: String,
        mealType: String,
        description: String,
        imageStoragePath: String,
        newImageBytes: ByteArray?
    ): Result<Unit> = runCatching {
        if (newImageBytes != null) {
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
        // עדכון מקומי ב־Room
        postDao.updatePostDetails(postId, mealType, description, updatedAt)
    }

    suspend fun deletePost(postId: String, imageStoragePath: String): Result<Unit> = runCatching {
        if (imageStoragePath.isNotEmpty()) {
            storage.reference.child(imageStoragePath).delete().await()
        }
        firestore.collection("posts").document(postId).delete().await()
        // מחיקה מקומית
        postDao.deleteById(postId)
    }

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

    fun commentsFlow(postId: String): Flow<List<CommentItem>> =
        firestore.collection("feedback").document(postId)
            .collection("items")
            .whereEqualTo("type", "comment")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshotsAsFlow { d ->
                CommentItem(
                    id = d.getString("id") ?: d.id,
                    postId = d.getString("postId") ?: return@snapshotsAsFlow null,
                    authorUid = d.getString("authorUid") ?: "",
                    text = d.getString("text") ?: "",
                    createdAt = d.getLong("createdAt") ?: 0L
                )
            }

    fun feedLocalFlow(): Flow<List<PostItem>> =
        postDao.feedFlow().map { rows -> rows.map { it.toItem() } }

    fun myPostsLocalFlow(ownerUid: String): Flow<List<PostItem>> =
        postDao.myPostsFlow(ownerUid).map { rows -> rows.map { it.toItem() } }

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

    fun startMyPostsSync(ownerUid: String): ListenerRegistration =
        firestore.collection("posts")
            .whereEqualTo("ownerUid", ownerUid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                val items = snap.documents.mapNotNull { doc ->
                    val id = doc.getString("id") ?: doc.id
                    val mealType = doc.getString("mealType") ?: ""
                    val description = doc.getString("description") ?: ""
                    val path = doc.getString("imageStoragePath") ?: ""
                    val createdAt = doc.getLong("createdAt") ?: 0L
                    val likesCount = (doc.getLong("likesCount") ?: 0L).toInt()
                    PostItem(id, ownerUid, mealType, description, path, createdAt, likesCount)
                }
                ioScope.launch { postDao.upsertAll(items.map { it.toEntity() }) }
            }

    suspend fun clearLocal() {
        postDao.clearAll()
    }

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
