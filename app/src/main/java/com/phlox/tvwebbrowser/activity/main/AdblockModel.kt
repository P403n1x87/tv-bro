package com.phlox.tvwebbrowser.activity.main

import android.net.Uri
import com.brave.adblock.AdBlockClient
import com.brave.adblock.AdBlockClient.FilterOption
import com.brave.adblock.Utils
import com.phlox.tvwebbrowser.AppContext
import com.phlox.tvwebbrowser.Config
import com.phlox.tvwebbrowser.TVBro
import com.phlox.tvwebbrowser.config.AdblockConfig
import com.phlox.tvwebbrowser.utils.activemodel.ActiveModel
import com.phlox.tvwebbrowser.utils.observable.ObservableValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.Calendar

class AdblockModel(
    val config: Config = AppContext.provideConfig(),
    private val filesDir: () -> File = { TVBro.instance.filesDir },
) : ActiveModel() {
    companion object {
        val TAG: String = AdblockModel::class.java.simpleName

        const val SERIALIZED_LIST_FILE = "adblock_ser.dat"
        const val AUTO_UPDATE_INTERVAL_MINUTES = 60 * 24 * 30 // 30 days
    }

    private var client: AdBlockClient? = null
    val clientLoading = ObservableValue(false)

    /**
     * Set when a list load fails, so the host can surface it (the model shows no UI itself); the
     * host resets it once it has shown the message.
     */
    val loadError = ObservableValue(false)

    init {
        loadAdBlockList(false)
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    fun loadAdBlockList(forceReload: Boolean) =
        modelScope.launch {
            if (clientLoading.value) return@launch
            val checkDate = Calendar.getInstance()
            checkDate.timeInMillis = config.adblockConfig.adBlockListLastUpdate
            checkDate.add(Calendar.MINUTE, AUTO_UPDATE_INTERVAL_MINUTES)
            val now = Calendar.getInstance()
            val needUpdate = forceReload || checkDate.before(now)
            clientLoading.value = true
            val client = AdBlockClient()
            var success = false
            withContext(Dispatchers.IO) ioContext@{
                val serializedFile = File(filesDir(), SERIALIZED_LIST_FILE)
                if ((!needUpdate) && serializedFile.exists() && client.deserialize(serializedFile.absolutePath)) {
                    success = true
                    return@ioContext
                }
                try {
                    val easyList =
                        URL(config.adblockConfig.adBlockListURL.value)
                            .openConnection()
                            .inputStream
                            .bufferedReader()
                            .use { it.readText() }
                    success = client.parse(easyList)
                    client.serialize(serializedFile.absolutePath)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            this@AdblockModel.client = client
            config.adblockConfig.adBlockListLastUpdate = now.timeInMillis
            if (!success) {
                loadError.value = true
            }
            clientLoading.value = false
        }

    fun isAd(
        url: Uri,
        type: String?,
        baseUri: Uri,
    ): Boolean {
        val client = client ?: return false
        val baseHost = baseUri.host
        val filterOption =
            try {
                mapRequestToFilterOption(url, type)
            } catch (e: Exception) {
                return false
            }
        val result =
            try {
                baseHost != null && client.matches(url.toString(), filterOption, baseHost)
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        return result
    }

    private fun mapRequestToFilterOption(
        url: Uri?,
        type: String?,
    ): FilterOption? = AdblockRequestType.filterOptionFor(url, type)

    // Protection settings (the Ads & privacy settings page renders and writes these)
    var adBlockEnabled: Boolean
        get() = config.adblockConfig.adBlockEnabled
        set(value) {
            config.adblockConfig.adBlockEnabled = value
        }

    val adBlockListUrl: String get() = config.adblockConfig.adBlockListURL.value

    fun setAdBlockListUrl(url: String) {
        config.adblockConfig.adBlockListURL.value = url.trim().ifEmpty { AdblockConfig.DEFAULT_ADBLOCK_LIST_URL }
    }

    val adBlockListLastUpdate: Long get() = config.adblockConfig.adBlockListLastUpdate

    var blockAutomaticRedirects: Boolean
        get() = config.blockAutomaticRedirects
        set(value) {
            config.blockAutomaticRedirects = value
        }
}

/**
 * Maps a request to the ad-block filter option it belongs to. Pure policy (no engine): the content
 * type wins, then the URL extension. Kept out of [AdblockModel] so it can be tested without the
 * native client.
 */
internal object AdblockRequestType {
    fun filterOptionFor(
        url: Uri?,
        type: String?,
    ): FilterOption {
        if (type != null) {
            if (type == "image" || type.contains("image/")) {
                return FilterOption.IMAGE
            }
            if (type == "style" || type.contains("/css")) {
                return FilterOption.CSS
            }
            if (type == "script" || type.contains("javascript")) {
                return FilterOption.SCRIPT
            }
            if (type.contains("video/")) {
                return FilterOption.OBJECT
            }
        }
        if (url != null) {
            if (Utils.uriHasExtension(url, "css")) {
                return FilterOption.CSS
            }
            if (Utils.uriHasExtension(url, "js")) {
                return FilterOption.SCRIPT
            }
            if (Utils.uriHasExtension(url, "png", "jpg", "jpeg", "webp", "svg", "gif", "bmp", "tiff")) {
                return FilterOption.IMAGE
            }
            if (Utils.uriHasExtension(url, "mp4", "mov", "avi")) {
                return FilterOption.OBJECT
            }
        }
        return FilterOption.UNKNOWN
    }
}
