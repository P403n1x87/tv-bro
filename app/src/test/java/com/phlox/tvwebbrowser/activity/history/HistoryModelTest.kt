package com.phlox.tvwebbrowser.activity.history

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.room.Room
import com.phlox.tvwebbrowser.AppContext
import com.phlox.tvwebbrowser.Config
import com.phlox.tvwebbrowser.model.HistoryItem
import com.phlox.tvwebbrowser.repository.HistoryRepository
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config as RobolectricConfig
import java.time.Duration
import java.util.concurrent.Executor

/**
 * Tests for [HistoryModel]'s loading window and its deletes, over an in-memory database so the
 * model can be built with an injected repository. The view appends what the model publishes, so what
 * matters here is which slice of the history a load returns.
 */
@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [26], application = Application::class)
class HistoryModelTest {

    private lateinit var db: AppDatabase
    private lateinit var history: HistoryRepository
    private lateinit var model: HistoryModel

    private val directExecutor = Executor { it.run() }

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        AppContext.init(app, Config(app.getSharedPreferences("history_test", Context.MODE_PRIVATE)))
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
        history = HistoryRepository(db.historyDao())
        model = HistoryModel(history)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun advance(seconds: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds))
    }

    private fun insert(title: String, url: String, time: Long = 0) = runBlocking {
        history.add(HistoryItem().apply {
            this.title = title
            this.url = url
            this.time = time
        })
    }

    private fun urls() = model.lastLoadedItems.value.map { it.url }

    @Test
    fun aLoadWithoutASearchQueryPublishesTheFirstWindow() {
        insert("One", "https://one.example/", time = 1)
        insert("Two", "https://two.example/", time = 2)

        model.loadItems()
        advance(1)

        assertEquals(listOf("https://two.example/", "https://one.example/"), urls())
    }

    @Test
    fun aLoadAtAnOffsetPublishesTheNextWindow() {
        insert("One", "https://one.example/", time = 1)
        insert("Two", "https://two.example/", time = 2)
        insert("Three", "https://three.example/", time = 3)

        model.loadItems(offset = 2)
        advance(1)

        assertEquals(listOf("https://one.example/"), urls())
    }

    @Test
    fun aSearchQueryIsMatchedAgainstTheStoredTitleOrUrl() {
        insert("Kotlin tips", "https://blog.example/one", time = 1)
        insert("Something else", "https://other.example/two", time = 2)

        // A partial query is enough: the repository wraps it in wildcards for the DAO's LIKE.
        model.searchQuery = "blog.example"
        model.loadItems()
        advance(1)

        assertEquals(listOf("https://blog.example/one"), urls())
    }

    @Test
    fun deletingTheSelectedVisitsRemovesThem() {
        insert("One", "https://one.example/", time = 1)
        insert("Two", "https://two.example/", time = 2)

        model.loadItems()
        advance(1)
        runBlocking { model.delete(model.lastLoadedItems.value) }
        model.loadItems()
        advance(1)

        assertTrue(urls().isEmpty())
    }

    @Test
    fun deletingEverythingEmptiesTheHistory() {
        insert("One", "https://one.example/", time = 1)
        insert("Two", "https://two.example/", time = 2)

        runBlocking { model.deleteAll() }
        model.loadItems()
        advance(1)

        assertTrue(urls().isEmpty())
    }
}
