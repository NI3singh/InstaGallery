package com.example.ml

import android.content.Context
import android.graphics.Bitmap
import com.example.data.local.dao.MediaDao
import com.example.data.local.dao.PersonDao
import com.example.data.local.entity.FaceEntity
import com.example.data.local.entity.MediaPersonCrossRef
import com.example.data.local.entity.PersonEntity
import com.google.mlkit.vision.face.Face
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.sqrt

class FaceClusterManager(
    private val context: Context,
    private val personDao: PersonDao,
    private val mediaDao: MediaDao
) {
    companion object {
        const val MATCH_THRESHOLD = 0.68f
        const val DUPLICATE_MIN_THRESHOLD = 0.55f
        const val DUPLICATE_MAX_THRESHOLD = 0.679f
    }

    private data class CachedCluster(
        val personId: Long,
        var centroid: FloatArray,
        var faceCount: Int,
        var name: String,
        var avatarCropPath: String
    )

    private val clusterCache = mutableListOf<CachedCluster>()
    private var isCacheLoaded = false

    private suspend fun ensureCacheLoaded() {
        if (!isCacheLoaded) {
            val people = personDao.getAllPeopleSync()
            clusterCache.clear()
            for (p in people) {
                val centroid = blobToFloatArray(p.centroidEmbeddingBlob)
                clusterCache.add(
                    CachedCluster(
                        personId = p.personId,
                        centroid = centroid,
                        faceCount = p.faceCount,
                        name = p.name,
                        avatarCropPath = p.avatarCropPath
                    )
                )
            }
            isCacheLoaded = true
        }
    }

    suspend fun processFace(
        mediaId: Long,
        face: Face,
        cropBitmap: Bitmap,
        embedding: FloatArray
    ): Long = withContext(Dispatchers.IO) {
        ensureCacheLoaded()

        var maxSim = -1.0f
        var bestCluster: CachedCluster? = null

        for (cluster in clusterCache) {
            val sim = cosineSimilarity(embedding, cluster.centroid)
            if (sim > maxSim) {
                maxSim = sim
                bestCluster = cluster
            }
        }

        val box = face.boundingBox

        if (bestCluster != null && maxSim >= MATCH_THRESHOLD) {
            // Match found! Update centroid
            val oldCentroid = bestCluster.centroid
            val count = bestCluster.faceCount
            val newCount = count + 1

            val updatedCentroid = FloatArray(embedding.size)
            for (i in embedding.indices) {
                updatedCentroid[i] = (oldCentroid[i] * count + embedding[i]) / newCount
            }
            val normalizedCentroid = l2Normalize(updatedCentroid)
            bestCluster.centroid = normalizedCentroid
            bestCluster.faceCount = newCount

            val cropPath = saveFaceCrop(cropBitmap)

            val faceEntity = FaceEntity(
                mediaId = mediaId,
                personId = bestCluster.personId,
                cropPath = cropPath,
                embeddingBlob = floatArrayToBlob(embedding),
                left = box.left,
                top = box.top,
                right = box.right,
                bottom = box.bottom
            )
            personDao.insertFace(faceEntity)
            mediaDao.insertMediaPersonCrossRef(MediaPersonCrossRef(mediaId, bestCluster.personId))

            // Update in DB
            personDao.updatePerson(
                PersonEntity(
                    personId = bestCluster.personId,
                    name = bestCluster.name,
                    avatarCropPath = bestCluster.avatarCropPath,
                    centroidEmbeddingBlob = floatArrayToBlob(normalizedCentroid),
                    faceCount = newCount
                )
            )

            bestCluster.personId
        } else {
            // New person detected!
            val cropPath = saveFaceCrop(cropBitmap)
            val nextNumber = clusterCache.size + 1
            val newPersonName = "Person $nextNumber"

            val newPerson = PersonEntity(
                name = newPersonName,
                avatarCropPath = cropPath,
                centroidEmbeddingBlob = floatArrayToBlob(embedding),
                faceCount = 1
            )
            val newPersonId = personDao.insertPerson(newPerson)

            val faceEntity = FaceEntity(
                mediaId = mediaId,
                personId = newPersonId,
                cropPath = cropPath,
                embeddingBlob = floatArrayToBlob(embedding),
                left = box.left,
                top = box.top,
                right = box.right,
                bottom = box.bottom
            )
            personDao.insertFace(faceEntity)
            mediaDao.insertMediaPersonCrossRef(MediaPersonCrossRef(mediaId, newPersonId))

            clusterCache.add(
                CachedCluster(
                    personId = newPersonId,
                    centroid = embedding,
                    faceCount = 1,
                    name = newPersonName,
                    avatarCropPath = cropPath
                )
            )

            newPersonId
        }
    }

    private fun saveFaceCrop(cropBitmap: Bitmap): String {
        val cropsDir = File(context.filesDir, "face_crops").apply { mkdirs() }
        val file = File(cropsDir, "face_${UUID.randomUUID()}.jpg")

        // Scale to 150x150
        val scaled = Bitmap.createScaledBitmap(cropBitmap, 150, 150, true)
        FileOutputStream(file).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        if (scaled != cropBitmap) {
            scaled.recycle()
        }
        return file.absolutePath
    }

    fun findPossibleDuplicatePair(people: List<PersonEntity>): Pair<PersonEntity, PersonEntity>? {
        if (people.size < 2) return null

        for (i in 0 until people.size) {
            val p1 = people[i]
            val emb1 = blobToFloatArray(p1.centroidEmbeddingBlob)
            for (j in i + 1 until people.size) {
                val p2 = people[j]
                val emb2 = blobToFloatArray(p2.centroidEmbeddingBlob)
                val sim = cosineSimilarity(emb1, emb2)
                if (sim in DUPLICATE_MIN_THRESHOLD..DUPLICATE_MAX_THRESHOLD) {
                    return Pair(p1, p2)
                }
            }
        }
        return null
    }

    private fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        val len = kotlin.math.min(v1.size, v2.size)
        var dot = 0f
        for (i in 0 until len) {
            dot += v1[i] * v2[i]
        }
        return dot
    }

    private fun l2Normalize(vector: FloatArray): FloatArray {
        var sum = 0.0
        for (v in vector) sum += (v * v).toDouble()
        val norm = sqrt(sum).toFloat()
        if (norm == 0f) return vector
        val res = FloatArray(vector.size)
        for (i in vector.indices) res[i] = vector[i] / norm
        return res
    }

    private fun floatArrayToBlob(floats: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(floats.size * 4).order(ByteOrder.nativeOrder())
        for (f in floats) buffer.putFloat(f)
        return buffer.array()
    }

    private fun blobToFloatArray(blob: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(blob).order(ByteOrder.nativeOrder())
        val floats = FloatArray(blob.size / 4)
        for (i in floats.indices) {
            floats[i] = buffer.float
        }
        return floats
    }
}
