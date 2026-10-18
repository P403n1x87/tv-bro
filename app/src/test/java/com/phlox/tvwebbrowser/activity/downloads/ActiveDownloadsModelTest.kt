package com.phlox.tvwebbrowser.activity.downloads

import android.app.Application
import android.net.Uri
import androidx.room.Room
import com.phlox.tvwebbrowser.model.Download
import com.phlox.tvwebbrowser.repository.DownloadsRepository
import com.phlox.tvwebbrowser.service.downloads.DownloadTask
import com.phlox.tvwebbrowser.service.downloads.FileDownloadTask
import com.phlox.tvwebbrowser.singleton.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config as RobolectricConfig
import java.util.concurrent.Executor

/**
 * Tests for [ActiveDownloadsModel]'s listener fan-out, cancellation and completion, plus the
 * repository-backed deletion. Runs against an in-memory download log.
 */
@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [26], application = Application::class)
class ActiveDownloadsModelTest {

    private lateinit var db: AppDatabase
    private lateinit var downloads: DownloadsRepository
    private lateinit var model: ActiveDownloadsModel

    private val directExecutor = Executor { it.run() }

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
        downloads = DownloadsRepository(db.downloadDao())
        model = ActiveDownloadsModel(downloads, contentResolver = { app.contentResolver })
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun task(id: Long, url: String = "https://example.com/a.apk"): DownloadTask {
        val download = Download(url, "a.apk", "/tmp/nonexistent-$id.apk", Download.OperationAfterDownload.NOP,
            null, null, null, null, null)
        download.id = id
        return FileDownloadTask(download, null, NoOpCallback, RuntimeEnvironment.getApplication().contentResolver)
    }

    @Test
    fun progressIsBroadcastToListeners() {
        val listener = FakeListener()
        model.registerListener(listener)
        val task = task(1)
        model.activeDownloads.add(task)

        model.notifyListenersAboutDownloadProgress(task)

        assertEquals(listOf(task.downloadInfo), listener.updated)
    }

    @Test
    fun anUnregisteredListenerStopsReceiving() {
        val listener = FakeListener()
        model.registerListener(listener)
        model.unregisterListener(listener)

        model.notifyListenersAboutDownloadProgress(task(1))

        assertTrue(listener.updated.isEmpty())
    }

    @Test
    fun errorsAreBroadcastToListeners() {
        val listener = FakeListener()
        model.registerListener(listener)
        val task = task(1)

        model.notifyListenersAboutError(task, 404, "Not Found")

        assertEquals(1, listener.errors.size)
        assertEquals(task.downloadInfo, listener.errors[0].first)
        assertEquals(404, listener.errors[0].second)
    }

    @Test
    fun cancellingOnlyMarksTheMatchingTask() {
        val first = task(1)
        val second = task(2)
        model.activeDownloads.add(first)
        model.activeDownloads.add(second)

        model.cancelDownload(second.downloadInfo)

        assertFalse(first.downloadInfo.cancelled)
        assertTrue(second.downloadInfo.cancelled)
    }

    @Test
    fun theLastCompletionNotifiesTheListeners() {
        val listener = FakeListener()
        model.registerListener(listener)
        val task = task(1)
        model.activeDownloads.add(task)

        model.onDownloadEnded(task)

        assertTrue(model.activeDownloads.isEmpty())
        assertEquals(1, listener.allComplete)
    }

    @Test
    fun deletingAnItemRemovesItFromTheLog() = runBlocking {
        downloads.add(Download("https://example.com/a.apk", "a.apk", "/tmp/nonexistent.apk",
            Download.OperationAfterDownload.NOP, null, null, null, null, null))
        val stored = downloads.all().single()

        model.deleteItem(stored)

        assertTrue(downloads.all().isEmpty())
    }

    @Test
    @RobolectricConfig(sdk = [30])
    fun deletingAContentUriOnApi30GoesThroughTheInjectedResolverAndStillClearsTheLog() = runBlocking {
        val uri = Uri.parse("content://com.phlox.tvwebbrowser.test/Download/a.apk")
        downloads.add(Download("https://example.com/a.apk", "a.apk", uri.toString(),
            Download.OperationAfterDownload.NOP, null, null, null, null, null))
        val stored = downloads.all().single()

        model.deleteItem(stored)

        assertTrue(downloads.all().isEmpty())
    }

    @Test
    fun deletingASelectionRemovesOnlyThoseRows() = runBlocking {
        downloads.add(Download("https://example.com/a.apk", "a.apk", "/tmp/a.apk",
            Download.OperationAfterDownload.NOP, null, null, null, null, null))
        downloads.add(Download("https://example.com/b.apk", "b.apk", "/tmp/b.apk",
            Download.OperationAfterDownload.NOP, null, null, null, null, null))
        val stored = downloads.all()
        val selected = stored.first { it.filename == "a.apk" }

        model.deleteItems(listOf(selected))

        assertEquals(listOf("b.apk"), downloads.all().map { it.filename })
    }

    @Test
    fun deletingAllEmptiesTheLog() = runBlocking {
        downloads.add(Download("https://example.com/a.apk", "a.apk", "/tmp/a.apk",
            Download.OperationAfterDownload.NOP, null, null, null, null, null))
        downloads.add(Download("https://example.com/b.apk", "b.apk", "/tmp/b.apk",
            Download.OperationAfterDownload.NOP, null, null, null, null, null))

        model.deleteAll()

        assertTrue(downloads.all().isEmpty())
    }

    private class FakeListener : ActiveDownloadsModel.Listener {
        val updated = mutableListOf<Download>()
        val errors = mutableListOf<Triple<Download, Int, String>>()
        var allComplete = 0

        override fun onDownloadUpdated(downloadInfo: Download) {
            updated += downloadInfo
        }

        override fun onDownloadError(downloadInfo: Download, responseCode: Int, responseMessage: String) {
            errors += Triple(downloadInfo, responseCode, responseMessage)
        }

        override fun onAllDownloadsComplete() {
            allComplete++
        }
    }

    private object NoOpCallback : DownloadTask.Callback {
        override fun onProgress(task: DownloadTask) {}
        override fun onError(task: DownloadTask, responseCode: Int, responseMessage: String) {}
        override fun onDone(task: DownloadTask) {}
    }
}
