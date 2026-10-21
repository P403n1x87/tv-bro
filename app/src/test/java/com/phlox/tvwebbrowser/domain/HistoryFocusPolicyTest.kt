package com.phlox.tvwebbrowser.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [HistoryFocusPolicy], the D-pad routing that connects the history list (a single
 * focus target) with each row's delete icon. The point of the policy is that neither control
 * becomes a dead end: RIGHT enters the icon, LEFT/UP/DOWN leave it, and nothing an ordinary list
 * needs (scrolling, multi-select dates, header rows) is swallowed.
 */
class HistoryFocusPolicyTest {
    private fun route(
        keyCode: Int,
        listFocusable: Boolean = false,
        rowDeleteFocusable: Boolean = false,
        deleteIconFocused: Boolean = false,
    ) = HistoryFocusPolicy.route(keyCode, listFocusable, rowDeleteFocusable, deleteIconFocused)

    @Test
    fun rightFromTheListEntersTheRowDeleteIcon() {
        assertEquals(
            HistoryFocusPolicy.Action.FOCUS_ROW_DELETE,
            route(KEY_DPAD_RIGHT, listFocusable = true, rowDeleteFocusable = true),
        )
    }

    @Test
    fun rightStaysInTheListWhenTheRowHasNoDeleteIcon() {
        // Date headers and multi-select rows hide the icon; the key must fall through so the list
        // can still scroll or change selection.
        assertEquals(
            HistoryFocusPolicy.Action.PASS_THROUGH,
            route(KEY_DPAD_RIGHT, listFocusable = true, rowDeleteFocusable = false),
        )
    }

    @Test
    fun rightIsIgnoredWhenTheListDoesNotHoldFocus() {
        assertEquals(
            HistoryFocusPolicy.Action.PASS_THROUGH,
            route(KEY_DPAD_RIGHT, listFocusable = false, rowDeleteFocusable = true),
        )
    }

    @Test
    fun leftUpAndDownLeaveTheDeleteIconForTheList() {
        // Every exit direction works, so entering the icon with RIGHT is never one-way.
        for (key in intArrayOf(KEY_DPAD_LEFT, KEY_DPAD_UP, KEY_DPAD_DOWN)) {
            assertEquals(
                "key $key should leave the icon",
                HistoryFocusPolicy.Action.FOCUS_LIST,
                route(key, deleteIconFocused = true),
            )
        }
    }

    @Test
    fun leftUpAndDownFallThroughWhenTheIconIsNotFocused() {
        for (key in intArrayOf(KEY_DPAD_LEFT, KEY_DPAD_UP, KEY_DPAD_DOWN)) {
            assertEquals(
                "key $key should not be swallowed in the list",
                HistoryFocusPolicy.Action.PASS_THROUGH,
                route(key, listFocusable = true),
            )
        }
    }

    @Test
    fun unrelatedKeysAreLeftToTheFramework() {
        assertEquals(HistoryFocusPolicy.Action.PASS_THROUGH, route(KEY_DPAD_CENTER, listFocusable = true))
        assertEquals(HistoryFocusPolicy.Action.PASS_THROUGH, route(KEYCODE_BACK))
    }

    private companion object {
        const val KEY_DPAD_UP = 19
        const val KEY_DPAD_DOWN = 20
        const val KEY_DPAD_LEFT = 21
        const val KEY_DPAD_RIGHT = 22
        const val KEY_DPAD_CENTER = 23
        const val KEYCODE_BACK = 4
    }
}
