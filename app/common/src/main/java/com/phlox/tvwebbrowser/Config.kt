package com.phlox.tvwebbrowser

import android.annotation.SuppressLint
import android.content.SharedPreferences
import com.phlox.tvwebbrowser.config.AdblockConfig
import com.phlox.tvwebbrowser.config.CursorConfig
import com.phlox.tvwebbrowser.config.EngineTuningConfig
import com.phlox.tvwebbrowser.config.ObservableOptStringPreference
import com.phlox.tvwebbrowser.config.ObservableStringPreference
import com.phlox.tvwebbrowser.config.PlaybackConfig
import com.phlox.tvwebbrowser.config.UpdatesConfig
import com.phlox.tvwebbrowser.utils.Utils
import com.phlox.tvwebbrowser.utils.observable.ObservableValue

/**
 * Typed access to the stored app-wide settings: a member mirrors a preference key, decodes the
 * stored value and writes it back — nothing else. Decisions over those values belong in `domain/`
 * (app module) or in [SearchEngines] (shared with the Gecko module); the one migration still in
 * here — the legacy "search engine as home page" flag — is storage's own job. The cohesive
 * slices live in their own classes next to this one ([CursorConfig], [AdblockConfig],
 * [UpdatesConfig], [PlaybackConfig], [EngineTuningConfig]).
 */
