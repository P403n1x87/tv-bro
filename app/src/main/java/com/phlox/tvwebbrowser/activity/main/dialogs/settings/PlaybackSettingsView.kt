package com.phlox.tvwebbrowser.activity.main.dialogs.settings

import android.content.Context
import com.phlox.tvwebbrowser.Config.PlayerKind
import com.phlox.tvwebbrowser.R

class PlaybackSettingsView(
    context: Context, private val deps: SettingsDeps
) : SettingsPageView(context) {

    private val settingsModel = deps.settings

    init {
        setItems(buildItems())
    }

    private fun buildItems(): List<SettingsItem> = mutableListOf<SettingsItem>().apply {
        add(
            SettingsItem.Toggle(
                iconRes = R.drawable.ic_settings_play_24dp,
                titleRes = R.string.allow_autoplay_media,
                summaryRes = R.string.allow_autoplay_media_summary,
                isChecked = { settingsModel.allowAutoplayMedia },
                onCheckedChange = { settingsModel.allowAutoplayMedia = it }
            )
        )
        add(
            SettingsItem.Choice(
                iconRes = R.drawable.ic_settings_play_24dp,
                titleRes = R.string.settings_video_player,
                summaryRes = R.string.settings_video_player_summary,
                value = { playerLabels()[settingsModel.playerKind.ordinal] },
                entries = { playerLabels() },
                selectedIndex = { settingsModel.playerKind.ordinal },
                onSelected = {
                    settingsModel.playerKind = PlayerKind.entries[it]
                    rebuild()
                }
            )
        )
    }

    private fun playerLabels(): List<CharSequence> = listOf(
        context.getString(R.string.cast_player_internal),
        context.getString(R.string.cast_player_system),
        context.getString(R.string.cast_player_external)
    )
}
