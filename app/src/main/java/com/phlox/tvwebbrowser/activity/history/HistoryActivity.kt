package com.phlox.tvwebbrowser.activity.history

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.AbsListView
import android.widget.AdapterView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.phlox.tvwebbrowser.R
import com.phlox.tvwebbrowser.databinding.ActivityHistoryBinding
import com.phlox.tvwebbrowser.domain.HistoryFocusPolicy
import com.phlox.tvwebbrowser.model.HistoryItem
import com.phlox.tvwebbrowser.utils.Utils
import com.phlox.tvwebbrowser.utils.VoiceSearchHelper
import com.phlox.tvwebbrowser.utils.activemodel.ActiveModelsRepository
import com.phlox.tvwebbrowser.webengine.WebEngineFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Created by fedex on 29.12.16.
 */

class HistoryActivity :
    AppCompatActivity(),
    AdapterView.OnItemClickListener,
    AdapterView.OnItemLongClickListener {
    private lateinit var vb: ActivityHistoryBinding
    private var adapter: HistoryAdapter? = null
    private lateinit var historyModel: HistoryModel
    private var focusedRow: HistoryItem? = null
    private val voiceSearchHelper =
        VoiceSearchHelper(
            this,
            VOICE_SEARCH_REQUEST_CODE,
            VOICE_SEARCH_PERMISSIONS_REQUEST_CODE,
        )

    internal var onListScrollListener: AbsListView.OnScrollListener =
        object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(
                view: AbsListView,
                scrollState: Int,
            ) {
            }

            override fun onScroll(
                view: AbsListView,
                firstVisibleItem: Int,
                visibleItemCount: Int,
                totalItemCount: Int,
            ) {
                if (totalItemCount != 0 && firstVisibleItem + visibleItemCount >= totalItemCount - 1 && "" == historyModel.searchQuery) {
                    historyModel.loadItems(adapter!!.realCount)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vb = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(vb.root)

        historyModel = ActiveModelsRepository.get(HistoryModel::class, this)

        adapter = HistoryAdapter()
        adapter!!.onDeleteRequest = { item -> confirmDeleteItem(item) }
        adapter!!.onDeleteFocus = { item -> focusedRow = item }
        vb.listView.adapter = adapter

        vb.listView.setOnScrollListener(onListScrollListener)
        vb.listView.onItemClickListener = this
        vb.listView.onItemLongClickListener = this

        vb.btnClose.setOnClickListener { finish() }
        vb.btnDelete.setOnClickListener {
            if (adapter!!.isMultiselectMode && adapter!!.selectedItems.isNotEmpty()) {
                showDeleteDialog(false)
            } else {
                showDeleteDialog(true)
            }
        }
        vb.btnClearBrowsingData.setOnClickListener { showClearBrowsingDataDialog() }

        // Back first exits multi-select; otherwise the dispatcher falls through to the default
        // behavior. Predictive-back gestures never reach an onBackPressed() override, so this
        // lives on the dispatcher instead.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (adapter!!.isMultiselectMode) {
                        adapter!!.isMultiselectMode = false
                        updatePlaceholder()
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            },
        )

        historyModel.lastLoadedItems.subscribe(this, false) {
            if (it.isEmpty()) {
                updatePlaceholder()
                return@subscribe
            }
            adapter!!.addItems(it)
            updatePlaceholder()
            vb.listView.requestFocus()
        }

        historyModel.loadItems()
        updatePlaceholder()
    }

    private fun updatePlaceholder() {
        vb.tvPlaceholder.visibility = if (adapter!!.items.isEmpty()) View.VISIBLE else View.GONE
    }

    /**
     * The row the RIGHT key should step onto, preferring the list's selected view and falling back
     * to the first visible row. `AbsListView` exposes no reliable "focused row" position without
     * selection, so the selected view is the best signal when present.
     */
    private fun focusedHistoryRow(): HistoryItemView? {
        (vb.listView.selectedView as? HistoryItemView)?.let { return it }
        for (i in 0 until vb.listView.childCount) {
            val child = vb.listView.getChildAt(i)
            if (child is HistoryItemView && child.historyItem?.isDateHeader == false) {
                return child
            }
        }
        return null
    }

    private fun showDeleteDialog(deleteAll: Boolean) {
        if (adapter!!.items.isEmpty() || (adapter!!.selectedItems.isEmpty() && !deleteAll)) return
        AlertDialog
            .Builder(this)
            .setTitle(R.string.delete)
            .setMessage(if (deleteAll) R.string.msg_delete_history_all else R.string.msg_delete_history)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                lifecycleScope.launch(Dispatchers.Main) {
                    if (deleteAll) {
                        historyModel.deleteAll()
                        adapter!!.erase()
                    } else {
                        historyModel.delete(adapter!!.selectedItems)
                        adapter!!.remove(adapter!!.selectedItems)
                    }
                    updatePlaceholder()
                }
            }.setNeutralButton(android.R.string.cancel) { dialogInterface, i -> }
            .show()
    }

    private fun confirmDeleteItem(item: HistoryItem) {
        AlertDialog
            .Builder(this)
            .setTitle(R.string.delete)
            .setMessage(R.string.msg_delete_history_item)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                lifecycleScope.launch(Dispatchers.Main) {
                    historyModel.delete(item)
                    adapter!!.remove(item)
                    updatePlaceholder()
                }
            }.setNeutralButton(android.R.string.cancel) { dialogInterface, i -> }
            .show()
    }

    private fun showClearBrowsingDataDialog() {
        AlertDialog
            .Builder(this)
            .setTitle(R.string.clear_browsing_data)
            .setMessage(R.string.msg_clear_browsing_data)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                lifecycleScope.launch(Dispatchers.Main) {
                    withContext(Dispatchers.IO) {
                        WebStorage.getInstance().deleteAllData()
                        CookieManager.getInstance().removeAllCookies(null)
                        CookieManager.getInstance().flush()
                    }
                    WebEngineFactory.clearCache(this@HistoryActivity)
                    Utils.showToast(this@HistoryActivity, getString(android.R.string.ok))
                }
            }.setNeutralButton(android.R.string.cancel) { dialogInterface, i -> }
            .show()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_SEARCH -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    // nop
                } else if (event.action == KeyEvent.ACTION_UP) {
                    voiceSearchHelper.initiateVoiceSearch(
                        object : VoiceSearchHelper.Callback {
                            override fun onResult(text: String?) {
                                if (text == null) {
                                    Utils.showToast(this@HistoryActivity, getString(R.string.can_not_recognize))
                                    return
                                }
                                adapter!!.erase()
                                updatePlaceholder()
                                historyModel.searchQuery = text
                                historyModel.loadItems()
                            }
                        },
                    )
                }
                return true
            }

            // The list is a single D-pad focus target, so the per-row delete icon can't be reached
            // by ordinary navigation. RIGHT steps off the focused row onto its trash; once the
            // trash holds focus, LEFT/UP/DOWN hand focus back to the list. The routing itself is
            // pure logic, tested in HistoryFocusPolicyTest.
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            -> {
                val row = focusedHistoryRow()
                when (
                    HistoryFocusPolicy.route(
                        keyCode = event.keyCode,
                        listFocusable = event.action == KeyEvent.ACTION_DOWN && vb.listView.isFocused,
                        rowDeleteFocusable = row?.canFocusDelete() == true,
                        deleteIconFocused = currentFocus?.id == R.id.ibDelete,
                    )
                ) {
                    HistoryFocusPolicy.Action.FOCUS_ROW_DELETE -> {
                        row?.requestDeleteFocus()
                        return true
                    }

                    HistoryFocusPolicy.Action.FOCUS_LIST -> {
                        vb.listView.requestFocus()
                        return true
                    }

                    HistoryFocusPolicy.Action.PASS_THROUGH -> {
                        Unit
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        if (!voiceSearchHelper.processActivityResult(requestCode, resultCode, data)) {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        if (!voiceSearchHelper.processPermissionsResult(requestCode, permissions, grantResults)) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onItemClick(
        parent: AdapterView<*>,
        view: View,
        position: Int,
        id: Long,
    ) {
        val hi = (view as HistoryItemView).historyItem
        if (hi!!.isDateHeader) return
        if (adapter!!.isMultiselectMode) {
            view.setSelection(!hi.selected)
        } else {
            val resultIntent = Intent()
            resultIntent.putExtra(KEY_URL, hi.url)
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }

    override fun onItemLongClick(
        adapterView: AdapterView<*>,
        view: View,
        i: Int,
        l: Long,
    ): Boolean {
        if (adapter!!.isMultiselectMode) return false
        adapter!!.isMultiselectMode = true
        val v = view as HistoryItemView
        v.setSelection(true)
        return true
    }

    companion object {
        private const val VOICE_SEARCH_REQUEST_CODE = 10001
        private const val VOICE_SEARCH_PERMISSIONS_REQUEST_CODE = 10002

        const val KEY_URL = "url"
    }
}
