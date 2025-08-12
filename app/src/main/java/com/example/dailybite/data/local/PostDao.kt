package com.example.dailybite.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PostDao {

    @Query("SELECT * FROM posts ORDER BY createdAt DESC")
    fun feedFlow(): Flow<List<PostEntity>>

    @Query("SELECT * FROM posts WHERE ownerUid = :uid ORDER BY createdAt DESC")
    fun myPostsFlow(uid: String): Flow<List<PostEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<PostEntity>)

    @Query("DELETE FROM posts")
    suspend fun clearAll()

    @Query("DELETE FROM posts WHERE id = :postId")
    suspend fun deleteById(postId: String)

    @Query("UPDATE posts SET mealType = :mealType, description = :description, updatedAt = :updatedAt WHERE id = :postId")
    suspend fun updatePostDetails(
        postId: String,
        mealType: String,
        description: String,
        updatedAt: Long
    )
}
