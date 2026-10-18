package com.phlox.tvwebbrowser.utils

import android.app.Activity
import android.app.Application
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as RobolectricConfig

/**
 * Tests for [BackNavigationEventsAdapter]: BACK key press and release is the normal case; game
 * controller long-press mode only fires on a long press and does not fire on release.
 */
@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [26], application = Application::class)
class BackNavigationEventsAdapterTest {
    private class Counter {
        var count = 0

        fun inc() {
            count++
        }
    }

    private fun keyEvent(action: Int): KeyEvent {
        val now = SystemClock.uptimeMillis()
        return KeyEvent(now, now, action, KeyEvent.KEYCODE_BACK, 0, 0, 0, 0, 0, InputDevice.SOURCE_KEYBOARD)
    }

    private fun gamepadKeyDown(repeat: Int = 0): KeyEvent =
        KeyEvent(
            0,
            0,
            KeyEvent.ACTION_DOWN,
            KeyEvent.KEYCODE_BACK,
            repeat,
            0,
            KeyEvent.META_ALT_ON,
            0,
            0,
            InputDevice.SOURCE_GAMEPAD,
        )

    private fun gamepadKeyUp(): KeyEvent =
        KeyEvent(
            0,
            0,
            KeyEvent.ACTION_UP,
            KeyEvent.KEYCODE_BACK,
            0,
            0,
            KeyEvent.META_ALT_ON,
            0,
            0,
            InputDevice.SOURCE_GAMEPAD,
        )

    @Test
    fun nonControllerBackPressesAndReleasesFireOnce() {
        val counter = Counter()
        val adapter = BackNavigationEventsAdapter({ counter.inc() }, longPressTimeoutMs = 500)

        val down = keyEvent(KeyEvent.ACTION_DOWN)
        val up = keyEvent(KeyEvent.ACTION_UP)

        assertTrue(adapter.dispatchKeyEvent(down))
        assertTrue(adapter.dispatchKeyEvent(up))

        assertEquals(1, counter.count)
    }

    @Test
    fun systemBackIsReportedAtOnce() {
        val counter = Counter()
        val adapter = BackNavigationEventsAdapter({ counter.inc() })

        assertTrue(adapter.dispatchSystemBackNavigationEvent())
        assertEquals(1, counter.count)
    }

    @Test
    fun aKeyRightAfterASystemBackIsSuppressed() {
        val counter = Counter()
        val adapter = BackNavigationEventsAdapter({ counter.inc() })

        adapter.dispatchSystemBackNavigationEvent()
        // The system back already reported a navigation, so both halves of the key press are ignored.
        assertFalse(adapter.dispatchKeyEvent(keyEvent(KeyEvent.ACTION_DOWN)))
        assertFalse(adapter.dispatchKeyEvent(keyEvent(KeyEvent.ACTION_UP)))

        assertEquals(1, counter.count)
    }

    @Test
    fun downWithoutUpDoesNotFire() {
        val counter = Counter()
        val adapter = BackNavigationEventsAdapter({ counter.inc() })

        assertTrue(adapter.dispatchKeyEvent(keyEvent(KeyEvent.ACTION_DOWN)))
        assertEquals(0, counter.count)
    }

    @Test
    fun aSecondDownWithoutUpDoesNotFireAgain() {
        val counter = Counter()
        val adapter = BackNavigationEventsAdapter({ counter.inc() })

        adapter.dispatchKeyEvent(keyEvent(KeyEvent.ACTION_DOWN))
        adapter.dispatchKeyEvent(keyEvent(KeyEvent.ACTION_DOWN))
        assertEquals(0, counter.count)
    }

    @Test
    fun upWithoutPriorDownDoesNotFire() {
        val counter = Counter()
        val adapter = BackNavigationEventsAdapter({ counter.inc() })

        // The adapter consumes the BACK key event even if it doesn't trigger a navigation.
        assertTrue(adapter.dispatchKeyEvent(keyEvent(KeyEvent.ACTION_UP)))
        assertEquals(0, counter.count)
    }

    @Test
    fun onlyBackButtonAndEscapeAndBackKeyAreAccepted() {
        val counter = Counter()
        val adapter = BackNavigationEventsAdapter({ counter.inc() })

        val volumeDown = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN)
        assertFalse(adapter.dispatchKeyEvent(volumeDown))
        assertEquals(0, counter.count)
    }

    @Test
    fun gameControllerLongPressModeFiresOnLongPressButNotOnRelease() {
        val counter = Counter()
        val adapter = BackNavigationEventsAdapter({ counter.inc() }, longPressTimeoutMs = 500)
        adapter.gameControllersLongPressBForBackNavigation = true

        val down = gamepadKeyDown()
        val up = gamepadKeyUp()

        assertTrue(adapter.dispatchKeyEvent(down))
        assertEquals(0, counter.count)

        val longPressDown = gamepadKeyDown(repeat = 1)
        assertTrue(adapter.dispatchKeyEvent(longPressDown))
        assertEquals(1, counter.count)

        assertTrue(adapter.dispatchKeyEvent(up))
        assertEquals(1, counter.count)
    }
}
