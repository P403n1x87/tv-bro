package com.phlox.tvwebbrowser.activity.main

import android.Manifest
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.phlox.tvwebbrowser.model.Download
import com.phlox.tvwebbrowser.service.downloads.DownloadService
import java.io.InputStream

/**
 * The platform side of [DownloadCoordinator]: the screen owns the service binding and the runtime
 * permission calls. Behind an interface so the coordinator's orchestration can be exercised without
 * an Activity. Implemented by the main screen.
 */
interface DownloadHost {
    fun bindDownloadService(connection: ServiceConnection)

    fun unbindDownloadService(connection: ServiceConnection)

    fun hasPermission(permission: String): Boolean

    fun requestPermission(permission: String, requestCode: Int)
}

/**
 * Owns the download use case for the main screen: it keeps the pending request, walks the runtime
 * permissions it needs and hands the request to [DownloadService]. [MainActivity] only forwards the
 * engine's download callbacks and the permission results, so the orchestration does not live in the
 * activity anymore.
 */
class DownloadCoordinator(
    private val host: DownloadHost,
    private val callbacks: Callbacks
) {
    fun interface Callbacks {
        /** The request was handed over; the host brings its UI back. */
        fun onDownloadStarted(fileName: String)
    }

    companion object {
        private val TAG = DownloadCoordinator::class.java.simpleName
        const val MY_PERMISSIONS_REQUEST_EXTERNAL_STORAGE_ACCESS = 10004
        const val MY_PERMISSIONS_REQUEST_POST_NOTIFICATIONS_ACCESS = 10003
    }

    private val uiHandler = Handler(Looper.getMainLooper())
    private var service: DownloadService? = null
    private var pending: Download? = null

    /** Binds the service; call from `onStart`/`onStop` so it is bound exactly while the screen is up. */
    fun bind() {
        host.bindDownloadService(connection)
    }

    fun unbind() {
        host.unbindDownloadService(connection)
        service = null
    }

    fun requestDownload(
        url: String, referer: String, originalDownloadFileName: String, userAgent: String?,
        mimeType: String? = null,
        operationAfterDownload: Download.OperationAfterDownload = Download.OperationAfterDownload.NOP,
        base64BlobData: String? = null, stream: InputStream?, size: Long = 0L
    ) {
        pending = Download(
            url, originalDownloadFileName, null, operationAfterDownload,
            mimeType, referer, userAgent, base64BlobData, stream, size
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
            !host.hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        ) {
            host.requestPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                MY_PERMISSIONS_REQUEST_EXTERNAL_STORAGE_ACCESS
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !host.hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            host.requestPermission(
                Manifest.permission.POST_NOTIFICATIONS,
                MY_PERMISSIONS_REQUEST_POST_NOTIFICATIONS_ACCESS
            )
        } else {
            start()
        }
    }

    fun onPermissionsResult(requestCode: Int, grantResults: IntArray): Boolean {
        return when (requestCode) {
            MY_PERMISSIONS_REQUEST_EXTERNAL_STORAGE_ACCESS,
            MY_PERMISSIONS_REQUEST_POST_NOTIFICATIONS_ACCESS -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    start()
                }
                true
            }

            else -> false
        }
    }

    private fun start() {
        val download = pending ?: return
        pending = null
        service?.startDownload(download)
        callbacks.onDownloadStarted(download.filename)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val downloadBinder = binder as? DownloadService.Binder
            if (downloadBinder == null) {
                Log.e(TAG, "Download service connection failed")
                //probably service still in another process due to incognito mode process switch
                //so we will try to reconnect in a few seconds
                uiHandler.postDelayed({
                    host.bindDownloadService(this)
                }, 1000)
                return
            }
            service = downloadBinder.service
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
        }
    }
}
