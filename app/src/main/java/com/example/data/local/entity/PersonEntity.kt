// InstaGallery v1.0
package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "people", indices = [Index("faceCount"), Index("isHiddenToOther")])
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val personId: Long = 0,
    val name: String,
    val avatarCropPath: String,
    val centroidEmbeddingBlob: ByteArray,
    val faceCount: Int = 1,
    val isNamedByUser: Boolean = false,
    val isHiddenToOther: Boolean = false,
    val bestQualityScore: Float = 0.75f,
    val createdAt: Long = System.currentTimeMillis()
) {
    @Ignore
    constructor(
        personId: Long = 0,
        name: String,
        avatarCropPath: String,
        centroidEmbeddingBlob: ByteArray,
        faceCount: Int = 1,
        isNamedByUser: Boolean = false,
        bestFaceQuality: Float,
        createdAt: Long = System.currentTimeMillis()
    ) : this(
        personId = personId,
        name = name,
        avatarCropPath = avatarCropPath,
        centroidEmbeddingBlob = centroidEmbeddingBlob,
        faceCount = faceCount,
        isNamedByUser = isNamedByUser,
        isHiddenToOther = false,
        bestQualityScore = bestFaceQuality,
        createdAt = createdAt
    )

    @get:Ignore
    val bestFaceQuality: Float
        get() = bestQualityScore

    fun copy(
        personId: Long = this.personId,
        name: String = this.name,
        avatarCropPath: String = this.avatarCropPath,
        centroidEmbeddingBlob: ByteArray = this.centroidEmbeddingBlob,
        faceCount: Int = this.faceCount,
        isNamedByUser: Boolean = this.isNamedByUser,
        bestFaceQuality: Float,
        createdAt: Long = this.createdAt
    ): PersonEntity = PersonEntity(
        personId = personId,
        name = name,
        avatarCropPath = avatarCropPath,
        centroidEmbeddingBlob = centroidEmbeddingBlob,
        faceCount = faceCount,
        isNamedByUser = isNamedByUser,
        isHiddenToOther = this.isHiddenToOther,
        bestQualityScore = bestFaceQuality,
        createdAt = createdAt
    )

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
        if (isHiddenToOther != other.isHiddenToOther) return false
        if (bestQualityScore != other.bestQualityScore) return false
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
        result = 31 * result + isHiddenToOther.hashCode()
        result = 31 * result + bestQualityScore.hashCode()
        result = 31 * result + createdAt.hashCode()
        return result
    }
}
