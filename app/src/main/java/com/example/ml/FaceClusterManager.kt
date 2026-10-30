// InstaGallery v1.0
package com.example.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.example.data.local.AppDatabase
import com.example.data.local.OnboardingPreferences
import com.example.data.local.dao.MediaDao
import com.example.data.local.dao.PersonDao
import com.example.data.local.entity.FaceEntity
import com.example.data.local.entity.MediaPersonCrossRef
import com.example.data.local.entity.PersonEntity
import com.google.mlkit.vision.face.Face
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class SimilarProfileCandidate(
    val person: PersonEntity,
    val similarityScore: Float,
    val photoCount: Int
)

/**
 * True MobileFaceNet Euclidean/Cosine Clustering Engine.
 *
 * In 192-D MobileFaceNet, Euclidean distance <= 0.90 corresponds to Cosine Similarity >= 0.60f (cos = 1 - d^2 / 2).
 * - Matches a face to `bestCluster` whenever `maxExemplarSim >= 0.60f || (maxExemplarSim >= 0.57f && centroidSim >= 0.56f)`.
 * - When no existing cluster matches, creates a new `PersonEntity` if `face.qualityScore >= 0.45f && box.width() >= 52`.
 * - Enforces same-photo co-occurrence exclusion.
 * - Serializes all in-memory + SQLite operations via a single coroutine [Mutex].
 */
