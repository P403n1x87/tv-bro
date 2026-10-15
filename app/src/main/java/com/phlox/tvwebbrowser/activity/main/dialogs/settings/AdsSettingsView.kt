package com.phlox.tvwebbrowser.activity.main.dialogs.settings

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.phlox.tvwebbrowser.R
import com.phlox.tvwebbrowser.utils.activity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdsSettingsView(
    context: Context, private val deps: SettingsDeps
) : SettingsPageView(context) {

    private val adblockModel = deps.adblock

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
                isChecked = { adblockModel.adBlockEnabled },
                onCheckedChange = { enabled ->
                    adblockModel.adBlockEnabled = enabled
                    post { rebuild() }
                }
            )
        )
        add(
            SettingsItem.TextInput(
                iconRes = R.drawable.ic_settings_link_24dp,
                titleRes = R.string.settings_ad_block_url,
                value = { adblockModel.adBlockListUrl },
                hint = { context.getString(R.string.url) },
                onCommit = { url ->
                    adblockModel.setAdBlockListUrl(url)
                    post { rebuild() }
                },
                visible = { adblockModel.adBlockEnabled }
            )
        )
        add(
            SettingsItem.Info(
                titleRes = R.string.last_update,
                value = { lastUpdateText() },
                visible = { adblockModel.adBlockEnabled }
            )
        )
        add(
            SettingsItem.Action(
                iconRes = R.drawable.ic_refresh_grey_900_24dp,
                titleRes = R.string.settings_update_ad_block_list,
                isInProgress = { adblockModel.clientLoading.value },
                onClick = { adblockModel.loadAdBlockList(true) },
                visible = { adblockModel.adBlockEnabled }
            )
        )
        add(
            SettingsItem.Toggle(
                iconRes = R.drawable.ic_settings_link_24dp,
                titleRes = R.string.settings_block_redirects,
                summaryRes = R.string.settings_block_redirects_summary,
                isChecked = { adblockModel.blockAutomaticRedirects },
                onCheckedChange = { adblockModel.blockAutomaticRedirects = it }
            )
        )
    }

    private fun lastUpdateText(): CharSequence {
        val lastUpdate = adblockModel.adBlockListLastUpdate
        if (lastUpdate == 0L) return context.getString(R.string.never)
        return SimpleDateFormat("hh:mm dd MMMM yyyy", Locale.getDefault())
            .format(Date(lastUpdate))
    }
}
