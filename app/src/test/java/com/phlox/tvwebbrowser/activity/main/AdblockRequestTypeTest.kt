package com.phlox.tvwebbrowser.activity.main

import android.app.Application
import android.net.Uri
import com.brave.adblock.AdBlockClient.FilterOption
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as RobolectricConfig

/**
 * Tests for [AdblockRequestType], the pure request-to-filter mapping behind `AdblockModel.isAd`.
 * Runs without the native engine, which is why the policy lives outside the model.
 */
@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [26], application = Application::class)
class AdblockRequestTypeTest {
    private fun option(
        url: String?,
        type: String?,
    ) = AdblockRequestType.filterOptionFor(url?.let { Uri.parse(it) }, type)

    @Test
    fun imageContentTypesAreImages() {
        assertEquals(FilterOption.IMAGE, option(null, "image"))
        assertEquals(FilterOption.IMAGE, option(null, "image/png"))
    }

    @Test
    fun styleContentTypesAreCss() {
        assertEquals(FilterOption.CSS, option(null, "style"))
        assertEquals(FilterOption.CSS, option(null, "text/css"))
    }

    @Test
    fun scriptContentTypesAreScripts() {
        assertEquals(FilterOption.SCRIPT, option(null, "script"))
        assertEquals(FilterOption.SCRIPT, option(null, "application/javascript"))
    }

    @Test
    fun videoContentTypesAreObjects() {
        assertEquals(FilterOption.OBJECT, option(null, "video/mp4"))
    }

    @Test
    fun urlExtensionsAreUsedWhenTheTypeIsUnknown() {
        assertEquals(FilterOption.CSS, option("https://cdn.example/site.css", null))
        assertEquals(FilterOption.SCRIPT, option("https://cdn.example/app.js", null))
        assertEquals(FilterOption.IMAGE, option("https://cdn.example/logo.png", null))
        assertEquals(FilterOption.OBJECT, option("https://cdn.example/clip.mp4", null))
    }

    @Test
    fun theContentTypeWinsOverTheExtension() {
        assertEquals(FilterOption.SCRIPT, option("https://cdn.example/site.css", "application/javascript"))
    }

    @Test
    fun plainRequestsAreUnknown() {
        assertEquals(FilterOption.UNKNOWN, option("https://example.com/page.html", "text/html"))
        assertEquals(FilterOption.UNKNOWN, option(null, "text/html"))
        assertEquals(FilterOption.UNKNOWN, option(null, null))
    }
}
