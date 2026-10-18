package com.phlox.tvwebbrowser.repository

import android.app.Application
import androidx.room.Room
import com.phlox.tvwebbrowser.model.WebTabState
import com.phlox.tvwebbrowser.singleton.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Data-layer tests for [TabsRepository]: the two browsing modes are stored side by side and every
 * bulk operation has to stay inside the mode it was asked about.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], application = Application::class)
class TabsRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: TabsRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).build()
        repository = TabsRepository(db.tabsDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun tab(
        url: String,
        incognito: Boolean = false,
        position: Int = 0,
        selected: Boolean = false,
    ) = WebTabState(url = url, incognito = incognito, position = position, selected = selected)

    private fun rowCount(incognito: Boolean): Int =
        db.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM tabs WHERE incognito = $incognito")
            .use {
                it.moveToFirst()
                it.getInt(0)
            }

    @Test
    fun tabsAreListedByPositionWithinTheMode() =
        runBlocking {
            repository.insert(tab("https://third.example/", position = 2))
            repository.insert(tab("https://first.example/", position = 0))
            repository.insert(tab("https://second.example/", position = 1))
            repository.insert(tab("https://incognito.example/", incognito = true, position = 0))

            assertEquals(
                listOf("https://first.example/", "https://second.example/", "https://third.example/"),
                repository.all(incognito = false).map { it.url },
            )
            assertEquals(listOf("https://incognito.example/"), repository.all(incognito = true).map { it.url })
        }

    @Test
    fun savedTabIsUpdatedInPlace() =
        runBlocking {
            val id = repository.insert(tab("https://example.com/", position = 0))
            assertTrue(id > 0)

            repository.update(tab("https://example.com/page", position = 0).apply { this.id = id })

            val reloaded = repository.all().single()
            assertEquals("https://example.com/page", reloaded.url)
        }

    @Test
    fun unselectAllOnlyTouchesItsOwnMode() =
        runBlocking {
            repository.insert(tab("https://normal.example/", selected = true))
            repository.insert(tab("https://incognito.example/", incognito = true, selected = true))

            repository.unselectAll(incognito = false)

            assertEquals(false, repository.all(incognito = false).single().selected)
            assertEquals(true, repository.all(incognito = true).single().selected)
        }

    @Test
    fun positionsArePersistedForTheWholeList() =
        runBlocking {
            val a = repository.insert(tab("https://a.example/", position = 0))
            val b = repository.insert(tab("https://b.example/", position = 1))

            // The model swaps the two tabs and persists the resulting positions, as TabsModel does when
            // the panel asks for a move.
            repository.updatePositions(
                listOf(
                    tab("https://b.example/", position = 0).apply { id = b },
                    tab("https://a.example/", position = 1).apply { id = a },
                ),
            )

            assertEquals(listOf("https://b.example/", "https://a.example/"), repository.all().map { it.url })
        }

    @Test
    fun deleteAllIsScopedToTheMode() =
        runBlocking {
            repository.insert(tab("https://normal.example/"))
            repository.insert(tab("https://incognito.example/", incognito = true))

            repository.deleteAll(incognito = false)

            assertEquals(0, rowCount(incognito = false))
            assertEquals(1, rowCount(incognito = true))
        }

    @Test
    fun deletingOneTabKeepsTheOthers() =
        runBlocking {
            val a = repository.insert(tab("https://a.example/", position = 0))
            repository.insert(tab("https://b.example/", position = 1))

            repository.delete(repository.all().first { it.id == a })

            assertEquals(listOf("https://b.example/"), repository.all().map { it.url })
        }
}
