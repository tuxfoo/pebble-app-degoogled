package com.anopticlabs.gravel.health

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import io.rebble.libpebblecommon.database.Database
import io.rebble.libpebblecommon.database.dao.insertOverlayDataWithDeduplication
import io.rebble.libpebblecommon.database.entity.OverlayDataEntity
import io.rebble.libpebblecommon.health.OverlayType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Edits and deletions made on the phone must survive the watch re-sending the original
 * overlay (insertOverlayDataWithDeduplication is the watch's insert path).
 */
@SmallTest
class ActivityEditTest {
    private lateinit var db: Database

    private val walk = OverlayDataEntity(
        startTime = 1_794_643_140, duration = 1800, type = OverlayType.Walk.value,
        steps = 2500, restingKiloCalories = 20, activeKiloCalories = 90, distanceCm = 180_000,
        offsetUTC = 46_800,
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<Database>(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ).setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun activities() = db.healthDao().getAllOverlayEntries()

    @Test
    fun edit_replacesActivity_andIgnoresWatchResendOfOriginal() = runBlocking {
        val dao = db.healthDao()
        dao.insertOverlayDataWithDeduplication(listOf(walk))
        val edited = walk.copy(type = OverlayType.Run.value, startTime = walk.startTime + 120, duration = 1680)

        dao.editOverlay(walk, edited)
        assertEquals(listOf(edited), activities())

        // Watch re-sends the original, even a longer copy of it.
        dao.insertOverlayDataWithDeduplication(listOf(walk, walk.copy(duration = 2400)))
        assertEquals(listOf(edited), activities())
    }

    @Test
    fun delete_removesActivity_andIgnoresWatchResend() = runBlocking {
        val dao = db.healthDao()
        dao.insertOverlayDataWithDeduplication(listOf(walk))

        dao.deleteOverlayPermanently(walk)
        assertEquals(emptyList(), activities())

        dao.insertOverlayDataWithDeduplication(listOf(walk))
        assertEquals(emptyList(), activities())
    }

    @Test
    fun otherActivities_stillInsert() = runBlocking {
        val dao = db.healthDao()
        dao.insertOverlayDataWithDeduplication(listOf(walk))
        dao.deleteOverlayPermanently(walk)

        val run = walk.copy(type = OverlayType.Run.value, startTime = walk.startTime + 60)
        dao.insertOverlayDataWithDeduplication(listOf(run))
        assertEquals(listOf(run), activities())
    }

    @Test
    fun housekeeping_expiresOldSuppressions() = runBlocking {
        val dao = db.healthDao()
        dao.deleteOverlayPermanently(walk)
        assertEquals(1, dao.deleteExpiredSuppressedOverlays(walk.startTime + 1))
        dao.insertOverlayDataWithDeduplication(listOf(walk))
        assertEquals(listOf(walk), activities())
    }
}
