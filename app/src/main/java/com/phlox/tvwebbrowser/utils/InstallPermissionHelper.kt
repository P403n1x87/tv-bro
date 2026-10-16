package com.phlox.tvwebbrowser.utils

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import com.phlox.tvwebbrowser.BuildConfig
import com.phlox.tvwebbrowser.R
import java.io.File

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

    /**
     * Hands the downloaded APK at [filePath] to the system installer.
     *
     * From API 30 the download goes through MediaStore, so [filePath] is already a `content://` Uri;
     * below that it is a filesystem path the installer can only read through this app's FileProvider.
     * A Service cannot start an activity without `NEW_TASK`, so it is added when [context] is not an
     * Activity. Failures are reported here.
     */
    fun launchInstaller(context: Context, filePath: String) {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Uri.parse(filePath)
        } else {
            FileProvider.getUriForFile(context, context.packageName + ".provider", File(filePath))
        }
        val install = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, MimeTypeMap.getSingleton().getMimeTypeFromExtension("apk"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        try {
            context.startActivity(install)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, R.string.error, Toast.LENGTH_SHORT).show()
        }
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
