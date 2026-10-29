// InstaGallery v1.0
package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import com.example.data.local.entity.MediaEntity
import com.example.data.local.entity.MediaPersonCrossRef
import com.example.data.local.entity.MediaWithAuthors
import kotlinx.coroutines.flow.Flow

data class MediaIdAndUri(
    val mediaId: Long,
    val uri: String
)

@Dao
interface MediaDao {

    @Transaction
    @Query("SELECT * FROM media_items ORDER BY dateAddedSeconds DESC")
    fun getAllMediaWithAuthors(): Flow<List<MediaWithAuthors>>

    @Transaction
    @Query("SELECT * FROM media_items ORDER BY dateAddedSeconds DESC")
    suspend fun getAllMediaWithAuthorsSync(): List<MediaWithAuthors>

    @Transaction
    @Query("SELECT * FROM media_items WHERE isVideo = 0 ORDER BY dateAddedSeconds DESC")
    fun getPhotosWithAuthors(): Flow<List<MediaWithAuthors>>

    @Transaction
    @Query("SELECT * FROM media_items WHERE isVideo = 1 ORDER BY dateAddedSeconds DESC")
    fun getVideosWithAuthors(): Flow<List<MediaWithAuthors>>

    @Transaction
    @Query("SELECT * FROM media_items WHERE isVideo = 0 AND isFavorite = 1 ORDER BY dateAddedSeconds DESC")
    fun getFavoritePhotosWithAuthors(): Flow<List<MediaWithAuthors>>

    @Transaction
    @Query("SELECT * FROM media_items WHERE isVideo = 0 AND isBookmarked = 1 ORDER BY dateAddedSeconds DESC")
    fun getBookmarkedPhotosWithAuthors(): Flow<List<MediaWithAuthors>>

    @Transaction
    @Query("SELECT * FROM media_items WHERE mediaId = :mediaId")
    fun getMediaById(mediaId: Long): Flow<MediaWithAuthors?>

    @Transaction
    @Query("SELECT * FROM media_items WHERE mediaId = :mediaId")
    fun getMediaWithAuthorsById(mediaId: Long): Flow<MediaWithAuthors?>

