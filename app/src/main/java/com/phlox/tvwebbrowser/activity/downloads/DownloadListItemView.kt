package com.phlox.tvwebbrowser.activity.downloads

import android.annotation.SuppressLint
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.phlox.tvwebbrowser.R
import com.phlox.tvwebbrowser.domain.SiteDomains
import com.phlox.tvwebbrowser.model.Download
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Created by PDT on 24.01.2017.
 */

class DownloadListItemView(private val downloadsActivity: DownloadsActivity, private val viewType: Int) : FrameLayout(downloadsActivity), ActiveDownloadsModel.Listener {
    private var tvDate: TextView? = null
    private var tvTitle: TextView? = null
    private var tvSubtitle: TextView? = null
    private var progressBar: ProgressBar? = null
    private var progressBar2: ProgressBar? = null
    private var cbSelection: CheckBox? = null
    var download: Download? = null

    fun setDownload(download: Download, multiselectMode: Boolean) {
        this.download = download
        when (viewType) {
            DownloadListAdapter.VIEW_TYPE_HEADER -> {
                val df = SimpleDateFormat.getDateInstance()
                tvDate!!.text = df.format(Date(download.time))
            }
            DownloadListAdapter.VIEW_TYPE_DOWNLOAD_ITEM -> {
                tvTitle!!.text = download.filename
                cbSelection!!.visibility = if (multiselectMode) View.VISIBLE else View.GONE
                cbSelection!!.isChecked = download.selected
                updateUI(download)
            }
        }
    }

    fun setSelection(selected: Boolean) {
        if (viewType == DownloadListAdapter.VIEW_TYPE_HEADER) return
        cbSelection!!.isChecked = selected
        download?.selected = selected
    }

    init {
        LayoutInflater.from(downloadsActivity).inflate(
                if (viewType == DownloadListAdapter.VIEW_TYPE_HEADER)
                    R.layout.view_history_header_item
                else
                    R.layout.view_download_item, this)
        when (viewType) {
            DownloadListAdapter.VIEW_TYPE_HEADER -> {
                tvDate = findViewById(R.id.tvDate)
            }
            DownloadListAdapter.VIEW_TYPE_DOWNLOAD_ITEM -> {
                tvTitle = findViewById(R.id.tvTitle)
                tvSubtitle = findViewById(R.id.tvSubtitle)
                progressBar = findViewById(R.id.progressBar)
                progressBar2 = findViewById(R.id.progressBar2)
                cbSelection = findViewById(R.id.cbSelection)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateUI(download: Download) {
        tvTitle!!.text = download.filename
        val sizeLabel: String
        if (download.size == Download.CANCELLED_MARK) {
            sizeLabel = context.getString(R.string.cancelled)
            progressBar!!.visibility = View.INVISIBLE
            progressBar2!!.visibility = View.GONE
        } else if (download.size == Download.BROKEN_MARK) {
            sizeLabel = context.getString(R.string.error)
            progressBar!!.visibility = View.INVISIBLE
            progressBar2!!.visibility = View.GONE
        } else if (download.size == 0L) {
            sizeLabel = Formatter.formatShortFileSize(context, download.bytesReceived)
            progressBar!!.visibility = View.INVISIBLE
            progressBar2!!.visibility = View.VISIBLE
        } else if (download.size == download.bytesReceived) {
            sizeLabel = Formatter.formatShortFileSize(context, download.size)
            progressBar!!.visibility = View.INVISIBLE
            progressBar2!!.visibility = View.GONE
        } else {
            sizeLabel = Formatter.formatShortFileSize(context, download.bytesReceived) + " / " +
                    Formatter.formatShortFileSize(context, download.size)
            progressBar!!.visibility = View.VISIBLE
            progressBar2!!.visibility = View.GONE
            if (download.bytesReceived > download.size) {
                download.size = 0L//wrong value from server - ignore it for future updates
            } else {
                progressBar!!.progress = (download.bytesReceived * 100 / download.size).toInt()
            }
        }
        val domain = SiteDomains.domainFromUrl(download.url)
        tvSubtitle!!.text = if (domain.isNullOrEmpty()) sizeLabel else "$sizeLabel \u00b7 $domain"
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        downloadsActivity.registerListener(this)
    }

    override fun onDetachedFromWindow() {
        downloadsActivity.unregisterListener(this)
        super.onDetachedFromWindow()
    }

    override fun onDownloadUpdated(downloadInfo: Download) {
        if (downloadInfo.id == this.download?.id) {
            updateUI(downloadInfo)
        }
    }

    override fun onDownloadError(downloadInfo: Download, responseCode: Int, responseMessage: String) {
        if (downloadInfo.id == this.download?.id) {
            updateUI(downloadInfo)
        }
    }

    override fun onAllDownloadsComplete() {}
}
