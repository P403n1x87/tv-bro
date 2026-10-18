package com.phlox.tvwebbrowser.domain

import com.phlox.tvwebbrowser.model.Download
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [DownloadStoragePolicy], the byte accounting behind the downloads header.
 */
class DownloadStoragePolicyTest {
    private fun download(
        size: Long,
        bytesReceived: Long,
    ): Download {
        val download = Download()
        download.size = size
        download.bytesReceived = bytesReceived
        return download
    }

    @Test
    fun aFinishedDownloadCountsItsFullSize() {
        assertEquals(500, DownloadStoragePolicy.usedBytes(listOf(download(size = 500, bytesReceived = 500))))
    }

    @Test
    fun aPartialDownloadCountsOnlyWhatArrived() {
        assertEquals(200, DownloadStoragePolicy.usedBytes(listOf(download(size = 500, bytesReceived = 200))))
    }

    @Test
    fun aBrokenOrCancelledDownloadCountsThePartialFile() {
        val broken = download(size = Download.BROKEN_MARK, bytesReceived = 120)
        val cancelled = download(size = Download.CANCELLED_MARK, bytesReceived = 30)
        assertEquals(150, DownloadStoragePolicy.usedBytes(listOf(broken, cancelled)))
    }

    @Test
    fun anUnknownSizeCountsTheReceivedBytes() {
        assertEquals(77, DownloadStoragePolicy.usedBytes(listOf(download(size = 0, bytesReceived = 77))))
    }

    @Test
    fun theTotalSumsEveryRow() {
        val rows =
            listOf(
                download(size = 500, bytesReceived = 500),
                download(size = 500, bytesReceived = 200),
                download(size = Download.BROKEN_MARK, bytesReceived = 120),
            )
        assertEquals(820, DownloadStoragePolicy.usedBytes(rows))
    }

    @Test
    fun aFileFitsOnlyWhenThereIsRoom() {
        assertTrue(DownloadStoragePolicy.canFit(freeBytes = 1000, neededBytes = 1000))
        assertTrue(DownloadStoragePolicy.canFit(freeBytes = 1000, neededBytes = 1))
        assertFalse(DownloadStoragePolicy.canFit(freeBytes = 1000, neededBytes = 1001))
        assertFalse(DownloadStoragePolicy.canFit(freeBytes = 0, neededBytes = 0))
    }
}
