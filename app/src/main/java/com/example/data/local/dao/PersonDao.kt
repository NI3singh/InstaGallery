// InstaGallery v1.0
package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
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

    @Query("SELECT * FROM people WHERE isHiddenToOther = 0 AND (faceCount >= 2 OR isNamedByUser = 1) ORDER BY isNamedByUser DESC, faceCount DESC")
    fun getVisiblePeopleFlow(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM people WHERE personId = :personId")
    fun getPersonById(personId: Long): Flow<PersonEntity?>

    @Query("SELECT * FROM people WHERE personId = :personId")
    suspend fun getPersonByIdSync(personId: Long): PersonEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerson(person: PersonEntity): Long

    @Update
    suspend fun updatePerson(person: PersonEntity)

    @Query("UPDATE people SET name = :name, isNamedByUser = 1, isHiddenToOther = 0 WHERE personId = :personId")
    suspend fun updatePersonName(personId: Long, name: String)

    @Query("UPDATE people SET name = :newName, isNamedByUser = 1, isHiddenToOther = 0 WHERE personId = :personId")
    suspend fun markPersonNamedByUser(personId: Long, newName: String)

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

    @Query("UPDATE people SET isHiddenToOther = 1 WHERE personId = :personId")
    suspend fun setPersonHiddenFlag(personId: Long)

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
                WHERE media_person_cross_ref.mediaId = media_items.mediaId
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
        deleteCrossRefsForPerson(personId)
        unassignFacesForPerson(personId)
        setPersonHiddenFlag(personId)
        if (affectedMediaIds.isNotEmpty()) {
            syncHasFacesForMediaIds(affectedMediaIds)
        }
    }

    @Transaction
    suspend fun movePersonToOther(personId: Long) {
        val affectedMediaIds = getMediaIdsForPerson(personId)
        deleteCrossRefsForPerson(personId)
        unassignFacesForPerson(personId)
        deletePerson(personId)
        if (affectedMediaIds.isNotEmpty()) {
            syncHasFacesForMediaIds(affectedMediaIds)
        }
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
        SELECT COUNT(DISTINCT mediaId) 
        FROM media_person_cross_ref 
        WHERE personId = :personId
        """
    )
    fun getTotalCountForPerson(personId: Long): Flow<Int>

    @Query(
        """
        SELECT COUNT(DISTINCT mediaId) 
        FROM media_person_cross_ref 
        WHERE personId = :personId
        """
    )
    suspend fun countDistinctMediaForPerson(personId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFace(face: FaceEntity): Long

    @Query("SELECT * FROM detected_faces ORDER BY qualityScore DESC")
    suspend fun getAllFacesSync(): List<FaceEntity>

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

    @Query("DELETE FROM detected_faces WHERE mediaId = :mediaId AND personId = :personId")
    suspend fun deleteFacesForMediaAndPerson(mediaId: Long, personId: Long)

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

        for (sourceId in sourcePersonIds) {
            if (sourceId == targetPersonId) continue

            // Reassign faces
            reassignFaces(sourceId, targetPersonId)

            // Reassign media cross refs
            val mediaIds = getMediaIdsForPerson(sourceId)
            for (mediaId in mediaIds) {
                insertCrossRefIgnore(mediaId, targetPersonId)
            }
            deleteCrossRefsForPerson(sourceId)

            // Delete source person
            deletePerson(sourceId)
        }

        // Update target person name and distinct media post count
        val totalMedia = countDistinctMediaForPerson(targetPersonId).coerceAtLeast(1)
        val resolvedName = newName.trim().ifBlank { target.name }
        updatePerson(
            target.copy(
                name = resolvedName,
                faceCount = totalMedia,
                isNamedByUser = true,
                isHiddenToOther = false
            )
        )
    }

    @Query("UPDATE detected_faces SET personId = :targetPersonId WHERE personId = :sourcePersonId")
    suspend fun reassignFaces(sourcePersonId: Long, targetPersonId: Long)

    @Query("SELECT mediaId FROM media_person_cross_ref WHERE personId = :personId")
    suspend fun getMediaIdsForPerson(personId: Long): List<Long>

    @Query("INSERT OR IGNORE INTO media_person_cross_ref (mediaId, personId) VALUES (:mediaId, :personId)")
    suspend fun insertCrossRefIgnore(mediaId: Long, personId: Long)

    @Query("DELETE FROM media_person_cross_ref WHERE mediaId = :mediaId AND personId = :personId")
    suspend fun deleteCrossRef(mediaId: Long, personId: Long)

    @Query("DELETE FROM media_person_cross_ref WHERE personId = :personId")
    suspend fun deleteCrossRefsForPerson(personId: Long)

    @Query("SELECT COUNT(*) FROM detected_faces WHERE personId = :personId")
    suspend fun countFacesForPerson(personId: Long): Int
}
