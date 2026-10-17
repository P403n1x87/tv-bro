package com.phlox.tvwebbrowser.activity.main

import android.app.Application
import android.content.Context
import com.phlox.tvwebbrowser.AppContext
import com.phlox.tvwebbrowser.Config
import com.phlox.tvwebbrowser.utils.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config as RobolectricConfig
import java.util.Calendar

/**
 * Tests for [AutoUpdateModel]'s host seam: the model decides *whether* and *what* to offer and the
 * platform effects run on a fake [AutoUpdateHost], so the whole flow is exercised without an
 * Activity.
 */
@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [26], application = Application::class)
class AutoUpdateModelTest {

    private lateinit var model: AutoUpdateModel
    private lateinit var host: FakeHost
    private lateinit var config: Config

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        config = Config(app.getSharedPreferences("autoupdate_test", Context.MODE_PRIVATE))
        // A different instance than the model's, so a test that flips [config] proves the model
        // follows the Config it was handed rather than a global.
        AppContext.init(app, Config(app.getSharedPreferences("autoupdate_global_test", Context.MODE_PRIVATE)))
        model = AutoUpdateModel(config)
        host = FakeHost()
    }

    /** Puts an available update in the checker, leaving the last-offer time alone. */
    private fun putAvailableUpdate() {
        model.updateChecker.versionCheckResult = UpdateChecker.UpdateCheckResult(
            "999.0.0",
            UpdateChecker.CHANNEL_RELEASE,
            "https://example.com/update.apk",
            ArrayList(),
            UpdateChecker.AVAILABLE_CHANNELS,
        )
    }

    /** Puts an available update in the checker and pretends the last offer was yesterday. */
    private fun withAnAvailableUpdate() {
        putAvailableUpdate()
        model.lastUpdateNotificationTime = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    }

    @Test
    fun aFreshInstallCountsAsJustOfferedSoTheFirstOfferWaits() {
        // No notification was ever stored, so the model starts at "now" — the offer must respect
        // the daily limit until the day moves on (only force gets through).
        putAvailableUpdate()

        model.showUpdateDialogIfNeeded(host)

        assertEquals(0, host.updateDialogs)
        model.showUpdateDialogIfNeeded(host, force = true)
        assertEquals(1, host.updateDialogs)
    }



    @Test
    fun theUpdateIsOfferedOncePerDay() {
        withAnAvailableUpdate()

        model.showUpdateDialogIfNeeded(host)
        model.showUpdateDialogIfNeeded(host)

        assertEquals(1, host.updateDialogs)
    }

    @Test
    fun forcingTheOfferIgnoresTheDailyLimit() {
        withAnAvailableUpdate()

        model.showUpdateDialogIfNeeded(host)
        model.showUpdateDialogIfNeeded(host, force = true)

        assertEquals(2, host.updateDialogs)
    }

    @Test
    fun downloadingRunsTheHostInstallWhenThePermissionIsAlreadyGranted() {
        withAnAvailableUpdate()
        host.allowInstall = true
        model.showUpdateDialogIfNeeded(host)

        host.onDownload!!.invoke()

        assertEquals(1, host.downloads)
        assertEquals(0, host.unknownSourcesDialogs)
        assertFalse(model.needToShowUpdateDlgAgain)
    }

    @Test
    fun downloadingAsksForTheInstallPermissionFirstWhenItIsMissing() {
        withAnAvailableUpdate()
        host.allowInstall = false
        model.showUpdateDialogIfNeeded(host)

        host.onDownload!!.invoke()

        assertEquals(0, host.downloads)
        assertEquals(1, host.unknownSourcesDialogs)

        host.onConfirmInstall!!.invoke()

        assertEquals(1, host.permissionOpenCalls)
        assertTrue("granting the permission re-offers the update", model.needToShowUpdateDlgAgain)
    }

    @Test
    fun aFailedPermissionJumpDoesNotReofferTheUpdate() {
        withAnAvailableUpdate()
        host.allowInstall = false
        host.permissionOpened = false
        model.showUpdateDialogIfNeeded(host)

        host.onDownload!!.invoke()
        host.onConfirmInstall!!.invoke()

        assertEquals(1, host.permissionOpenCalls)
        assertFalse(model.needToShowUpdateDlgAgain)
    }

    @Test
    fun theSettingsButtonOpensTheHostSettings() {
        withAnAvailableUpdate()
        model.showUpdateDialogIfNeeded(host)

        host.onSettings!!.invoke()

        assertEquals(1, host.settingsOpenCalls)
    }

    @Test
    fun aFinishingScreenRunsNoFollowUpAction() {
        withAnAvailableUpdate()
        host.isScreenFinishing = true
        model.showUpdateDialogIfNeeded(host)

        host.onDownload!!.invoke()
        host.onSettings!!.invoke()

        assertEquals(0, host.downloads)
        assertEquals(0, host.unknownSourcesDialogs)
        assertEquals(0, host.permissionOpenCalls)
        assertEquals(0, host.settingsOpenCalls)
    }

    @Test
    fun theUpdateBadgeFollowsTheCheckResult() {
        config.autoCheckUpdates = true
        assertFalse(model.isUpdateAvailable())

        withAnAvailableUpdate()

        assertTrue(model.isUpdateAvailable())
    }

    private class FakeHost : AutoUpdateHost {
        override var isScreenFinishing = false
        var allowInstall = true
        var permissionOpened = true

        var updateDialogs = 0
        var unknownSourcesDialogs = 0
        var permissionOpenCalls = 0
        var settingsOpenCalls = 0
        var downloads = 0

        var onDownload: (() -> Unit)? = null
        var onSettings: (() -> Unit)? = null
        var onConfirmInstall: (() -> Unit)? = null

        override fun canInstallPackages(): Boolean = allowInstall

        override fun showUpdateDialog(onDownload: () -> Unit, onSettings: () -> Unit) {
            updateDialogs++
            this.onDownload = onDownload
            this.onSettings = onSettings
        }

        override fun showUnknownSourcesDialog(onConfirm: () -> Unit) {
            unknownSourcesDialogs++
            onConfirmInstall = onConfirm
        }

        override fun openInstallPermissionSettings(): Boolean {
            permissionOpenCalls++
            return permissionOpened
        }

        override fun showSettings() {
            settingsOpenCalls++
        }

        override fun downloadUpdate() {
            downloads++
        }
    }
}
