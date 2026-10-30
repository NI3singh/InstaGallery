// InstaGallery v1.0
package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.local.entity.CoOccurringPerson
import com.example.data.local.entity.FaceEntity
import com.example.data.local.entity.PersonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {

    @Query("SELECT * FROM people WHERE isHiddenToOther = 0 ORDER BY isNamedByUser DESC, faceCount DESC, createdAt ASC")
    fun getAllPeople(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM people WHERE isHiddenToOther = 0 ORDER BY isNamedByUser DESC, faceCount DESC, createdAt ASC")
    suspend fun getAllPeopleSync(): List<PersonEntity>

    @Query("SELECT * FROM people")
    suspend fun getAllPeopleIncludeHiddenSync(): List<PersonEntity>

    @Query("SELECT * FROM people ORDER BY isNamedByUser DESC, faceCount DESC, bestQualityScore DESC, createdAt ASC")
    fun getAllPeopleIncludeHiddenFlow(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM people ORDER BY isNamedByUser DESC, faceCount DESC, personId ASC")
    suspend fun getAllPeopleForDeduplicationSync(): List<PersonEntity>

    @Query(
        """
        SELECT p.* FROM people p
        WHERE p.isHiddenToOther = 0
          AND (
              p.isNamedByUser = 1
              OR p.decisionState = 'KEPT'
              OR (
                  SELECT COUNT(DISTINCT ref.mediaId)
                  FROM media_person_cross_ref ref
                  INNER JOIN media_items m ON m.mediaId = ref.mediaId
                  WHERE ref.personId = p.personId AND m.isVideo = 0
              ) >= 2
              OR (
                  EXISTS (
                      SELECT 1 FROM media_person_cross_ref ref
                      INNER JOIN media_items m ON m.mediaId = ref.mediaId
                      WHERE ref.personId = p.personId AND m.isVideo = 0
                  )
                  AND NOT EXISTS (
                      SELECT 1 FROM people p2
                      WHERE p2.isHiddenToOther = 0
                        AND (p2.isNamedByUser = 1 OR p2.decisionState = 'KEPT' OR p2.faceCount >= 2)
                  )
              )
          )
        ORDER BY p.faceCount DESC, p.personId ASC
        """
    )
    fun getAllPeopleForSetupFlow(): Flow<List<PersonEntity>>

    @Query(
        """
        SELECT p.* FROM people p
        WHERE p.isHiddenToOther = 0
          AND (
              p.isNamedByUser = 1
              OR p.decisionState = 'KEPT'
              OR (
                  SELECT COUNT(DISTINCT ref.mediaId)
                  FROM media_person_cross_ref ref
                  INNER JOIN media_items m ON m.mediaId = ref.mediaId
                  WHERE ref.personId = p.personId AND m.isVideo = 0
              ) >= 2
              OR (
                  EXISTS (
                      SELECT 1 FROM media_person_cross_ref ref
                      INNER JOIN media_items m ON m.mediaId = ref.mediaId
                      WHERE ref.personId = p.personId AND m.isVideo = 0
                  )
                  AND NOT EXISTS (
                      SELECT 1 FROM people p2
                      WHERE p2.isHiddenToOther = 0
                        AND (p2.isNamedByUser = 1 OR p2.decisionState = 'KEPT' OR p2.faceCount >= 2)
                  )
              )
          )
        ORDER BY p.isNamedByUser DESC, p.faceCount DESC, p.bestQualityScore DESC
        """
    )
    fun getVisiblePeopleFlow(): Flow<List<PersonEntity>>

    @Query(
        """
        SELECT p.* FROM people p
        WHERE p.isHiddenToOther = 0
          AND (
              p.isNamedByUser = 1
              OR p.decisionState = 'KEPT'
              OR (
                  SELECT COUNT(DISTINCT ref.mediaId)
                  FROM media_person_cross_ref ref
                  INNER JOIN media_items m ON m.mediaId = ref.mediaId
                  WHERE ref.personId = p.personId AND m.isVideo = 0
              ) >= 2
              OR (
                  EXISTS (
                      SELECT 1 FROM media_person_cross_ref ref
                      INNER JOIN media_items m ON m.mediaId = ref.mediaId
                      WHERE ref.personId = p.personId AND m.isVideo = 0
                  )
                  AND NOT EXISTS (
                      SELECT 1 FROM people p2
                      WHERE p2.isHiddenToOther = 0
                        AND (p2.isNamedByUser = 1 OR p2.decisionState = 'KEPT' OR p2.faceCount >= 2)
                  )
              )
          )
        ORDER BY p.isNamedByUser DESC, p.faceCount DESC, p.bestQualityScore DESC
        """
    )
    suspend fun getVisiblePeopleSync(): List<PersonEntity>

    @Query(
        """
        SELECT p.* FROM people p
        WHERE p.isHiddenToOther = 0
          AND p.isNamedByUser = 0
          AND p.decisionState = 'UNDECIDED'
          AND (
              SELECT COUNT(DISTINCT ref.mediaId)
              FROM media_person_cross_ref ref
              INNER JOIN media_items m ON m.mediaId = ref.mediaId
              WHERE ref.personId = p.personId AND m.isVideo = 0
          ) >= 3
        ORDER BY p.faceCount DESC, p.createdAt DESC
        """
    )
    fun getNewUndecidedPeopleFlow(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM people WHERE personId = :personId")
    fun getPersonById(personId: Long): Flow<PersonEntity?>

    @Query("SELECT * FROM people WHERE personId = :personId")
    suspend fun getPersonByIdSync(personId: Long): PersonEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerson(person: PersonEntity): Long

    @Update
    suspend fun updatePerson(person: PersonEntity)

    @Query("UPDATE people SET name = :name, isNamedByUser = 1, isHiddenToOther = 0, decisionState = 'KEPT' WHERE personId = :personId")
    suspend fun updatePersonName(personId: Long, name: String)

    @Query("UPDATE people SET name = :newName, isNamedByUser = 1, isHiddenToOther = 0, decisionState = 'KEPT' WHERE personId = :personId")
    suspend fun markPersonNamedByUser(personId: Long, newName: String)

    @Query("UPDATE people SET isHiddenToOther = 1, decisionState = 'MOVED_TO_OTHER' WHERE personId = :personId")
    suspend fun movePersonToOtherSql(personId: Long)

    @Query("UPDATE people SET isHiddenToOther = 0, decisionState = 'UNDECIDED' WHERE personId = :personId")
    suspend fun restorePersonFromOtherSql(personId: Long)

    @Query("UPDATE people SET name = :newName, isNamedByUser = 1, isHiddenToOther = 0, decisionState = 'KEPT' WHERE personId = :personId")
    suspend fun markPersonKeptSql(personId: Long, newName: String)

    @Query(
        """
        UPDATE people 
        SET centroidEmbeddingBlob = :centroidBlob, 
            faceCount = :faceCount, 
            avatarCropPath = :avatarCropPath, 
            bestQualityScore = :bestQuality 
        WHERE personId = :personId
        """
    )
    suspend fun updatePersonStats(
        personId: Long,
        centroidBlob: ByteArray,
        faceCount: Int,
        avatarCropPath: String,
        bestQuality: Float
    )

    @Query("UPDATE people SET avatarCropPath = :cropPath WHERE personId = :personId")
    suspend fun updateAvatar(personId: Long, cropPath: String)

    @Query("UPDATE people SET faceCount = :count WHERE personId = :personId")
    suspend fun updatePersonFaceCount(personId: Long, count: Int)

    @Query("UPDATE people SET isHiddenToOther = 1, decisionState = 'MOVED_TO_OTHER' WHERE personId = :personId")
    suspend fun setPersonHiddenFlag(personId: Long)

    @Query("UPDATE people SET isHiddenToOther = 0, decisionState = 'UNDECIDED' WHERE personId = :personId")
    suspend fun clearPersonHiddenFlag(personId: Long)

    @Query("DELETE FROM people WHERE personId = :personId")
    suspend fun deletePerson(personId: Long)

    @Query("DELETE FROM people")
    suspend fun deleteAllPeople()

    @Query("DELETE FROM detected_faces")
    suspend fun deleteAllFaces()

    @Query("DELETE FROM detected_faces WHERE personId = :personId")
    suspend fun deleteFacesForPerson(personId: Long)

    @Query("UPDATE detected_faces SET personId = NULL, isAnchorExemplar = 0 WHERE personId = :personId")
    suspend fun unassignFacesForPerson(personId: Long)

    @Query(
        """
        UPDATE media_items 
        SET hasFaces = CASE 
            WHEN EXISTS (
                SELECT 1 FROM media_person_cross_ref 
                INNER JOIN people ON people.personId = media_person_cross_ref.personId
                WHERE media_person_cross_ref.mediaId = media_items.mediaId
                  AND people.isHiddenToOther = 0
            ) THEN 1 
            ELSE 0 
        END 
        WHERE mediaId IN (:mediaIds)
        """
    )
    suspend fun syncHasFacesForMediaIds(mediaIds: List<Long>)

    @Transaction
    suspend fun hidePersonToOther(personId: Long) {
        val affectedMediaIds = getMediaIdsForPerson(personId)
        movePersonToOtherSql(personId)
        if (affectedMediaIds.isNotEmpty()) {
            syncHasFacesForMediaIds(affectedMediaIds)
        }
    }

    @Transaction
    suspend fun unhidePersonFromOther(personId: Long) {
        restorePersonFromOtherSql(personId)
        val affectedMediaIds = getMediaIdsForPerson(personId)
        if (affectedMediaIds.isNotEmpty()) {
            syncHasFacesForMediaIds(affectedMediaIds)
        }
    }

    @Transaction
    suspend fun movePersonToOther(personId: Long) {
        hidePersonToOther(personId)
    }

    @Query(
        """
        SELECT COUNT(DISTINCT media_person_cross_ref.mediaId) 
        FROM media_person_cross_ref 
        INNER JOIN media_items ON media_items.mediaId = media_person_cross_ref.mediaId 
        WHERE media_person_cross_ref.personId = :personId AND media_items.isVideo = 0
        """
    )
    fun getPhotoCountForPerson(personId: Long): Flow<Int>

    @Query(
        """
        SELECT COUNT(DISTINCT media_person_cross_ref.mediaId) 
        FROM media_person_cross_ref 
        INNER JOIN media_items ON media_items.mediaId = media_person_cross_ref.mediaId 
        WHERE media_person_cross_ref.personId = :personId AND media_items.isVideo = 1
        """
    )
    fun getReelCountForPerson(personId: Long): Flow<Int>

    @Query(
        """
        SELECT COUNT(DISTINCT media_person_cross_ref.mediaId) 
        FROM media_person_cross_ref 
        INNER JOIN media_items ON media_items.mediaId = media_person_cross_ref.mediaId 
        WHERE media_person_cross_ref.personId = :personId AND media_items.isVideo = 0
        """
    )
    fun getTotalCountForPerson(personId: Long): Flow<Int>

    @Query(
        """
        SELECT COUNT(DISTINCT media_person_cross_ref.mediaId) 
        FROM media_person_cross_ref 
        INNER JOIN media_items ON media_items.mediaId = media_person_cross_ref.mediaId 
        WHERE media_person_cross_ref.personId = :personId AND media_items.isVideo = 0
        """
    )
    suspend fun countDistinctMediaForPerson(personId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFace(face: FaceEntity): Long

    @Query("SELECT * FROM detected_faces ORDER BY qualityScore DESC")
    suspend fun getAllFacesSync(): List<FaceEntity>

    @Query("SELECT COUNT(*) FROM detected_faces")
    fun getTotalDetectedFacesCountFlow(): Flow<Int>

    @Query("SELECT * FROM detected_faces WHERE personId IS NULL ORDER BY qualityScore DESC")
    suspend fun getUnassignedFacesSync(): List<FaceEntity>

    @Query("SELECT * FROM detected_faces WHERE personId = :personId ORDER BY qualityScore DESC LIMIT 12")
    suspend fun getAnchorFacesForPersonSync(personId: Long): List<FaceEntity>

    @Query("UPDATE detected_faces SET personId = :personId, isAnchorExemplar = :isAnchor WHERE faceId = :faceId")
    suspend fun assignFaceToPerson(faceId: Long, personId: Long, isAnchor: Boolean)

    @Query("SELECT * FROM detected_faces WHERE personId = :personId ORDER BY qualityScore DESC, faceId DESC")
    fun getFacesForPerson(personId: Long): Flow<List<FaceEntity>>

    @Query("SELECT * FROM detected_faces WHERE personId = :personId ORDER BY qualityScore DESC, faceId DESC")
    suspend fun getFacesForPersonSync(personId: Long): List<FaceEntity>

    @Query("SELECT * FROM detected_faces WHERE mediaId = :mediaId")
    fun getFacesForMedia(mediaId: Long): Flow<List<FaceEntity>>

    @Query("SELECT * FROM detected_faces WHERE mediaId = :mediaId")
    suspend fun getFacesForMediaSync(mediaId: Long): List<FaceEntity>

    @Query("DELETE FROM detected_faces WHERE mediaId = :mediaId")
    suspend fun deleteFacesForMediaId(mediaId: Long)

    @Query("DELETE FROM detected_faces WHERE mediaId = :mediaId AND personId = :personId")
    suspend fun deleteFacesForMediaAndPerson(mediaId: Long, personId: Long)

    @Query(
        """
        DELETE FROM people
        WHERE isNamedByUser = 0
          AND NOT EXISTS (
              SELECT 1 FROM media_person_cross_ref ref
              INNER JOIN media_items m ON m.mediaId = ref.mediaId
              WHERE ref.personId = people.personId AND m.isVideo = 0
          )
        """
    )
    suspend fun deleteOrphanPeopleWithZeroPhotos()

    @Transaction
    suspend fun removeMediaFromPersonAndSyncCount(mediaId: Long, personId: Long) {
        deleteCrossRef(mediaId, personId)
        deleteFacesForMediaAndPerson(mediaId, personId)
        val remaining = countDistinctMediaForPerson(personId)
        updatePersonFaceCount(personId, remaining)
        syncHasFacesForMediaIds(listOf(mediaId))
    }

    @Transaction
    suspend fun tagPersonInMediaAndSyncCount(mediaId: Long, personId: Long) {
        insertCrossRefIgnore(mediaId, personId)
        val total = countDistinctMediaForPerson(personId)
        updatePersonFaceCount(personId, total)
        syncHasFacesForMediaIds(listOf(mediaId))
    }

    @Transaction
    suspend fun mergePeople(sourcePersonIds: List<Long>, targetPersonId: Long, newName: String) {
        val target = getPersonByIdSync(targetPersonId) ?: return
        var anySourceNamed = false
        val affectedMediaIds = mutableSetOf<Long>()

        for (sourceId in sourcePersonIds) {
            if (sourceId == targetPersonId) continue
            val sourcePerson = getPersonByIdSync(sourceId)
            if (sourcePerson?.isNamedByUser == true) {
                anySourceNamed = true
            }

            // Reassign faces
            reassignFaces(sourceId, targetPersonId)

            // Reassign media cross refs
            val mediaIds = getMediaIdsForPerson(sourceId)
            for (mediaId in mediaIds) {
                insertCrossRefIgnore(mediaId, targetPersonId)
                affectedMediaIds.add(mediaId)
            }
            deleteCrossRefsForPerson(sourceId)

            // Delete source person
            deletePerson(sourceId)
        }

        // Update target person name and distinct photo count
        val totalMedia = countDistinctMediaForPerson(targetPersonId).coerceAtLeast(1)
        val resolvedName = newName.trim().removePrefix("@").trim().ifBlank { target.name }
        val isDefaultPlaceholder = resolvedName.matches(Regex("^Person\\s+\\d+$", RegexOption.IGNORE_CASE))
        val shouldMarkNamed = target.isNamedByUser || anySourceNamed || !isDefaultPlaceholder
        val resolvedDecisionState = if (shouldMarkNamed || target.decisionState == "KEPT") {
            "KEPT"
        } else {
            "UNDECIDED"
        }

        updatePerson(
            target.copy(
                name = resolvedName,
                faceCount = totalMedia,
                isNamedByUser = shouldMarkNamed,
                isHiddenToOther = false,
                decisionState = resolvedDecisionState
            )
        )
        if (affectedMediaIds.isNotEmpty()) {
            syncHasFacesForMediaIds(affectedMediaIds.toList())
        }
    }

    @Query("UPDATE detected_faces SET personId = :targetPersonId WHERE personId = :sourcePersonId")
    suspend fun reassignFaces(sourcePersonId: Long, targetPersonId: Long)

    @Query(
        """
        SELECT DISTINCT media_person_cross_ref.mediaId 
        FROM media_person_cross_ref 
        INNER JOIN media_items ON media_items.mediaId = media_person_cross_ref.mediaId
        WHERE media_person_cross_ref.personId = :personId AND media_items.isVideo = 0
        """
    )
    suspend fun getMediaIdsForPerson(personId: Long): List<Long>

    @Query("INSERT OR IGNORE INTO media_person_cross_ref (mediaId, personId) VALUES (:mediaId, :personId)")
    suspend fun insertCrossRefIgnore(mediaId: Long, personId: Long)

    @Query("DELETE FROM media_person_cross_ref WHERE mediaId = :mediaId AND personId = :personId")
    suspend fun deleteCrossRef(mediaId: Long, personId: Long)

    @Query("DELETE FROM media_person_cross_ref WHERE personId = :personId")
    suspend fun deleteCrossRefsForPerson(personId: Long)

    @Query("SELECT * FROM detected_faces WHERE personId = :personId ORDER BY qualityScore DESC, faceId DESC LIMIT 1")
    suspend fun getHighestQualityFaceForPersonSync(personId: Long): FaceEntity?

    @Query("SELECT COUNT(*) FROM detected_faces WHERE personId = :personId")
    suspend fun countFacesForPerson(personId: Long): Int

    @Query(
        """
        UPDATE people
        SET faceCount = (
            SELECT COUNT(DISTINCT ref.mediaId)
            FROM media_person_cross_ref ref
            INNER JOIN media_items m ON m.mediaId = ref.mediaId
            WHERE ref.personId = people.personId AND m.isVideo = 0
        )
        """
    )
    suspend fun syncAllPeoplePhotoCountsSql()

    @Query(
        """
        DELETE FROM people
        WHERE isNamedByUser = 0
          AND decisionState != 'KEPT'
          AND NOT EXISTS (
              SELECT 1 FROM media_person_cross_ref ref
              INNER JOIN media_items m ON m.mediaId = ref.mediaId
              WHERE ref.personId = people.personId AND m.isVideo = 0
          )
        """
    )
    suspend fun deleteEmptyUnnamedPeopleSql()

    @Transaction
    suspend fun reconcilePeopleAfterMediaDeletion() {
        syncAllPeoplePhotoCountsSql()
        deleteEmptyUnnamedPeopleSql()
    }

    @Query(
        """
        SELECT p.*, COUNT(DISTINCT ref1.mediaId) AS sharedPhotoCount
        FROM media_person_cross_ref ref1
        INNER JOIN media_person_cross_ref ref2 ON ref1.mediaId = ref2.mediaId
        INNER JOIN people p ON p.personId = ref2.personId
        INNER JOIN media_items m ON m.mediaId = ref1.mediaId
        WHERE ref1.personId = :personId
          AND ref2.personId != :personId
          AND p.isHiddenToOther = 0
          AND m.isVideo = 0
        GROUP BY p.personId
        HAVING sharedPhotoCount >= 1
        ORDER BY sharedPhotoCount DESC, p.isNamedByUser DESC
        """
    )
    fun getCoOccurringPeopleFlow(personId: Long): Flow<List<CoOccurringPerson>>
}
