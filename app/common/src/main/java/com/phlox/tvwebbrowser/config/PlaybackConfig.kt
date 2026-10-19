package com.phlox.tvwebbrowser.config

import android.content.SharedPreferences

/**
 * Playback preferences, stored in [SharedPreferences]: which player detected streams are handed
 * to, and whether media may autoplay without a user gesture.
 */
class PlaybackConfig(
    private val prefs: SharedPreferences,
) {
    companion object {
        const val PLAYER_KIND_KEY = "player_kind"
        const val ALLOW_AUTOPLAY_MEDIA = "allow_autoplay_media"
    }

    /** Player a detected stream is handed to. Default is the built-in ExoPlayer. */
    enum class PlayerKind {
        BUILT_IN,
        SYSTEM,
        EXTERNAL,
        ;

        companion object {
            /** Stored ordinal → kind; an unknown value falls back to [BUILT_IN]. */
            fun fromStored(ordinal: Int): PlayerKind = entries.getOrElse(ordinal) { BUILT_IN }
        }
    }

    var playerKind: PlayerKind
        get() = PlayerKind.fromStored(prefs.getInt(PLAYER_KIND_KEY, 0))
        set(value) {
            prefs.edit().putInt(PLAYER_KIND_KEY, value.ordinal).apply()
        }

    var allowAutoplayMedia: Boolean
        get() = prefs.getBoolean(ALLOW_AUTOPLAY_MEDIA, false)
        set(value) {
            prefs.edit().putBoolean(ALLOW_AUTOPLAY_MEDIA, value).apply()
        }
}
