package com.phlox.tvwebbrowser.activity.main

import android.app.Activity
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isInvisible
import com.phlox.tvwebbrowser.R
import com.phlox.tvwebbrowser.databinding.ActivityMainBinding

/**
 * Owns the state of the top bar and its overlay: showing/hiding the toolbar over the live page,
 * the overlay style, the menu toggle and the in-page fullscreen window flags.
 *
 * [MainActivity] keeps the [com.phlox.tvwebbrowser.activity.main.view.ActionBar] wiring (address
 * box, buttons) and only forwards the lifecycle/fullscreen events here.
 */
class ToolbarController(
    private val activity: Activity,
    private val vb: ActivityMainBinding,
    /** Reads the "keep screen on" setting, used when leaving fullscreen. */
    private val keepScreenOn: () -> Boolean,
    /** Puts the focus back on the page, called when the overlay is dismissed. */
    private val focusEngine: () -> Unit,
) {
    var isFullscreen: Boolean = false
        private set

    fun applyOverlayStyle() {
        vb.rlActionBar.setBackgroundColor(ContextCompat.getColor(activity, R.color.top_bar_background))
        vb.rlActionBar.elevation = 5f * activity.resources.displayMetrics.density
        vb.vActionBar.setOverlayTransparency(false)
        vb.vTabs.setOverlayTransparency(false)
    }

    fun toggleMenu() {
        if (vb.rlActionBar.isInvisible) {
            showMenuOverlay()
        } else {
            hideMenuOverlay()
        }
    }

    fun showMenuOverlay() {
        if (isFullscreen) return
        applyOverlayStyle()
        vb.flWebViewContainer.visibility = View.VISIBLE
        vb.ivMiniatures.visibility = View.INVISIBLE
        vb.rlActionBar.visibility = View.VISIBLE
        vb.vActionBar.dismissExtendedAddressBarMode()
        vb.vActionBar.catchFocus()
    }

    fun hideTopBar() {
        if (isFullscreen) return
        vb.rlActionBar.visibility = View.GONE
    }

    fun showTopBar() {
        if (isFullscreen) return
        applyOverlayStyle()
        vb.rlActionBar.visibility = View.VISIBLE
    }

    /**
     * Kept for the auto-hide call sites (tab switch, URL done, history, voice search, …). With the
     * "keep toolbar open" behaviour the overlay stays up, so this is intentionally a no-op.
     */
    @Suppress("UNUSED_PARAMETER")
    fun autoHideMenuOverlay(hideBottomButtons: Boolean = true) {
    }

    fun hideMenuOverlay(hideBottomButtons: Boolean = true) {
        vb.llTabPanel.visibility = View.GONE
        if (hideBottomButtons) {
            focusEngine()
        }
    }

    fun enterFullscreen() {
        isFullscreen = true
        vb.rlActionBar.visibility = View.GONE
        vb.llTabPanel.visibility = View.GONE
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // The SYSTEM_UI_FLAG_* bitmask is deprecated in API 30: the insets controller hides the
        // same bars and keeps them transient on a swipe, like SYSTEM_UI_FLAG_IMMERSIVE_STICKY did.
        insetsController.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    fun exitFullscreen() {
        if (!keepScreenOn()) {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        insetsController.show(WindowInsetsCompat.Type.systemBars())
        isFullscreen = false
        vb.rlActionBar.visibility = View.VISIBLE
    }

    private val insetsController: WindowInsetsControllerCompat
        get() = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
}
