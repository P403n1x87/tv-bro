package com.phlox.tvwebbrowser.activity.main

import android.app.Application
import android.content.Context
import com.phlox.tvwebbrowser.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config as RobolectricConfig

/**
 * Tests for [SettingsModel]'s derived behaviour: the home-page modes and the settings that are
 * written straight through to [Config].
 */
@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [26], application = Application::class)
class SettingsModelTest {

    private lateinit var config: Config
    private lateinit var model: SettingsModel

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        config = Config(app.getSharedPreferences("settings_test", Context.MODE_PRIVATE))
        model = SettingsModel(config)
    }

    @Test
    fun customModeUsesTheGivenUrl() {
        model.setHomePageProperties(Config.HomePageMode.CUSTOM, "https://custom.example/")

        assertEquals(Config.HomePageMode.CUSTOM, model.homePageMode)
        assertEquals("https://custom.example/", model.homePage)
    }

    @Test
    fun customModeWithoutAUrlFallsBackToTheHomeAlias() {
        model.setHomePageProperties(Config.HomePageMode.CUSTOM, null)

        assertEquals(Config.HOME_URL_ALIAS, model.homePage)
    }

    @Test
    fun homePageModeFallsBackToTheHomeAlias() {
        model.setHomePageProperties(Config.HomePageMode.HOME_PAGE, "https://ignored.example/")

        assertEquals(Config.HomePageMode.HOME_PAGE, model.homePageMode)
        assertEquals(Config.HOME_URL_ALIAS, model.homePage)
    }

    @Test
    fun searchEngineModeUsesTheConfiguredSearchEngine() {
        model.setHomePageProperties(Config.HomePageMode.SEARCH_ENGINE, null)

        // Only the search engine's origin becomes the home page ("https://www.google.com/search?q=..."
        // is stripped down to its origin), which is what the home page needs.
        assertEquals("https://www.google.com", model.homePage)
    }

    @Test
    fun theUserAgentIsWrittenThrough() {
        model.setUserAgentString("custom-agent")

        assertEquals("custom-agent", model.userAgentString.value)
        assertEquals("custom-agent", config.userAgentString.value)
    }

    @Test
    fun theThemeIsWrittenThrough() {
        model.setTheme(Config.Theme.BLACK)

        assertEquals(Config.Theme.BLACK, model.theme.value)
        assertEquals(Config.Theme.BLACK, config.theme.value)
    }

    @Test
    fun keepScreenOnRoundTripsThroughConfig() {
        model.keepScreenOn.value = true

        assertTrue(config.keepScreenOn)
    }

    @Test
    fun algorithmicDarkeningRoundTripsThroughConfig() {
        model.algorithmicDarkening = true

        assertTrue(config.webviewUseAlgorithmicDarkeningWithDarkUiMode)
    }
}
