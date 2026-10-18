package com.phlox.tvwebbrowser.repository

import com.phlox.tvwebbrowser.model.HistoryItem
import com.phlox.tvwebbrowser.model.dao.HistoryDao

/**
 * The single gateway to browsing history. See [FavoritesRepository] for the rationale.
 */
class HistoryRepository(
    private val dao: HistoryDao,
) {
    suspend fun count(): Int = dao.count()

    suspend fun latest(limit: Int = 1): List<HistoryItem> = dao.last(limit)

    suspend fun page(offset: Long): List<HistoryItem> = dao.allByLimitOffset(offset)

    /** Partial matches on the title or the url, newest first. */
    suspend fun search(query: String): List<HistoryItem> = dao.search("%$query%", "%$query%")

    suspend fun all(): List<HistoryItem> = dao.getAll()

    suspend fun add(item: HistoryItem): Long = dao.insert(item)

    suspend fun delete(vararg items: HistoryItem) = dao.delete(*items)

    suspend fun deleteOlderThan(time: Long) = dao.deleteWhereTimeLessThan(time)

    /**
     * Removes any existing entry for [url] so a new visit replaces it instead of stacking a second
     * row (history uniqueness: the same site appears once, with the most recent timestamp).
     */
    suspend fun deleteByUrl(url: String) = dao.deleteByUrl(url)

    suspend fun updateTitle(
        id: Long,
        title: String,
    ) = dao.updateTitle(id, title)
}
