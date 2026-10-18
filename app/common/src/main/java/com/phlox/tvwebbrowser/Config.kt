package com.phlox.tvwebbrowser

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Build
import com.phlox.tvwebbrowser.config.CursorConfig
import com.phlox.tvwebbrowser.utils.Utils
import com.phlox.tvwebbrowser.utils.observable.ObservableValue

/**
 * Typed access to the stored settings: a member mirrors a preference key, decodes the stored value
 * and writes it back — nothing else. Decisions over those values belong in `domain/` (app module)
 * or in [SearchEngines] (shared with the Gecko module); the one migration still in here — the
 * legacy "search engine as home page" flag — is storage's own job.
 */
class Config(
    val prefs: SharedPreferences,
    private val installedByApk: () -> Boolean = { Utils.isInstalledByAPK(AppContext.get()) },
) {
    val cursorConfig: CursorConfig = CursorConfig(prefs)

    companion object {
        const val SEARCH_ENGINE_URL_PREF_KEY = "search_engine_url"
        const val SEARCH_ENGINE_AS_HOME_PAGE_KEY = "search_engine_as_home_page"
        const val HOME_PAGE_KEY = "home_page"
        const val USER_AGENT_PREF_KEY = "user_agent"
        const val THEME_KEY = "theme"
        const val LAST_UPDATE_USER_NOTIFICATION_TIME_KEY = "last_update_notif"
        const val AUTO_CHECK_UPDATES_KEY = "auto_check_updates"
        const val UPDATE_CHANNEL_KEY = "update_channel"
        const val TV_BRO_UA_PREFIX = "TV Bro/1.0 "
        const val HOME_URL_ALIAS = "about:home"
        const val KEEP_SCREEN_ON_KEY = "keep_screen_on"
        const val INCOGNITO_MODE_KEY = "incognito_mode"
        const val INCOGNITO_MODE_HINT_SUPPRESS_KEY = "incognito_mode_hint_suppress"
        const val DIRECT_NAVIGATION_MODE_HINT_SUPPRESS_KEY = "direct_navigation_mode_hint_suppress"
        const val HOME_PAGE_MODE = "home_page_mode"
        const val WEB_ENGINE = "web_engine"
        const val ALLOW_AUTOPLAY_MEDIA = "allow_autoplay_media"
        const val WEBVIEW_USE_ALGORITHMIC_DARKENING_WITH_DARK_UI_MODE_KEY =
            "webview_use_algorithmic_darkening_with_dark_ui_mode"
        const val WEB_ENGINE_DEBUG_KEY = "web_engine_debug"
        const val PLAYER_KIND_KEY = "player_kind"
        const val BLOCK_AUTOMATIC_REDIRECTS_KEY = "block_automatic_redirects"
        const val KEEP_TOOLBAR_OPEN_KEY = "keep_toolbar_open"
        const val RESTORE_TABS_ON_STARTUP_KEY = "restore_tabs_on_startup"

        // const val HOME_PAGE_VERSION_EXTRACTED = "home_page_version_extracted"
        const val INITIAL_BOOKMARKS_SUGGESTIONS_LOADED = "initial_bookmarks_suggestions_loaded"
        const val ADBLOCK_ENABLED_PREF_KEY = "adblock_enabled"
        const val ADBLOCK_LAST_UPDATE_LIST_KEY = "adblock_last_update"
        const val ADBLOCK_LIST_URL_KEY = "adblock_list_url"
        const val APP_WEB_EXTENSION_VERSION_KEY = "app_web_extension_version"
        const val NOTIFICATION_ABOUT_ENGINE_CHANGE_SHOWN_KEY = "notification_about_engine_change_shown"
        const val APP_VERSION_CODE_MARK_KEY = "app_version_code_mark"

        const val ENGINE_GECKO_VIEW = "GeckoView"
        const val ENGINE_WEB_VIEW = "WebView"

        /** Engines the app can run, in recommendation order — the [webEngine] default picks from here. */
        val SupportedWebEngines = arrayOf(ENGINE_GECKO_VIEW, ENGINE_WEB_VIEW)

        const val DEFAULT_ADBLOCK_LIST_URL = "https://easylist.to/easylist/easylist.txt"

        const val HOME_PAGE_URL = "https://tvbro.phlox.dev/appcontent/home/"
        // const val HOME_PAGE_URL = "http://10.0.2.2:5000/appcontent/home/"

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

    /** Player a detected stream is handed to. Default is the built-in ExoPlayer. */
    enum class PlayerKind {
        BUILT_IN,
        SYSTEM,
        EXTERNAL,
        ;

        companion object {
            /** Stored ordinal → kind; an unknown value falls back to [BUILT_IN]. */
            fun fromStored(ordinal: Int): PlayerKind = entries.getOrElse(ordinal) { BUILT_IN }
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

    var searchEngineURL = ObservableStringPreference(SearchEngines.urls[0], SEARCH_ENGINE_URL_PREF_KEY)

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

    var allowAutoplayMedia: Boolean
        get() = prefs.getBoolean(ALLOW_AUTOPLAY_MEDIA, false)
        set(value) {
            prefs.edit().putBoolean(ALLOW_AUTOPLAY_MEDIA, value).apply()
        }

    var webviewUseAlgorithmicDarkeningWithDarkUiMode: Boolean
        get() = prefs.getBoolean(WEBVIEW_USE_ALGORITHMIC_DARKENING_WITH_DARK_UI_MODE_KEY, true)
        set(value) {
            prefs.edit().putBoolean(WEBVIEW_USE_ALGORITHMIC_DARKENING_WITH_DARK_UI_MODE_KEY, value).apply()
        }

    var webEngineDebug: Boolean
        get() = prefs.getBoolean(WEB_ENGINE_DEBUG_KEY, false)
        set(value) {
            prefs.edit().putBoolean(WEB_ENGINE_DEBUG_KEY, value).apply()
        }

    var playerKind: PlayerKind
        get() = PlayerKind.fromStored(prefs.getInt(PLAYER_KIND_KEY, 0))
        set(value) {
            prefs.edit().putInt(PLAYER_KIND_KEY, value.ordinal).apply()
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

    /*var homePageVersionExtracted: Int
        get() = prefs.getInt(HOME_PAGE_VERSION_EXTRACTED, 0)
        set(value) {
            prefs.edit().putInt(HOME_PAGE_VERSION_EXTRACTED, value).apply()
        }*/

    var initialBookmarksSuggestionsLoaded: Boolean
        get() = prefs.getBoolean(INITIAL_BOOKMARKS_SUGGESTIONS_LOADED, false)
        set(value) {
            prefs.edit().putBoolean(INITIAL_BOOKMARKS_SUGGESTIONS_LOADED, value).apply()
        }

    var userAgentString = ObservableOptStringPreference(null, USER_AGENT_PREF_KEY)

    var adBlockEnabled: Boolean = prefs.getBoolean(ADBLOCK_ENABLED_PREF_KEY, true)
        set(value) {
            field = value
            prefs.edit().putBoolean(ADBLOCK_ENABLED_PREF_KEY, field).apply()
        }

    var adBlockListURL = ObservableStringPreference(DEFAULT_ADBLOCK_LIST_URL, ADBLOCK_LIST_URL_KEY)

    var adBlockListLastUpdate: Long
        get() = prefs.getLong(ADBLOCK_LAST_UPDATE_LIST_KEY, 0)
        set(value) {
            prefs.edit().putLong(ADBLOCK_LAST_UPDATE_LIST_KEY, value).apply()
        }

    var appWebExtensionVersion: Int
        get() = prefs.getInt(APP_WEB_EXTENSION_VERSION_KEY, 0)
        set(value) {
            prefs.edit().putInt(APP_WEB_EXTENSION_VERSION_KEY, value).apply()
        }

    // 0 - not shown, any other value - app version code where notification already shown (to show only once after update)
    var notificationAboutEngineChangeShown: Int
        get() = prefs.getInt(NOTIFICATION_ABOUT_ENGINE_CHANGE_SHOWN_KEY, 0)
        set(value) {
            prefs.edit().putInt(NOTIFICATION_ABOUT_ENGINE_CHANGE_SHOWN_KEY, value).apply()
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

    var appVersionCodeMark: Int
        get() = prefs.getInt(APP_VERSION_CODE_MARK_KEY, 0)
        set(value) {
            prefs.edit().putInt(APP_VERSION_CODE_MARK_KEY, value).apply()
        }

    inner class ObservableStringPreference(
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

    inner class ObservableOptStringPreference(
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
}
