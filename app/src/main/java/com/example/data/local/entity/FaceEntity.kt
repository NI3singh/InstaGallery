package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "detected_faces",
    foreignKeys = [
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["mediaId"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["personId"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("mediaId"), Index("personId")]
)
data class FaceEntity(
    @PrimaryKey(autoGenerate = true) val faceId: Long = 0,
    val mediaId: Long,
    val personId: Long,
    val cropPath: String,
    val embeddingBlob: ByteArray,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FaceEntity

        if (faceId != other.faceId) return false
        if (mediaId != other.mediaId) return false
        if (personId != other.personId) return false
        if (cropPath != other.cropPath) return false
        if (!embeddingBlob.contentEquals(other.embeddingBlob)) return false
        if (left != other.left) return false
        if (top != other.top) return false
        if (right != other.right) return false
        if (bottom != other.bottom) return false

        return true
    }

    override fun hashCode(): Int {
        var result = faceId.hashCode()
        result = 31 * result + mediaId.hashCode()
        result = 31 * result + personId.hashCode()
        result = 31 * result + cropPath.hashCode()
        result = 31 * result + embeddingBlob.contentHashCode()
        result = 31 * result + left
        result = 31 * result + top
        result = 31 * result + right
        result = 31 * result + bottom
        return result
    }
}
