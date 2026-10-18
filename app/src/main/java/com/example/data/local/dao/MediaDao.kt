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

@Dao
interface MediaDao {

    @Transaction
    @Query("SELECT * FROM media_items ORDER BY dateAddedSeconds DESC")
    fun getAllMediaWithAuthors(): Flow<List<MediaWithAuthors>>

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

    @RewriteQueriesToDropUnusedColumns
    @Transaction
    @Query("""
        SELECT media_items.* FROM media_items 
        INNER JOIN media_person_cross_ref ON media_items.mediaId = media_person_cross_ref.mediaId 
        WHERE media_person_cross_ref.personId = :personId 
        ORDER BY dateAddedSeconds DESC
    """)
    fun getMediaForPerson(personId: Long): Flow<List<MediaWithAuthors>>

    @RewriteQueriesToDropUnusedColumns
    @Transaction
    @Query("""
        SELECT media_items.* FROM media_items 
        INNER JOIN media_person_cross_ref ON media_items.mediaId = media_person_cross_ref.mediaId 
        WHERE media_person_cross_ref.personId = :personId AND isVideo = 0 
        ORDER BY dateAddedSeconds DESC
    """)
    fun getPhotosForPerson(personId: Long): Flow<List<MediaWithAuthors>>

    @RewriteQueriesToDropUnusedColumns
    @Transaction
    @Query("""
        SELECT media_items.* FROM media_items 
        INNER JOIN media_person_cross_ref ON media_items.mediaId = media_person_cross_ref.mediaId 
        WHERE media_person_cross_ref.personId = :personId AND isVideo = 1 
        ORDER BY dateAddedSeconds DESC
    """)
    fun getVideosForPerson(personId: Long): Flow<List<MediaWithAuthors>>

    @Transaction
    @Query("""
        SELECT * FROM media_items 
        WHERE mediaId NOT IN (SELECT DISTINCT mediaId FROM media_person_cross_ref)
        ORDER BY dateAddedSeconds DESC
    """)
    fun getUnassociatedMedia(): Flow<List<MediaWithAuthors>>

    @Transaction
    @Query("""
        SELECT * FROM media_items 
        WHERE mediaId NOT IN (SELECT DISTINCT mediaId FROM media_person_cross_ref) AND isVideo = 0
        ORDER BY dateAddedSeconds DESC
    """)
    fun getUnassociatedPhotos(): Flow<List<MediaWithAuthors>>

    @Transaction
    @Query("""
        SELECT * FROM media_items 
        WHERE mediaId NOT IN (SELECT DISTINCT mediaId FROM media_person_cross_ref) AND isVideo = 1
        ORDER BY dateAddedSeconds DESC
    """)
    fun getUnassociatedVideos(): Flow<List<MediaWithAuthors>>

    @Query("""
        SELECT COUNT(*) FROM media_items 
        WHERE mediaId NOT IN (SELECT DISTINCT mediaId FROM media_person_cross_ref)
    """)
    fun getUnassociatedCount(): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM media_items 
        WHERE mediaId NOT IN (SELECT DISTINCT mediaId FROM media_person_cross_ref) AND isVideo = 0
    """)
    fun getUnassociatedPhotoCount(): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM media_items 
        WHERE mediaId NOT IN (SELECT DISTINCT mediaId FROM media_person_cross_ref) AND isVideo = 1
    """)
    fun getUnassociatedVideoCount(): Flow<Int>

    @Query("SELECT * FROM media_items WHERE isProcessedForFaces = 0 ORDER BY isVideo ASC, dateAddedSeconds DESC LIMIT :limit")
    suspend fun getUnindexedMedia(limit: Int = 25): List<MediaEntity>

    @Query("SELECT COUNT(*) FROM media_items WHERE isProcessedForFaces = 0")
    fun getUnindexedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM media_items")
    fun getTotalMediaCount(): Flow<Int>

    @Transaction
    @Query("SELECT * FROM media_items WHERE mediaId = :mediaId")
    fun getMediaById(mediaId: Long): Flow<MediaWithAuthors?>

    @Query("SELECT mediaId FROM media_items")
    suspend fun getAllMediaIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMediaList(mediaList: List<MediaEntity>)

    @Query("UPDATE media_items SET isProcessedForFaces = :isProcessed, hasFaces = :hasFaces WHERE mediaId = :mediaId")
    suspend fun updateMediaProcessed(mediaId: Long, isProcessed: Boolean, hasFaces: Boolean)

    @Query("UPDATE media_items SET isFavorite = :isFavorite WHERE mediaId = :mediaId")
    suspend fun setFavorite(mediaId: Long, isFavorite: Boolean)

    @Query("UPDATE media_items SET isBookmarked = :isBookmarked WHERE mediaId = :mediaId")
    suspend fun setBookmarked(mediaId: Long, isBookmarked: Boolean)

    @Query("DELETE FROM media_items WHERE mediaId NOT IN (:activeIds)")
    suspend fun deleteMissingMedia(activeIds: List<Long>)

    @Query("DELETE FROM media_person_cross_ref WHERE mediaId = :mediaId AND personId = :personId")
    suspend fun removeMediaFromPerson(mediaId: Long, personId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMediaPersonCrossRef(crossRef: MediaPersonCrossRef)
}
