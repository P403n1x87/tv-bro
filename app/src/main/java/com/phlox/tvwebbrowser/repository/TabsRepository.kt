package com.phlox.tvwebbrowser.repository

import com.phlox.tvwebbrowser.model.WebTabState
import com.phlox.tvwebbrowser.model.dao.TabsDao

/**
 * The single gateway to persisted tab state. See [FavoritesRepository] for the rationale.
 */
class TabsRepository(
    private val dao: TabsDao,
) {
    suspend fun all(incognito: Boolean = false): List<WebTabState> = dao.getAll(incognito)

    suspend fun insert(tab: WebTabState): Long = dao.insert(tab)

    suspend fun update(tab: WebTabState) = dao.update(tab)

    suspend fun delete(tab: WebTabState) = dao.delete(tab)

    suspend fun deleteAll(incognito: Boolean = false) = dao.deleteAll(incognito)

    suspend fun unselectAll(incognito: Boolean = false) = dao.unselectAll(incognito)

    suspend fun updatePositions(tabs: List<WebTabState>) = dao.updatePositions(tabs)
}
