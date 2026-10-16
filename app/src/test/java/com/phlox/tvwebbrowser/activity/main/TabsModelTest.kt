package com.phlox.tvwebbrowser.activity.main

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.room.Room
import com.phlox.tvwebbrowser.Config
import com.phlox.tvwebbrowser.model.WebTabState
import com.phlox.tvwebbrowser.repository.HostsRepository
import com.phlox.tvwebbrowser.repository.TabsRepository
import com.phlox.tvwebbrowser.singleton.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
 * Tests for [TabsModel]'s state intents, over an in-memory database so the model can be built with
 * injected repositories. The engine-touching intents are out of reach here (a tab's engine is a lazy
 * factory call), so this covers the decisions that are pure list/config logic.
 *
 * The model is built with every dependency injected — including its [Config] — and `AppContext` is
 * never initialised, which is the point of the constructor seam: the model reads no global.
 */
@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [26], application = Application::class)
class TabsModelTest {

    private lateinit var app: Application
    private lateinit var config: Config
    private lateinit var db: AppDatabase
    private lateinit var tabsRepository: TabsRepository
    private lateinit var model: TabsModel

    private val directExecutor = Executor { it.run() }

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        config = Config(app.getSharedPreferences("tabs_test", Context.MODE_PRIVATE))
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
        tabsRepository = TabsRepository(db.tabsDao())
        model = TabsModel(
            tabsRepository,
            HostsRepository(db.hostsDao()),
            config,
            filesDir = { app.filesDir },
            cacheDir = { app.cacheDir },
            networkAvailable = { true },
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun advance(seconds: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds))
    }

    private fun insert(tab: WebTabState) = runBlocking { tabsRepository.insert(tab) }

    // -- onToggleAdBlock --------------------------------------------------------------------

    @Test
    fun adBlockFollowsTheBrowserDefaultWhileTheTabHasNoOverride() {
        val tab = WebTabState()

        assertTrue("enabled default -> disabled for this tab", !model.onToggleAdBlock(tab, defaultEnabled = true))
        assertEquals(false, tab.adblock)

        assertTrue("disabled default -> enabled for this tab", model.onToggleAdBlock(tab, defaultEnabled = false))
        assertEquals(true, tab.adblock)
    }

    @Test
    fun anOverrideWinsOverTheBrowserDefault() {
        val tab = WebTabState(adblock = true)

        // The global default says "off", but this tab was switched on: the toggle turns it off.
        assertFalse(model.onToggleAdBlock(tab, defaultEnabled = false))
        assertEquals(false, tab.adblock)
    }

    // -- loadState --------------------------------------------------------------------------

    @Test
    fun persistedTabsOfTheCurrentModeAreLoaded() {
        insert(WebTabState(url = "https://first.example/", position = 0))
        insert(WebTabState(url = "https://second.example/", position = 1))
        insert(WebTabState(url = "https://incognito.example/", incognito = true, position = 0))

        model.loadState()
        advance(1)

        assertEquals(
            listOf("https://first.example/", "https://second.example/"),
            model.tabsStates.map { it.url }
        )
        assertTrue(model.loaded)
    }

    @Test
    fun theInjectedConfigSelectsWhichTabsAreLoaded() {
        // The model must follow the Config it was handed, not a global one.
        config.incognitoMode = true
        insert(WebTabState(url = "https://normal.example/", position = 0))
        insert(WebTabState(url = "https://incognito.example/", incognito = true, position = 0))

        model.loadState()
        advance(1)

        assertEquals(listOf("https://incognito.example/"), model.tabsStates.map { it.url })
    }

    @Test
    fun loadingAgainKeepsTheAlreadyLoadedTabsWhenTheModeDidNotChange() {
        insert(WebTabState(url = "https://first.example/"))
        model.loadState()
        advance(1)

        // A row added behind the model's back is not picked up while the mode is unchanged.
        insert(WebTabState(url = "https://added.example/", position = 1))
        model.loadState()
        advance(1)

        assertEquals(listOf("https://first.example/"), model.tabsStates.map { it.url })
    }

    @Test
    fun noTabIsSelectedWhileNothingIsLoaded() {
        assertNull(model.currentTab.value)
    }
}
