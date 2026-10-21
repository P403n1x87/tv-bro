package com.phlox.tvwebbrowser.activity.history

import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import com.phlox.tvwebbrowser.model.HistoryItem
import com.phlox.tvwebbrowser.utils.Utils
import de.halfbit.pinnedsection.PinnedSectionListView
import java.util.ArrayList

/**
 * Created by fedex on 29.12.16.
 */

class HistoryAdapter :
    BaseAdapter(),
    PinnedSectionListView.PinnedSectionListAdapter {
    val items = ArrayList<HistoryItem>()
    private var lastHeaderDate: Long = -1
    var realCount: Long = 0
        private set
    var isMultiselectMode = false
        set(multiselectMode) {
            field = multiselectMode
            if (!multiselectMode) {
                for (hi in items) {
                    hi.selected = false
                }
            }
            notifyDataSetChanged()
        }
    private val tmpSelected = ArrayList<HistoryItem>()

    var onDeleteRequest: ((HistoryItem) -> Unit)? = null

    /** Reports which row's delete icon currently holds focus, so the host can track it. */
    var onDeleteFocus: ((HistoryItem) -> Unit)? = null

    val selectedItems: List<HistoryItem>
        get() {
            tmpSelected.clear()
            for (hi in items) {
                if (hi.selected) {
                    tmpSelected.add(hi)
                }
            }
            return tmpSelected
        }

    fun addItems(items: List<HistoryItem>) {
        if (items.isEmpty()) {
            return
        }
        for (hi in items) {
            if (!Utils.isSameDate(hi.time, lastHeaderDate)) {
                lastHeaderDate = hi.time
                this.items.add(HistoryItem.createDateHeaderInfo(hi.time))
            }
            this.items.add(hi)
            realCount++
        }
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): Any = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(
        position: Int,
        convertView: View?,
        parent: ViewGroup,
    ): View {
        val hiv: HistoryItemView
        if (convertView != null) {
            hiv = convertView as HistoryItemView
        } else {
            hiv = HistoryItemView(parent.context, getItemViewType(position))
        }
        hiv.onDeleteClick = onDeleteRequest
        hiv.onDeleteFocus = onDeleteFocus
        hiv.setHistoryItem(items[position], isMultiselectMode)
        return hiv
    }

    override fun getViewTypeCount(): Int = 2

    override fun getItemViewType(position: Int): Int = if (items[position].isDateHeader) VIEW_TYPE_HEADER else VIEW_TYPE_HISTORY_ITEM

    override fun isItemViewTypePinned(viewType: Int): Boolean = viewType == VIEW_TYPE_HEADER

    fun erase() {
        items.clear()
        lastHeaderDate = -1
        notifyDataSetChanged()
    }

    fun remove(historyItem: HistoryItem) {
        items.remove(historyItem)
        pruneOrphanHeaders()
        notifyDataSetChanged()
    }

    fun remove(selectedItems: List<HistoryItem>) {
        items.removeAll(selectedItems)
        pruneOrphanHeaders()
        notifyDataSetChanged()
    }

    /** Drops date headers that no longer have any visit under them. */
    private fun pruneOrphanHeaders() {
        var i = 0
        while (i < items.size) {
            if (items[i].isDateHeader &&
                (i + 1 >= items.size || items[i + 1].isDateHeader)
            ) {
                items.removeAt(i)
                continue
            }
            i++
        }
        if (items.isEmpty()) {
            lastHeaderDate = -1
        }
    }

    companion object {
        val VIEW_TYPE_HISTORY_ITEM = 0
        val VIEW_TYPE_HEADER = 1
    }
}
