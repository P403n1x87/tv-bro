package com.phlox.tvwebbrowser.service.downloads

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor.AutoCloseOutputStream
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.webkit.CookieManager
import com.phlox.tvwebbrowser.model.Download
import com.phlox.tvwebbrowser.repository.Repositories
import com.phlox.tvwebbrowser.utils.DownloadUtils
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Created by PDT on 23.01.2017.
 */

const val MAX_CONNECT_RETRIES = 5

interface DownloadTask {
    var downloadInfo: Download

    interface Callback {
        fun onProgress(task: DownloadTask)

        fun onError(
            task: DownloadTask,
            responseCode: Int,
            responseMessage: String,
        )

        fun onDone(task: DownloadTask)
    }
}

class FileDownloadTask(
    override var downloadInfo: Download,
    private val userAgent: String?,
    val callback: DownloadTask.Callback,
    private val contentResolver: ContentResolver,
) : Runnable,
    DownloadTask {
    companion object {
        val TAG = FileDownloadTask::class.java.simpleName
    }

    override fun run() {
        downloadInfo.id = runBlocking { Repositories.downloads.add(downloadInfo) }

        var input: InputStream? = null
        var output: OutputStream? = null
        val url = URL(downloadInfo.url)

        var connection: HttpURLConnection? = null
        try {
            var retries = 0
            do {
                connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    readTimeout = 10000
                    connectTimeout = 20000
                    setRequestProperty("User-Agent", userAgent)
                    if (downloadInfo.mimeType != null && downloadInfo.mimeType != "application/octet-stream") {
                        setRequestProperty("Accept", downloadInfo.mimeType)
                    }
                    downloadInfo.referer?.apply { setRequestProperty("Referer", this) }
                    useCaches = false
                    val cookie = CookieManager.getInstance().getCookie(url.toString())
                    if (cookie != null) setRequestProperty("cookie", cookie)
                    if (retries > 0) {
                        // trust me, sometimes this helps! Don't ask me how...
                        Thread.sleep(3000)
                    }
                    connect()
                }

                when (connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        break
                    }

                    HttpURLConnection.HTTP_GATEWAY_TIMEOUT,
                    HttpURLConnection.HTTP_UNAVAILABLE,
                    -> {
                        retries++
                        if (retries >= MAX_CONNECT_RETRIES) {
                            downloadInfo.size = Download.BROKEN_MARK
                            callback.onError(this, connection.responseCode, connection.responseMessage)
                            return
                        }
                    }

                    else -> {
                        downloadInfo.size = Download.BROKEN_MARK
                        callback.onError(this, connection.responseCode, connection.responseMessage)
                        return
                    }
                }
            } while (true)

            input = connection.inputStream

            val fileLength = connection.contentLength
            downloadInfo.size = fileLength.toLong()

            if (connection.headerFields.containsKey("Content-Disposition")) {
                val mime = connection.getHeaderField("Content-Type")
                downloadInfo.filename =
                    DownloadUtils.guessFileName(downloadInfo.url, connection.getHeaderField("Content-Disposition"), mime)
                downloadInfo.filepath = File(File(downloadInfo.filepath).parentFile, downloadInfo.filename).absolutePath
            }

            output = prepareDownloadOutput(downloadInfo, contentResolver)
            Log.d(TAG, "URI: " + downloadInfo.filename)
            val data = ByteArray(4096)
            var total: Long = 0
            var count = input.read(data)
            while (count != -1) {
                if (downloadInfo.cancelled) {
                    downloadInfo.bytesReceived = 0
                    downloadInfo.size = Download.CANCELLED_MARK
                    callback.onDone(this)
                    return
                }
                total += count.toLong()
                output.write(data, 0, count)
                downloadInfo.bytesReceived = total
                callback.onProgress(this)
                count = input.read(data)
            }
        } catch (e: Exception) {
            downloadInfo.size = Download.BROKEN_MARK
            callback.onError(this, 0, e.toString())
            return
        } finally {
            try {
                output?.close()
                input?.close()
            } catch (ignored: IOException) {
            }

            connection?.disconnect()
            cancelDownloadIfNeeded(downloadInfo, contentResolver)
        }
        downloadInfo.size = downloadInfo.bytesReceived
        callback.onDone(this)
    }
}

