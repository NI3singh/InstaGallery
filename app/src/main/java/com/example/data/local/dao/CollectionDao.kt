// InstaGallery v1.0
package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import com.example.data.local.entity.CollectionEntity
import com.example.data.local.entity.CollectionMediaCrossRef
import com.example.data.local.entity.MediaEntity
import com.example.data.local.entity.MediaWithAuthors
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY createdAt ASC")
    fun getAllCollectionsFlow(): Flow<List<CollectionEntity>>

    @Query("SELECT COUNT(*) FROM collections")
    suspend fun getCollectionCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCollection(collection: CollectionEntity): Long

    @Query("DELETE FROM collections WHERE collectionId = :collectionId")
    suspend fun deleteCollection(collectionId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addMediaToCollection(crossRef: CollectionMediaCrossRef)

    @Query("DELETE FROM collection_media_cross_ref WHERE collectionId = :collectionId AND mediaId = :mediaId")
    suspend fun removeMediaFromCollection(collectionId: Long, mediaId: Long)

    @Query("DELETE FROM collection_media_cross_ref WHERE mediaId = :mediaId")
    suspend fun removeMediaFromAllCollections(mediaId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM collection_media_cross_ref WHERE mediaId = :mediaId)")
    suspend fun isMediaInAnyCollection(mediaId: Long): Boolean

    @Query("SELECT collectionId FROM collection_media_cross_ref WHERE mediaId = :mediaId")
    fun getCollectionIdsForMediaFlow(mediaId: Long): Flow<List<Long>>

    @Query("SELECT * FROM collection_media_cross_ref ORDER BY addedAt DESC")
    fun getAllCollectionCrossRefsFlow(): Flow<List<CollectionMediaCrossRef>>

    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT m.* FROM media_items m
        INNER JOIN collection_media_cross_ref ref ON m.mediaId = ref.mediaId
        WHERE ref.collectionId = :collectionId AND m.isVideo = 0 AND m.isBookmarked = 1
        ORDER BY ref.addedAt DESC
        """
    )
    fun getMediaForCollectionFlow(collectionId: Long): Flow<List<MediaEntity>>

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT m.* FROM media_items m
        INNER JOIN collection_media_cross_ref ref ON m.mediaId = ref.mediaId
        WHERE ref.collectionId = :collectionId AND m.isVideo = 0 AND m.isBookmarked = 1
        ORDER BY ref.addedAt DESC
        """
    )
    fun getMediaWithAuthorsForCollectionFlow(collectionId: Long): Flow<List<MediaWithAuthors>>
}
