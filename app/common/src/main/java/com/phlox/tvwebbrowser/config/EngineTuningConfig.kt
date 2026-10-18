package com.phlox.tvwebbrowser.config

import android.content.SharedPreferences

/**
 * Engine debugging and rendering-tuning preferences, stored in [SharedPreferences].
 */
class EngineTuningConfig(
    private val prefs: SharedPreferences,
) {
    companion object {
        const val WEB_ENGINE_DEBUG_KEY = "web_engine_debug"
        const val WEBVIEW_USE_ALGORITHMIC_DARKENING_WITH_DARK_UI_MODE_KEY =
            "webview_use_algorithmic_darkening_with_dark_ui_mode"
    }

    var webEngineDebug: Boolean
        get() = prefs.getBoolean(WEB_ENGINE_DEBUG_KEY, false)
        set(value) {
            prefs.edit().putBoolean(WEB_ENGINE_DEBUG_KEY, value).apply()
        }

    var webviewUseAlgorithmicDarkeningWithDarkUiMode: Boolean
        get() = prefs.getBoolean(WEBVIEW_USE_ALGORITHMIC_DARKENING_WITH_DARK_UI_MODE_KEY, true)
        set(value) {
            prefs.edit().putBoolean(WEBVIEW_USE_ALGORITHMIC_DARKENING_WITH_DARK_UI_MODE_KEY, value).apply()
        }
}
