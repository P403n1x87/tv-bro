package com.phlox.tvwebbrowser.activity.main.view

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import com.phlox.tvwebbrowser.Config
import com.phlox.tvwebbrowser.R
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
 * Integration tests for the top bar over a real inflated layout: what the address box does with the
 * focus and with the keys, and which buttons the D-pad chain can still reach.
 *
 * The address box is a button until it is activated (OK/ENTER, a tap or typing on a keyboard), so
 * these tests pin that state machine and the focus flags that make the D-pad chain skip the disabled
 * (Back/Forward) and the missing (Tabs, Media) buttons instead of getting trapped on them.
 */
@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [26], application = Application::class)
class ActionBarTest {

    private lateinit var activity: Activity
    private lateinit var actionBar: ActionBar
    private lateinit var etUrl: EditText
    private lateinit var callback: RecordingCallback

    private class RecordingCallback : ActionBar.Callback {
        val searched = ArrayList<String>()
        var backNavigations = 0
        var extendedModeCalls = 0
        var inputDoneCalls = 0

        override fun closeWindow() {}
        override fun showDownloads() {}
        override fun showFavorites() {}
        override fun showHistory() {}
        override fun showSettings() {}
        override fun initiateVoiceSearch() {}
        override fun search(text: String) {
            searched.add(text)
        }

        override fun onExtendedAddressBarMode() {
            extendedModeCalls++
        }

        override fun onUrlInputDone() {
            inputDoneCalls++
        }

        override fun toggleIncognitoMode() {}
        override fun onNavigateBack() {
            backNavigations++
        }

        override fun onNavigateForward() {}
        override fun onReload() {}
        override fun onShowStreams() {}
        override fun onToggleTabPanel() {}
        override fun onShowMenu() {}
    }

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        actionBar = ActionBar(activity)
        callback = RecordingCallback()
        actionBar.callback = callback
        activity.setContentView(actionBar)
        shadowOf(Looper.getMainLooper()).idle()
        etUrl = actionBar.findViewById(R.id.etUrl)
    }

    /** Sends a full key press (down then up) to the address box, as the framework would. */
    private fun pressOnUrlBox(keyCode: Int) {
        etUrl.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        etUrl.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun button(id: Int): View = actionBar.findViewById(id)

    @Test
    fun theAddressBoxShowsTheCurrentUrl() {
        actionBar.setAddressBoxText("http://example.com/page")

        assertEquals("http://example.com/page", etUrl.text.toString())
    }

    @Test
    fun theBuiltInHomePageIsShownAsAnEmptyBox() {
        actionBar.setAddressBoxText(Config.HOME_PAGE_URL)

        assertEquals("", etUrl.text.toString())
    }

    @Test
    fun theAddressBoxIsAButtonUntilItIsActivated() {
        assertTrue(etUrl.requestFocus())

        assertFalse("the box must not start editing just because it has the focus", etUrl.isCursorVisible)
    }

    @Test
    fun okTurnsTheAddressBoxIntoATextField() {
        assertTrue(etUrl.requestFocus())

        pressOnUrlBox(KeyEvent.KEYCODE_DPAD_CENTER)

        assertTrue(etUrl.isCursorVisible)
        assertEquals(1, callback.extendedModeCalls)
    }

    @Test
    fun backspaceLeavesTheAddressAloneWhileTheBoxIsAButton() {
        actionBar.setAddressBoxText("http://example.com/page")
        assertTrue(etUrl.requestFocus())

        pressOnUrlBox(KeyEvent.KEYCODE_DEL)

        assertEquals("http://example.com/page", etUrl.text.toString())
        assertFalse(etUrl.isCursorVisible)
    }

    @Test
    fun enterOnTheActivatedAddressBoxOpensTheTypedUrl() {
        actionBar.setAddressBoxText("http://example.com/page")
        assertTrue(etUrl.requestFocus())
        pressOnUrlBox(KeyEvent.KEYCODE_DPAD_CENTER)
        assertTrue(etUrl.isCursorVisible)

        etUrl.setText("example.org")
        etUrl.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))

        assertEquals(listOf("example.org"), callback.searched)
        assertFalse(etUrl.isCursorVisible)
        assertEquals(1, callback.inputDoneCalls)
    }

    @Test
    fun rightFromTheAddressBoxReachesTheNextButton() {
        assertTrue(etUrl.requestFocus())

        pressOnUrlBox(KeyEvent.KEYCODE_DPAD_RIGHT)

        assertFalse("the caret must not swallow the D-pad while the box is a button", etUrl.hasFocus())
        assertTrue(button(R.id.ibFavorites).hasFocus())
    }

    @Test
    fun aDisabledBackButtonCannotTakeFocus() {
        actionBar.setBackEnabled(false)

        assertFalse(button(R.id.ibBack).isEnabled)
        assertFalse("a disabled view is still focusable, the flag has to be cleared too",
            button(R.id.ibBack).isFocusable)

        actionBar.setBackEnabled(true)

        assertTrue(button(R.id.ibBack).isFocusable)
    }

    @Test
    fun catchFocusSkipsTheDisabledBackAndForwardButtons() {
        actionBar.setBackEnabled(false)
        actionBar.setForwardEnabled(false)

        actionBar.catchFocus()

        assertTrue(button(R.id.ibRefresh).hasFocus())
    }

    @Test
    fun theTabsButtonIsUnfocusableWithASingleTab() {
        actionBar.setTabsCounter(1)

        assertFalse(button(R.id.ibTabs).isFocusable)

        actionBar.setTabsCounter(2)

        assertTrue(button(R.id.ibTabs).isFocusable)
        assertTrue(button(R.id.flTabsButton).isShown)
    }

    @Test
    fun anEnabledBackButtonForwardsItsClick() {
        actionBar.setBackEnabled(true)

        button(R.id.ibBack).performClick()

        assertEquals(1, callback.backNavigations)
    }
}
