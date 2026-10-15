package com.phlox.tvwebbrowser.repository

import com.phlox.tvwebbrowser.model.Download
import com.phlox.tvwebbrowser.model.dao.DownloadDao

/**
 * The single gateway to the download log. See [FavoritesRepository] for the rationale.
 *
 * [add] and [update] are intentionally *not* `suspend`: they are called from `DownloadTask.run()`
 * on the download worker pool, outside any coroutine. Room no longer allows main-thread queries, so
 * calling them from the UI thread now throws instead of silently blocking it.
 */
class DownloadsRepository(private val dao: DownloadDao) {

    suspend fun all(): List<Download> = dao.getAll()

    fun add(download: Download): Long = dao.insert(download)

    fun update(vararg downloads: Download) = dao.update(*downloads)

    suspend fun delete(download: Download) = dao.delete(download)

    suspend fun page(offset: Long): List<Download> = dao.allByLimitOffset(offset)
}
