package com.phlox.tvwebbrowser.activity.main

import android.content.ServiceConnection

/**
 * A [DownloadHost] that records what the coordinator asks of the screen, so its permission walk,
 * space pre-flight and binding can be asserted without an Activity.
 */
class FakeDownloadHost : DownloadHost {
    val granted = mutableSetOf<String>()
    val requested = mutableListOf<Pair<String, Int>>()
    val noSpaceWarnings = mutableListOf<Triple<String, Long, Long>>()
    var bindCalls = 0
    var unbindCalls = 0
    var freeSpace = Long.MAX_VALUE

    override fun bindDownloadService(connection: ServiceConnection) {
        bindCalls++
    }

    override fun unbindDownloadService(connection: ServiceConnection) {
        unbindCalls++
    }

    override fun hasPermission(permission: String): Boolean = permission in granted

    override fun requestPermission(
        permission: String,
        requestCode: Int,
    ) {
        requested += permission to requestCode
    }

    override fun downloadFreeSpaceBytes(): Long = freeSpace

    override fun onDownloadNoSpace(
        fileName: String,
        neededBytes: Long,
        freeBytes: Long,
    ) {
        noSpaceWarnings += Triple(fileName, neededBytes, freeBytes)
    }
}
