package com.phlox.tvwebbrowser.repository

import android.app.Application
import androidx.room.Room
import com.phlox.tvwebbrowser.model.HistoryItem
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
 * Data-layer tests for [HistoryRepository], including the uniqueness rule the app relies on: a
 * revisited site replaces its row instead of stacking a second one, and keeps its newest time.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], application = Application::class)
class HistoryRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: HistoryRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).build()
        repository = HistoryRepository(db.historyDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun visit(
        title: String,
        url: String,
        time: Long,
    ) = HistoryItem().apply {
        this.title = title
        this.url = url
        this.time = time
    }

    private fun rowCount(): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM history").use {
            it.moveToFirst()
            it.getInt(0)
        }

    /** The write path `MainActivityViewModel.logVisitedHistory` uses. */
    private suspend fun logVisit(
        title: String,
        url: String,
        time: Long,
    ) {
        repository.deleteByUrl(url)
        repository.add(visit(title, url, time))
    }

    @Test
    fun revisitingASiteReplacesItsRow() =
        runBlocking {
            logVisit("Example", "https://example.com/", 1000)
            logVisit("Other", "https://other.example/", 2000)
            logVisit("Example again", "https://example.com/", 3000)

            assertEquals(2, rowCount())
            assertEquals("Example again", repository.latest(1).single().title)
            assertEquals(3000, repository.latest(1).single().time)
        }

    @Test
    fun latestIsNewestFirstAndLimited() =
        runBlocking {
            logVisit("One", "https://one.example/", 1000)
            logVisit("Two", "https://two.example/", 2000)
            logVisit("Three", "https://three.example/", 3000)

            assertEquals(listOf("Three", "Two"), repository.latest(2).map { it.title })
            assertEquals(listOf("Three", "Two", "One"), repository.latest(8).map { it.title })
        }

    @Test
    fun searchMatchesTitleOrUrl() =
        runBlocking {
            logVisit("Cooking recipes", "https://recipes.example/", 1000)
            logVisit("Docs", "https://developer.android.com/", 2000)
            logVisit("News", "https://news.example/android-16", 3000)

            assertEquals(
                listOf("News", "Docs"),
                repository.search("android").map { it.title },
            )
            assertEquals(
                listOf("Cooking recipes"),
                repository.search("recipes.example").map { it.title },
            )
            assertEquals(emptyList<String>(), repository.search("nothing"))
        }

    @Test
    fun pageWalksBackInTime() =
        runBlocking {
            logVisit("One", "https://one.example/", 1000)
            logVisit("Two", "https://two.example/", 2000)
            logVisit("Three", "https://three.example/", 3000)

            assertEquals(3, repository.page(0).size)
            assertEquals(listOf("Two", "One"), repository.page(1).map { it.title })
        }

    @Test
    fun titleOfALoggedVisitCanBeFilledInLater() =
        runBlocking {
            logVisit("", "https://example.com/", 1000)
            val item = repository.latest(1).single()

            repository.updateTitle(item.id, "Example")

            assertEquals("Example", repository.latest(1).single().title)
        }

    @Test
    fun oldVisitsCanBePurged() =
        runBlocking {
            logVisit("Old", "https://old.example/", 1000)
            logVisit("New", "https://new.example/", 5000)

            repository.deleteOlderThan(2000)

            assertEquals(listOf("New"), repository.all().map { it.title })
            assertTrue(repository.count() == 1)
        }
}
