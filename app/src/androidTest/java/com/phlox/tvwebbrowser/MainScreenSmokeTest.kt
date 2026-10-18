package com.phlox.tvwebbrowser

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/**
 * Device-level smoke test for the flows a JVM test can not reach: a full `MainActivity` launch
 * (the ad-block native library and the download-service binding's null `ComponentName` keep
 * that out of Robolectric), a real page load through the configured engine, back navigation
 * to the home page and the bookmarks panel open/close.
 *
 * Runs against any flavor with a booted device/emulator:
 * `./gradlew :app:connectedP403n1x87GeckoIncludedDebugAndroidTest`.
 * Selectors are resource-id based, so nothing depends on the screen language except the
 * fetched page content itself. The instrumentation runs inside the app process (so no
 * `pm clear` resets here — they would kill the run): every test is written to be
 * state-independent instead, and can run in any order on a device with any data.
 */
@RunWith(AndroidJUnit4::class)
class MainScreenSmokeTest {
    private lateinit var device: UiDevice

    private val targetContext
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val targetPkg
        get() = targetContext.packageName

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        // Android 13+ can show the notification-permission prompt on first launch; grant it up
        // front so the tests see the browser, not the dialog. Fails silently where the
        // permission is not declared (or the API level predates it).
        try {
            device.executeShellCommand("pm grant $targetPkg android.permission.POST_NOTIFICATIONS")
        } catch (ignored: Exception) {
            // pre-T devices, permission not requested, or already granted: nothing to do
        }
    }

    @Test
    fun coldStartShowsToolbar() {
        launch()
        device.wait(Until.findObject(By.res(URL_BOX)), 60_000)
            ?: error("address box missing after a cold start")
    }

    @Test
    fun viewIntentLoadsPage() {
        openUrl("http://example.com")
        device.wait(Until.findObject(By.res(URL_BOX).textContains("example.com")), 60_000)
            ?: error("address box never picked up the requested url")
        device.wait(Until.findObject(By.textContains(PAGE_MARKER)), 60_000)
            ?: error("page content never rendered")
    }

    @Test
    fun backNavigatesBrowserHistory() {
        // A plain VIEW intent opens a *new* tab whose only history entry is the page itself;
        // the same-tab extra then navigates that tab in place, so back has real history to
        // walk. Starting from an intent tab keeps the test independent of whatever session
        // state previous tests (or the user) left behind.
        openUrl("http://example.com")
        device.wait(Until.findObject(By.res(URL_BOX).textContains("example.com")), 60_000)
            ?: error("first url never loaded")
        openUrl("http://example.org", sameTab = true)
        device.wait(Until.findObject(By.res(URL_BOX).textContains("example.org")), 60_000)
            ?: error("second url never loaded in the same tab")
        val back =
            device.wait(Until.findObject(By.res(BACK_BUTTON)), 10_000)
                ?: error("back button missing from the toolbar")
        back.click()
        device.wait(Until.findObject(By.res(URL_BOX).textContains("example.com")), 60_000)
            ?: error("back did not return to the previous page")
    }

    @Test
    fun favoritesPanelOpensAndCloses() {
        launch()
        val bookmarks =
            device.wait(Until.findObject(By.res(BOOKMARKS_BUTTON)), 30_000)
                ?: error("bookmarks button missing from the toolbar")
        bookmarks.click()
        // The panel root, not the "Add this page" row: that row is hidden on the home page.
        device.wait(Until.findObject(By.res(BOOKMARKS_PANEL)), 15_000)
            ?: error("bookmarks panel did not open")
        device.pressBack()
        check(device.wait(Until.gone(By.res(BOOKMARKS_PANEL)), 15_000)) { "bookmarks panel did not close on back" }
    }

    private fun launch() {
        val intent =
            checkNotNull(targetContext.packageManager.getLaunchIntentForPackage(targetPkg)) {
                "no launch intent for $targetPkg"
            }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        targetContext.startActivity(intent)
    }

    private fun openUrl(
        url: String,
        sameTab: Boolean = false,
    ) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(targetPkg)
        if (sameTab) {
            // Contract constant of IntentHandler: navigate the current tab instead of a new one.
            intent.putExtra("com.phlox.tvwebbrowser.EXTRA_OPEN_IN_SAME_TAB", true)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        targetContext.startActivity(intent)
    }

    companion object {
        private val URL_BOX = Pattern.compile(".*:id/etUrl")
        private val BACK_BUTTON = Pattern.compile(".*:id/ibBack")
        private val BOOKMARKS_BUTTON = Pattern.compile(".*:id/ibFavorites")
        private val BOOKMARKS_PANEL = Pattern.compile(".*:id/vBookmarks")

        /** First paragraph of example.com — proof that engine content reaches the view tree. */
        private const val PAGE_MARKER = "This domain is for use in documentation"
    }
}
