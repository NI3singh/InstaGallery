// InstaGallery v1.0
package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.FaceEntity
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

        val emb1 = FloatArray(192) { i -> if (i % 2 == 0) 1f else -1f }.also { norm(it) }
        val emb2 = FloatArray(192) { i -> if ((i / 3) % 2 == 0) 1f else -1f }.also { norm(it) }

        val p1 = personDao.insertPerson(
            PersonEntity(
                name = "Person 1",
                avatarCropPath = "/tmp/p1.jpg",
                centroidEmbeddingBlob = clusterManager.floatArrayToBlob(emb1),
                faceCount = 3,
                isNamedByUser = false,
                bestQualityScore = 0.9f
            )
        )
        val p2 = personDao.insertPerson(
            PersonEntity(
                name = "Person 2",
                avatarCropPath = "/tmp/p2.jpg",
                centroidEmbeddingBlob = clusterManager.floatArrayToBlob(emb2),
                faceCount = 1,
                isNamedByUser = false,
                bestQualityScore = 0.7f
            )
        )

        mediaDao.insertMediaPersonCrossRef(MediaPersonCrossRef(mediaId = 1L, personId = p1))
        mediaDao.insertMediaPersonCrossRef(MediaPersonCrossRef(mediaId = 2L, personId = p2))

        personDao.markPersonNamedByUser(p1, "Papa")
        personDao.hidePersonToOther(p2)

        val remainingPeople = personDao.getAllPeopleSync()
        assertEquals(1, remainingPeople.size)
        assertEquals("Papa", remainingPeople[0].name)
        assertTrue(remainingPeople[0].isNamedByUser)
    }

    @Test
    fun `pass 2 anchor sweep assigns unassigned side-angle faces to named anchor profile`() = runBlocking {
        val mediaDao = db.mediaDao()
        val personDao = db.personDao()

        mediaDao.insertMediaList(
            listOf(
                MediaEntity(
                    mediaId = 10L,
                    uri = "content://media/10",
                    dateAddedSeconds = 1000L,
                    mimeType = "image/jpeg",
                    isVideo = false,
                    width = 1080,
                    height = 1350,
                    durationMs = 0L,
                    displayName = "papa_frontal.jpg"
                ),
                MediaEntity(
                    mediaId = 11L,
                    uri = "content://media/11",
                    dateAddedSeconds = 2000L,
                    mimeType = "image/jpeg",
                    isVideo = false,
                    width = 1080,
                    height = 1350,
                    durationMs = 0L,
                    displayName = "papa_angled.jpg"
                )
            )
        )

        val anchorVec = FloatArray(192) { i -> (i % 7 - 3).toFloat() }.also { norm(it) }
        val angledVec = FloatArray(192) { i -> (i % 7 - 3).toFloat() + if (i < 20) 0.4f else 0f }.also { norm(it) }

        val papaId = personDao.insertPerson(
            PersonEntity(
                name = "Papa",
                avatarCropPath = "/tmp/papa.jpg",
                centroidEmbeddingBlob = clusterManager.floatArrayToBlob(anchorVec),
                faceCount = 1,
                isNamedByUser = true,
                bestQualityScore = 0.88f
            )
        )
        personDao.insertFace(
            FaceEntity(
                mediaId = 10L,
                personId = papaId,
                cropPath = "/tmp/papa.jpg",
                embeddingBlob = clusterManager.floatArrayToBlob(anchorVec),
                qualityScore = 0.88f,
                isAnchorExemplar = true,
                left = 10,
                top = 10,
                right = 110,
                bottom = 110
            )
        )
        mediaDao.insertMediaPersonCrossRef(MediaPersonCrossRef(mediaId = 10L, personId = papaId))

        // Insert an unassigned side-angle face (personId = null) on mediaId = 11L
        personDao.insertFace(
            FaceEntity(
                mediaId = 11L,
                personId = null,
                cropPath = "/tmp/papa_side.jpg",
                embeddingBlob = clusterManager.floatArrayToBlob(angledVec),
                qualityScore = 0.54f,
                headEulerY = 34f,
                isAnchorExemplar = false,
                left = 20,
                top = 20,
                right = 100,
                bottom = 100
            )
        )

        clusterManager.runPass2ConsolidationAndAnchorSweep()

        val unassignedAfterSweep = personDao.getUnassignedFacesSync()
        assertEquals(0, unassignedAfterSweep.size)

        val papaMediaIds = personDao.getMediaIdsForPerson(papaId)
        assertTrue(papaMediaIds.containsAll(listOf(10L, 11L)))
    }

    private fun norm(vec: FloatArray) {
        var s = 0f
        for (v in vec) s += v * v
        val n = sqrt(s).coerceAtLeast(1e-6f)
        for (i in vec.indices) vec[i] /= n
    }
}
