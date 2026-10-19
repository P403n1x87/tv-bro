package com.phlox.tvwebbrowser.activity.main

import com.phlox.tvwebbrowser.AppContext
import com.phlox.tvwebbrowser.Config
import com.phlox.tvwebbrowser.config.PlaybackConfig
import com.phlox.tvwebbrowser.domain.HomePagePolicy
import com.phlox.tvwebbrowser.utils.activemodel.ActiveModel
import com.phlox.tvwebbrowser.utils.observable.ObservableValue

class SettingsModel(
    private val config: Config = AppContext.provideConfig(),
) : ActiveModel() {
    companion object {
        val TAG = SettingsModel::class.java.simpleName

        // See Config.TV_BRO_UA_PREFIX: detection of legacy stored UA prefs, not emitted anywhere.
        const val TV_BRO_UA_PREFIX = "TV Bro/1.0 "
    }

    // Home page settings
    var homePage by config::homePage
    var homePageMode by config::homePageMode

    // User agent strings configuration
    val userAgentStringTitles =
        arrayOf(
            "Default (recommended)",
            "Chrome (Desktop)",
            "Chrome (Mobile)",
            "Firefox (Desktop)",
            "Firefox (Mobile)",
            "Edge (Desktop)",
            "Custom",
        )
    val uaStrings =
        listOf(
            "",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Linux; Android 16; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Mobile Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:147.0) Gecko/20100101 Firefox/147.0",
            "Mozilla/5.0 (Android 16; Mobile; rv:147.0) Gecko/125.0 Firefox/147.0",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36 Edg/145.0.0.0",
            "",
        )

    var keepScreenOn =
        object : ObservableValue<Boolean>(config.keepScreenOn) {
            override var value: Boolean = config.keepScreenOn
                set(value) {
                    config.keepScreenOn = value
                    field = value
                    notifyObservers()
                }
                get() = config.keepScreenOn
        } // makeObservable(config::keepScreenOn)

    // One-shot hints the user dismissed for good ("don't show again"): the screens flip them here,
    // so no view writes those Config flags directly.
    var incognitoModeHintSuppress by config::incognitoModeHintSuppress
    var directNavigationModeHintSuppress by config::directNavigationModeHintSuppress

    fun setSearchEngineURL(url: String) {
        config.searchEngineURL.value = url
        if (homePageMode == Config.HomePageMode.SEARCH_ENGINE) {
            homePage = HomePagePolicy.searchEngineHomePage(url)
        }
    }

    fun setHomePageProperties(
        homePageMode: Config.HomePageMode,
        customHomePageUrl: String?,
    ) {
        this.homePageMode = homePageMode
        homePage =
            HomePagePolicy.homePageUrlFor(homePageMode, customHomePageUrl, config.searchEngineURL.value)
    }

    // Browsing

    /** The configured search engine URL; the built-in default while unset. */
    val searchEngineURL: String get() = config.searchEngineURL.value

    /** Observable so the host can push a new user agent string to every open tab. */
    val userAgentString: ObservableValue<String?> get() = config.userAgentString

    var restoreTabsOnStartup: Boolean
        get() = config.restoreTabsOnStartup
        set(value) {
            config.restoreTabsOnStartup = value
        }

    fun setUserAgentString(userAgent: String?) {
        config.userAgentString.value = userAgent
    }

    // Appearance
    val theme: ObservableValue<Config.Theme> get() = config.theme

    fun setTheme(theme: Config.Theme) {
        config.theme.value = theme
    }

    var algorithmicDarkening: Boolean
        get() = config.engineTuningConfig.webviewUseAlgorithmicDarkeningWithDarkUiMode
        set(value) {
            config.engineTuningConfig.webviewUseAlgorithmicDarkeningWithDarkUiMode = value
        }

    /** Observable because the changed value restyles the toolbar the host is showing right now. */
    var keepToolbarOpen =
        object : ObservableValue<Boolean>(config.keepToolbarOpen) {
            override var value: Boolean = config.keepToolbarOpen
                set(value) {
                    config.keepToolbarOpen = value
                    field = value
                    notifyObservers()
                }
                get() = config.keepToolbarOpen
        }

    // Playback
    var allowAutoplayMedia: Boolean
        get() = config.playbackConfig.allowAutoplayMedia
        set(value) {
            config.playbackConfig.allowAutoplayMedia = value
        }

    var playerKind: PlaybackConfig.PlayerKind
        get() = config.playbackConfig.playerKind
        set(value) {
            config.playbackConfig.playerKind = value
        }

    // Controls
    var disableMotionAxesDpadNavigation: Boolean
        get() = config.cursorConfig.disableMotionAxesDpadNavigation
        set(value) {
            config.cursorConfig.disableMotionAxesDpadNavigation = value
        }

    var cursorMaxSpeedPercent: Int
        get() = config.cursorConfig.cursorMaxSpeedPercent
        set(value) {
            config.cursorConfig.cursorMaxSpeedPercent = value
        }

    var cursorAccelerationPercent: Int
        get() = config.cursorConfig.cursorAccelerationPercent
        set(value) {
            config.cursorConfig.cursorAccelerationPercent = value
        }

    // Advanced
    var webEngine: String
        get() = config.webEngine
        set(value) {
            config.webEngine = value
        }

    var webEngineDebug: Boolean
        get() = config.engineTuningConfig.webEngineDebug
        set(value) {
            config.engineTuningConfig.webEngineDebug = value
        }

    // About
    val incognitoMode: Boolean get() = config.incognitoMode
}
