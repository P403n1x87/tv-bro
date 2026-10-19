package com.phlox.tvwebbrowser.config

import android.content.SharedPreferences

/**
 * Update-checking preferences, stored in [SharedPreferences]. The auto-check default follows how
 * the app was installed (sideloaded builds default to on), decided by [installedByApk].
 */
class UpdatesConfig(
    private val prefs: SharedPreferences,
    private val installedByApk: () -> Boolean,
) {
    companion object {
        const val AUTO_CHECK_UPDATES_KEY = "auto_check_updates"
        const val UPDATE_CHANNEL_KEY = "update_channel"
        const val LAST_UPDATE_USER_NOTIFICATION_TIME_KEY = "last_update_notif"
        const val APP_VERSION_CODE_MARK_KEY = "app_version_code_mark"
    }

    var autoCheckUpdates: Boolean
        get() = prefs.getBoolean(AUTO_CHECK_UPDATES_KEY, installedByApk())
        set(value) {
            prefs.edit().putBoolean(AUTO_CHECK_UPDATES_KEY, value).apply()
        }

    var updateChannel: String
        get() = prefs.getString(UPDATE_CHANNEL_KEY, "release")!!
        set(value) {
            prefs.edit().putString(UPDATE_CHANNEL_KEY, value).apply()
        }

    /**
     * Time of the last "update available" user notification, `null` when none was ever shown — the
     * difference matters: a fresh install must count as "just now shown" so the offer waits.
     */
    var lastUpdateUserNotificationTime: Long?
        get() =
            if (prefs.contains(LAST_UPDATE_USER_NOTIFICATION_TIME_KEY)) {
                prefs.getLong(LAST_UPDATE_USER_NOTIFICATION_TIME_KEY, 0)
            } else {
                null
            }
        set(value) {
            val editor = prefs.edit()
            if (value == null) {
                editor.remove(LAST_UPDATE_USER_NOTIFICATION_TIME_KEY)
            } else {
                editor.putLong(LAST_UPDATE_USER_NOTIFICATION_TIME_KEY, value)
            }
            editor.apply()
        }

    /** App version code the install last ran as; a bump marks the first launch after an update. */
    var appVersionCodeMark: Int
        get() = prefs.getInt(APP_VERSION_CODE_MARK_KEY, 0)
        set(value) {
            prefs.edit().putInt(APP_VERSION_CODE_MARK_KEY, value).apply()
        }
}
