package com.phlox.tvwebbrowser.activity.downloads

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.PopupMenu
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.phlox.tvwebbrowser.BuildConfig
import com.phlox.tvwebbrowser.R
import com.phlox.tvwebbrowser.databinding.ActivityDownloadsBinding
import com.phlox.tvwebbrowser.model.Download
import com.phlox.tvwebbrowser.utils.InstallPermissionHelper
import com.phlox.tvwebbrowser.utils.Utils
import com.phlox.tvwebbrowser.utils.activemodel.ActiveModelsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class DownloadsActivity :
    AppCompatActivity(),
    AdapterView.OnItemClickListener,
    ActiveDownloadsModel.Listener,
    AdapterView.OnItemLongClickListener {
    private lateinit var vb: ActivityDownloadsBinding
    private lateinit var adapter: DownloadListAdapter
    private val listeners = ArrayList<ActiveDownloadsModel.Listener>()

    private lateinit var activeDownloadsModel: ActiveDownloadsModel
    private lateinit var downloadsHistoryModel: DownloadsHistoryModel
    private lateinit var storageModel: DownloadStorageModel

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
                if (totalItemCount != 0 && firstVisibleItem + visibleItemCount >= totalItemCount - 1) {
                    downloadsHistoryModel.loadNextItems()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate this:" + System.identityHashCode(this))
        vb = ActivityDownloadsBinding.inflate(layoutInflater)
        setContentView(vb.root)

        activeDownloadsModel = ActiveModelsRepository.get(ActiveDownloadsModel::class, this)
        downloadsHistoryModel = ActiveModelsRepository.get(DownloadsHistoryModel::class, this)
        storageModel = ActiveModelsRepository.get(DownloadStorageModel::class, this)

        adapter = DownloadListAdapter(this)
        vb.listView.adapter = adapter

        vb.listView.setOnScrollListener(onListScrollListener)
        vb.listView.onItemClickListener = this
        vb.listView.onItemLongClickListener = this

        vb.btnClose.setOnClickListener { finish() }
        vb.btnDeleteAll.setOnClickListener { showDeleteDialog(deleteAll = true) }
        vb.btnDeleteSelected.setOnClickListener { showDeleteDialog(deleteAll = false) }
        vb.cbSelectAll.setOnClickListener {
            adapter.setAllSelected(vb.cbSelectAll.isChecked)
            updateControls()
        }

        // Back first exits multi-select; otherwise the dispatcher falls through to the default
        // behavior. Predictive-back gestures never reach an onBackPressed() override, so this
        // lives on the dispatcher instead.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (adapter.isMultiselectMode) {
                        adapter.isMultiselectMode = false
                        updateControls()
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            },
        )

        storageModel.location.subscribe(this, false) { updateStorageSummary() }
        storageModel.freeSpace.subscribe(this, false) { updateStorageSummary() }
        storageModel.usedSpace.subscribe(this, false) { updateStorageSummary() }

        downloadsHistoryModel.lastLoadedItems.subscribe(this, false, {
            if (it.isNotEmpty()) {
                vb.tvPlaceholder.visibility = View.GONE
                adapter.addItems(it)
                vb.listView.requestFocus()
            }
            updateControls()
        })

        if (downloadsHistoryModel.allItems.isEmpty()) {
            downloadsHistoryModel.loadNextItems()
        } else {
            vb.tvPlaceholder.visibility = View.GONE
            adapter.addItems(downloadsHistoryModel.allItems)
            vb.listView.requestFocus()
        }
        updateControls()
    }

    override fun onResume() {
        super.onResume()
        activeDownloadsModel.registerListener(this)
        storageModel.refresh()
    }

    override fun onPause() {
        activeDownloadsModel.unregisterListener(this@DownloadsActivity)
        super.onPause()
    }

    override fun onItemClick(
        adapterView: AdapterView<*>,
        view: View,
        i: Int,
        l: Long,
    ) {
        val v = view as DownloadListItemView
        val download = v.download ?: return

        if (download.isDateHeader) {
            return
        }

        if (adapter.isMultiselectMode) {
            v.setSelection(!download.selected)
            updateControls()
            return
        }

        if (download.size != download.bytesReceived) {
            // file should be already marked in UI as broken
            return
        }

        val fileURI =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Uri.parse(download.filepath)
            } else {
                val file = File(download.filepath)
                if (!file.exists()) {
                    Utils.showToast(this, R.string.file_not_found)
                    return
                }

                FileProvider.getUriForFile(
                    this@DownloadsActivity,
                    BuildConfig.APPLICATION_ID + ".provider",
                    file,
                )
            }

        val openIntent = Intent(Intent.ACTION_VIEW)
        val mimeType = contentResolver.getType(fileURI)
        openIntent.setDataAndType(fileURI, mimeType)
        openIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        try {
            startActivity(openIntent)
        } catch (e: ActivityNotFoundException) {
            Utils.showToast(this, getString(R.string.no_app_for_file_type))
        }
    }

    override fun onItemLongClick(
        adapterView: AdapterView<*>,
        view: View,
        i: Int,
        l: Long,
    ): Boolean {
        val v = view as DownloadListItemView
        val download = v.download ?: return false
        if (download.isDateHeader) {
            return true
        }
        if (!adapter.isMultiselectMode) {
            // Long press enters multi-select; before that the row's own actions were the long-press menu.
            adapter.isMultiselectMode = true
            v.setSelection(true)
            updateControls()
            return true
        }
        if (download.size == Download.BROKEN_MARK || download.size == Download.CANCELLED_MARK ||
            download.size == download.bytesReceived
        ) {
            showFinishedDownloadOptionsPopup(v)
        } else {
            showUnfinishedDownloadOptionsPopup(v)
        }
        return true
    }

    /** Reflects the list/selection state in the header controls. */
    private fun updateControls() {
        val hasDownloads = adapter.realCount > 0
        vb.btnDeleteAll.visibility = if (hasDownloads) View.VISIBLE else View.GONE
        if (!hasDownloads) {
            adapter.isMultiselectMode = false
        }
        if (!adapter.isMultiselectMode) {
            vb.cbSelectAll.visibility = View.GONE
            vb.btnDeleteSelected.visibility = View.GONE
            return
        }
        vb.cbSelectAll.visibility = View.VISIBLE
        vb.cbSelectAll.isChecked = adapter.allSelected
        vb.btnDeleteSelected.visibility =
            if (adapter.selectedItems.isEmpty()) View.GONE else View.VISIBLE
        vb.btnDeleteSelected.text =
            getString(R.string.delete_count, adapter.selectedItems.size)
    }

    private fun updateStorageSummary() {
        val used = storageModel.usedSpace.value
        val free = storageModel.freeSpace.value
        val location = storageModel.location.value
        vb.tvStorageSummary.text =
            if (used != null && free != null && location != null) {
                getString(R.string.downloads_using_of_in, used, free, location)
            } else {
                ""
            }
    }

    private fun showDeleteDialog(deleteAll: Boolean) {
        val selected = adapter.selectedItems
        if (adapter.realCount == 0L || (!deleteAll && selected.isEmpty())) return
        AlertDialog
            .Builder(this)
            .setTitle(R.string.delete)
            .setMessage(if (deleteAll) R.string.msg_delete_download_all else R.string.msg_delete_download)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                lifecycleScope.launch(Dispatchers.Main) {
                    if (deleteAll) {
                        activeDownloadsModel.deleteAll()
                        downloadsHistoryModel.reset()
                        adapter.erase()
                    } else {
                        activeDownloadsModel.deleteItems(selected)
                        downloadsHistoryModel.remove(selected)
                        adapter.remove(selected)
                    }
                    adapter.isMultiselectMode = false
                    vb.tvPlaceholder.visibility =
                        if (adapter.realCount == 0L) View.VISIBLE else View.GONE
                    updateControls()
                    storageModel.refresh()
                }
            }.setNeutralButton(android.R.string.cancel) { _, _ -> }
            .show()
    }

    private fun showUnfinishedDownloadOptionsPopup(v: DownloadListItemView) {
        val pm = PopupMenu(this, v, Gravity.BOTTOM)
        pm.menu.add(R.string.cancel)
        pm.setOnMenuItemClickListener {
            activeDownloadsModel.cancelDownload(v.download!!)
            true
        }
        pm.show()
    }

    private fun showFinishedDownloadOptionsPopup(v: DownloadListItemView) {
        val download = v.download ?: return
        val pm = PopupMenu(this, v, Gravity.BOTTOM)
        if (download.filename.endsWith(".apk", true) &&
            download.size == download.bytesReceived
        ) {
            pm.menu.add(0, 0, 0, R.string.install)
        }
        pm.menu.add(0, 1, 1, R.string.open_folder)
        pm.menu.add(0, 2, 2, R.string.delete)
        pm.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                0 -> installAPK(download)
                1 -> openFolder(v)
                2 -> confirmDeleteItem(download)
            }
            true
        }
        pm.show()
    }

    private fun installAPK(download: Download) {
        // The downloads screen always lets the system installer handle the grant on API 26+ — it
        // prompts the user itself; only below that does the global setting gate it.
        val canInstallFromOtherSources =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ||
                Settings.Secure.getInt(contentResolver, Settings.Secure.INSTALL_NON_MARKET_APPS) == 1

        if (canInstallFromOtherSources) {
            InstallPermissionHelper.launchInstaller(this, download.filepath)
        } else {
            InstallPermissionHelper.showEnableUnknownSourcesDialog(this) {
                InstallPermissionHelper.openUnknownSourcesSettings(this)
            }
        }
    }

    private fun openFolder(v: DownloadListItemView) {
        val uri = Uri.parse(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "resource/folder")
        if (intent.resolveActivityInfo(packageManager, 0) != null) {
            startActivity(intent)
        } else {
            Utils.showToast(this, R.string.no_file_explorer_msg)
        }
    }

    private fun confirmDeleteItem(download: Download) {
        AlertDialog
            .Builder(this)
            .setTitle(R.string.delete)
            .setMessage(R.string.msg_delete_download)
            .setPositiveButton(android.R.string.ok) { _, _ -> deleteItem(download) }
            .setNeutralButton(android.R.string.cancel) { _, _ -> }
            .show()
    }

    private fun deleteItem(download: Download) =
        lifecycleScope.launch(Dispatchers.Main) {
            activeDownloadsModel.deleteItem(download)
            downloadsHistoryModel.remove(listOf(download))
            adapter.remove(download)
            vb.tvPlaceholder.visibility = if (adapter.realCount == 0L) View.VISIBLE else View.GONE
            updateControls()
            storageModel.refresh()
        }

    override fun onDownloadUpdated(downloadInfo: Download) {
        for (i in listeners.indices) {
            listeners[i].onDownloadUpdated(downloadInfo)
        }
    }

    override fun onDownloadError(
        downloadInfo: Download,
        responseCode: Int,
        responseMessage: String,
    ) {
        for (i in listeners.indices) {
            listeners[i].onDownloadError(downloadInfo, responseCode, responseMessage)
        }
    }

    override fun onAllDownloadsComplete() {
        // A finished download changed the footprint; refresh the header's storage summary.
        storageModel.refresh()
    }

    fun registerListener(listener: ActiveDownloadsModel.Listener) {
        listeners.add(listener)
    }

    fun unregisterListener(listener: ActiveDownloadsModel.Listener) {
        listeners.remove(listener)
    }

    companion object {
        val TAG: String = DownloadsActivity::class.java.simpleName
    }
}
