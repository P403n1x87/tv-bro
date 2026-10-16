package com.phlox.tvwebbrowser.repository

import android.app.Application
import androidx.room.Room
import com.phlox.tvwebbrowser.model.FavoriteItem
import com.phlox.tvwebbrowser.singleton.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Data-layer tests for [FavoritesRepository] over a real (in-memory) Room database, so the queries
 * the app depends on — and the `favorites_url_idx` lookup behind the toolbar star — are exercised
 * as SQL, not as mocks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], application = Application::class)
class FavoritesRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: FavoritesRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).build()
        repository = FavoritesRepository(db.favoritesDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun bookmark(title: String, url: String, homePage: Boolean = false, order: Int = 0) =
        FavoriteItem().apply {
            this.title = title
            this.url = url
            this.homePageBookmark = homePage
            this.order = order
        }

    @Test
    fun topLevelBookmarksAreListedWithoutFoldersOrChildren() = runBlocking {
        val bookmarkId = repository.add(bookmark("A", "https://a.example"))
        val folder = FavoriteItem().apply {
            title = "Folder"
            url = null
            parent = 0
        }
        val folderId = repository.add(folder)
        repository.add(bookmark("Child", "https://child.example").apply { parent = folderId })

        val bookmarks = repository.allBookmarks()

        assertEquals(listOf("https://a.example"), bookmarks.map { it.url })
        assertTrue(bookmarks.all { it.parent == 0L })
        assertEquals("A", repository.byId(bookmarkId)?.title)
    }

    @Test
    fun bookmarkIsFoundByUrl() = runBlocking {
        repository.add(bookmark("Example", "https://example.com/"))

        assertNotNull(repository.byUrl("https://example.com/"))
        assertTrue(repository.isBookmarked("https://example.com/"))
        assertNull(repository.byUrl("https://example.com/other"))
        assertFalse(repository.isBookmarked("https://example.com/other"))
    }

    @Test
    fun urlLookupFindsTheOnlyMatchRegardlessOfInsertOrder() = runBlocking {
        repository.add(bookmark("Example", "https://example.com/"))
        repository.add(bookmark("Other", "https://other.example/"))
        repository.add(bookmark("Example", "https://example.com/"))

        val found = repository.byUrl("https://example.com/")

        assertEquals("Example", found?.title)
    }

    @Test
    fun addUpdateAndDeletePersist() = runBlocking {
        val id = repository.add(bookmark("Before", "https://example.com/"))
        assertTrue(id > 0)

        val item = repository.byId(id)!!
        item.title = "After"
        repository.update(item)
        assertEquals("After", repository.byId(id)?.title)

        repository.delete(id)
        assertNull(repository.byId(id))
        assertEquals(0, repository.allBookmarks().size)
    }

    @Test
    fun homePageBookmarksAreASeparateSet() = runBlocking {
        repository.add(bookmark("Normal", "https://normal.example"))
        repository.add(bookmark("Home first", "https://first.example", homePage = true, order = 0))
        repository.add(bookmark("Home second", "https://second.example", homePage = true, order = 1))

        assertEquals(
            listOf("https://normal.example"),
            repository.all(homePageBookmarks = false).map { it.url }
        )
        // The grid is ordered by the author's own order, not by insertion.
        assertEquals(
            listOf("https://first.example", "https://second.example"),
            repository.homePageBookmarks().map { it.url }
        )
        assertEquals(
            listOf("https://second.example", "https://first.example"),
            repository.all(homePageBookmarks = true).map { it.url } // ORDER BY id DESC
        )
    }

    @Test
    fun recommendationCanBeMarkedAsUseful() = runBlocking {
        val id = repository.add(bookmark("Suggested", "https://suggested.example"))
        assertFalse(repository.byId(id)!!.useful)

        repository.markAsUseful(id)

        assertTrue(repository.byId(id)!!.useful)
    }
}
