package com.phlox.tvwebbrowser.activity.main.dialogs.settings

import com.phlox.tvwebbrowser.activity.main.AdblockModel
import com.phlox.tvwebbrowser.activity.main.AutoUpdateModel
import com.phlox.tvwebbrowser.activity.main.SettingsModel

/**
 * The models a settings page acts on, handed down by the composition root ([SettingsDialog]), plus
 * the host actions a page cannot perform itself.
 *
 * Pages are views: they render state and forward user input, so they must not look the models up in
 * the container themselves (that couples the view to the DI mechanism and to a `Context`), and they
 * must not cast the hosting `Context` to a concrete activity to reach its helpers. Anything that
 * leaves the dialog (installing the update, opening a link in a new browsing session) is a callback
 * the composition root supplies.
 */
class SettingsDeps(
    val settings: SettingsModel,
    val adblock: AdblockModel,
    val autoUpdate: AutoUpdateModel,
    /** Shows the installer flow for the update that is available right now. */
    val installUpdate: () -> Unit = {},
    /** Opens [url] in a new browsing session of the current mode. */
    val openUrl: (String) -> Unit = {},
)
