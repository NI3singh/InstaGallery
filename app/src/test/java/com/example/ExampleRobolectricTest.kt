// InstaGallery v1.0
package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.MediaEntity
import com.example.data.local.entity.MediaPersonCrossRef
import com.example.data.local.entity.PersonEntity
import com.example.ml.FaceClusterManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var clusterManager: FaceClusterManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        clusterManager = FaceClusterManager(context, db.personDao(), db.mediaDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `read string from context`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("InstaGallery", appName)
    }

    @Test
    fun `profile setup renames known people and moves unknown people to Other`() = runBlocking {
        val mediaDao = db.mediaDao()
        val personDao = db.personDao()

        mediaDao.insertMediaList(
            listOf(
                MediaEntity(
                    mediaId = 1L,
                    uri = "content://media/1",
                    dateAddedSeconds = 1000L,
                    mimeType = "image/jpeg",
                    isVideo = false,
                    width = 1080,
                    height = 1350,
                    durationMs = 0L,
                    displayName = "img1.jpg"
                ),
                MediaEntity(
                    mediaId = 2L,
                    uri = "content://media/2",
                    dateAddedSeconds = 2000L,
                    mimeType = "image/jpeg",
                    isVideo = false,
                    width = 1080,
                    height = 1350,
                    durationMs = 0L,
                    displayName = "img2.jpg"
                )
            )
        )

        val emb1 = FloatArray(256) { i -> if (i % 2 == 0) 1f else -1f }.also { norm(it) }
        val emb2 = FloatArray(256) { i -> if ((i / 3) % 2 == 0) 1f else -1f }.also { norm(it) }

        val p1 = personDao.insertPerson(
            PersonEntity(
                name = "Person1",
                avatarCropPath = "/tmp/p1.jpg",
                centroidEmbeddingBlob = clusterManager.floatArrayToBlob(emb1),
                faceCount = 3,
                isNamedByUser = false,
                bestFaceQuality = 0.9f
            )
        )
        val p2 = personDao.insertPerson(
            PersonEntity(
                name = "Person2",
                avatarCropPath = "/tmp/p2.jpg",
                centroidEmbeddingBlob = clusterManager.floatArrayToBlob(emb2),
                faceCount = 1,
                isNamedByUser = false,
                bestFaceQuality = 0.7f
            )
        )

        mediaDao.insertMediaPersonCrossRef(MediaPersonCrossRef(mediaId = 1L, personId = p1))
        mediaDao.insertMediaPersonCrossRef(MediaPersonCrossRef(mediaId = 2L, personId = p2))

        // Verify Person1 can be named and Person2 moved to @Other
        val p1Entity = personDao.getPersonByIdSync(p1)!!
        personDao.updatePerson(p1Entity.copy(name = "Alex", isNamedByUser = true))
        personDao.movePersonToOther(p2)

        val remainingPeople = personDao.getAllPeopleSync()
        assertEquals(1, remainingPeople.size)
        assertEquals("Alex", remainingPeople[0].name)
        assertTrue(remainingPeople[0].isNamedByUser)
    }

    private fun norm(vec: FloatArray) {
        var s = 0f
        for (v in vec) s += v * v
        val n = sqrt(s).coerceAtLeast(1e-6f)
        for (i in vec.indices) vec[i] /= n
    }
}
