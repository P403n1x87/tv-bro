package com.phlox.tvwebbrowser.activity.main

import android.os.Build
import com.phlox.tvwebbrowser.AppContext
import com.phlox.tvwebbrowser.BuildConfig
import com.phlox.tvwebbrowser.Config
import com.phlox.tvwebbrowser.utils.UpdateChecker
import com.phlox.tvwebbrowser.utils.activemodel.ActiveModel
import com.phlox.tvwebbrowser.utils.sameDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * The screen side of the auto-update flow: it renders the dialogs and performs the platform
 * actions, so [AutoUpdateModel] never touches Activity/dialog classes itself. Implemented by the
 * main screen.
 */
interface AutoUpdateHost {
    /** True while the screen is going away; no dialog or follow-up action may start after that. */
    val isScreenFinishing: Boolean

    /** True when the app is allowed to install the downloaded APK. */
    fun canInstallPackages(): Boolean

    /** Renders the changelog dialog; [onDownload]/[onSettings] are its action buttons. */
    fun showUpdateDialog(
        onDownload: () -> Unit,
        onSettings: () -> Unit,
    )

    /** Renders the "allow installing from unknown sources" dialog; [onConfirm] runs on OK. */
    fun showUnknownSourcesDialog(onConfirm: () -> Unit)

    /**
     * Opens the system screen that grants the install permission; false when it could not be
     * opened (the host reports the failure itself).
     */
    fun openInstallPermissionSettings(): Boolean

    /** Opens the settings screen. */
    fun showSettings()

    /** Downloads and installs the pending update. */
    fun downloadUpdate()
}

class AutoUpdateModel(
    val config: Config = AppContext.provideConfig(),
) : ActiveModel() {
    var needToShowUpdateDlgAgain: Boolean = false
    val updateChecker = UpdateChecker(BuildConfig.VERSION_NAME)
    var lastUpdateNotificationTime: Calendar
    var needAutoCheckUpdates: Boolean
        get() = config.autoCheckUpdates && BuildConfig.BUILT_IN_AUTO_UPDATE
        set(value) {
            config.autoCheckUpdates = value
        }

    init {
        lastUpdateNotificationTime = config.lastUpdateUserNotificationTime
            ?.let { stored -> Calendar.getInstance().apply { timeInMillis = stored } }
            ?: Calendar.getInstance()
    }

    fun checkUpdate(
        force: Boolean,
        onDoneCallback: () -> Unit,
    ) = modelScope.launch(Dispatchers.Main) {
        if (updateChecker.versionCheckResult == null || force) {
            launch(Dispatchers.IO) {
                try {
                    updateChecker.checkGitHubReleases(
                        BuildConfig.UPDATE_REPO,
                        arrayOf(config.updateChannel),
                        BuildConfig.FLAVOR_appstore,
                        BuildConfig.FLAVOR_webengine,
                        Build.SUPPORTED_ABIS[0],
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.join()
        }
        onDoneCallback()
    }

    /**
     * Offers [host] the update, at most once a day unless [force]. The model only decides *that*
     * there is something to show; the host renders the dialogs and runs the platform actions.
     */
    fun showUpdateDialogIfNeeded(
        host: AutoUpdateHost,
        force: Boolean = false,
    ) {
        val now = Calendar.getInstance()
        if (lastUpdateNotificationTime.sameDay(now) && !force) {
            return
        }
        if (!updateChecker.hasUpdate()) {
            throw IllegalStateException()
        }
        lastUpdateNotificationTime = now
        config.lastUpdateUserNotificationTime = lastUpdateNotificationTime.timeInMillis

        host.showUpdateDialog(
            onDownload = download@{
                if (host.isScreenFinishing) return@download
                updateChecker.versionCheckResult ?: return@download

                if (host.canInstallPackages()) {
                    host.downloadUpdate()
                } else {
                    host.showUnknownSourcesDialog {
                        if (host.openInstallPermissionSettings()) {
                            needToShowUpdateDlgAgain = true
                        }
                    }
                }
            },
            onSettings = {
                if (!host.isScreenFinishing) {
                    host.showSettings()
                }
            },
        )
    }

    fun saveAutoCheckUpdates(need: Boolean) {
        config.autoCheckUpdates = need
    }

    /**
     * True when the user has update checking enabled and the last check found something newer. Drives
     * the toolbar settings badge and the marked About category, so they match the About page rows.
     */
    fun isUpdateAvailable(): Boolean = needAutoCheckUpdates && updateChecker.hasUpdate()
}
