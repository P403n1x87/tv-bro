package com.phlox.tvwebbrowser.repository

import android.app.Application
import androidx.room.Room
import com.phlox.tvwebbrowser.model.Download
import com.phlox.tvwebbrowser.model.HostConfig
import com.phlox.tvwebbrowser.singleton.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Data-layer tests for the two smaller repositories: per-host configuration (one row per host, even
 * across repeated upserts, because the host-name index is unique) and the download log.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], application = Application::class)
class HostsAndDownloadsRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var hosts: HostsRepository
    private lateinit var downloads: DownloadsRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).build()
        hosts = HostsRepository(db.hostsDao())
        downloads = DownloadsRepository(db.downloadDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun hostRowCount(): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM hosts").use {
            it.moveToFirst()
            it.getInt(0)
        }

    private fun download(
        filename: String,
        time: Long,
        bytes: Long = 0,
    ) = Download().apply {
        this.filename = filename
        this.filepath = "/downloads/$filename"
        this.url = "https://example.com/$filename"
        this.time = time
        this.size = 100
        this.bytesReceived = bytes
    }

    @Test
    fun unknownHostHasNoConfig() =
        runBlocking {
            assertNull(hosts.findByHostName("example.com"))
        }

    @Test
    fun upsertKeepsASingleRowPerHost() =
        runBlocking {
            hosts.upsert(HostConfig("example.com"))
            hosts.upsert(HostConfig("other.example").apply { popupBlockLevel = HostConfig.POPUP_BLOCK_NONE })
            hosts.upsert(HostConfig("example.com").apply { popupBlockLevel = HostConfig.POPUP_BLOCK_DIALOGS })

            assertEquals(2, hostRowCount())
            assertEquals(
                HostConfig.POPUP_BLOCK_DIALOGS,
                hosts.findByHostName("example.com")?.popupBlockLevel,
            )
        }

    @Test
    fun hostConfigCanBeUpdated() =
        runBlocking {
            val id = hosts.upsert(HostConfig("example.com"))
            val config = hosts.findByHostName("example.com")!!

            config.popupBlockLevel = HostConfig.POPUP_BLOCK_NEW_TABS_EVEN_BY_USER_GESTURE
            hosts.update(config)

            assertEquals(
                HostConfig.POPUP_BLOCK_NEW_TABS_EVEN_BY_USER_GESTURE,
                hosts.findByHostName("example.com")?.popupBlockLevel,
            )
            assertEquals(id, hosts.findByHostName("example.com")?.id)
        }

    @Test
    fun downloadLogIsNewestFirstAndPaged() =
        runBlocking {
            val id = downloads.add(download("one.apk", 1000))
            downloads.add(download("two.apk", 2000))
            downloads.add(download("three.apk", 3000))
            assertEquals(3, downloads.all().size)

            assertEquals(
                listOf("three.apk", "two.apk", "one.apk"),
                downloads.page(0).map { it.filename },
            )
            assertEquals(listOf("two.apk", "one.apk"), downloads.page(1).map { it.filename })

            val first = downloads.all().first { it.id == id }
            first.bytesReceived = 100
            downloads.update(first)
            assertEquals(100, downloads.all().first { it.id == id }.bytesReceived)

            downloads.delete(first)
            assertEquals(2, downloads.all().size)
        }
}