class BlobDownloadTask(
    override var downloadInfo: Download,
    val blobBase64Data: String,
    val callback: DownloadTask.Callback,
    private val contentResolver: ContentResolver,
) : Runnable,
    DownloadTask {
    override fun run() {
        downloadInfo.id = runBlocking { Repositories.downloads.add(downloadInfo) }

        try {
            val blobAsBytes: ByteArray = Base64.decode(blobBase64Data.replaceFirst("data:${downloadInfo.mimeType};base64,", ""), 0)
            val output = prepareDownloadOutput(downloadInfo, contentResolver)
            output.buffered().use { it.write(blobAsBytes) }
            downloadInfo.size = blobAsBytes.size.toLong()
            downloadInfo.bytesReceived = downloadInfo.size
        } catch (e: Exception) {
            downloadInfo.size = Download.BROKEN_MARK
            callback.onError(this, 0, e.toString())
            return
        } finally {
            cancelDownloadIfNeeded(downloadInfo, contentResolver)
        }
        callback.onDone(this)
    }
}

class StreamDownloadTask(
    override var downloadInfo: Download,
    val stream: InputStream,
    val callback: DownloadTask.Callback,
    private val contentResolver: ContentResolver,
) : Runnable,
    DownloadTask {
    override fun run() {
        downloadInfo.id = runBlocking { Repositories.downloads.add(downloadInfo) }

        var output: OutputStream? = null
        try {
            output = prepareDownloadOutput(downloadInfo, contentResolver)
            val data = ByteArray(4096)
            var total: Long = 0
            var count = stream.read(data)
            while (count != -1) {
                if (downloadInfo.cancelled) {
                    downloadInfo.bytesReceived = 0
                    downloadInfo.size = Download.CANCELLED_MARK
                    callback.onDone(this)
                    return
                }
                total += count.toLong()
                output.write(data, 0, count)
                downloadInfo.bytesReceived = total
                callback.onProgress(this)
                count = stream.read(data)
            }
        } catch (e: Exception) {
            downloadInfo.size = Download.BROKEN_MARK
            callback.onError(this, 0, e.toString())
            return
        } finally {
            try {
                output?.close()
                stream.close()
            } catch (ignored: IOException) {
            }

            cancelDownloadIfNeeded(downloadInfo, contentResolver)
        }
        downloadInfo.size = downloadInfo.bytesReceived
        callback.onDone(this)
    }
}

private fun cancelDownloadIfNeeded(
    downloadInfo: Download,
    contentResolver: ContentResolver,
) {
    val filePath = downloadInfo.filepath
    if (filePath.isEmpty()) return
    if (downloadInfo.cancelled) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val rowsDeleted = contentResolver.delete(Uri.parse(filePath), null)
            if (rowsDeleted < 1) {
                Log.e(FileDownloadTask.TAG, "Download cancelled but content not deleted??")
            }
        } else {
            File(filePath).delete()
        }
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val downloadDetails =
            ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
        contentResolver.update(Uri.parse(filePath), downloadDetails, null, null)
    }
}

private fun prepareDownloadOutput(
    downloadInfo: Download,
    contentResolver: ContentResolver,
): OutputStream =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val downloadsCollection =
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val newDownloadDetails =
            ContentValues().apply {
                if (downloadInfo.filename.isNotEmpty()) {
                    put(MediaStore.Downloads.DISPLAY_NAME, downloadInfo.filename)
                }
                put(MediaStore.Downloads.DOWNLOAD_URI, downloadInfo.url)
                put(MediaStore.Downloads.REFERER_URI, downloadInfo.referer)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        val downloadUri =
            contentResolver
                .insert(downloadsCollection, newDownloadDetails)
        if (downloadUri == null) {
            throw IllegalStateException("Can not create file")
        }
        val fd = contentResolver.openFileDescriptor(downloadUri, "w", null)
        downloadInfo.filepath = downloadUri.toString()
        runBlocking { Repositories.downloads.update(downloadInfo) }
        AutoCloseOutputStream(fd)
    } else {
        FileOutputStream(downloadInfo.filepath)
    }
