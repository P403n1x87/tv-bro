package com.phlox.tvwebbrowser.activity.downloads

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.util.Log
import com.phlox.tvwebbrowser.TVBro
import com.phlox.tvwebbrowser.model.Download
import com.phlox.tvwebbrowser.service.downloads.DownloadTask
import com.phlox.tvwebbrowser.service.downloads.FileDownloadTask
import com.phlox.tvwebbrowser.repository.DownloadsRepository
import com.phlox.tvwebbrowser.repository.Repositories
import com.phlox.tvwebbrowser.utils.observable.ObservableList
import com.phlox.tvwebbrowser.utils.activemodel.ActiveModel
import java.io.File

class ActiveDownloadsModel(
    private val downloads: DownloadsRepository = Repositories.downloads,
    private val contentResolver: () -> ContentResolver = { TVBro.instance.contentResolver },
): ActiveModel() {
    val activeDownloads = ObservableList<DownloadTask>()
    private val listeners = java.util.ArrayList<Listener>()

    interface Listener {
        fun onDownloadUpdated(downloadInfo: Download)
        fun onDownloadError(downloadInfo: Download, responseCode: Int, responseMessage: String)
        fun onAllDownloadsComplete()
    }

    /** Deletes one logged download: its file first, then its row. */
    suspend fun deleteItem(download: Download) {
        deleteFile(download)
        downloads.delete(download)
    }

    /** Deletes a multi-selected set of downloads. */
    suspend fun deleteItems(items: List<Download>) {
        for (item in items) {
            deleteFile(item)
            downloads.delete(item)
        }
    }

    /** Empties the download log, removing every file it references. */
    suspend fun deleteAll() {
        for (download in downloads.all()) {
            deleteFile(download)
        }
        downloads.deleteAll()
    }

    private fun deleteFile(download: Download) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val contentResolver = contentResolver()
            val rowsDeleted = try {
                contentResolver.delete(Uri.parse(download.filepath), null)
            } catch (e: Exception) {
                Log.w(FileDownloadTask.TAG, "Failed to delete file from MediaStore", e)
                0
            }
            if (rowsDeleted < 1) {
                Log.e(FileDownloadTask.TAG, "Failed to delete file from MediaStore")
            }
        } else {
            File(download.filepath).delete()
        }
    }

    fun registerListener(listener: Listener) {
        listeners.add(listener)
    }

    fun unregisterListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun cancelDownload(download: Download) {
        for (i in activeDownloads.indices) {
            val task = activeDownloads[i]
            if (task.downloadInfo.id == download.id) {
                task.downloadInfo.cancelled = true
                break
            }
        }
    }

    fun notifyListenersAboutError(task: DownloadTask, responseCode: Int, responseMessage: String) {
        for (i in listeners.indices) {
            listeners[i].onDownloadError(task.downloadInfo, responseCode, responseMessage)
        }
    }

    fun notifyListenersAboutDownloadProgress(task: DownloadTask) {
        for (i in listeners.indices) {
            listeners[i].onDownloadUpdated(task.downloadInfo)
        }
    }

    fun onDownloadEnded(task: DownloadTask) {
        activeDownloads.remove(task)
        if (activeDownloads.isEmpty()) {
            for (i in listeners.indices) {
                listeners[i].onAllDownloadsComplete()
            }
        }
    }
}