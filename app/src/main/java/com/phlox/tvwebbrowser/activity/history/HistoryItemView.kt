package com.phlox.tvwebbrowser.activity.history

import android.content.Context
import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import com.phlox.tvwebbrowser.R
import com.phlox.tvwebbrowser.model.HistoryItem
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Created by fedex on 29.12.16.
 */

class HistoryItemView(
    context: Context,
    private val viewType: Int,
) : FrameLayout(context) {
    private var tvDate: TextView? = null
    private var tvTitle: TextView? = null
    private var tvURL: TextView? = null
    private var ibDelete: ImageButton? = null
    private var cbSelection: CheckBox? = null
    var historyItem: HistoryItem? = null
    var onDeleteClick: ((HistoryItem) -> Unit)? = null
    var onDeleteFocus: ((HistoryItem) -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(
            if (viewType == HistoryAdapter.VIEW_TYPE_HEADER) {
                R.layout.view_history_header_item
            } else {
                R.layout.view_history_item
            },
            this,
        )
        when (viewType) {
            HistoryAdapter.VIEW_TYPE_HEADER -> {
                tvDate = findViewById(R.id.tvDate)
            }

            HistoryAdapter.VIEW_TYPE_HISTORY_ITEM -> {
                tvTitle = findViewById(R.id.tvTitle)
                tvURL = findViewById(R.id.tvURL)
                ibDelete = findViewById(R.id.ibDelete)
                cbSelection = findViewById(R.id.cbSelection)
                ibDelete!!.setOnClickListener {
                    historyItem?.let { onDeleteClick?.invoke(it) }
                }
                ibDelete!!.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        historyItem?.let { onDeleteFocus?.invoke(it) }
                    }
                }
            }
        }
    }

    fun setHistoryItem(
        historyItem: HistoryItem,
        multiselectMode: Boolean,
    ) {
        this.historyItem = historyItem
        when (viewType) {
            HistoryAdapter.VIEW_TYPE_HEADER -> {
                val df = SimpleDateFormat.getDateInstance()
                tvDate!!.text = df.format(Date(historyItem.time))
            }

            HistoryAdapter.VIEW_TYPE_HISTORY_ITEM -> {
                tvTitle!!.text = historyItem.title
                tvURL!!.text = historyItem.url
                cbSelection!!.visibility = if (multiselectMode) VISIBLE else GONE
                cbSelection!!.isChecked = historyItem.selected
                ibDelete!!.visibility = if (multiselectMode) GONE else VISIBLE
            }
        }
    }

    fun setSelection(selected: Boolean) {
        if (viewType == HistoryAdapter.VIEW_TYPE_HEADER) return
        cbSelection!!.isChecked = selected
        historyItem?.selected = selected
    }

    /**
     * Whether this row currently offers a focusable delete icon — false for date headers and for
     * any row whose icon is hidden (multi-select). Callers use it to decide if RIGHT should step
     * onto the icon or let the list keep the key.
     */
    fun canFocusDelete(): Boolean {
        if (viewType != HistoryAdapter.VIEW_TYPE_HISTORY_ITEM) return false
        val icon = ibDelete ?: return false
        return icon.visibility == VISIBLE
    }

    /**
     * Moves D-pad focus onto this row's delete icon, so a single visit can be removed without
     * leaving the list. No-op on date headers. Returns whether focus moved.
     */
    fun requestDeleteFocus(): Boolean {
        if (!canFocusDelete()) return false
        return ibDelete!!.requestFocus()
    }
}
