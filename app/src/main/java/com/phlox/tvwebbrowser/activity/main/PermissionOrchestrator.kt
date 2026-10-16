package com.phlox.tvwebbrowser.activity.main

import android.content.Intent
import com.phlox.tvwebbrowser.utils.VoiceSearchHelper

/**
 * Routes the permission and activity-result callbacks the main screen receives. The engine needs to
 * see permission results too, so it is consulted through [enginePermissionsResult]; voice search and
 * the download coordinator keep their own request codes and are asked in the same order as before.
 */
class PermissionOrchestrator(
    private val voiceSearchHelper: VoiceSearchHelper,
    private val downloadCoordinator: DownloadCoordinator,
    private val enginePermissionsResult: (Int, Array<String>, IntArray) -> Boolean,
) {
    companion object {
        const val COMMON_REQUESTS_START_CODE = 10100
    }

    private var lastCommonRequestsCode = COMMON_REQUESTS_START_CODE

    /** Allocates a request code for a runtime permission request made on the host's behalf. */
    fun nextRequestCode(): Int = lastCommonRequestsCode++

    /** @return true when the result was handled here (the caller must not call `super`). */
    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ): Boolean {
        if (voiceSearchHelper.processPermissionsResult(requestCode, permissions, grantResults)) {
            return true
        }
        if (enginePermissionsResult(requestCode, permissions, grantResults)) {
            return true
        }
        if (downloadCoordinator.onPermissionsResult(requestCode, grantResults)) {
            return true
        }
        return false
    }

    /** @return true when the result was handled here (the caller must not handle it further). */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean =
        voiceSearchHelper.processActivityResult(requestCode, resultCode, data)
}