class FaceClusterManager(
    private val context: Context,
    private val personDao: PersonDao,
    private val mediaDao: MediaDao
) {
    constructor(
        context: Context,
        database: AppDatabase
    ) : this(
        context = context.applicationContext,
        personDao = database.personDao(),
        mediaDao = database.mediaDao()
    )

    companion object {
        private const val EXEMPLAR_MATCH_THRESHOLD = 0.60f
        private const val CENTROID_MATCH_THRESHOLD = 0.56f
        private const val SECONDARY_EXEMPLAR_THRESHOLD = 0.57f
        const val PASS1_STRONG_MATCH_THRESHOLD = 0.60f

        const val EXEMPLAR_QUALITY_GATE = 0.55f
        const val MAX_EXEMPLARS_PER_PERSON = 12

        const val PASS2_MERGE_MULTI_THRESHOLD = 0.61f
        const val PASS2_MERGE_SINGLETON_THRESHOLD = 0.58f

        const val PASS2_NAMED_ANCHOR_SWEEP_THRESHOLD = 0.54f
        const val PASS2_MULTI_ANCHOR_SWEEP_THRESHOLD = 0.56f

        const val DUPLICATE_MIN_THRESHOLD = 0.52f
        private const val AVATAR_TARGET_SIZE = 200

        @Volatile
        private var INSTANCE: FaceClusterManager? = null

        fun getInstance(context: Context): FaceClusterManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FaceClusterManager(
                    context.applicationContext,
                    AppDatabase.getInstance(context.applicationContext)
                ).also { INSTANCE = it }
            }
        }
    }

    data class PersonCluster(
        val personId: Long,
        var name: String,
        var isNamedByUser: Boolean,
        var centroid: FloatArray,
        val exemplars: MutableList<FloatArray>,
        var faceCount: Int,
        var bestQualityScore: Float,
        var avatarCropPath: String,
        val mediaIds: MutableSet<Long> = mutableSetOf()
    )

    private val clusterMutex = Mutex()
    private val clusterCache = mutableListOf<PersonCluster>()
    private val inMemoryClusters: MutableList<PersonCluster>
        get() = clusterCache
    private var isCacheLoaded = false

    suspend fun invalidateCache() = withContext(Dispatchers.IO) {
        clusterMutex.withLock {
            isCacheLoaded = false
            clusterCache.clear()
        }
    }

    suspend fun reloadClustersFromDb() = withContext(Dispatchers.IO) {
        clusterMutex.withLock {
            loadExistingClustersFromDb()
        }
    }

    private suspend fun loadExistingClustersFromDb() {
        ensureCacheLoadedLocked(forceReload = true)
    }

    private suspend fun ensureCacheLoadedLocked(forceReload: Boolean = false) {
        if (isCacheLoaded && clusterCache.isNotEmpty() && !forceReload) return
        clusterCache.clear()

        val activePeople = personDao.getAllPeopleSync()
        for (person in activePeople) {
            if (person.isHiddenToOther) continue

            val centroid = FaceEmbeddingExtractor.bytesToFloatArray(person.centroidEmbeddingBlob)
            val anchorFaces = personDao.getAnchorFacesForPersonSync(person.personId)
            val exemplarVectors = mutableListOf<FloatArray>()
            for (face in anchorFaces) {
                val emb = FaceEmbeddingExtractor.bytesToFloatArray(face.embeddingBlob)
                if (emb.isNotEmpty()) {
                    exemplarVectors.add(emb)
                }
                if (exemplarVectors.size >= MAX_EXEMPLARS_PER_PERSON) break
            }
            if (exemplarVectors.isEmpty() && centroid.isNotEmpty()) {
                exemplarVectors.add(centroid)
            }
            val resolvedCentroid = if (centroid.isNotEmpty()) {
                centroid
            } else {
                exemplarVectors.firstOrNull() ?: FloatArray(0)
            }

            val mediaIds = personDao.getMediaIdsForPerson(person.personId).toMutableSet()
            clusterCache.add(
                PersonCluster(
                    personId = person.personId,
                    name = person.name,
                    isNamedByUser = person.isNamedByUser,
                    centroid = resolvedCentroid,
                    exemplars = exemplarVectors,
                    faceCount = person.faceCount.coerceAtLeast(1),
                    bestQualityScore = person.bestQualityScore,
                    avatarCropPath = person.avatarCropPath,
                    mediaIds = mediaIds
                )
            )
        }
        isCacheLoaded = true
    }

    fun computePersonMatchScore(embedding: FloatArray, cluster: PersonCluster): Float {
        if (embedding.isEmpty() || cluster.centroid.isEmpty()) return -1f
        val centroidSim = dotProduct(embedding, cluster.centroid)
        val exemplarSims = cluster.exemplars
            .filter { it.size == embedding.size }
            .map { dotProduct(embedding, it) }
            .sortedDescending()
        val maxExemplarSim = exemplarSims.firstOrNull() ?: centroidSim
        val top3AvgSim = if (exemplarSims.isNotEmpty()) {
            exemplarSims.take(3).average().toFloat()
        } else {
            centroidSim
        }
        return (maxExemplarSim * 0.55f) + (top3AvgSim * 0.30f) + (centroidSim * 0.15f)
    }

    private fun evaluateClusterMatch(embedding: FloatArray, cluster: PersonCluster): Pair<Boolean, Float> {
        if (embedding.isEmpty() || cluster.centroid.isEmpty()) return false to -1f
        val centroidSim = dotProduct(embedding, cluster.centroid)
        val exemplarSims = cluster.exemplars
            .filter { it.size == embedding.size }
            .map { dotProduct(embedding, it) }
            .sortedDescending()
        val maxExemplarSim = exemplarSims.firstOrNull() ?: centroidSim
        val top3AvgSim = if (exemplarSims.isNotEmpty()) {
            exemplarSims.take(3).average().toFloat()
        } else {
            centroidSim
        }
        val combinedScore = (maxExemplarSim * 0.55f) + (top3AvgSim * 0.30f) + (centroidSim * 0.15f)
        val isMatch = maxExemplarSim >= EXEMPLAR_MATCH_THRESHOLD ||
            (maxExemplarSim >= SECONDARY_EXEMPLAR_THRESHOLD && centroidSim >= CENTROID_MATCH_THRESHOLD) ||
            centroidSim >= 0.64f
        return isMatch to max(combinedScore, max(maxExemplarSim, centroidSim))
    }

    /**
     * Processes all detected faces for a single photo (ignores videos).
     * Returns true if at least one face was linked to an active PersonEntity profile.
     */
    suspend fun processDetectedFacesForMedia(
        mediaId: Long,
        detectedFaces: List<DetectedAlignedFace>,
        embeddingExtractor: FaceEmbeddingExtractor,
        isVideo: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        if (isVideo || detectedFaces.isEmpty()) return@withContext false

        val sortedFaces = detectedFaces.sortedByDescending { it.qualityScore }
        val extractedPairs = sortedFaces.map { face ->
            face to embeddingExtractor.extractEmbedding(face.alignedFaceBitmap, face.rawFace)
        }

        clusterMutex.withLock {
            if (inMemoryClusters.isEmpty() || !isCacheLoaded) {
                loadExistingClustersFromDb()
            }

            // Ensure re-indexing a photo never duplicates face rows or stale cross-refs
            personDao.deleteFacesForMediaId(mediaId)
            mediaDao.deleteCrossRefsForMediaId(mediaId)
            for (cluster in inMemoryClusters) {
                cluster.mediaIds.remove(mediaId)
            }

            val matchedPersonIdsInThisMedia = mutableSetOf<Long>()
            var anyAssignedToPerson = false

            for ((face, embedding) in extractedPairs) {
                val assignedPersonId = processSingleAlignedFaceLocked(
                    mediaId = mediaId,
                    face = face,
                    embedding = embedding,
                    isVideo = false,
                    excludePersonIdsInSameMedia = matchedPersonIdsInThisMedia
                )
                if (assignedPersonId != null) {
                    matchedPersonIdsInThisMedia.add(assignedPersonId)
                    anyAssignedToPerson = true
                }
            }

            anyAssignedToPerson
        }
    }

    /**
     * Processes a single DetectedAlignedFace with a pre-extracted flip-augmented embedding.
     */
    suspend fun processSingleAlignedFace(
        mediaId: Long,
        face: DetectedAlignedFace,
        embedding: FloatArray,
        isVideo: Boolean = false,
        excludePersonIdsInSameMedia: Set<Long> = emptySet()
    ): Long? = withContext(Dispatchers.IO) {
        if (isVideo) return@withContext null
        clusterMutex.withLock {
            if (inMemoryClusters.isEmpty() || !isCacheLoaded) {
                loadExistingClustersFromDb()
            }
            processSingleAlignedFaceLocked(
                mediaId = mediaId,
                face = face,
                embedding = embedding,
                isVideo = false,
                excludePersonIdsInSameMedia = excludePersonIdsInSameMedia
            )
        }
    }

    private suspend fun processSingleAlignedFaceLocked(
        mediaId: Long,
        face: DetectedAlignedFace,
        embedding: FloatArray,
        isVideo: Boolean = false,
        excludePersonIdsInSameMedia: Set<Long> = emptySet()
    ): Long? {
        if (isVideo) return null

        val cropPath = saveFaceCrop(face.avatarCropBitmap)

        var bestScore = -1f
        var bestCluster: PersonCluster? = null

        for (cluster in clusterCache) {
            if (excludePersonIdsInSameMedia.contains(cluster.personId) || cluster.mediaIds.contains(mediaId)) {
                continue
            }
            val (isMatch, score) = evaluateClusterMatch(embedding, cluster)
            if (isMatch && score > bestScore) {
                bestScore = score
                bestCluster = cluster
            }
        }

        val box = face.boundingBox

        // Case A — Match to existing PersonCluster
        if (bestCluster != null) {
            val isExemplar = face.qualityScore >= EXEMPLAR_QUALITY_GATE &&
                bestCluster.exemplars.size < MAX_EXEMPLARS_PER_PERSON

            if (isExemplar) {
                bestCluster.exemplars.add(embedding)
            }

            val oldWeight = bestCluster.faceCount.coerceAtLeast(1).toFloat()
            val newWeight = if (face.isStrictAnchorCandidate) 1.0f else 0.75f
            bestCluster.centroid = updateCentroid(bestCluster.centroid, oldWeight, embedding, newWeight)
            bestCluster.mediaIds.add(mediaId)

            val shouldUpgradeAvatar = face.qualityScore > bestCluster.bestQualityScore ||
                bestCluster.avatarCropPath.isBlank() ||
                !File(bestCluster.avatarCropPath).exists()

            if (shouldUpgradeAvatar) {
                bestCluster.avatarCropPath = cropPath
                bestCluster.bestQualityScore = max(bestCluster.bestQualityScore, face.qualityScore)
            }

            personDao.insertFace(
                FaceEntity(
                    mediaId = mediaId,
                    personId = bestCluster.personId,
                    cropPath = cropPath,
                    embeddingBlob = floatArrayToBlob(embedding),
                    qualityScore = face.qualityScore,
                    headEulerY = face.headEulerY,
                    headEulerZ = face.headEulerZ,
                    isAnchorExemplar = isExemplar,
                    left = box.left,
                    top = box.top,
                    right = box.right,
                    bottom = box.bottom
                )
            )
            mediaDao.insertMediaPersonCrossRef(MediaPersonCrossRef(mediaId, bestCluster.personId))

            val distinctMediaCount = personDao.countDistinctMediaForPerson(bestCluster.personId).coerceAtLeast(1)
            bestCluster.faceCount = distinctMediaCount

            personDao.updatePersonStats(
                personId = bestCluster.personId,
                centroidBlob = floatArrayToBlob(bestCluster.centroid),
                faceCount = distinctMediaCount,
                avatarCropPath = bestCluster.avatarCropPath,
                bestQuality = bestCluster.bestQualityScore
            )

            return bestCluster.personId
        }

        // Case B — Create a new PersonEntity if face.qualityScore >= 0.35f && box.width() >= 36
        val canSpawnNewPerson = (face.qualityScore >= 0.35f && box.width() >= 36) || face.isStrictAnchorCandidate
        if (canSpawnNewPerson) {
            val nextNumber = computeNextPersonNumber()
            val newPersonName = "Person $nextNumber"
            val centroidBlob = floatArrayToBlob(embedding)

            val newPersonId = personDao.insertPerson(
                PersonEntity(
                    name = newPersonName,
                    avatarCropPath = cropPath,
                    centroidEmbeddingBlob = centroidBlob,
                    faceCount = 1,
                    isNamedByUser = false,
                    isHiddenToOther = false,
                    bestQualityScore = face.qualityScore
                )
            )

            personDao.insertFace(
                FaceEntity(
                    mediaId = mediaId,
                    personId = newPersonId,
                    cropPath = cropPath,
                    embeddingBlob = centroidBlob,
                    qualityScore = face.qualityScore,
                    headEulerY = face.headEulerY,
                    headEulerZ = face.headEulerZ,
                    isAnchorExemplar = true,
                    left = box.left,
                    top = box.top,
                    right = box.right,
                    bottom = box.bottom
                )
            )
            mediaDao.insertMediaPersonCrossRef(MediaPersonCrossRef(mediaId, newPersonId))

            clusterCache.add(
                PersonCluster(
                    personId = newPersonId,
                    name = newPersonName,
                    isNamedByUser = false,
                    centroid = embedding,
                    exemplars = mutableListOf(embedding),
                    faceCount = 1,
                    bestQualityScore = face.qualityScore,
                    avatarCropPath = cropPath,
                    mediaIds = mutableSetOf(mediaId)
                )
            )

            return newPersonId
        }

        // Case C — Unassigned Face awaiting Pass-2 Anchor Sweep
        personDao.insertFace(
            FaceEntity(
                mediaId = mediaId,
                personId = null,
                cropPath = cropPath,
                embeddingBlob = floatArrayToBlob(embedding),
                qualityScore = face.qualityScore,
                headEulerY = face.headEulerY,
                headEulerZ = face.headEulerZ,
                isAnchorExemplar = false,
                left = box.left,
                top = box.top,
                right = box.right,
                bottom = box.bottom
            )
        )

        return null
    }

    suspend fun processFace(
        mediaId: Long,
        face: Face,
        avatarBitmap: Bitmap,
        embedding: FloatArray,
        faceQuality: Float,
        isVideo: Boolean = false,
        excludePersonIdsInSameMedia: Set<Long> = emptySet()
    ): Long? {
        if (isVideo) return null
        val isStrictAnchor = faceQuality >= 0.35f && face.boundingBox.width() >= 36

        val alignedFace = DetectedAlignedFace(
            alignedFaceBitmap = avatarBitmap,
            avatarCropBitmap = avatarBitmap,
            boundingBox = Rect(face.boundingBox),
            qualityScore = faceQuality,
            headEulerY = face.headEulerAngleY,
            headEulerZ = face.headEulerAngleZ,
            isStrictAnchorCandidate = isStrictAnchor,
            rawFace = face
        )
        return processSingleAlignedFace(
            mediaId = mediaId,
            face = alignedFace,
            embedding = embedding,
            isVideo = false,
            excludePersonIdsInSameMedia = excludePersonIdsInSameMedia
        )
    }

    /**
     * Global Graph Consolidation & Anchor k-NN Sweep using calibrated MobileFaceNet thresholds.
     */
    suspend fun runPass2ConsolidationAndAnchorSweep() = withContext(Dispatchers.IO) {
        clusterMutex.withLock {
            ensureCacheLoadedLocked(forceReload = true)

            // STEP A — Auto-Merge Over-Split Clusters
            if (clusterCache.size >= 2) {
                var mergedInPass = true
                while (mergedInPass) {
                    mergedInPass = false
                    var bestI = -1
                    var bestJ = -1
                    var bestSim = -1f

                    for (i in 0 until clusterCache.size) {
                        val cA = clusterCache[i]
                        for (j in i + 1 until clusterCache.size) {
                            val cB = clusterCache[j]

                            // Never merge two clusters that appear together in the same photo
                            if (cA.mediaIds.any { cB.mediaIds.contains(it) }) continue

                            // Never auto-merge two clusters explicitly given different names by the user
                            if (cA.isNamedByUser && cB.isNamedByUser && !cA.name.equals(cB.name, ignoreCase = true)) {
                                continue
                            }

                            val sim = computeClusterToClusterSimilarity(cA, cB)
                            val requiredThreshold = if (cA.faceCount == 1 || cB.faceCount == 1) {
                                PASS2_MERGE_SINGLETON_THRESHOLD
                            } else {
                                PASS2_MERGE_MULTI_THRESHOLD
                            }

                            if (sim >= requiredThreshold && sim > bestSim) {
                                bestSim = sim
                                bestI = i
                                bestJ = j
                            }
                        }
                    }

                    if (bestI >= 0 && bestJ >= 0) {
                        val cA = clusterCache[bestI]
                        val cB = clusterCache[bestJ]

                        val keepA = when {
                            cA.isNamedByUser && !cB.isNamedByUser -> true
                            cB.isNamedByUser && !cA.isNamedByUser -> false
                            cA.faceCount != cB.faceCount -> cA.faceCount > cB.faceCount
                            else -> cA.bestQualityScore >= cB.bestQualityScore
                        }

                        val target = if (keepA) cA else cB
                        val source = if (keepA) cB else cA

                        mergeClusterIntoTarget(source, target)
                        clusterCache.remove(source)
                        mergedInPass = true
                    }
                }
            }

            // STEP B — Sweep Unassigned (personId == null) Faces Into Active Clusters
            val anchorClusters = clusterCache.toList()
            if (anchorClusters.isNotEmpty()) {
                val unassignedFaces = personDao.getUnassignedFacesSync()
                for (face in unassignedFaces) {
                    val faceEmb = blobToFloatArray(face.embeddingBlob)
                    if (faceEmb.isEmpty()) continue

                    var bestAnchor: PersonCluster? = null
                    var bestScore = -1f

                    for (anchor in anchorClusters) {
                        if (anchor.mediaIds.contains(face.mediaId)) continue

                        val score = computePersonMatchScore(faceEmb, anchor)
                        val threshold = when {
                            anchor.isNamedByUser -> PASS2_NAMED_ANCHOR_SWEEP_THRESHOLD
                            anchor.faceCount >= 2 -> PASS2_MULTI_ANCHOR_SWEEP_THRESHOLD
                            else -> PASS2_MERGE_SINGLETON_THRESHOLD
                        }

                        if (score >= threshold && score > bestScore) {
                            bestScore = score
                            bestAnchor = anchor
                        }
                    }

                    if (bestAnchor != null) {
                        val isNewExemplar = face.qualityScore >= EXEMPLAR_QUALITY_GATE &&
                            bestAnchor.exemplars.size < MAX_EXEMPLARS_PER_PERSON

                        personDao.assignFaceToPerson(
                            faceId = face.faceId,
                            personId = bestAnchor.personId,
                            isAnchor = isNewExemplar
                        )
                        mediaDao.insertMediaPersonCrossRef(
                            MediaPersonCrossRef(face.mediaId, bestAnchor.personId)
                        )
                        mediaDao.markMediaAsProcessed(face.mediaId, hasFaces = true)

                        if (isNewExemplar) {
                            bestAnchor.exemplars.add(faceEmb)
                        }
                        val oldWeight = bestAnchor.faceCount.coerceAtLeast(1).toFloat()
                        bestAnchor.centroid = updateCentroid(bestAnchor.centroid, oldWeight, faceEmb, 0.70f)
                        bestAnchor.mediaIds.add(face.mediaId)

                        if (face.qualityScore > bestAnchor.bestQualityScore && File(face.cropPath).exists()) {
                            bestAnchor.bestQualityScore = face.qualityScore
                            bestAnchor.avatarCropPath = face.cropPath
                        }

                        val distinctMedia = personDao.countDistinctMediaForPerson(bestAnchor.personId).coerceAtLeast(1)
                        bestAnchor.faceCount = distinctMedia
                        personDao.updatePersonStats(
                            personId = bestAnchor.personId,
                            centroidBlob = floatArrayToBlob(bestAnchor.centroid),
                            faceCount = distinctMedia,
                            avatarCropPath = bestAnchor.avatarCropPath,
                            bestQuality = bestAnchor.bestQualityScore
                        )
                    }
                }
            }

            mediaDao.syncAllMediaHasFaces()
        }
    }

    suspend fun consolidateConvergedClusters() {
        runPass2ConsolidationAndAnchorSweep()
    }

    fun computeClusterToClusterSimilarity(a: PersonCluster, b: PersonCluster): Float {
        if (a.centroid.isEmpty() || b.centroid.isEmpty()) return -1f
        val scoreAB = computePersonMatchScore(a.centroid, b)
        val scoreBA = computePersonMatchScore(b.centroid, a)
        val symmetricMultiScore = (scoreAB + scoreBA) * 0.5f

        var maxPairwiseExemplarSim = dotProduct(a.centroid, b.centroid)
        for (ea in a.exemplars) {
            for (eb in b.exemplars) {
                if (ea.size == eb.size) {
                    val sim = dotProduct(ea, eb)
                    if (sim > maxPairwiseExemplarSim) {
                        maxPairwiseExemplarSim = sim
                    }
                }
            }
        }

        return (symmetricMultiScore * 0.65f) + (maxPairwiseExemplarSim * 0.35f)
    }

    private suspend fun mergeClusterIntoTarget(source: PersonCluster, target: PersonCluster) {
        val wTarget = target.faceCount.coerceAtLeast(1).toFloat()
        val wSource = source.faceCount.coerceAtLeast(1).toFloat()
        target.centroid = updateCentroid(target.centroid, wTarget, source.centroid, wSource)
        target.mediaIds.addAll(source.mediaIds)

        if (source.bestQualityScore > target.bestQualityScore && File(source.avatarCropPath).exists()) {
            target.avatarCropPath = source.avatarCropPath
            target.bestQualityScore = source.bestQualityScore
        }

        personDao.mergePeople(
            sourcePersonIds = listOf(source.personId),
            targetPersonId = target.personId,
            newName = target.name
        )

        val topAnchorFaces = personDao.getAnchorFacesForPersonSync(target.personId)
        target.exemplars.clear()
        for (face in topAnchorFaces) {
            val emb = blobToFloatArray(face.embeddingBlob)
            if (emb.isNotEmpty()) {
                target.exemplars.add(emb)
            }
            if (target.exemplars.size >= MAX_EXEMPLARS_PER_PERSON) break
        }
        if (target.exemplars.isEmpty() && target.centroid.isNotEmpty()) {
            target.exemplars.add(target.centroid)
        }

        val distinctMedia = personDao.countDistinctMediaForPerson(target.personId).coerceAtLeast(1)
        target.faceCount = distinctMedia

        personDao.updatePersonStats(
            personId = target.personId,
            centroidBlob = floatArrayToBlob(target.centroid),
            faceCount = distinctMedia,
            avatarCropPath = target.avatarCropPath,
            bestQuality = target.bestQualityScore
        )
    }

    private fun computeNextPersonNumber(): Int {
        val maxExisting = clusterCache.mapNotNull {
            it.name.removePrefix("Person ").trim().toIntOrNull()
        }.maxOrNull() ?: clusterCache.size
        return maxExisting + 1
    }

    private fun updateCentroid(
        oldCentroid: FloatArray,
        oldWeight: Float,
        newVector: FloatArray,
        newWeight: Float
    ): FloatArray {
        val dim = min(oldCentroid.size, newVector.size)
        if (dim == 0) return newVector
        val combined = FloatArray(dim)
        val totalWeight = (oldWeight + newWeight).coerceAtLeast(1e-5f)
        for (i in 0 until dim) {
            combined[i] = (oldCentroid[i] * oldWeight + newVector[i] * newWeight) / totalWeight
        }
        return l2Normalize(combined)
    }

    private fun saveFaceCrop(cropBitmap: Bitmap): String {
        val cropsDir = File(context.filesDir, "face_crops").apply { mkdirs() }
        val file = File(cropsDir, "face_${UUID.randomUUID()}.jpg")

        val scaled = if (cropBitmap.width == AVATAR_TARGET_SIZE && cropBitmap.height == AVATAR_TARGET_SIZE) {
            cropBitmap
        } else {
            Bitmap.createScaledBitmap(cropBitmap, AVATAR_TARGET_SIZE, AVATAR_TARGET_SIZE, true)
        }
        FileOutputStream(file).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        if (scaled != cropBitmap && !scaled.isRecycled) {
            scaled.recycle()
        }
        return file.absolutePath
    }

    suspend fun mergePeople(
        sourcePersonIds: List<Long>,
        targetPersonId: Long,
        newName: String
    ) = withContext(Dispatchers.IO) {
        clusterMutex.withLock {
            val cleanName = newName.trim().removePrefix("@").trim()
            personDao.mergePeople(
                sourcePersonIds = sourcePersonIds.filter { it != targetPersonId },
                targetPersonId = targetPersonId,
                newName = cleanName
            )
            loadExistingClustersFromDb()
        }
    }

    /**
     * 4.1 Automatic Deduplication of Clone Profiles & High-Similarity Matches (>= 0.68f).
     * - Step 1: Purges screenshot grid clones (screenshots that spawned >= 3 person links) and orphan 0-photo profiles.
     * - Step 2: Auto-merges all duplicate profiles with cosine similarity >= 0.68f into the highest-priority profile.
     * - Step 3: Reconciles counts, purges 0-photo orphans, and reloads inMemoryClusters from SQLite.
     */
    suspend fun autoDeduplicateClonesAndHighMatches(): Int = withContext(Dispatchers.IO) {
        clusterMutex.withLock {
            var mergedCount = 0

            // Step 1 — Purge Screenshot Grid Clones
            val screenshotCloneMediaIds = mediaDao.getScreenshotGridCloneMediaIdsSync()
            if (screenshotCloneMediaIds.isNotEmpty()) {
                for (mediaId in screenshotCloneMediaIds) {
                    mediaDao.deleteCrossRefsForMediaId(mediaId)
                    personDao.deleteFacesForMediaId(mediaId)
                    mediaDao.updateMediaHasFaces(mediaId, false)
                }
                personDao.deleteOrphanPeopleWithZeroPhotos()
                personDao.reconcilePeopleAfterMediaDeletion()
            }

            // Step 2 — Auto-Merge All Duplicate Profiles with Cosine Similarity >= 0.68f
            val myProfileId = OnboardingPreferences.getMyProfilePersonId(context)
            val allPeople = personDao.getAllPeopleForDeduplicationSync()
                .sortedWith(
                    compareByDescending<PersonEntity> { it.isNamedByUser }
                        .thenByDescending { it.personId == myProfileId }
                        .thenByDescending { it.faceCount }
                        .thenBy { it.personId }
                )

            if (allPeople.size >= 2) {
                val centroidMap = HashMap<Long, FloatArray>(allPeople.size)
                val anchorMap = HashMap<Long, List<FloatArray>>(allPeople.size)
                val personMap = HashMap<Long, PersonEntity>(allPeople.size)

                for (person in allPeople) {
                    personMap[person.personId] = person
                    val anchors = personDao.getAnchorFacesForPersonSync(person.personId).mapNotNull { f ->
                        FaceEmbeddingExtractor.bytesToFloatArray(f.embeddingBlob).takeIf { it.isNotEmpty() }
                    }
                    anchorMap[person.personId] = anchors
                    val decodedCentroid = FaceEmbeddingExtractor.bytesToFloatArray(person.centroidEmbeddingBlob)
                    val resolvedCentroid = if (decodedCentroid.isNotEmpty()) {
                        decodedCentroid
                    } else {
                        anchors.firstOrNull() ?: FloatArray(0)
                    }
                    if (resolvedCentroid.isNotEmpty()) {
                        centroidMap[person.personId] = resolvedCentroid
                    }
                }

                val mergedOutIds = HashSet<Long>()
                for (i in 0 until allPeople.size) {
                    val initialA = allPeople[i]
                    if (mergedOutIds.contains(initialA.personId)) continue

                    for (j in i + 1 until allPeople.size) {
                        val personB = allPeople[j]
                        if (mergedOutIds.contains(personB.personId)) continue

                        val currentA = personMap[initialA.personId] ?: initialA

                        // Never auto-merge two profiles explicitly given different custom names by the user
                        if (currentA.isNamedByUser &&
                            personB.isNamedByUser &&
                            !currentA.name.trim().removePrefix("@")
                                .equals(personB.name.trim().removePrefix("@"), ignoreCase = true)
                        ) {
                            continue
                        }

                        val vecA = centroidMap[currentA.personId] ?: continue
                        val vecB = centroidMap[personB.personId] ?: continue
                        if (vecA.size != vecB.size || vecA.isEmpty()) continue

                        var maxSim = cosineSimilarity(vecA, vecB)
                        val anchorsA = anchorMap[currentA.personId].orEmpty()
                        val anchorsB = anchorMap[personB.personId].orEmpty()
                        for (ea in anchorsA) {
                            for (eb in anchorsB) {
                                if (ea.size == eb.size && ea.isNotEmpty()) {
                                    val s = cosineSimilarity(ea, eb)
                                    if (s > maxSim) maxSim = s
                                }
                            }
                        }

                        if (maxSim >= 0.68f) {
                            val wA = currentA.faceCount.coerceAtLeast(1).toFloat()
                            val wB = personB.faceCount.coerceAtLeast(1).toFloat()
                            val updatedCentroid = updateCentroid(vecA, wA, vecB, wB)

                            val bestAvatarPath = if (
                                personB.bestQualityScore > currentA.bestQualityScore &&
                                personB.avatarCropPath.isNotBlank() &&
                                File(personB.avatarCropPath).exists()
                            ) {
                                personB.avatarCropPath
                            } else if (currentA.avatarCropPath.isNotBlank() && File(currentA.avatarCropPath).exists()) {
                                currentA.avatarCropPath
                            } else {
                                personB.avatarCropPath
                            }
                            val bestQuality = max(currentA.bestQualityScore, personB.bestQualityScore)

                            personDao.mergePeople(
                                sourcePersonIds = listOf(personB.personId),
                                targetPersonId = currentA.personId,
                                newName = currentA.name
                            )

                            val distinctMediaCount = personDao.countDistinctMediaForPerson(currentA.personId)
                                .coerceAtLeast(1)

                            personDao.updatePersonStats(
                                personId = currentA.personId,
                                centroidBlob = floatArrayToBlob(updatedCentroid),
                                faceCount = distinctMediaCount,
                                avatarCropPath = bestAvatarPath,
                                bestQuality = bestQuality
                            )

                            if (OnboardingPreferences.getMyProfilePersonId(context) == personB.personId) {
                                OnboardingPreferences.setMyProfilePersonId(context, currentA.personId)
                            }

                            centroidMap[currentA.personId] = updatedCentroid
                            anchorMap[currentA.personId] = (anchorsA + anchorsB).take(MAX_EXEMPLARS_PER_PERSON)
                            personMap[currentA.personId] = currentA.copy(
                                centroidEmbeddingBlob = floatArrayToBlob(updatedCentroid),
                                faceCount = distinctMediaCount,
                                avatarCropPath = bestAvatarPath,
                                bestQualityScore = bestQuality
                            )

                            mergedOutIds.add(personB.personId)
                            mergedCount++
                        }
                    }
                }
            }

            // Step 3 — Sync Counts & Reload Cache
            personDao.reconcilePeopleAfterMediaDeletion()
            personDao.deleteOrphanPeopleWithZeroPhotos()
            mediaDao.syncAllMediaHasFaces()
            loadExistingClustersFromDb()

            mergedCount
        }
    }

    suspend fun getSimilarProfilesFor(
        targetPersonId: Long,
        limit: Int = 8
    ): List<SimilarProfileCandidate> = withContext(Dispatchers.IO) {
        clusterMutex.withLock {
            ensureCacheLoadedLocked()

            val allPeople = personDao.getAllPeopleSync()
            val visibleOthers = allPeople.filter { it.personId != targetPersonId && !it.isHiddenToOther }
            if (visibleOthers.isEmpty()) return@withLock emptyList()

            val cachedById = clusterCache.associateBy { it.personId }
            val targetCluster = cachedById[targetPersonId] ?: run {
                val targetEntity = personDao.getPersonByIdSync(targetPersonId) ?: return@withLock emptyList()
                val centroid = blobToFloatArray(targetEntity.centroidEmbeddingBlob)
                val anchorFaces = personDao.getAnchorFacesForPersonSync(targetPersonId)
                val exemplars = anchorFaces.mapNotNull { face ->
                    blobToFloatArray(face.embeddingBlob).takeIf { it.isNotEmpty() }
                }.toMutableList()
                if (exemplars.isEmpty() && centroid.isNotEmpty()) {
                    exemplars.add(centroid)
                }
                PersonCluster(
                    personId = targetEntity.personId,
                    name = targetEntity.name,
                    isNamedByUser = targetEntity.isNamedByUser,
                    centroid = centroid,
                    exemplars = exemplars,
                    faceCount = targetEntity.faceCount.coerceAtLeast(1),
                    bestQualityScore = targetEntity.bestQualityScore,
                    avatarCropPath = targetEntity.avatarCropPath
                )
            }

            val scoredCandidates = visibleOthers.map { otherPerson ->
                val otherCluster = cachedById[otherPerson.personId]
                val score = if (otherCluster != null) {
                    val matchScore = computePersonMatchScore(targetCluster.centroid, otherCluster)
                    var maxExSim = matchScore
                    for (ex1 in targetCluster.exemplars) {
                        for (ex2 in otherCluster.exemplars) {
                            if (ex1.size == ex2.size && ex1.isNotEmpty()) {
                                val sim = dotProduct(ex1, ex2)
                                if (sim > maxExSim) maxExSim = sim
                            }
                        }
                    }
                    max(matchScore, maxExSim)
                } else {
                    val otherCentroid = blobToFloatArray(otherPerson.centroidEmbeddingBlob)
                    if (targetCluster.centroid.isNotEmpty() && targetCluster.centroid.size == otherCentroid.size) {
                        dotProduct(targetCluster.centroid, otherCentroid)
                    } else {
                        0f
                    }
                }

                SimilarProfileCandidate(
                    person = otherPerson,
                    similarityScore = score.coerceIn(-1f, 1f),
                    photoCount = otherPerson.faceCount.coerceAtLeast(1)
                )
            }.sortedByDescending { it.similarityScore }

            val namedCandidates = scoredCandidates.filter { it.person.isNamedByUser }
            val topBySimilarity = scoredCandidates.take(limit)

            (topBySimilarity + namedCandidates)
                .distinctBy { it.person.personId }
                .sortedByDescending { it.similarityScore }
                .take(max(limit, namedCandidates.size))
        }
    }

    /**
     * Returns the highest-confidence borderline lookalike pair (similarity in 0.52f..0.679f)
     * where both profiles have at least 1 valid photo and are not ignored.
     * Pairs >= 0.68f are automatically merged by [autoDeduplicateClonesAndHighMatches].
     */
    fun findPossibleDuplicatePair(
        people: List<PersonEntity>,
        ignoredPairKeys: Set<String> = emptySet()
    ): Pair<PersonEntity, PersonEntity>? {
        val validPeople = people.filter { it.faceCount >= 1 }
        if (validPeople.size < 2) return null

        val cachedById = clusterCache.associateBy { it.personId }
        var bestPair: Pair<PersonEntity, PersonEntity>? = null
        var bestSim = -1f

        for (i in 0 until validPeople.size) {
            val p1 = validPeople[i]
            val c1 = cachedById[p1.personId]
            val emb1 = c1?.centroid?.takeIf { it.isNotEmpty() } ?: blobToFloatArray(p1.centroidEmbeddingBlob)
            for (j in i + 1 until validPeople.size) {
                val p2 = validPeople[j]
                if (p1.personId == p2.personId) continue
                val key = pairKey(p1.personId, p2.personId)
                if (ignoredPairKeys.contains(key)) continue

                val c2 = cachedById[p2.personId]
                val sim = if (c1 != null && c2 != null) {
                    if (c1.mediaIds.any { c2.mediaIds.contains(it) }) continue
                    computeClusterToClusterSimilarity(c1, c2)
                } else {
                    val emb2 = blobToFloatArray(p2.centroidEmbeddingBlob)
                    if (emb1.size != emb2.size || emb1.isEmpty()) continue
                    dotProduct(emb1, emb2)
                }

                if (sim in 0.52f..0.679f && sim > bestSim) {
                    bestSim = sim
                    val (primary, secondary) = if (p1.isNamedByUser && !p2.isNamedByUser) {
                        p1 to p2
                    } else if (!p1.isNamedByUser && p2.isNamedByUser) {
                        p2 to p1
                    } else if (p1.faceCount >= p2.faceCount) {
                        p1 to p2
                    } else {
                        p2 to p1
                    }
                    bestPair = Pair(primary, secondary)
                }
            }
        }
        return bestPair
    }

    fun pairKey(id1: Long, id2: Long): String {
        return if (id1 < id2) "${id1}_${id2}" else "${id2}_${id1}"
    }

    fun dotProduct(v1: FloatArray, v2: FloatArray): Float {
        val len = min(v1.size, v2.size)
        if (len == 0) return 0f
        var dot = 0f
        for (i in 0 until len) {
            dot += v1[i] * v2[i]
        }
        return dot
    }

    fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float = dotProduct(v1, v2)

    fun l2Normalize(vector: FloatArray): FloatArray {
        var sum = 0.0
        for (v in vector) sum += (v * v).toDouble()
        val norm = sqrt(sum).toFloat()
        if (norm < 1e-6f) return vector
        val res = FloatArray(vector.size)
        for (i in vector.indices) res[i] = vector[i] / norm
        return res
    }

    fun floatArrayToBlob(floats: FloatArray): ByteArray {
        return FaceEmbeddingExtractor.floatArrayToBytes(floats)
    }

    fun blobToFloatArray(blob: ByteArray): FloatArray {
        return FaceEmbeddingExtractor.bytesToFloatArray(blob)
    }
}
