// InstaGallery v1.0
package com.example.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val collectionId: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "collection_media_cross_ref",
    primaryKeys = ["collectionId", "mediaId"],
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["collectionId"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["mediaId"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("collectionId"), Index("mediaId")]
)
data class CollectionMediaCrossRef(
    val collectionId: Long,
    val mediaId: Long,
    val addedAt: Long = System.currentTimeMillis()
)

data class CoOccurringPerson(
    @Embedded val person: PersonEntity,
    val sharedPhotoCount: Int
)
