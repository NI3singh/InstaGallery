// InstaGallery v1.0
package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "people")
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val personId: Long = 0,
    val name: String, // Initially "Person 1", user-editable to "Papa", "Maa", etc.
    val avatarCropPath: String, // Internal file path to 200x200 face crop JPEG
    val centroidEmbeddingBlob: ByteArray, // L2-normalized 256-D FloatArray serialized to ByteArray
    val faceCount: Int = 1,
    val isNamedByUser: Boolean = false,
    val bestFaceQuality: Float = 0f,
    val createdAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PersonEntity

        if (personId != other.personId) return false
        if (name != other.name) return false
        if (avatarCropPath != other.avatarCropPath) return false
        if (!centroidEmbeddingBlob.contentEquals(other.centroidEmbeddingBlob)) return false
        if (faceCount != other.faceCount) return false
        if (isNamedByUser != other.isNamedByUser) return false
        if (bestFaceQuality != other.bestFaceQuality) return false
        if (createdAt != other.createdAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = personId.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + avatarCropPath.hashCode()
        result = 31 * result + centroidEmbeddingBlob.contentHashCode()
        result = 31 * result + faceCount
        result = 31 * result + isNamedByUser.hashCode()
        result = 31 * result + bestFaceQuality.hashCode()
        result = 31 * result + createdAt.hashCode()
        return result
    }
}
