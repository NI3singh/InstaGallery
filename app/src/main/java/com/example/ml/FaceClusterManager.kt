// InstaGallery v1.0
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
import kotlin.math.min
import kotlin.math.sqrt

class FaceClusterManager(
    private val context: Context,
    private val personDao: PersonDao,
    private val mediaDao: MediaDao
) {
    companion object {
        const val PHOTO_MATCH_THRESHOLD = 0.54f
        const val VIDEO_MATCH_THRESHOLD = 0.60f
        const val DUPLICATE_MIN_THRESHOLD = 0.45f
        const val CONSOLIDATION_THRESHOLD = 0.58f
    }

    private data class CachedCluster(
        val personId: Long,
        var centroid: FloatArray,
        var faceCount: Int,
        var name: String,
        var avatarCropPath: String,
        var bestQuality: Float,
        var isNamedByUser: Boolean,
        val mediaIds: MutableSet<Long> = mutableSetOf()
    )

    private val clusterCache = mutableListOf<CachedCluster>()
    private var isCacheLoaded = false

    private suspend fun ensureCacheLoaded() {
        if (!isCacheLoaded) {
            val people = personDao.getAllPeopleSync()
            clusterCache.clear()
            for (p in people) {
                val centroid = blobToFloatArray(p.centroidEmbeddingBlob)
                val mediaIds = personDao.getMediaIdsForPerson(p.personId).toMutableSet()
                clusterCache.add(
                    CachedCluster(
                        personId = p.personId,
                        centroid = centroid,
                        faceCount = p.faceCount,
                        name = p.name,
                        avatarCropPath = p.avatarCropPath,
                        bestQuality = p.bestFaceQuality,
                        isNamedByUser = p.isNamedByUser,
                        mediaIds = mediaIds
                    )
                )
            }
            isCacheLoaded = true
        }
    }

    /**
     * Processes a detected face, matching it to the closest existing person cluster
     * (excluding any person already matched in the same photo, since two faces in one photo
     * are two distinct people!) or creating a new person cluster.
     */
    suspend fun processFace(
        mediaId: Long,
        face: Face,
        avatarBitmap: Bitmap,
        embedding: FloatArray,
        faceQuality: Float,
        isVideo: Boolean = false,
        excludePersonIdsInSameMedia: Set<Long> = emptySet()
    ): Long? = withContext(Dispatchers.IO) {
        ensureCacheLoaded()

        var maxSim = -1.0f
        var bestCluster: CachedCluster? = null

        for (cluster in clusterCache) {
            if (!isVideo && excludePersonIdsInSameMedia.contains(cluster.personId)) {
                continue
            }
            val sim = cosineSimilarity(embedding, cluster.centroid)
            if (sim > maxSim) {
                maxSim = sim
                bestCluster = cluster
            }
        }

        val threshold = if (isVideo) VIDEO_MATCH_THRESHOLD else PHOTO_MATCH_THRESHOLD
        val box = face.boundingBox

        if (bestCluster != null && maxSim >= threshold) {
            val oldCentroid = bestCluster.centroid
            val count = bestCluster.faceCount.coerceAtLeast(1)
            val newCount = count + 1

            val dim = min(embedding.size, oldCentroid.size)
            val updatedCentroid = FloatArray(dim)
            // Weight higher-quality photo faces more strongly in the centroid update
            val weightNew = if (isVideo) 0.5f else 1.0f
            for (i in 0 until dim) {
                updatedCentroid[i] = (oldCentroid[i] * count + embedding[i] * weightNew) / (count + weightNew)
            }
            val normalizedCentroid = l2Normalize(updatedCentroid)
            bestCluster.centroid = normalizedCentroid
            bestCluster.mediaIds.add(mediaId)

            val cropPath = saveFaceCrop(avatarBitmap)

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

            val latestInDb = personDao.getPersonByIdSync(bestCluster.personId)
            val currentName = latestInDb?.name ?: bestCluster.name
            val isNamed = latestInDb?.isNamedByUser ?: bestCluster.isNamedByUser

            // Upgrade avatar crop automatically if this new photo has a sharper/more frontal face
            val shouldUpgradeAvatar = (!isVideo && faceQuality > bestCluster.bestQuality + 0.05f) ||
                bestCluster.avatarCropPath.isBlank() ||
                !File(bestCluster.avatarCropPath).exists()

            val updatedAvatar = if (shouldUpgradeAvatar) {
                bestCluster.bestQuality = faceQuality
                cropPath
            } else {
                latestInDb?.avatarCropPath?.ifBlank { cropPath } ?: bestCluster.avatarCropPath
            }

            val distinctMediaCount = personDao.countDistinctMediaForPerson(bestCluster.personId).coerceAtLeast(1)
            bestCluster.name = currentName
            bestCluster.isNamedByUser = isNamed
            bestCluster.avatarCropPath = updatedAvatar
            bestCluster.faceCount = distinctMediaCount

            personDao.updatePerson(
                PersonEntity(
                    personId = bestCluster.personId,
                    name = currentName,
                    avatarCropPath = updatedAvatar,
                    centroidEmbeddingBlob = floatArrayToBlob(normalizedCentroid),
                    faceCount = distinctMediaCount,
                    isNamedByUser = isNamed,
                    bestFaceQuality = bestCluster.bestQuality
                )
            )

            bestCluster.personId
        } else {
            // For videos: if person clusters already exist from photos and a random video frame
            // doesn't match any known person and isn't super high quality, avoid creating noisy
            // video-only stranger profiles.
            if (isVideo && clusterCache.isNotEmpty() && faceQuality < 0.72f) {
                return@withContext null
            }

            val cropPath = saveFaceCrop(avatarBitmap)
            val maxExistingNumber = clusterCache.mapNotNull {
                it.name.removePrefix("Person ").trim().toIntOrNull()
            }.maxOrNull() ?: clusterCache.size
            val nextNumber = maxExistingNumber + 1
            val newPersonName = "Person $nextNumber"

            val newPerson = PersonEntity(
                name = newPersonName,
                avatarCropPath = cropPath,
                centroidEmbeddingBlob = floatArrayToBlob(embedding),
                faceCount = 1,
                isNamedByUser = false,
                bestFaceQuality = faceQuality
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
                    avatarCropPath = cropPath,
                    bestQuality = faceQuality,
                    isNamedByUser = false,
                    mediaIds = mutableSetOf(mediaId)
                )
            )

            newPersonId
        }
    }

    /**
     * Consolidates split clusters whose running centroids have converged above CONSOLIDATION_THRESHOLD,
     * provided the two clusters never co-occur in the same photo (which would mean two distinct people).
     */
    suspend fun consolidateConvergedClusters() = withContext(Dispatchers.IO) {
        ensureCacheLoaded()
        if (clusterCache.size < 2) return@withContext

        var mergedAny = true
        while (mergedAny) {
            mergedAny = false
            outer@ for (i in 0 until clusterCache.size) {
                val c1 = clusterCache[i]
                for (j in i + 1 until clusterCache.size) {
                    val c2 = clusterCache[j]
                    // Never auto-merge two clusters if they appear together in the same photo
                    val coOccurs = c1.mediaIds.any { c2.mediaIds.contains(it) }
                    if (coOccurs) continue

                    // Never auto-merge if both were already given different custom names by the user
                    if (c1.isNamedByUser && c2.isNamedByUser && !c1.name.equals(c2.name, ignoreCase = true)) {
                        continue
                    }

                    val sim = cosineSimilarity(c1.centroid, c2.centroid)
                    if (sim >= CONSOLIDATION_THRESHOLD) {
                        // Keep the cluster with more faces (or custom name) as target
                        val keepFirst = c1.isNamedByUser || (!c2.isNamedByUser && c1.faceCount >= c2.faceCount)
                        val target = if (keepFirst) c1 else c2
                        val source = if (keepFirst) c2 else c1

                        val dim = min(target.centroid.size, source.centroid.size)
                        val combinedCentroid = FloatArray(dim)
                        val w1 = target.faceCount.coerceAtLeast(1)
                        val w2 = source.faceCount.coerceAtLeast(1)
                        for (k in 0 until dim) {
                            combinedCentroid[k] = (target.centroid[k] * w1 + source.centroid[k] * w2) / (w1 + w2)
                        }
                        target.centroid = l2Normalize(combinedCentroid)
                        target.mediaIds.addAll(source.mediaIds)
                        if (source.bestQuality > target.bestQuality && File(source.avatarCropPath).exists()) {
                            target.avatarCropPath = source.avatarCropPath
                            target.bestQuality = source.bestQuality
                        }

                        personDao.mergePeople(
                            sourcePersonIds = listOf(source.personId),
                            targetPersonId = target.personId,
                            newName = target.name
                        )
                        val updatedEntity = personDao.getPersonByIdSync(target.personId)
                        if (updatedEntity != null) {
                            target.faceCount = updatedEntity.faceCount
                            personDao.updatePerson(
                                updatedEntity.copy(
                                    avatarCropPath = target.avatarCropPath,
                                    centroidEmbeddingBlob = floatArrayToBlob(target.centroid),
                                    bestFaceQuality = target.bestQuality,
                                    isNamedByUser = target.isNamedByUser
                                )
                            )
                        }

                        clusterCache.remove(source)
                        mergedAny = true
                        break@outer
                    }
                }
            }
        }
    }

    private fun saveFaceCrop(cropBitmap: Bitmap): String {
        val cropsDir = File(context.filesDir, "face_crops").apply { mkdirs() }
        val file = File(cropsDir, "face_${UUID.randomUUID()}.jpg")

        val scaled = Bitmap.createScaledBitmap(cropBitmap, 200, 200, true)
        FileOutputStream(file).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        if (scaled != cropBitmap) {
            scaled.recycle()
        }
        return file.absolutePath
    }

    fun findPossibleDuplicatePair(
        people: List<PersonEntity>,
        ignoredPairKeys: Set<String> = emptySet()
    ): Pair<PersonEntity, PersonEntity>? {
        if (people.size < 2) return null

        var bestPair: Pair<PersonEntity, PersonEntity>? = null
        var bestSim = -1f

        for (i in 0 until people.size) {
            val p1 = people[i]
            val emb1 = blobToFloatArray(p1.centroidEmbeddingBlob)
            for (j in i + 1 until people.size) {
                val p2 = people[j]
                val key = pairKey(p1.personId, p2.personId)
                if (ignoredPairKeys.contains(key)) continue

                val emb2 = blobToFloatArray(p2.centroidEmbeddingBlob)
                val sim = cosineSimilarity(emb1, emb2)
                if (sim >= DUPLICATE_MIN_THRESHOLD && sim > bestSim) {
                    bestSim = sim
                    bestPair = Pair(p1, p2)
                }
            }
        }
        return bestPair
    }

    fun pairKey(id1: Long, id2: Long): String {
        return if (id1 < id2) "${id1}_${id2}" else "${id2}_${id1}"
    }

    fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        val len = min(v1.size, v2.size)
        if (len == 0) return 0f
        var dot = 0f
        for (i in 0 until len) {
            dot += v1[i] * v2[i]
        }
        return dot
    }

    fun l2Normalize(vector: FloatArray): FloatArray {
        var sum = 0.0
        for (v in vector) sum += (v * v).toDouble()
        val norm = sqrt(sum).toFloat()
        if (norm == 0f) return vector
        val res = FloatArray(vector.size)
        for (i in vector.indices) res[i] = vector[i] / norm
        return res
    }

    fun floatArrayToBlob(floats: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(floats.size * 4).order(ByteOrder.nativeOrder())
        for (f in floats) buffer.putFloat(f)
        return buffer.array()
    }

    fun blobToFloatArray(blob: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(blob).order(ByteOrder.nativeOrder())
        val floats = FloatArray(blob.size / 4)
        for (i in floats.indices) {
            floats[i] = buffer.float
        }
        return floats
    }
}
