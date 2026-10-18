package com.phlox.tvwebbrowser.domain

import com.phlox.tvwebbrowser.model.Download

/**
 * Pure decision logic for the download log's footprint: how many bytes the stored downloads take
 * and whether a new one still fits. Kept out of the views so it can be unit-tested with plain JUnit;
 * reading the filesystem stays with the caller.
 */
object DownloadStoragePolicy {
    /** The bytes a download currently occupies: its final size when complete, else what arrived. */
    fun bytesOnDisk(download: Download): Long =
        if (download.size > 0) {
            minOf(download.size, download.bytesReceived)
        } else {
            download.bytesReceived
        }

    /** Total footprint of [downloads], for the "Using X of Y" summary. */
    fun usedBytes(downloads: List<Download>): Long = downloads.sumOf(::bytesOnDisk)

    /** True when a file of [neededBytes] still fits in [freeBytes]. */
    fun canFit(
        freeBytes: Long,
        neededBytes: Long,
    ): Boolean = freeBytes > 0 && neededBytes <= freeBytes
}
