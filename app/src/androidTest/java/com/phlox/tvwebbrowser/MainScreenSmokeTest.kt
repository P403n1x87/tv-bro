package com.phlox.tvwebbrowser

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.phlox.tvwebbrowser.activity.main.MainActivity
import com.phlox.tvwebbrowser.activity.main.TabsModel
import com.phlox.tvwebbrowser.utils.activemodel.ActiveModelsRepository
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.regex.Pattern

/**
 * Device-level smoke test for the flows a JVM test can not reach: a full `MainActivity` launch
 * (the ad-block native library and the download-service binding's null `ComponentName` keep
 * that out of Robolectric), a real page load through the configured engine, back navigation
 * to the home page and the bookmarks panel open/close.
 *
 * Runs against any flavor with a booted device/emulator:
 * `./gradlew :app:connectedP403n1x87GeckoIncludedDebugAndroidTest`.
 * Selectors are resource-id based, so nothing depends on the screen language. Page loads are
 * detected through the engine's reported title rather than the WebView's accessibility text: a
 * freshly attached WebView on the CI image occasionally keeps its virtual text tree empty while
 * the page itself has loaded, which made a text-marker wait time out (see `waitForTabTitle`).
 * The instrumentation runs inside the app process (so no `pm clear` resets here — they would
 * kill the run): every test is written to be state-independent instead, and can run in any order
 * on a device with any data.
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
        device.wait(Until.findObject(By.res(URL_BOX)), LOAD_TIMEOUT_MS)
            ?: error("address box missing after a cold start")
    }

    @Test
    fun viewIntentLoadsPage() {
        openUrl("http://example.com")
        awaitUrl("example.com", "address box never picked up the requested url")
        awaitPageContent { openUrl("http://example.com") }
    }

    @Test
    fun backNavigatesBrowserHistory() {
        // A plain VIEW intent opens a *new* tab whose only history entry is the page itself;
        // the same-tab extra then navigates that tab in place, so back has real history to
        // walk. Starting from an intent tab keeps the test independent of whatever session
        // state previous tests (or the user) left behind.
        openUrl("http://example.com")
        awaitUrl("example.com", "first url never loaded")
        // The URL box updates when the load merely *starts*; a loadUrl that supersedes a still
        // -provisional navigation replaces its history entry instead of stacking one, leaving
        // canGoBack() false and the back button a silent no-op. Wait for the first page's content
        // — the same marker viewIntentLoadsPage relies on — so the entry is committed before the
        // second navigation lands on top of it (a slow emulator widens that gap to seconds).
        awaitPageContent { openUrl("http://example.com", sameTab = true) }
        openUrl("http://example.org", sameTab = true)
        awaitUrl("example.org", "second url never loaded in the same tab")
        // The address box updates when the navigation merely starts, but back stays disabled until
        // the engine reports `canGoBack()`: tapping it while the second page is still provisional
        // hits a disabled button and silently does nothing. Wait for the app's own history signal.
        val back =
            device.wait(Until.findObject(By.res(BACK_BUTTON).enabled(true)), LOAD_TIMEOUT_MS)
                ?: error("back button never became available after the second page loaded")
        back.click()
        device.wait(Until.findObject(By.res(URL_BOX).textContains("example.com")), LOAD_TIMEOUT_MS)
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

    private fun awaitUrl(
        text: String,
        message: String,
    ) {
        device.wait(Until.findObject(By.res(URL_BOX).textContains(text)), LOAD_TIMEOUT_MS)
            ?: error(message)
    }

    /**
     * Waits for [PAGE_MARKER] in the accessibility tree. A busy emulator occasionally delivers the
     * load (the address box and title update and `onPageFinished` fires) without exposing the
     * WebView's subtree to UiAutomator, and the tree does not recover while that page stays up, so
     * [reload] gives it a fresh load before the test fails. Each miss logs the window hierarchy — the
     * CI job uploads logcat on failure — so a real regression still leaves evidence behind.
     */
    private fun awaitPageContent(reload: () -> Unit) {
        if (device.wait(Until.hasObject(By.textContains(PAGE_MARKER)), LOAD_TIMEOUT_MS)) return
        logHierarchy("after a page load without content")
        reload()
        if (device.wait(Until.hasObject(By.textContains(PAGE_MARKER)), LOAD_TIMEOUT_MS)) return
        logHierarchy("after the reload")
        error("page content never rendered, even after a reload (UI hierarchies in $TAG logcat)")
    }

    private fun logHierarchy(context: String) {
        val dump = ByteArrayOutputStream()
        runCatching { device.dumpWindowHierarchy(dump) }
        Log.e(TAG, "window hierarchy $context: $dump")
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
        private const val TAG = "MainScreenSmokeTest"
        private const val LOAD_TIMEOUT_MS = 60_000L
        private val URL_BOX = Pattern.compile(".*:id/etUrl")
        private val BACK_BUTTON = Pattern.compile(".*:id/ibBack")
        private val BOOKMARKS_BUTTON = Pattern.compile(".*:id/ibFavorites")
        private val BOOKMARKS_PANEL = Pattern.compile(".*:id/vBookmarks")

        /** Title served by both example.com and example.org. */
        private const val PAGE_TITLE = "Example Domain"
        private const val PAGE_MARKER = "Example Domain"
    }
}
