package com.phlox.tvwebbrowser.activity.downloads

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.room.Room
import com.phlox.tvwebbrowser.AppContext
import com.phlox.tvwebbrowser.Config
import com.phlox.tvwebbrowser.model.Download
import com.phlox.tvwebbrowser.repository.DownloadsRepository
import com.phlox.tvwebbrowser.singleton.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config as RobolectricConfig
import java.time.Duration
import java.util.concurrent.Executor

/**
 * Tests for [DownloadsHistoryModel]'s paging over an in-memory database: the view appends every
 * window it is handed, so the model has to keep track of how much it already gave out.
 */
@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [26], application = Application::class)
class DownloadsHistoryModelTest {

    private lateinit var db: AppDatabase
    private lateinit var downloads: DownloadsRepository
    private lateinit var model: DownloadsHistoryModel

    private val directExecutor = Executor { it.run() }

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        AppContext.init(app, Config(app.getSharedPreferences("downloads_test", Context.MODE_PRIVATE)))
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
        downloads = DownloadsRepository(db.downloadDao())
        model = DownloadsHistoryModel(downloads)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun advance(seconds: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds))
    }

    private fun insert(fileName: String, time: Long) = runBlocking {
        downloads.add(Download().apply {
            this.filename = fileName
            this.filepath = "/tmp/$fileName"
            this.url = "https://example.com/$fileName"
            this.time = time
        })
    }

    @Test
    fun theFirstLoadPublishesTheStoredDownloads() {
        insert("a.bin", time = 1)
        insert("b.bin", time = 2)

        model.loadNextItems()
        advance(1)

        assertEquals(listOf("b.bin", "a.bin"), model.lastLoadedItems.value.map { it.filename })
        assertEquals(listOf("b.bin", "a.bin"), model.allItems.map { it.filename })
    }

    @Test
    fun theNextLoadContinuesAfterWhatWasAlreadyGivenOut() {
        insert("a.bin", time = 2)
        insert("b.bin", time = 3)
        model.loadNextItems()
        advance(1)

        // A download that finished afterwards but sorts last: the second window starts at offset 2.
        insert("c.bin", time = 1)
        model.loadNextItems()
        advance(1)

        assertEquals(listOf("c.bin"), model.lastLoadedItems.value.map { it.filename })
        assertEquals(listOf("b.bin", "a.bin", "c.bin"), model.allItems.map { it.filename })
    }

    @Test
    fun loadingWhenNothingIsLeftPublishesNothing() {
        insert("a.bin", time = 1)

        model.loadNextItems()
        advance(1)
        model.loadNextItems()
        advance(1)

        assertEquals(listOf("a.bin"), model.allItems.map { it.filename })
    }
}
