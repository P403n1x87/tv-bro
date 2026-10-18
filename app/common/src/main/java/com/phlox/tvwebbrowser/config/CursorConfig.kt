package com.phlox.tvwebbrowser.config

import android.content.SharedPreferences

/**
 * Virtual cursor physics and related input flags, stored in [SharedPreferences].
 */
class CursorConfig(
    private val prefs: SharedPreferences,
) {
    companion object {
        const val CURSOR_MAX_SPEED_PERCENT_KEY = "cursor_max_speed_percent"
        const val CURSOR_ACCELERATION_PERCENT_KEY = "cursor_acceleration_percent"
        const val DISABLE_MOTION_AXES_DPAD_NAVIGATION_KEY = "disable_motion_axes_dpad_navigation"
        const val CURSOR_PHYSICS_PERCENT_MIN = 25
        const val CURSOR_PHYSICS_PERCENT_MAX = 200
    }

    /** Virtual cursor max speed as a percent of the default (screen-width–based) cap. */
    var cursorMaxSpeedPercent: Int
        get() =
            prefs
                .getInt(CURSOR_MAX_SPEED_PERCENT_KEY, 100)
                .coerceIn(CURSOR_PHYSICS_PERCENT_MIN, CURSOR_PHYSICS_PERCENT_MAX)
        set(value) {
            prefs
                .edit()
                .putInt(
                    CURSOR_MAX_SPEED_PERCENT_KEY,
                    value.coerceIn(CURSOR_PHYSICS_PERCENT_MIN, CURSOR_PHYSICS_PERCENT_MAX),
                ).apply()
        }

    /** Virtual cursor acceleration as a percent of the default ramp-up per frame. */
    var cursorAccelerationPercent: Int
        get() =
            prefs
                .getInt(CURSOR_ACCELERATION_PERCENT_KEY, 100)
                .coerceIn(CURSOR_PHYSICS_PERCENT_MIN, CURSOR_PHYSICS_PERCENT_MAX)
        set(value) {
            prefs
                .edit()
                .putInt(
                    CURSOR_ACCELERATION_PERCENT_KEY,
                    value.coerceIn(CURSOR_PHYSICS_PERCENT_MIN, CURSOR_PHYSICS_PERCENT_MAX),
                ).apply()
        }

    /** When true, analog stick / hat axes from generic motion events are not translated to DPAD keys. */
    var disableMotionAxesDpadNavigation: Boolean
        get() = prefs.getBoolean(DISABLE_MOTION_AXES_DPAD_NAVIGATION_KEY, false)
        set(value) {
            prefs.edit().putBoolean(DISABLE_MOTION_AXES_DPAD_NAVIGATION_KEY, value).apply()
        }
}
