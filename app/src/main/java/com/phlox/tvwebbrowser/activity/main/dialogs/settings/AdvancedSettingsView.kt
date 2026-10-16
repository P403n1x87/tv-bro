package com.phlox.tvwebbrowser.activity.main.dialogs.settings

import android.app.AlertDialog
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.phlox.tvwebbrowser.Config
import com.phlox.tvwebbrowser.R
import com.phlox.tvwebbrowser.TVBro
import com.phlox.tvwebbrowser.activity.main.MainActivity
import com.phlox.tvwebbrowser.utils.activity
import com.phlox.tvwebbrowser.webengine.WebEngineFactory
import kotlinx.coroutines.launch

class AdvancedSettingsView(
    context: Context, private val deps: SettingsDeps
) : SettingsPageView(context) {

    private val settingsModel = deps.settings

    init {
        setItems(buildItems())
    }

    private fun buildItems(): List<SettingsItem> = mutableListOf<SettingsItem>().apply {
        add(
            SettingsItem.Choice(
                iconRes = R.drawable.ic_settings_devices_24dp,
                titleRes = R.string.settings_web_engine,
                summaryRes = R.string.settings_web_engine_summary,
                value = { settingsModel.webEngine },
                entries = { Config.SupportedWebEngines.toList() },
                selectedIndex = { Config.SupportedWebEngines.indexOf(settingsModel.webEngine).coerceAtLeast(0) },
                onSelected = { position -> onWebEngineSelected(position) },
                visible = { WebEngineFactory.getProviders().size > 1 }
            )
        )
        add(
            SettingsItem.Toggle(
                iconRes = R.drawable.ic_settings_bug_24dp,
                titleRes = R.string.settings_web_engine_debug,
                summaryRes = R.string.settings_web_engine_debug_summary,
                isChecked = { settingsModel.webEngineDebug },
                onCheckedChange = { onWebEngineDebugChanged(it) }
            )
        )
        add(
            SettingsItem.Action(
                iconRes = R.drawable.ic_settings_storage_24dp,
                titleRes = R.string.clear_web_cache,
                onClick = { clearWebCache() }
            )
        )
    }

    private fun onWebEngineSelected(position: Int) {
        val target = Config.SupportedWebEngines[position]
        if (settingsModel.webEngine == target) {
            post { rebuild() }
            return
        }
        when {
            target == Config.ENGINE_GECKO_VIEW && !Config.canRecommendGeckoView() ->
                confirm(R.string.settings_engine_change_gecko_msg) { applyWebEngine(target) }

            target == Config.ENGINE_WEB_VIEW ->
                confirm(R.string.settings_engine_change_webview_msg) { applyWebEngine(target) }

            else -> applyWebEngine(target)
        }
    }

    private fun applyWebEngine(target: String) {
        settingsModel.webEngine = target
        showRestartDialog()
    }

    private fun confirm(messageRes: Int, onConfirm: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle(R.string.warning)
            .setMessage(messageRes)
            .setPositiveButton(R.string.ok) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.cancel) { _, _ -> post { rebuild() } }
            .setOnCancelListener { post { rebuild() } }
            .show()
    }

    private fun onWebEngineDebugChanged(enabled: Boolean) {
        if (!enabled) {
            settingsModel.webEngineDebug = false
            post { rebuild() }
            return
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.warning)
            .setMessage(R.string.web_engine_debug_warning_message)
            .setPositiveButton(R.string.ok) { _, _ ->
                settingsModel.webEngineDebug = true
                Toast.makeText(context, context.getString(R.string.need_restart), Toast.LENGTH_SHORT).show()
                post { rebuild() }
            }
            .setNegativeButton(R.string.cancel) { _, _ -> post { rebuild() } }
            .setOnCancelListener { post { rebuild() } }
            .show()
    }

    private fun clearWebCache() {
        val host = activity as? MainActivity ?: return
        host.lifecycleScope.launch {
            WebEngineFactory.clearCache(context)
            Toast.makeText(context, android.R.string.ok, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRestartDialog() {
        AlertDialog.Builder(context)
            .setTitle(R.string.need_restart)
            .setMessage(R.string.need_restart_message)
            .setPositiveButton(R.string.exit) { _, _ ->
                TVBro.instance.needToExitProcessAfterMainActivityFinish = true
                TVBro.instance.needRestartMainActivityAfterExitingProcess = true
                activity?.finish()
            }
            .setCancelable(false)
            .show()
    }
}