    @Query("SELECT * FROM media_items WHERE mediaId = :mediaId")
    suspend fun getMediaByIdSync(mediaId: Long): MediaEntity?

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT DISTINCT m.* FROM media_items m
        INNER JOIN media_person_cross_ref ref ON m.mediaId = ref.mediaId
        WHERE ref.personId = :personId AND m.isVideo = 0
        ORDER BY m.dateAddedSeconds DESC
        """
    )
    fun getMediaWithAuthorsForPersonFlow(personId: Long): Flow<List<MediaWithAuthors>>

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT DISTINCT m.* FROM media_items m
        INNER JOIN media_person_cross_ref ref ON m.mediaId = ref.mediaId
        WHERE ref.personId = :personId AND m.isVideo = 0
        ORDER BY m.dateAddedSeconds DESC
        """
    )
    fun getMediaForPerson(personId: Long): Flow<List<MediaWithAuthors>>

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT DISTINCT m.* FROM media_items m
        INNER JOIN media_person_cross_ref ref ON m.mediaId = ref.mediaId
        WHERE ref.personId = :personId AND m.isVideo = 0
        ORDER BY m.dateAddedSeconds DESC
        """
    )
    suspend fun getMediaForPersonSync(personId: Long): List<MediaWithAuthors>

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT DISTINCT m.* FROM media_items m
        INNER JOIN media_person_cross_ref ref ON m.mediaId = ref.mediaId
        WHERE ref.personId = :personId AND m.isVideo = 0
        ORDER BY m.dateAddedSeconds DESC
        """
    )
    fun getPhotosForPerson(personId: Long): Flow<List<MediaWithAuthors>>

    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT DISTINCT m.* FROM media_items m
        INNER JOIN media_person_cross_ref ref ON m.mediaId = ref.mediaId
        WHERE ref.personId = :personId AND m.isVideo = 0
        ORDER BY m.dateAddedSeconds DESC
        LIMIT :limit
        """
    )
    suspend fun getSamplePhotosForPersonSync(personId: Long, limit: Int = 3): List<MediaEntity>

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT DISTINCT media_items.* FROM media_items 
        INNER JOIN media_person_cross_ref ON media_items.mediaId = media_person_cross_ref.mediaId 
        WHERE media_person_cross_ref.personId = :personId AND media_items.isVideo = 1
        ORDER BY media_items.dateAddedSeconds DESC
        """
    )
    fun getVideosForPerson(personId: Long): Flow<List<MediaWithAuthors>>

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT DISTINCT media_items.* FROM media_items 
        INNER JOIN media_person_cross_ref ON media_items.mediaId = media_person_cross_ref.mediaId 
        WHERE media_person_cross_ref.personId = :personId AND media_items.isVideo = 1
        ORDER BY media_items.dateAddedSeconds DESC
        """
    )
    fun getReelsForPerson(personId: Long): Flow<List<MediaWithAuthors>>

    @Transaction
    @Query(
        """
        SELECT * FROM media_items m
        WHERE m.isVideo = 0
          AND NOT EXISTS (
              SELECT 1 FROM media_person_cross_ref ref
              INNER JOIN people p ON p.personId = ref.personId
              WHERE ref.mediaId = m.mediaId AND p.isHiddenToOther = 0
          )
        ORDER BY m.dateAddedSeconds DESC
        """
    )
    fun getUnassignedPhotosWithAuthorsFlow(): Flow<List<MediaWithAuthors>>

    @Transaction
    @Query(
        """
        SELECT * FROM media_items m
        WHERE m.isVideo = 0
          AND NOT EXISTS (
              SELECT 1 FROM media_person_cross_ref ref
              INNER JOIN people p ON p.personId = ref.personId
              WHERE ref.mediaId = m.mediaId AND p.isHiddenToOther = 0
          )
        ORDER BY m.dateAddedSeconds DESC
        """
    )
    fun getUnassociatedMedia(): Flow<List<MediaWithAuthors>>

    @Transaction
    @Query(
        """
        SELECT * FROM media_items m
        WHERE m.isVideo = 0
          AND NOT EXISTS (
              SELECT 1 FROM media_person_cross_ref ref
              INNER JOIN people p ON p.personId = ref.personId
              WHERE ref.mediaId = m.mediaId AND p.isHiddenToOther = 0
          )
        ORDER BY m.dateAddedSeconds DESC
        """
    )
    fun getUnassociatedPhotos(): Flow<List<MediaWithAuthors>>

    @Transaction
    @Query(
        """
        SELECT * FROM media_items 
        WHERE isVideo = 1 AND NOT EXISTS (
            SELECT 1 FROM media_person_cross_ref 
            INNER JOIN people ON people.personId = media_person_cross_ref.personId
            WHERE media_person_cross_ref.mediaId = media_items.mediaId
              AND people.isHiddenToOther = 0
        ) 
        ORDER BY dateAddedSeconds DESC
        """
    )
    fun getUnassociatedVideos(): Flow<List<MediaWithAuthors>>

    @Transaction
    @Query(
        """
        SELECT * FROM media_items 
        WHERE isVideo = 1 AND NOT EXISTS (
            SELECT 1 FROM media_person_cross_ref 
            INNER JOIN people ON people.personId = media_person_cross_ref.personId
            WHERE media_person_cross_ref.mediaId = media_items.mediaId
              AND people.isHiddenToOther = 0
        ) 
        ORDER BY dateAddedSeconds DESC
        """
    )
    fun getUnassociatedReels(): Flow<List<MediaWithAuthors>>

    @Query(
        """
        SELECT COUNT(*) FROM media_items m
        WHERE m.isVideo = 0
          AND NOT EXISTS (
              SELECT 1 FROM media_person_cross_ref ref
              INNER JOIN people p ON p.personId = ref.personId
              WHERE ref.mediaId = m.mediaId AND p.isHiddenToOther = 0
          )
        """
    )
    fun getUnassociatedCount(): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM media_items m
        WHERE m.isVideo = 0
          AND NOT EXISTS (
              SELECT 1 FROM media_person_cross_ref ref
              INNER JOIN people p ON p.personId = ref.personId
              WHERE ref.mediaId = m.mediaId AND p.isHiddenToOther = 0
          )
        """
    )
    fun getUnassociatedPhotoCount(): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM media_items 
        WHERE isVideo = 1 AND NOT EXISTS (
            SELECT 1 FROM media_person_cross_ref 
            INNER JOIN people ON people.personId = media_person_cross_ref.personId
            WHERE media_person_cross_ref.mediaId = media_items.mediaId
              AND people.isHiddenToOther = 0
        )
        """
    )
    fun getUnassociatedVideoCount(): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM media_items 
        WHERE isVideo = 1 AND NOT EXISTS (
            SELECT 1 FROM media_person_cross_ref 
            INNER JOIN people ON people.personId = media_person_cross_ref.personId
            WHERE media_person_cross_ref.mediaId = media_items.mediaId
              AND people.isHiddenToOther = 0
        )
        """
    )
    fun getUnassociatedReelCount(): Flow<Int>

    @Query("SELECT * FROM media_items WHERE isVideo = 0 AND isProcessedForFaces = 0 ORDER BY dateAddedSeconds DESC LIMIT :limit")
    suspend fun getUnindexedMedia(limit: Int = 5000): List<MediaEntity>

    @Query("SELECT * FROM media_items WHERE isVideo = 0 AND isProcessedForFaces = 0 ORDER BY dateAddedSeconds DESC LIMIT :limit")
    suspend fun getUnindexedPhotos(limit: Int = 5000): List<MediaEntity>

    @Query("SELECT * FROM media_items WHERE isVideo = 0 AND isProcessedForFaces = 0 ORDER BY dateAddedSeconds DESC LIMIT :limit")
    suspend fun getNewestUnindexedPhotosSync(limit: Int): List<MediaEntity>

    @Query("UPDATE media_items SET isProcessedForFaces = 1, hasFaces = 0 WHERE isVideo = 1 AND isProcessedForFaces = 0")
    suspend fun markAllVideosProcessed()

    @Query("SELECT COALESCE(MAX(dateAddedSeconds), 0) FROM media_items")
    suspend fun getMaxDateAddedSeconds(): Long

    @Query("SELECT COUNT(*) FROM media_items WHERE isVideo = 0 AND isProcessedForFaces = 0")
    fun getUnindexedPhotoCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM media_items WHERE isVideo = 0 AND isProcessedForFaces = 0")
    fun getUnindexedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM media_items WHERE isVideo = 0 AND isProcessedForFaces = 0")
    suspend fun getUnindexedCountSync(): Int

    @Query("SELECT COUNT(*) FROM media_items WHERE isVideo = 0 AND isProcessedForFaces = 0")
    suspend fun getUnprocessedMediaCount(): Int

    @Query("SELECT COUNT(*) FROM media_items WHERE isVideo = 0 AND isProcessedForFaces = 1")
    fun getProcessedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM media_items WHERE isVideo = 0")
    fun getTotalMediaCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM media_items WHERE isVideo = 0")
    suspend fun getTotalMediaCountSync(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMediaList(media: List<MediaEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMediaItems(media: List<MediaEntity>): List<Long>

    @Query("UPDATE media_items SET isFavorite = :isFavorite WHERE mediaId = :mediaId")
    suspend fun setFavorite(mediaId: Long, isFavorite: Boolean)

    @Query("UPDATE media_items SET isBookmarked = :isBookmarked WHERE mediaId = :mediaId")
    suspend fun setBookmarked(mediaId: Long, isBookmarked: Boolean)

    @Query("UPDATE media_items SET customCaption = :caption WHERE mediaId = :mediaId")
    suspend fun updateCustomCaption(mediaId: Long, caption: String?)

    @Query("UPDATE media_items SET isProcessedForFaces = :isProcessed, hasFaces = :hasFaces WHERE mediaId = :mediaId")
    suspend fun updateMediaProcessed(mediaId: Long, isProcessed: Boolean, hasFaces: Boolean)

    @Query("UPDATE media_items SET isProcessedForFaces = 1, hasFaces = :hasFaces WHERE mediaId = :mediaId")
    suspend fun markMediaAsProcessed(mediaId: Long, hasFaces: Boolean)

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
        """
    )
    suspend fun syncAllMediaHasFaces()

    @Query("UPDATE media_items SET isProcessedForFaces = 0, hasFaces = 0")
    suspend fun resetAllMediaProcessed()

    @Query("UPDATE media_items SET isProcessedForFaces = 0, hasFaces = 0")
    suspend fun resetAllMediaProcessedFlags()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMediaPersonCrossRef(crossRef: MediaPersonCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMediaPersonCrossRefs(crossRefs: List<MediaPersonCrossRef>)

    @Query("SELECT * FROM media_person_cross_ref")
    suspend fun getAllCrossRefsSync(): List<MediaPersonCrossRef>

    @Query("DELETE FROM media_person_cross_ref WHERE personId = :personId")
    suspend fun deleteMediaPersonCrossRefsForPerson(personId: Long)

    @Query("DELETE FROM media_person_cross_ref")
    suspend fun clearAllCrossRefs()

    @Query("DELETE FROM media_person_cross_ref")
    suspend fun deleteAllCrossRefs()

    @Query("DELETE FROM media_person_cross_ref WHERE mediaId IN (SELECT mediaId FROM media_items WHERE isVideo = 1)")
    suspend fun deleteAllVideoPersonLinks()

    @Query("SELECT mediaId FROM media_items")
    suspend fun getAllMediaIds(): List<Long>

    @Query("SELECT mediaId FROM media_items")
    suspend fun getAllMediaIdsSync(): List<Long>

    @Query("SELECT EXISTS(SELECT 1 FROM media_items WHERE mediaId = :mediaId)")
    suspend fun existsMediaId(mediaId: Long): Boolean

    @Query("SELECT mediaId, uri FROM media_items")
    suspend fun getAllMediaIdsAndUris(): List<MediaIdAndUri>

    @Query("DELETE FROM media_items WHERE mediaId = :mediaId")
    suspend fun deleteMediaById(mediaId: Long): Int

    @Query("DELETE FROM media_items WHERE mediaId IN (:mediaIds)")
    suspend fun deleteMediaByIds(mediaIds: List<Long>): Int

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT DISTINCT m.* FROM media_items m
        INNER JOIN media_person_cross_ref ref1 ON m.mediaId = ref1.mediaId
        INNER JOIN media_person_cross_ref ref2 ON m.mediaId = ref2.mediaId
        WHERE ref1.personId = :personId
          AND ref2.personId = :otherPersonId
          AND m.isVideo = 0
        ORDER BY m.dateAddedSeconds DESC
        """
    )
    fun getSharedMediaBetweenPeopleFlow(personId: Long, otherPersonId: Long): Flow<List<MediaWithAuthors>>

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT DISTINCT m.* FROM media_items m
        INNER JOIN media_person_cross_ref ref1 ON m.mediaId = ref1.mediaId
        WHERE ref1.personId = :personId
          AND m.isVideo = 0
          AND NOT EXISTS (
              SELECT 1 FROM media_person_cross_ref ref2
              INNER JOIN people p2 ON p2.personId = ref2.personId
              WHERE ref2.mediaId = m.mediaId
                AND ref2.personId != :personId
                AND p2.isHiddenToOther = 0
          )
        ORDER BY m.dateAddedSeconds DESC
        """
    )
    fun getSoloMediaForPersonFlow(personId: Long): Flow<List<MediaWithAuthors>>

    @Transaction
    @Query("SELECT * FROM media_items WHERE isBookmarked = 1 AND isVideo = 0 ORDER BY dateAddedSeconds DESC")
    fun getAllBookmarkedPhotosWithAuthorsFlow(): Flow<List<MediaWithAuthors>>
}
