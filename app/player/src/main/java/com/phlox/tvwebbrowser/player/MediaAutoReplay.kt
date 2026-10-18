package com.phlox.tvwebbrowser.player

import android.os.Handler

/**
 * Engine-agnostic recovery for a live stream the native player gave up on: reload the page, then
 * replay the same stream (matched by [MediaUrlIdentity.key]) once the page re-issues it. A live
 * HLS manifest slides forward, so a retry at the stalled position asks for an already-purged
 * segment; reloading restarts the page's own player at the live edge, and replaying the re-detected
 * stream does the same for the native player.
 *
 * Lives outside the two engine implementations so the WebView and GeckoView adapters do not drift.
 * The caller supplies how to reload, how to look the stream up again and how to start playback;
 * polling runs on the injected [Scheduler] (the main looper in production).
 *
 * At most one automatic replay happens per user-initiated playback: [onUserPlaybackStarted] resets
 * the guard, so a replay that also fails falls through to the ordinary "unavailable" message.
 */
class MediaAutoReplay(
    private val scheduler: Scheduler,
    private val onReload: () -> Unit,
    private val findReplayUrl: (String) -> String?,
    private val onReplay: (String) -> Unit,
    private val initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
    private val intervalMs: Long = DEFAULT_INTERVAL_MS,
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
) {
    /** Small seam over a main-thread handler so the state machine is unit-testable off Android. */
    interface Scheduler {
        fun postDelayed(
            delayMs: Long,
            task: Runnable,
        )

        fun removeCallbacks(task: Runnable)
    }

    private var pendingKey: String? = null
    private var attempts = 0
    private var used = false

    private val tick = Runnable { run() }

    /** A user-initiated playback resets the one-shot guard and abandons any pending replay. */
    fun onUserPlaybackStarted() {
        used = false
        cancel()
    }

    /** Whether an automatic replay is still allowed (one per user-initiated playback). */
    fun canStart(): Boolean = !used

    /** Reloads the page and replays [key] once it reappears, at most once until the user plays again. */
    fun start(key: String) {
        used = true
        attempts = 0
        pendingKey = key
        onReload()
        scheduler.removeCallbacks(tick)
        scheduler.postDelayed(initialDelayMs, tick)
    }

    fun cancel() {
        pendingKey = null
        scheduler.removeCallbacks(tick)
    }

    private fun run() {
        val key = pendingKey ?: return
        val url = findReplayUrl(key)
        if (url != null) {
            pendingKey = null
            onReplay(url)
            return
        }
        if (++attempts < maxAttempts) {
            scheduler.postDelayed(intervalMs, tick)
        } else {
            pendingKey = null
        }
    }

    companion object {
        const val DEFAULT_INITIAL_DELAY_MS = 1500L
        const val DEFAULT_INTERVAL_MS = 1000L
        const val DEFAULT_MAX_ATTEMPTS = 15
    }
}

/** [MediaAutoReplay.Scheduler] backed by a [Handler] (the main looper in production). */
class HandlerScheduler(
    private val handler: Handler,
) : MediaAutoReplay.Scheduler {
    override fun postDelayed(
        delayMs: Long,
        task: Runnable,
    ) {
        handler.postDelayed(task, delayMs)
    }

    override fun removeCallbacks(task: Runnable) {
        handler.removeCallbacks(task)
    }
}
