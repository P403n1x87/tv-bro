package com.phlox.tvwebbrowser.activity.main

import com.phlox.tvwebbrowser.AppContext
import com.phlox.tvwebbrowser.Config
import com.phlox.tvwebbrowser.utils.activemodel.ActiveModel
import com.phlox.tvwebbrowser.utils.observable.ObservableValue

class SettingsModel : ActiveModel() {
    companion object {
        val TAG = SettingsModel::class.java.simpleName
        const val TV_BRO_UA_PREFIX = "TV Bro/1.0 "
    }

    val config = AppContext.provideConfig()

    //Home page settings
    var homePage by config::homePage
    var homePageMode by config::homePageMode
    //User agent strings configuration
    val userAgentStringTitles = arrayOf("Default (recommended)", "Chrome (Desktop)", "Chrome (Mobile)", "Firefox (Desktop)", "Firefox (Mobile)", "Edge (Desktop)", "Custom")
    val uaStrings = listOf("",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Linux; Android 16; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Mobile Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:147.0) Gecko/20100101 Firefox/147.0",
            "Mozilla/5.0 (Android 16; Mobile; rv:147.0) Gecko/125.0 Firefox/147.0",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36 Edg/145.0.0.0",
            "")

    var keepScreenOn = object : ObservableValue<Boolean>(config.keepScreenOn) {
        override var value: Boolean = config.keepScreenOn
            set(value) {
                config.keepScreenOn = value
                field = value
                notifyObservers()
            }
            get() = config.keepScreenOn
    }//makeObservable(config::keepScreenOn)

    fun setSearchEngineURL(url: String) {
        config.searchEngineURL.value = url
        if (homePageMode == Config.HomePageMode.SEARCH_ENGINE) {
            updateHomeAsSearchEngine(url)
        }
    }

    private fun updateHomeAsSearchEngine(url: String) {
        val regexForUrl = """^https?://[^#?/]+""".toRegex()
        val homePageUrl = regexForUrl.find(url)?.value ?: Config.HOME_URL_ALIAS
        homePage = homePageUrl
    }

    fun setHomePageProperties(homePageMode: Config.HomePageMode, customHomePageUrl: String?) {
        this.homePageMode = homePageMode
        when (homePageMode) {
            Config.HomePageMode.SEARCH_ENGINE -> {
                updateHomeAsSearchEngine(config.searchEngineURL.value)
            }
            Config.HomePageMode.HOME_PAGE, Config.HomePageMode.BLANK -> {
                homePage = Config.HOME_URL_ALIAS
            }
            Config.HomePageMode.CUSTOM -> {
                homePage = customHomePageUrl ?: Config.HOME_URL_ALIAS
            }
        }
    }

    //Browsing
    /** The configured search engine URL; the built-in default while unset. */
    val searchEngineURL: String get() = config.searchEngineURL.value

    /** Observable so the host can push a new user agent string to every open tab. */
    val userAgentString: ObservableValue<String?> get() = config.userAgentString

    var restoreTabsOnStartup: Boolean
        get() = config.restoreTabsOnStartup
        set(value) { config.restoreTabsOnStartup = value }

    fun setUserAgentString(userAgent: String?) {
        config.userAgentString.value = userAgent
    }

    //Appearance
    val theme: ObservableValue<Config.Theme> get() = config.theme

    fun setTheme(theme: Config.Theme) {
        config.theme.value = theme
    }

    var algorithmicDarkening: Boolean
        get() = config.webviewUseAlgorithmicDarkeningWithDarkUiMode
        set(value) { config.webviewUseAlgorithmicDarkeningWithDarkUiMode = value }

    /** Observable because the changed value restyles the toolbar the host is showing right now. */
    var keepToolbarOpen = object : ObservableValue<Boolean>(config.keepToolbarOpen) {
        override var value: Boolean = config.keepToolbarOpen
            set(value) {
                config.keepToolbarOpen = value
                field = value
                notifyObservers()
            }
            get() = config.keepToolbarOpen
    }

    //Playback
    var allowAutoplayMedia: Boolean
        get() = config.allowAutoplayMedia
        set(value) { config.allowAutoplayMedia = value }

    var playerKind: Config.PlayerKind
        get() = config.playerKind
        set(value) { config.playerKind = value }

    //Controls
    var disableMotionAxesDpadNavigation: Boolean
        get() = config.disableMotionAxesDpadNavigation
        set(value) { config.disableMotionAxesDpadNavigation = value }

    var cursorMaxSpeedPercent: Int
        get() = config.cursorMaxSpeedPercent
        set(value) { config.cursorMaxSpeedPercent = value }

    var cursorAccelerationPercent: Int
        get() = config.cursorAccelerationPercent
        set(value) { config.cursorAccelerationPercent = value }

    //Advanced
    var webEngine: String
        get() = config.webEngine
        set(value) { config.webEngine = value }

    var webEngineDebug: Boolean
        get() = config.webEngineDebug
        set(value) { config.webEngineDebug = value }

    //About
    val incognitoMode: Boolean get() = config.incognitoMode
}
