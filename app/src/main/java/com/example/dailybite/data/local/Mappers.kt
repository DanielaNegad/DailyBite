package com.example.dailybite.data.local

import com.example.dailybite.data.post.PostItem

fun PostItem.toEntity() = PostEntity(
    id = id,
    ownerUid = ownerUid,
    mealType = mealType,
    description = description,
    imageStoragePath = imageStoragePath,
    createdAt = createdAt,
    likesCount = likesCount,
    updatedAt = updatedAt // ✅ כאן המפתח שחסר
)

fun PostEntity.toItem() = PostItem(
    id = id,
    ownerUid = ownerUid,
    mealType = mealType,
    description = description,
    imageStoragePath = imageStoragePath,
    createdAt = createdAt,
    likesCount = likesCount,
    updatedAt = updatedAt
)
