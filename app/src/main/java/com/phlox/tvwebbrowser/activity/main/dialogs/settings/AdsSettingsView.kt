package com.phlox.tvwebbrowser.activity.main.dialogs.settings

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.phlox.tvwebbrowser.AppContext
import com.phlox.tvwebbrowser.Config
import com.phlox.tvwebbrowser.R
import com.phlox.tvwebbrowser.utils.activity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdsSettingsView(
    context: Context, private val deps: SettingsDeps
) : SettingsPageView(context) {

    private val adblockModel = deps.adblock
    private val config = AppContext.provideConfig()

    init {
        adblockModel.clientLoading.subscribe(activity as FragmentActivity) { rebuild() }
        setItems(buildItems())
    }

    private fun buildItems(): List<SettingsItem> = mutableListOf<SettingsItem>().apply {
        add(SettingsItem.Header(R.string.settings_section_protection))
        add(
            SettingsItem.Toggle(
                iconRes = R.drawable.ic_settings_block_24dp,
                titleRes = R.string.settings_block_ads,
                summaryRes = R.string.settings_block_ads_summary,
                isChecked = { config.adBlockEnabled },
                onCheckedChange = { enabled ->
                    config.adBlockEnabled = enabled
                    post { rebuild() }
                }
            )
        )
        add(
            SettingsItem.TextInput(
                iconRes = R.drawable.ic_settings_link_24dp,
                titleRes = R.string.settings_ad_block_url,
                value = { config.adBlockListURL.value },
                hint = { context.getString(R.string.url) },
                onCommit = { url ->
                    config.adBlockListURL.value = url.trim().ifEmpty { Config.DEFAULT_ADBLOCK_LIST_URL }
                    post { rebuild() }
                },
                visible = { config.adBlockEnabled }
            )
        )
        add(
            SettingsItem.Info(
                titleRes = R.string.last_update,
                value = { lastUpdateText() },
                visible = { config.adBlockEnabled }
            )
        )
        add(
            SettingsItem.Action(
                iconRes = R.drawable.ic_refresh_grey_900_24dp,
                titleRes = R.string.settings_update_ad_block_list,
                isInProgress = { adblockModel.clientLoading.value },
                onClick = { adblockModel.loadAdBlockList(true) },
                visible = { config.adBlockEnabled }
            )
        )
        add(
            SettingsItem.Toggle(
                iconRes = R.drawable.ic_settings_link_24dp,
                titleRes = R.string.settings_block_redirects,
                summaryRes = R.string.settings_block_redirects_summary,
                isChecked = { config.blockAutomaticRedirects },
                onCheckedChange = { config.blockAutomaticRedirects = it }
            )
        )
    }

    private fun lastUpdateText(): CharSequence {
        if (config.adBlockListLastUpdate == 0L) return context.getString(R.string.never)
        return SimpleDateFormat("hh:mm dd MMMM yyyy", Locale.getDefault())
            .format(Date(config.adBlockListLastUpdate))
    }
}
