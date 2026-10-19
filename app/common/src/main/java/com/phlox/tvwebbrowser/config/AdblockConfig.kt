package com.phlox.tvwebbrowser.config

import android.content.SharedPreferences

/**
 * Ad-block preferences, stored in [SharedPreferences]: the on/off flag (held in memory after the
 * first read — the engines check it per request), the observable filter-list url the settings
 * screen edits, and the time of the last list update.
 */
class AdblockConfig(
    private val prefs: SharedPreferences,
) {
    companion object {
        const val ADBLOCK_ENABLED_PREF_KEY = "adblock_enabled"
        const val ADBLOCK_LAST_UPDATE_LIST_KEY = "adblock_last_update"
        const val ADBLOCK_LIST_URL_KEY = "adblock_list_url"
        const val DEFAULT_ADBLOCK_LIST_URL = "https://easylist.to/easylist/easylist.txt"
    }

    var adBlockEnabled: Boolean = prefs.getBoolean(ADBLOCK_ENABLED_PREF_KEY, true)
        set(value) {
            field = value
            prefs.edit().putBoolean(ADBLOCK_ENABLED_PREF_KEY, field).apply()
        }

    var adBlockListURL = ObservableStringPreference(prefs, DEFAULT_ADBLOCK_LIST_URL, ADBLOCK_LIST_URL_KEY)

    var adBlockListLastUpdate: Long
        get() = prefs.getLong(ADBLOCK_LAST_UPDATE_LIST_KEY, 0)
        set(value) {
            prefs.edit().putLong(ADBLOCK_LAST_UPDATE_LIST_KEY, value).apply()
        }
}
