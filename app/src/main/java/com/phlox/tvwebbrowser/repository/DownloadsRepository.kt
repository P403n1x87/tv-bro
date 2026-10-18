package com.phlox.tvwebbrowser.repository

import com.phlox.tvwebbrowser.model.Download
import com.phlox.tvwebbrowser.model.dao.DownloadDao

/**
 * The single gateway to the download log. See [FavoritesRepository] for the rationale.
 *
 * Every method suspends, so Room runs the work off the main thread. The two places that write from
 * outside a coroutine — `DownloadTask.run()` and the download service's task listener, both on the
 * worker pool — bridge the call with `runBlocking`.
 */
class DownloadsRepository(
    private val dao: DownloadDao,
) {
    suspend fun all(): List<Download> = dao.getAll()

    suspend fun add(download: Download): Long = dao.insert(download)

    suspend fun update(vararg downloads: Download) = dao.update(*downloads)

    suspend fun delete(download: Download) = dao.delete(download)

    suspend fun deleteAll() = dao.deleteAll()

    suspend fun page(offset: Long): List<Download> = dao.allByLimitOffset(offset)
}
