package com.phlox.tvwebbrowser.config

import android.content.SharedPreferences
import com.phlox.tvwebbrowser.utils.observable.ObservableValue

/**
 * A [SharedPreferences] string value that stays observable across processes of the app: a write
 * goes to prefs first, then the in-memory value and the observers follow. The nullable variant
 * treats `null` as "remove the key" rather than storing it.
 */
class ObservableStringPreference(
    private val prefs: SharedPreferences,
    default: String,
    private val prefsKey: String,
) : ObservableValue<String>(default) {
    override var value: String = prefs.getString(prefsKey, default)!!
        set(value) {
            prefs.edit().putString(prefsKey, value).apply()
            field = value
            notifyObservers()
        }
}

class ObservableOptStringPreference(
    private val prefs: SharedPreferences,
    default: String?,
    private val prefsKey: String,
) : ObservableValue<String?>(default) {
    override var value: String? = prefs.getString(prefsKey, default)
        set(value) {
            if (value == null) {
                prefs.edit().remove(prefsKey).apply()
            } else {
                prefs.edit().putString(prefsKey, value).apply()
            }
            field = value
            notifyObservers()
        }
}
