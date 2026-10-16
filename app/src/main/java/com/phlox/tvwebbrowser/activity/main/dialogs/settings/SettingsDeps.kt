package com.phlox.tvwebbrowser.activity.main.dialogs.settings

import com.phlox.tvwebbrowser.activity.main.AdblockModel
import com.phlox.tvwebbrowser.activity.main.AutoUpdateModel
import com.phlox.tvwebbrowser.activity.main.SettingsModel

/**
 * The models a settings page acts on, handed down by the composition root ([SettingsDialog]).
 *
 * Pages are views: they render state and forward user input, so they must not look the models up in
 * the container themselves (that couples the view to the DI mechanism and to a `Context`).
 */
class SettingsDeps(
    val settings: SettingsModel,
    val adblock: AdblockModel,
    val autoUpdate: AutoUpdateModel
)
