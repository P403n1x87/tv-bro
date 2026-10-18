package com.phlox.tvwebbrowser.domain

import com.phlox.tvwebbrowser.model.HostConfig

/**
 * Popup / new-window blocking rules, extracted from `MainActivity.WebEngineCallback` so the
 * per-host levels are testable in isolation from the engine plumbing.
 *
 * The level itself comes from the per-host configuration (`HostConfig.popupBlockLevel`, see
 * `TabsModel.findHostConfig`); a missing host config or an unset level falls back to
 * [HostConfig.DEFAULT_BLOCK_POPUPS_VALUE], matching the behaviour the callback had inline.
 */
object PopupPolicy {
    /**
     * Whether a new window request must be blocked for the given per-host popup level.
     *
     * @param dialog whether the request comes from a `window.open(..., "dialog")`-style call.
     * @param userGesture whether a user gesture (tap/key press) preceded the request.
     * @param popupBlockLevel the per-host level, or null to use the default level.
     */
    fun shouldBlockNewWindow(
        dialog: Boolean,
        userGesture: Boolean,
        popupBlockLevel: Int?,
    ): Boolean =
        when (popupBlockLevel ?: HostConfig.DEFAULT_BLOCK_POPUPS_VALUE) {
            HostConfig.POPUP_BLOCK_NONE -> false
            HostConfig.POPUP_BLOCK_DIALOGS -> dialog
            HostConfig.POPUP_BLOCK_NEW_AUTO_OPENED_TABS -> dialog || !userGesture
            else -> true
        }
}
