package com.phlox.tvwebbrowser.domain

/**
 * D-pad focus routing for the history list.
 *
 * The screen hosts two kinds of focus targets that the framework's default focus search cannot
 * connect: the list is a *single* focus target (its rows are not individually focusable), and each
 * row carries a delete icon that must be reachable with a remote. Without help, the icon can only
 * be pressed with a pointer, which strands remote-only users. This policy describes the two
 * escapes that bridge them, so [com.phlox.tvwebbrowser.activity.history.HistoryActivity] only has
 * to apply the decision to real views.
 */
object HistoryFocusPolicy {
    /** What the host should do with a key event while the history screen is focused. */
    enum class Action {
        /** Let the framework handle the event untouched. */
        PASS_THROUGH,

        /** Move focus from the list onto the focused row's delete icon. */
        FOCUS_ROW_DELETE,

        /** Move focus from a row's delete icon back onto the list. */
        FOCUS_LIST,
    }

    /**
     * Decides how to route [keyCode].
     *
     * @param listFocusable whether the list currently holds the focus (the only state from which
     *   the row's delete icon may be entered).
     * @param rowDeleteFocusable whether the target row actually has a visible delete icon; a
     *   date-header row does not, and neither does any row while multi-select is on.
     * @param deleteIconFocused whether the focus currently sits on a row's delete icon, the state
     *   from which the user must be able to step back out.
     */
    fun route(
        keyCode: Int,
        listFocusable: Boolean,
        rowDeleteFocusable: Boolean,
        deleteIconFocused: Boolean,
    ): Action =
        when (keyCode) {
            // A short RIGHT from the list is the only way onto a row's delete icon. When the row
            // has no icon (header, or multi-select) the key falls through so the list can scroll.
            KEY_DPAD_RIGHT -> {
                if (listFocusable && rowDeleteFocusable) Action.FOCUS_ROW_DELETE else Action.PASS_THROUGH
            }

            // Any of LEFT/UP/DOWN steps back off the icon onto the list, so the icon is never a
            // dead end. UP/DOWN also cover the vertical exit that a lone RIGHT entry point would
            // otherwise make one-way.
            KEY_DPAD_LEFT, KEY_DPAD_UP, KEY_DPAD_DOWN -> {
                if (deleteIconFocused) Action.FOCUS_LIST else Action.PASS_THROUGH
            }

            else -> {
                Action.PASS_THROUGH
            }
        }

    // Values mirror KeyEvent's constants without importing android.view.KeyEvent, so the policy
    // stays plain-JVM testable. Callers pass KeyEvent.KEYCODE_* through unchanged.
    private const val KEY_DPAD_RIGHT = 22
    private const val KEY_DPAD_LEFT = 21
    private const val KEY_DPAD_UP = 19
    private const val KEY_DPAD_DOWN = 20
}
