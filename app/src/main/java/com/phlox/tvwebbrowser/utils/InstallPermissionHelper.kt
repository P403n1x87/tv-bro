package com.phlox.tvwebbrowser.utils

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.phlox.tvwebbrowser.BuildConfig
import com.phlox.tvwebbrowser.R

/**
 * The "the app is about to install an APK" permission flow, shared by the screens and the download
 * service: the availability check, the "enable unknown sources" dialog and the jump to the system
 * screen that grants it.
 *
 * The caller still owns what happens once permission is granted — the main screen re-offers the
 * update, the downloads screen opens the installer.
 */
object InstallPermissionHelper {
    /** Request code for [openUnknownSourcesSettings]; callers that care handle the result. */
    const val REQUEST_CODE_UNKNOWN_APP_SOURCES = 10007

    /**
     * True when the app may install a package itself.
     *
     * On API 26+ this is the per-app "install unknown apps" grant; below that the global
     * "unknown sources" setting.
     */
    fun canInstallPackages(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            Settings.Secure.getInt(context.contentResolver, Settings.Secure.INSTALL_NON_MARKET_APPS) == 1
        }

    /**
     * Opens the system screen that grants install permission. Returns false when it could not be
     * opened; the failure toast is shown here.
     */
    fun openUnknownSourcesSettings(
        activity: Activity,
        requestCode: Int = REQUEST_CODE_UNKNOWN_APP_SOURCES
    ): Boolean {
        return try {
            activity.startActivityForResult(unknownSourcesIntent(), requestCode)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(activity, R.string.error, Toast.LENGTH_SHORT).show()
            false
        }
    }

    /** Renders the "turn on unknown sources" dialog; [onConfirm] runs on OK. */
    fun showEnableUnknownSourcesDialog(activity: Activity, onConfirm: () -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.app_name)
            .setMessage(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                R.string.turn_on_unknown_sources_for_app else R.string.turn_on_unknown_sources)
            .setPositiveButton(android.R.string.ok) { _, _ -> onConfirm() }
            .setNegativeButton(android.R.string.cancel) { _, _ -> }
            .show()
    }

    private fun unknownSourcesIntent(): Intent {
        val intent = Intent()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.action = Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES
            intent.data = Uri.parse("package:${BuildConfig.APPLICATION_ID}")
        } else {
            intent.action = Settings.ACTION_SECURITY_SETTINGS
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
    }
}
