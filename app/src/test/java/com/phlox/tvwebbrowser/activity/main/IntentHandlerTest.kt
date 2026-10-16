package com.phlox.tvwebbrowser.activity.main

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.Room
import com.phlox.tvwebbrowser.AppContext
import com.phlox.tvwebbrowser.Config
import com.phlox.tvwebbrowser.activity.history.HistoryActivity
import com.phlox.tvwebbrowser.model.WebTabState
import com.phlox.tvwebbrowser.repository.HostsRepository
import com.phlox.tvwebbrowser.repository.TabsRepository
import com.phlox.tvwebbrowser.singleton.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config as RobolectricConfig

/**
 * Tests for [IntentHandler]'s deep-link branching and the History activity result. The view-side
 * effects are captured as plain callbacks.
 */
@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [26], application = Application::class)
class IntentHandlerTest {

    private lateinit var db: AppDatabase
    private lateinit var tabsModel: TabsModel

    private val opened = mutableListOf<Triple<String?, Int, Boolean>>()
    private val navigated = mutableListOf<String>()
    private val changed = mutableListOf<WebTabState>()
    private var overlayHidden = 0

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        AppContext.init(app, Config(app.getSharedPreferences("intent_test", Context.MODE_PRIVATE)))
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tabsModel = TabsModel(TabsRepository(db.tabsDao()), HostsRepository(db.hostsDao()))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun handler() = IntentHandler(
        tabsModel = tabsModel,
        openInNewTab = { url, index, navigateImmediately -> opened += Triple(url, index, navigateImmediately) },
        navigate = { navigated += it },
        changeTab = { changed += it },
        autoHideMenuOverlay = { overlayHidden++ },
    )

    private fun viewIntent(url: String, sameTab: Boolean = false) =
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            if (sameTab) {
                putExtra("com.phlox.tvwebbrowser.EXTRA_OPEN_IN_SAME_TAB", true)
            }
        }

    @Test
    fun aPlainDeepLinkOpensANewTabAtTheEnd() {
        tabsModel.tabsStates.add(WebTabState(url = "https://one.example/"))
        tabsModel.tabsStates.add(WebTabState(url = "https://two.example/"))

        handler().handleIntent(viewIntent("https://example.com/"))

        assertEquals(listOf(Triple("https://example.com/", 2, true)), opened)
        assertTrue("a plain deep link must not navigate in place", navigated.isEmpty())
    }

    @Test
    fun theSameTabExtraNavigatesTheCurrentTab() {
        val tab = WebTabState(url = "https://one.example/")
        tabsModel.tabsStates.add(tab)

        handler().handleIntent(viewIntent("https://example.com/", sameTab = true))

        assertTrue("no new tab is opened", opened.isEmpty())
        assertEquals(listOf("https://example.com/"), navigated)
        assertEquals("no current tab was set, so the first one is selected", listOf(tab), changed)
    }

    @Test
    fun theSameTabExtraWithoutTabsFallsBackToANewTab() {
        handler().handleIntent(viewIntent("https://example.com/", sameTab = true))

        assertEquals(listOf(Triple("https://example.com/", 0, true)), opened)
    }

    @Test
    fun aHistoryResultNavigatesAndRestoresTheOverlay() {
        val data = Intent().putExtra(HistoryActivity.KEY_URL, "https://picked.example/")

        handler().handleHistoryResult(Activity.RESULT_OK, data)

        assertEquals(listOf("https://picked.example/"), navigated)
        assertEquals(1, overlayHidden)
    }

    @Test
    fun aCancelledHistoryResultDoesNothing() {
        handler().handleHistoryResult(Activity.RESULT_CANCELED, Intent())

        assertTrue(navigated.isEmpty())
        assertEquals(0, overlayHidden)
    }
}
