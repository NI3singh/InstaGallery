// InstaGallery v1.0
package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.MediaDao
import com.example.data.local.dao.PersonDao
import com.example.data.local.entity.FaceEntity
import com.example.data.local.entity.MediaEntity
import com.example.data.local.entity.MediaPersonCrossRef
import com.example.data.local.entity.PersonEntity

@Database(
    entities = [
        MediaEntity::class,
        PersonEntity::class,
        FaceEntity::class,
        MediaPersonCrossRef::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun mediaDao(): MediaDao
    abstract fun personDao(): PersonDao

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

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "instagallery.db"
                )
                    .addMigrations(MIGRATION_6_7, MIGRATION_7_8)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
