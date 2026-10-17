package com.phlox.tvwebbrowser.activity.main

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Looper
import android.view.View
import com.phlox.tvwebbrowser.Config
import com.phlox.tvwebbrowser.R
import com.phlox.tvwebbrowser.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config as RobolectricConfig

/**
 * Integration tests for the main screen's two side panels over the real layout: which panel is
 * shown, which one has the focus while it is shown, and who gets the focus back when it is closed.
 * The focus claim is the delicate part - a panel that has just been made VISIBLE is not laid out
 * yet, so it has to take the focus itself before handing it down to a row.
 */
@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [26], application = Application::class)
class PanelControllerTest {

    private lateinit var activity: Activity
    private lateinit var binding: ActivityMainBinding
    private lateinit var panelController: PanelController
    private var focusReturnedToEngine = 0

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        binding = ActivityMainBinding.inflate(activity.layoutInflater)
        // The cursor widget reads the live cursor tuning from Config; the composition root hands
        // it the instance right after inflation (MainActivity.onCreate).
        binding.flWebViewContainer.config =
            Config(activity.getSharedPreferences("panel_test", Context.MODE_PRIVATE))
        activity.setContentView(binding.root)
        shadowOf(Looper.getMainLooper()).idle()
        panelController = PanelController(binding) { focusReturnedToEngine++ }
    }

    @Test
    fun showingTheTabsPanelClaimsItsFocus() {
        assertFalse(panelController.isTabsPanelVisible)

        panelController.showTabsPanel()

        assertTrue(panelController.isTabsPanelVisible)
        assertTrue(binding.vTabs.hasFocus())
    }

    @Test
    fun showingTheBookmarksPanelClosesTheTabsPanelAndClaimsItsFocus() {
        panelController.showTabsPanel()

        panelController.showBookmarksPanel(CoroutineScope(Dispatchers.Main)) {
            binding.vBookmarks.setBookmarks(emptyList())
        }
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(panelController.isTabsPanelVisible)
        assertTrue(panelController.isBookmarksPanelVisible)
        assertTrue(binding.vBookmarks.hasFocus())
    }

    @Test
    fun closingTheTabsPanelReturnsTheFocusToThePage() {
        panelController.toggleTabsPanel()
        assertTrue(panelController.isTabsPanelVisible)
        assertEquals(0, focusReturnedToEngine)

        panelController.toggleTabsPanel()

        assertFalse(panelController.isTabsPanelVisible)
        assertEquals(1, focusReturnedToEngine)
    }

    @Test
    fun theFirstBookmarkRowTakesTheFocusOnceTheListIsSettled() {
        panelController.showBookmarksPanel(CoroutineScope(Dispatchers.Main)) {
            binding.vBookmarks.setCurrentPage("http://example.com/page", bookmarked = false)
            binding.vBookmarks.setBookmarks(emptyList())
        }
        shadowOf(Looper.getMainLooper()).idle()

        val addThisPage = binding.vBookmarks.findViewById<View>(R.id.llAddThisPage)
        assertTrue(addThisPage.isShown)
        assertTrue(addThisPage.hasFocus())
    }

    @Test
    fun hideAllPanelsClosesBoth() {
        panelController.showTabsPanel()
        panelController.showBookmarksPanel(CoroutineScope(Dispatchers.Main)) {
            binding.vBookmarks.setBookmarks(emptyList())
        }
        shadowOf(Looper.getMainLooper()).idle()

        panelController.hideAllPanels()

        assertFalse(panelController.isTabsPanelVisible)
        assertFalse(panelController.isBookmarksPanelVisible)
    }
}