class Config(
    val prefs: SharedPreferences,
    private val installedByApk: () -> Boolean = { Utils.isInstalledByAPK(AppContext.get()) },
) {
    val cursorConfig: CursorConfig = CursorConfig(prefs)
    val adblockConfig: AdblockConfig = AdblockConfig(prefs)
    val updatesConfig: UpdatesConfig = UpdatesConfig(prefs, installedByApk)
    val playbackConfig: PlaybackConfig = PlaybackConfig(prefs)
    val engineTuningConfig: EngineTuningConfig = EngineTuningConfig(prefs)

    companion object {
        const val SEARCH_ENGINE_URL_PREF_KEY = "search_engine_url"
        const val SEARCH_ENGINE_AS_HOME_PAGE_KEY = "search_engine_as_home_page"
        const val HOME_PAGE_KEY = "home_page"
        const val USER_AGENT_PREF_KEY = "user_agent"
        const val THEME_KEY = "theme"
        const val TV_BRO_UA_PREFIX = "TV Bro/1.0 "
        const val HOME_URL_ALIAS = "about:home"
        const val KEEP_SCREEN_ON_KEY = "keep_screen_on"
        const val INCOGNITO_MODE_KEY = "incognito_mode"
        const val INCOGNITO_MODE_HINT_SUPPRESS_KEY = "incognito_mode_hint_suppress"
        const val DIRECT_NAVIGATION_MODE_HINT_SUPPRESS_KEY = "direct_navigation_mode_hint_suppress"
        const val HOME_PAGE_MODE = "home_page_mode"
        const val WEB_ENGINE = "web_engine"
        const val BLOCK_AUTOMATIC_REDIRECTS_KEY = "block_automatic_redirects"
        const val KEEP_TOOLBAR_OPEN_KEY = "keep_toolbar_open"
        const val RESTORE_TABS_ON_STARTUP_KEY = "restore_tabs_on_startup"

        const val INITIAL_BOOKMARKS_SUGGESTIONS_LOADED = "initial_bookmarks_suggestions_loaded"
        const val APP_WEB_EXTENSION_VERSION_KEY = "app_web_extension_version"

        const val ENGINE_GECKO_VIEW = "GeckoView"
        const val ENGINE_WEB_VIEW = "WebView"

        /** Engines the app can run, in recommendation order — the [webEngine] default picks from here. */
        val SupportedWebEngines = arrayOf(ENGINE_GECKO_VIEW, ENGINE_WEB_VIEW)

        const val HOME_PAGE_URL = "https://tvbro.phlox.dev/appcontent/home/"

        /**
         * Set by the loopback asset server (see LocalAssetServer) when it is running. When null
         * the remote [HOME_PAGE_URL] is used (loopback server unavailable). Engines that can not
         * load bundled assets directly (GeckoView) load this URL instead.
         */
        var localHomePageUrl: String? = null

        /** URL an engine should load for the built-in home page (loopback when available). */
        fun currentHomePageUrl(): String = localHomePageUrl ?: HOME_PAGE_URL

        fun canRecommendGeckoView(): Boolean {
            // disable recommendation for now
            // There are plans to keep its support, but user reports and internal testing show
            // that, at least for now, in terms of performance and stability, it is inferior to WebView.
            return false
        }
    }

    enum class Theme {
        SYSTEM,
        WHITE,
        BLACK,
    }

    enum class HomePageMode {
        HOME_PAGE,
        SEARCH_ENGINE,
        CUSTOM,
        BLANK,
        ;

        companion object {
            /** Stored ordinal → mode; an unknown value falls back to [HOME_PAGE]. */
            fun fromStored(ordinal: Int): HomePageMode = entries.getOrElse(ordinal) { HOME_PAGE }
        }
    }

    var incognitoMode: Boolean
        get() = prefs.getBoolean(INCOGNITO_MODE_KEY, false)

        @SuppressLint("ApplySharedPref")
        set(value) {
            prefs.edit().putBoolean(INCOGNITO_MODE_KEY, value).commit()
        }

    var incognitoModeHintSuppress: Boolean
        get() = prefs.getBoolean(INCOGNITO_MODE_HINT_SUPPRESS_KEY, false)
        set(value) {
            prefs.edit().putBoolean(INCOGNITO_MODE_HINT_SUPPRESS_KEY, value).apply()
        }

    var directNavigationModeHintSuppress: Boolean
        get() = prefs.getBoolean(DIRECT_NAVIGATION_MODE_HINT_SUPPRESS_KEY, false)
        set(value) {
            prefs.edit().putBoolean(DIRECT_NAVIGATION_MODE_HINT_SUPPRESS_KEY, value).apply()
        }

    var keepScreenOn: Boolean
        get() = prefs.getBoolean(KEEP_SCREEN_ON_KEY, false)
        set(value) {
            prefs.edit().putBoolean(KEEP_SCREEN_ON_KEY, value).apply()
        }

    var theme =
        object : ObservableValue<Theme>(Theme.SYSTEM) {
            override var value: Theme = Theme.entries[prefs.getInt(THEME_KEY, 0)]
                set(value) {
                    prefs.edit().putInt(THEME_KEY, value.ordinal).apply()
                    field = value
                    notifyObservers()
                }
        }

    var homePageMode: HomePageMode
        get() =
            prefs
                .getInt(HOME_PAGE_MODE, 0)
                .let {
                    // ignore value if search engine as home page is set
                    if (prefs.getBoolean(SEARCH_ENGINE_AS_HOME_PAGE_KEY, false)) {
                        prefs.edit().remove(SEARCH_ENGINE_AS_HOME_PAGE_KEY).apply()
                        HomePageMode.SEARCH_ENGINE.ordinal
                    } else {
                        it
                    }
                }.let { HomePageMode.fromStored(it) }
        set(value) {
            prefs.edit().putInt(HOME_PAGE_MODE, value.ordinal).apply()
        }

    var homePage: String
        get() = prefs.getString(HOME_PAGE_KEY, HOME_URL_ALIAS)!!
        set(value) {
            prefs.edit().putString(HOME_PAGE_KEY, value).apply()
        }

    var searchEngineURL = ObservableStringPreference(prefs, SearchEngines.urls[0], SEARCH_ENGINE_URL_PREF_KEY)

    var webEngine: String
        get() {
            if (!prefs.contains(WEB_ENGINE)) {
                prefs
                    .edit()
                    .putString(
                        WEB_ENGINE,
                        if (canRecommendGeckoView()) {
                            SupportedWebEngines[0]
                        } else {
                            SupportedWebEngines[1]
                        },
                    ).apply()
            }

            return prefs.getString(WEB_ENGINE, SupportedWebEngines[0])!!
        }
        set(value) {
            prefs.edit().putString(WEB_ENGINE, value).apply()
        }

    var blockAutomaticRedirects: Boolean
        get() = prefs.getBoolean(BLOCK_AUTOMATIC_REDIRECTS_KEY, true)
        set(value) {
            prefs.edit().putBoolean(BLOCK_AUTOMATIC_REDIRECTS_KEY, value).apply()
        }

    var keepToolbarOpen: Boolean
        get() = prefs.getBoolean(KEEP_TOOLBAR_OPEN_KEY, true)
        set(value) {
            prefs.edit().putBoolean(KEEP_TOOLBAR_OPEN_KEY, value).apply()
        }

    var restoreTabsOnStartup: Boolean
        get() = prefs.getBoolean(RESTORE_TABS_ON_STARTUP_KEY, false)
        set(value) {
            prefs.edit().putBoolean(RESTORE_TABS_ON_STARTUP_KEY, value).apply()
        }

    var initialBookmarksSuggestionsLoaded: Boolean
        get() = prefs.getBoolean(INITIAL_BOOKMARKS_SUGGESTIONS_LOADED, false)
        set(value) {
            prefs.edit().putBoolean(INITIAL_BOOKMARKS_SUGGESTIONS_LOADED, value).apply()
        }

    var appWebExtensionVersion: Int
        get() = prefs.getInt(APP_WEB_EXTENSION_VERSION_KEY, 0)
        set(value) {
            prefs.edit().putInt(APP_WEB_EXTENSION_VERSION_KEY, value).apply()
        }

    var userAgentString = ObservableOptStringPreference(prefs, null, USER_AGENT_PREF_KEY)
}
