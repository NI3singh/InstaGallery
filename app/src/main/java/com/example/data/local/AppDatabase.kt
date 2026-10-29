// InstaGallery v1.0
package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.CollectionDao
import com.example.data.local.dao.MediaDao
import com.example.data.local.dao.PersonDao
import com.example.data.local.entity.CollectionEntity
import com.example.data.local.entity.CollectionMediaCrossRef
import com.example.data.local.entity.FaceEntity
import com.example.data.local.entity.MediaEntity
import com.example.data.local.entity.MediaPersonCrossRef
import com.example.data.local.entity.PersonEntity

@Database(
    entities = [
        MediaEntity::class,
        PersonEntity::class,
        FaceEntity::class,
        MediaPersonCrossRef::class,
        CollectionEntity::class,
        CollectionMediaCrossRef::class
    ],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun mediaDao(): MediaDao
    abstract fun personDao(): PersonDao
    abstract fun collectionDao(): CollectionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private fun addDecisionStateColumnIfMissing(database: SupportSQLiteDatabase) {
            var hasDecisionState = false
            database.query("PRAGMA table_info(people)").use { cursor ->
                val nameCol = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameCol >= 0 && cursor.getString(nameCol) == "decisionState") {
                        hasDecisionState = true
                        break
                    }
                }
            }
            if (!hasDecisionState) {
                database.execSQL("ALTER TABLE people ADD COLUMN decisionState TEXT NOT NULL DEFAULT 'UNDECIDED'")
            }
        }

        private fun addCustomCaptionColumnIfMissing(database: SupportSQLiteDatabase) {
            var hasCustomCaption = false
            database.query("PRAGMA table_info(media_items)").use { cursor ->
                val nameCol = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameCol >= 0 && cursor.getString(nameCol) == "customCaption") {
                        hasCustomCaption = true
                        break
                    }
                }
            }
            if (!hasCustomCaption) {
                database.execSQL("ALTER TABLE media_items ADD COLUMN customCaption TEXT DEFAULT NULL")
            }
        }

        private fun addCollectionsTablesAndBookmarkColumnIfMissing(database: SupportSQLiteDatabase) {
            var hasIsBookmarked = false
            database.query("PRAGMA table_info(media_items)").use { cursor ->
                val nameCol = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameCol >= 0 && cursor.getString(nameCol) == "isBookmarked") {
                        hasIsBookmarked = true
                        break
                    }
                }
            }
            if (!hasIsBookmarked) {
                database.execSQL("ALTER TABLE media_items ADD COLUMN isBookmarked INTEGER NOT NULL DEFAULT 0")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_items_isBookmarked` ON `media_items` (`isBookmarked`)")
            }

            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `collections` (
                    `collectionId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `collection_media_cross_ref` (
                    `collectionId` INTEGER NOT NULL,
                    `mediaId` INTEGER NOT NULL,
                    `addedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`collectionId`, `mediaId`),
                    FOREIGN KEY(`collectionId`) REFERENCES `collections`(`collectionId`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`mediaId`) REFERENCES `media_items`(`mediaId`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_collection_media_cross_ref_collectionId` ON `collection_media_cross_ref` (`collectionId`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_collection_media_cross_ref_mediaId` ON `collection_media_cross_ref` (`mediaId`)")
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addDecisionStateColumnIfMissing(db)
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addDecisionStateColumnIfMissing(db)
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addCustomCaptionColumnIfMissing(db)
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addCollectionsTablesAndBookmarkColumnIfMissing(db)
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "instagallery.db"
                )
                    .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
