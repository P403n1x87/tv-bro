package com.phlox.tvwebbrowser.activity.main

import android.app.Application
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import org.robolectric.annotation.Config as RobolectricConfig

/**
 * Tests for [DefaultWebViewDataDirManager] on the filesystem path (below API 30), which is also what
 * exercises the lazy directory providers: the manager is built from temp directories without
 * touching `AppContext`.
 */
@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [26], application = Application::class)
class DefaultWebViewDataDirManagerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun manager(): Pair<DefaultWebViewDataDirManager, File> {
        val root = tmp.root
        val files = File(root, "files").apply { mkdirs() }
        val cache = File(root, "cache").apply { mkdirs() }
        return DefaultWebViewDataDirManager({ files }, { cache }) to root
    }

    @Test
    fun belowApi30ASessionMovesTheLiveDirectoriesAsideAndRestoresThem() {
        val (manager, root) = manager()
        val files = File(root, "files")
        val cache = File(root, "cache")
        val liveData = File(root, "app_webview").apply { mkdirs() }
        val liveCache = File(cache, "WebView").apply { mkdirs() }

        manager.prepareIncognitoSession()

        assertFalse("the live data dir is moved aside", liveData.exists())
        assertFalse("the live cache dir is moved aside", liveCache.exists())
        assertTrue(File(root, "app_webview_backup").exists())
        assertTrue(File(cache, "WebView_backup").exists())
        assertTrue(files.exists())

        manager.clearIncognitoData()

        assertTrue("the live data dir is restored", liveData.exists())
        assertTrue("the live cache dir is restored", liveCache.exists())
        assertFalse(File(root, "app_webview_backup").exists())
        assertFalse(File(cache, "WebView_backup").exists())
    }

    @Test
    fun preparingASecondSessionIsANoOp() {
        val (manager, root) = manager()
        File(root, "app_webview").apply { mkdirs() }
        File(File(root, "cache"), "WebView").apply { mkdirs() }

        manager.prepareIncognitoSession()
        File(File(root, "app_webview_backup"), "sentinel").writeText("keep me")
        manager.prepareIncognitoSession()

        assertTrue(
            "an already-prepared session must not be moved again",
            File(File(root, "app_webview_backup"), "sentinel").exists(),
        )
    }
}
