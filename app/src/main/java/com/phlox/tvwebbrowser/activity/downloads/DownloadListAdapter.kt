package com.phlox.tvwebbrowser.activity.downloads

import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter

import com.phlox.tvwebbrowser.model.Download
import com.phlox.tvwebbrowser.utils.Utils

import java.util.ArrayList

import de.halfbit.pinnedsection.PinnedSectionListView

/**
 * Created by PDT on 24.01.2017.
 */

class DownloadListAdapter(private val downloadsActivity: DownloadsActivity) : BaseAdapter(), PinnedSectionListView.PinnedSectionListAdapter {
    private val downloads = ArrayList<Download>()
    private var lastHeaderDate: Long = -1

    /** How many rows are real downloads, headers excluded. */
    val realCount: Long
        get() = downloads.count { !it.isDateHeader }.toLong()

    /** The downloads currently checked, headers excluded. */
    val selectedItems: List<Download>
        get() = downloads.filter { it.selected && !it.isDateHeader }

    /** True when every real download is checked (and there is at least one). */
    val allSelected: Boolean
        get() = realCount > 0 && selectedItems.size.toLong() == realCount

    var isMultiselectMode = false
        set(multiselectMode) {
            field = multiselectMode
            if (!multiselectMode) {
                for (download in downloads) {
                    download.selected = false
                }
            }
            notifyDataSetChanged()
        }

    fun addItems(items: List<Download>) {
        if (items.isEmpty()) {
            return
        }
        for (download in items) {
            if (!Utils.isSameDate(download.time, lastHeaderDate)) {
                lastHeaderDate = download.time
                this.downloads.add(Download.createDateHeaderInfo(download.time))
            }
            this.downloads.add(download)
        }
        notifyDataSetChanged()
    }

    override fun getCount(): Int {
        return downloads.size
    }

    override fun getItem(i: Int): Any {
        return downloads[i]
    }

    override fun getItemId(i: Int): Long {
        return i.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val hiv = if (convertView != null) {
            convertView as DownloadListItemView
        } else {
            DownloadListItemView(downloadsActivity, getItemViewType(position))
        }
        hiv.setDownload(downloads[position], isMultiselectMode)
        return hiv
    }

    override fun getViewTypeCount(): Int {
        return 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (downloads[position].isDateHeader) VIEW_TYPE_HEADER else VIEW_TYPE_DOWNLOAD_ITEM
    }

    override fun isItemViewTypePinned(viewType: Int): Boolean {
        return viewType == VIEW_TYPE_HEADER
    }

    /** Checks or clears every real download at once. */
    fun setAllSelected(selected: Boolean) {
        for (download in downloads) {
            if (!download.isDateHeader) {
                download.selected = selected
            }
        }
        notifyDataSetChanged()
    }

    fun remove(download: Download) {
        downloads.remove(download)
        pruneOrphanHeaders()
        notifyDataSetChanged()
    }

    fun remove(items: List<Download>) {
        downloads.removeAll(items.toSet())
        pruneOrphanHeaders()
        notifyDataSetChanged()
    }

    fun erase() {
        downloads.clear()
        lastHeaderDate = -1
        notifyDataSetChanged()
    }

    /** Drops date headers that no longer have any download under them. */
    private fun pruneOrphanHeaders() {
        var i = 0
        while (i < downloads.size) {
            if (downloads[i].isDateHeader &&
                (i + 1 >= downloads.size || downloads[i + 1].isDateHeader)) {
                downloads.removeAt(i)
                continue
            }
            i++
        }
        if (downloads.isEmpty()) {
            lastHeaderDate = -1
        }
    }

    companion object {
        val VIEW_TYPE_DOWNLOAD_ITEM = 0
        val VIEW_TYPE_HEADER = 1
    }
}
