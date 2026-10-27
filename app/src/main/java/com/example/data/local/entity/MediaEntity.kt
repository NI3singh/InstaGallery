// InstaGallery v1.0
package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_items",
    indices = [
        Index("isVideo"),
        Index("isProcessedForFaces"),
        Index("isFavorite"),
        Index("isBookmarked"),
        Index("dateAddedSeconds")
    ]
)
data class MediaEntity(
    @PrimaryKey val mediaId: Long,
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val isVideo: Boolean,
    val dateAddedSeconds: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long?,
    val isProcessedForFaces: Boolean = false,
    val hasFaces: Boolean = false,
    val isFavorite: Boolean = false,
    val isBookmarked: Boolean = false,
    val customCaption: String? = null
)
