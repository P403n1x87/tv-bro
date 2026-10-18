package com.phlox.tvwebbrowser.activity.downloads

import android.os.Looper
import androidx.room.Room
import com.phlox.tvwebbrowser.domain.DownloadStoragePolicy
import com.phlox.tvwebbrowser.model.Download
import com.phlox.tvwebbrowser.repository.DownloadsRepository
import com.phlox.tvwebbrowser.singleton.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.io.File
import org.robolectric.annotation.Config as RobolectricConfig

/**
 * Tests the storage-info plumbing: the injected directory, formatter and repository reach the
 * observables. A same-thread IO dispatcher keeps [DownloadStorageModel.refresh] deterministic on
 * the test's main thread.
 */
@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [26], application = android.app.Application::class)
class DownloadStorageModelTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: DownloadsRepository

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    RuntimeEnvironment.getApplication(),
                    AppDatabase::class.java,
                ).allowMainThreadQueries()
                .setQueryExecutor { it.run() }
                .setTransactionExecutor { it.run() }
                .build()
        repository = DownloadsRepository(db.downloadDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun model(dir: File) =
        DownloadStorageModel(
            downloads = repository,
            downloadsDir = { dir },
            formatBytes = { "$it bytes" },
            ioDispatcher = Dispatchers.Unconfined,
        )

    private fun drainMainLooper() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun refreshPublishesTheLocationAndTheFormattedFreeSpace() {
        val dir = File("/tmp/tvbro-downloads-storage-test")
        val model = model(dir)

        model.refresh()
        drainMainLooper()

        assertEquals(dir.absolutePath, model.location.value)
        assertEquals("${dir.freeSpace} bytes", model.freeSpace.value)
    }

    @Test
    fun refreshPublishesTheFormattedUsedSpaceFromTheLog() {
        val dir = File("/tmp/tvbro-downloads-storage-test")
        val model = model(dir)
        val finished =
            Download().apply {
                size = 500
                bytesReceived = 500
            }
        val partial =
            Download().apply {
                size = 500
                bytesReceived = 200
            }
        runBlocking {
            repository.add(finished)
            repository.add(partial)
        }

        model.refresh()
        shadowOf(Looper.getMainLooper()).idle()

        val expected = DownloadStoragePolicy.usedBytes(listOf(finished, partial))
        assertEquals("$expected bytes", model.usedSpace.value)
    }
}
