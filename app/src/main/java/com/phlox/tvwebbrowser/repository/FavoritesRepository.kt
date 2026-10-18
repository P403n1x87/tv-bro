package com.phlox.tvwebbrowser.repository

import com.phlox.tvwebbrowser.model.FavoriteItem
import com.phlox.tvwebbrowser.model.dao.FavoritesDao

/**
 * The single gateway to bookmark storage.
 *
 * Introduced as part of the MVVM layering pass: the UI layer and the active models must not talk to
 * [FavoritesDao] (or to Room at all) directly, so persistence can change without rippling through
 * the app, and so the same operations can be exercised from a test with a fake DAO.
 *
 * Every method is `suspend` because the DAO is; callers run them from a coroutine scope.
 */
class FavoritesRepository(
    private val dao: FavoritesDao,
) {
    /** All top-level bookmarks (the set the bookmarks panel and home page render). */
    suspend fun allBookmarks(): List<FavoriteItem> = dao.getAllBookmarks()

    suspend fun all(homePageBookmarks: Boolean = false): List<FavoriteItem> = dao.getAll(homePageBookmarks)

    suspend fun homePageBookmarks(): List<FavoriteItem> = dao.getHomePageBookmarks()

    suspend fun byId(id: Long): FavoriteItem? = dao.getById(id)

    suspend fun byUrl(url: String): FavoriteItem? = dao.getByUrl(url)

    suspend fun isBookmarked(url: String): Boolean = dao.getByUrl(url) != null

    suspend fun add(item: FavoriteItem): Long = dao.insert(item)

    suspend fun update(item: FavoriteItem) = dao.update(item)

    suspend fun delete(item: FavoriteItem) = dao.delete(item)

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun markAsUseful(favoriteId: Long) = dao.markAsUseful(favoriteId)
}
