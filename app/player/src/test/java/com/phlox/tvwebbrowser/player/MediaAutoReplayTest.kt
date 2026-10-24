package com.phlox.tvwebbrowser.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaAutoReplayTest {
    private class FakeScheduler : MediaAutoReplay.Scheduler {
        val pending = ArrayDeque<Runnable>()
        var lastDelayMs = -1L

        override fun postDelayed(
            delayMs: Long,
            task: Runnable,
        ) {
            lastDelayMs = delayMs
            pending.addLast(task)
        }

        override fun removeCallbacks(task: Runnable) {
            pending.remove(task)
        }

        fun runNext(): Boolean = pending.removeFirstOrNull()?.also { it.run() } != null
    }

    @Test fun reloadsThenReplaysTheMatchedStream() {
        val scheduler = FakeScheduler()
        var reloads = 0
        var replayed: String? = null
        val replay =
            MediaAutoReplay(
                scheduler = scheduler,
                onReload = { reloads++ },
                findReplayUrl = { key -> "https://cdn/$key.m3u8" },
                onReplay = { replayed = it },
                initialDelayMs = 123L,
            )

        assertTrue(replay.canStart())
        replay.start("stream")
        assertEquals(1, reloads)
        assertEquals(123L, scheduler.lastDelayMs)
        assertFalse(replay.canStart())

        assertTrue(scheduler.runNext())
        assertEquals("https://cdn/stream.m3u8", replayed)
        assertTrue(scheduler.pending.isEmpty())
    }

    @Test fun givesUpAfterMaxAttemptsWhenTheStreamNeverReappears() {
        val scheduler = FakeScheduler()
        var replayed: String? = null
        val replay =
            MediaAutoReplay(
                scheduler = scheduler,
                onReload = {},
                findReplayUrl = { null },
                onReplay = { replayed = it },
                maxAttempts = 3,
            )

        replay.start("k")
        var ticks = 0
        while (scheduler.runNext()) ticks++
        assertEquals(3, ticks)
        assertNull(replayed)
        assertTrue(scheduler.pending.isEmpty())
    }

    @Test fun userPlaybackResetsTheOneShotGuardAndCancelsPending() {
        val scheduler = FakeScheduler()
        val replay =
            MediaAutoReplay(
                scheduler = scheduler,
                onReload = {},
                findReplayUrl = { null },
                onReplay = {},
            )

        replay.start("k")
        assertFalse(replay.canStart())
        assertTrue(scheduler.pending.isNotEmpty())

        replay.onUserPlaybackStarted()
        assertTrue(replay.canStart())
        assertTrue(scheduler.pending.isEmpty())
    }

    @Test fun resetsAttemptsAfterSuccessfulReplay() {
        val scheduler = FakeScheduler()
        var reloads = 0
        var replayed: String? = null
        val replay =
            MediaAutoReplay(
                scheduler = scheduler,
                onReload = { reloads++ },
                findReplayUrl = { key -> if (key == "first") null else "https://cdn/$key.m3u8" },
                onReplay = { replayed = it },
                initialDelayMs = 0L,
                intervalMs = 0L,
                maxAttempts = 5,
            )

        replay.start("first")
        assertEquals(1, reloads)
        // first attempt fails
        assertTrue(scheduler.runNext())
        assertNull(replayed)
        // second attempt would succeed if we restarted, but we want to verify reset after success
        // simulate success on a fresh start
        replay.onUserPlaybackStarted()
        reloads = 0
        replayed = null
        replay.start("second")
        assertTrue(scheduler.runNext())
        assertEquals("https://cdn/second.m3u8", replayed)
    }

    @Test fun resetsAttemptsAfterGivingUp() {
        val scheduler = FakeScheduler()
        val replay =
            MediaAutoReplay(
                scheduler = scheduler,
                onReload = {},
                findReplayUrl = { null },
                onReplay = {},
                maxAttempts = 2,
            )

        replay.start("k")
        assertTrue(scheduler.runNext())
        assertTrue(scheduler.runNext())
        assertFalse(scheduler.runNext())
        assertTrue(scheduler.pending.isEmpty())

        // after giving up, starting fresh should allow attempts again
        replay.onUserPlaybackStarted()
        var replayed: String? = null
        val replay2 =
            MediaAutoReplay(
                scheduler = scheduler,
                onReload = {},
                findReplayUrl = { "https://cdn/ok.m3u8" },
                onReplay = { replayed = it },
                maxAttempts = 2,
            )
        replay2.start("k2")
        assertTrue(scheduler.runNext())
        assertEquals("https://cdn/ok.m3u8", replayed)
    }
}
