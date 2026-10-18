package com.phlox.tvwebbrowser.activity.main

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Handler
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.PopupMenu
import android.widget.Toast
import com.phlox.tvwebbrowser.R
import com.phlox.tvwebbrowser.databinding.ActivityMainBinding
import com.phlox.tvwebbrowser.domain.LinkActionPolicy
import com.phlox.tvwebbrowser.model.WebTabState
import com.phlox.tvwebbrowser.utils.BackNavigationEventsAdapter
import com.phlox.tvwebbrowser.webengine.WebEngineWindowProviderCallback
import com.phlox.tvwebbrowser.widgets.cursor.CursorDrawerDelegate

/**
 * Owns the page-level interaction surfaces of the main screen: fullscreen enter/exit, the cursor
 * menu, the link-actions popup and the "a site asks to open an unknown URL" dialog.
 *
 * [MainActivity] only forwards the engine callbacks to it, so the two popups' lifetimes (and the
 * fields that track them) no longer live in the activity.
 */
class CursorMenuController(
    private val activity: Activity,
    private val vb: ActivityMainBinding,
    private val uiHandler: Handler,
    private val toolbarController: ToolbarController,
    private val backNavigationEventsAdapter: BackNavigationEventsAdapter,
) {
    private var linkActionsMenu: PopupMenu? = null
    private var openUrlInExternalAppDialog: AlertDialog? = null

    fun onPrepareForFullscreen() {
        toolbarController.enterFullscreen()
    }

    fun onExitFullscreen() {
        toolbarController.exitFullscreen()
    }

    fun showCursorMenu(
        tab: WebTabState,
        callback: WebEngineWindowProviderCallback,
        cursorDrawer: CursorDrawerDelegate,
        baseUri: String?,
        linkUri: String?,
        srcUri: String?,
        title: String?,
        altText: String?,
        textContent: String?,
        x: Int,
        y: Int,
    ) {
        uiHandler.post {
            vb.vCursorMenu.show(
                tab,
                callback,
                cursorDrawer,
                baseUri,
                linkUri,
                srcUri,
                title,
                altText,
                textContent,
                x,
                y,
                backNavigationEventsAdapter,
            )
        }
    }

    fun suggestActionsForLink(
        tab: WebTabState,
        callback: WebEngineWindowProviderCallback,
        baseUri: String?,
        linkUri: String?,
        srcUri: String?,
        title: String?,
        altText: String?,
        textContent: String?,
        x: Int,
        y: Int,
    ) {
        val url = LinkActionPolicy.targetUrl(linkUri, srcUri)
        val isHTTPUrl = LinkActionPolicy.isHttpUrl(url)
        val anchor = View(activity)
        val lp = FrameLayout.LayoutParams(1, 1)
        lp.setMargins(x, y, 0, 0)
        vb.flWebViewContainer.addView(anchor, lp)
        linkActionsMenu =
            PopupMenu(activity, anchor, Gravity.BOTTOM).also {
                it.inflate(R.menu.menu_link)
                it.menu.findItem(R.id.miOpenInNewTab).isVisible = isHTTPUrl
                it.menu.findItem(R.id.miOpenInExternalApp).isVisible = isHTTPUrl
                it.menu.findItem(R.id.miDownload).isVisible = isHTTPUrl
                it.menu.findItem(R.id.miCopyToClipboard).isVisible = url != null
                it.menu.findItem(R.id.miShare).isVisible = url != null
                it.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.miRefreshPage -> tab.webEngine.reload()
                        R.id.miOpenInNewTab -> callback.onOpenInNewTabRequested(url!!, true)
                        R.id.miOpenInExternalApp -> callback.onOpenInExternalAppRequested(url!!)
                        R.id.miDownload -> callback.onDownloadRequested(url!!)
                        R.id.miCopyToClipboard -> callback.onCopyTextToClipboardRequested(url!!)
                        R.id.miShare -> callback.onShareUrlRequested(url!!)
                    }
                    true
                }

                it.setOnDismissListener {
                    vb.flWebViewContainer.removeView(anchor)
                    linkActionsMenu = null
                }
                it.show()
            }
    }

    fun askUserAndOpenInExternalApp(
        url: String,
        intent: Intent,
    ) {
        if (openUrlInExternalAppDialog != null) {
            return
        }
        openUrlInExternalAppDialog =
            AlertDialog
                .Builder(activity)
                .setTitle(R.string.site_asks_to_open_unknown_url)
                .setMessage(activity.getString(R.string.site_asks_to_open_unknown_url_message) + "\n\n" + url)
                .setPositiveButton(R.string.yes) { _, _ ->
                    try {
                        activity.startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(activity, R.string.external_app_open_error, Toast.LENGTH_SHORT).show()
                    }
                }.setNegativeButton(R.string.no, null)
                .setOnDismissListener {
                    openUrlInExternalAppDialog = null
                }.show()
    }
}
