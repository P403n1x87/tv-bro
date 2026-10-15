package com.phlox.tvwebbrowser.activity.main

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.room.Room
import com.phlox.tvwebbrowser.AppContext
import com.phlox.tvwebbrowser.Config
import com.phlox.tvwebbrowser.model.FavoriteItem
import com.phlox.tvwebbrowser.model.WebTabState
import com.phlox.tvwebbrowser.repository.FavoritesRepository
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
 * Tests for [MainActivityViewModel]'s history-logging intent: the 5 second debounce, the
 * replacements that keep one history row per url, and the title update that follows a saved visit.
 *
 * The model runs against an in-memory Room database whose query executors are the calling thread's,
 * so a visit is written as soon as the (virtual) debounce elapses and the assertions do not race the
 * data layer.
 */
@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [26], application = Application::class)
class MainActivityViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var favorites: FavoritesRepository
    private lateinit var history: HistoryRepository
    private lateinit var viewModel: MainActivityViewModel

    private val directExecutor = Executor { it.run() }

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        AppContext.init(app, Config(app.getSharedPreferences("main_test", Context.MODE_PRIVATE)))
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
        history = HistoryRepository(db.historyDao())
        favorites = FavoritesRepository(db.favoritesDao())
        viewModel = MainActivityViewModel(
            favorites = favorites,
            history = history,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** Runs the main looper (and its virtual clock) for [seconds], so `delay`s elapse. */
    private fun advance(seconds: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds))
    }

    private fun saved() = runBlocking { history.all() }

    @Test
    fun aVisitIsSavedOnceTheDebounceElapses() {
        viewModel.logVisitedHistory("Title", "https://example.com/", "hash")

        assertTrue("nothing is written before the debounce elapses", saved().isEmpty())

        advance(6)

        val item = saved().single()
        assertEquals("https://example.com/", item.url)
        assertEquals("Title", item.title)
        assertEquals("hash", item.favicon)
        assertTrue("the visit is marked as saved so the title can be updated later", viewModel.lastHistoryItem!!.saved)
    }

    @Test
    fun aRepeatedVisitOfTheSameSiteIsIgnored() {
        viewModel.logVisitedHistory("Title", "https://example.com/", null)
        advance(6)

        viewModel.logVisitedHistory("Title again", "https://example.com/", null)
        advance(6)

        assertEquals(1, saved().size)
    }

    @Test
    fun homePageAndNonHttpUrlsAreNeverLogged() {
        viewModel.logVisitedHistory("Home", Config.HOME_PAGE_URL, null)
        viewModel.logVisitedHistory("About", "about:blank", null)

        advance(6)

        assertTrue(saved().isEmpty())
    }

    @Test
    fun rapidVisitsCollapseIntoTheLastOne() {
        viewModel.logVisitedHistory("One", "https://one.example/", null)
        advance(2) // still inside the debounce window, so the pending write is dropped
        viewModel.logVisitedHistory("Two", "https://two.example/", null)

        advance(6)

        assertEquals(listOf("https://two.example/"), saved().map { it.url })
    }

    @Test
    fun revisitingASiteReplacesItsRowInsteadOfAppending() {
        viewModel.logVisitedHistory("First", "https://example.com/", null)
        advance(6)
        viewModel.logVisitedHistory("Other", "https://other.example/", null)
        advance(6)
        viewModel.logVisitedHistory("Second", "https://example.com/", null)
        advance(6)

        val items = saved()
        assertEquals(2, items.size)
        assertEquals("Second", items.single { it.url == "https://example.com/" }.title)
    }

    @Test
    fun theTitleOfASavedVisitIsUpdatedInPlace() {
        viewModel.logVisitedHistory("", "https://example.com/", null)
        advance(6)

        viewModel.onTabTitleUpdated(WebTabState(url = "https://example.com/", title = "The real title"))
        advance(1)

        assertEquals("The real title", saved().single().title)
    }

    @Test
    fun aNewBookmarkIsSavedWithTheEditedValues() {
        val item = FavoriteItem()

        viewModel.onHomePageLinkEdited(item, "Edited title", "https://edited.example/")
        advance(1)

        val stored = runBlocking { favorites.allBookmarks().single() }
        assertEquals("Edited title", stored.title)
        assertEquals("https://edited.example/", stored.url)
        assertEquals("the generated id is handed back", stored.id, item.id)
        assertEquals(
            listOf("https://edited.example/"),
            viewModel.homePageLinks.map { it.url }
        )
    }

    @Test
    fun editingAnExistingBookmarkUpdatesItInsteadOfAddingASecondOne() {
        val item = FavoriteItem()
        viewModel.onHomePageLinkEdited(item, "Before", "https://before.example/")
        advance(1)

        viewModel.onHomePageLinkEdited(item, "After", "https://after.example/")
        advance(1)

        val stored = runBlocking { favorites.allBookmarks() }
        assertEquals(1, stored.size)
        assertEquals("After", stored.single().title)
        assertEquals("https://after.example/", stored.single().url)
        assertEquals(listOf("https://after.example/"), viewModel.homePageLinks.map { it.url })
    }
}
