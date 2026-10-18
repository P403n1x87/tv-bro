package com.phlox.tvwebbrowser.model

import android.graphics.Bitmap
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileOutputStream
import org.robolectric.annotation.Config as RobolectricConfig

/**
 * The tab state's file helpers take the directories they operate on; they must never fall back to a
 * global. `AppContext` is deliberately left uninitialised here, so a regression that reaches for it
 * would blow up rather than silently pass.
 */
@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [26])
class WebTabStateTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun removeFilesDeletesTheStateFileAndThumbnailFromTheGivenDirectories() {
        val filesDir = temp.newFolder("files")
        val cacheDir = temp.newFolder("cache")
        val stateFile = File(File(filesDir, WebTabState.TAB_WVSTATES_DIR).apply { mkdirs() }, "state1")
        val thumbnailFile = File(File(cacheDir, WebTabState.TAB_THUMBNAILS_DIR).apply { mkdirs() }, "thumb1.png")
        stateFile.writeText("state")
        thumbnailFile.writeText("thumb")

        val tab =
            WebTabState(
                url = "https://example/",
                thumbnailHash = "thumb1",
                wvStateFileName = "state1",
            )

        tab.removeFiles(filesDir, cacheDir)

        assertFalse("state file removed", stateFile.exists())
        assertFalse("thumbnail removed", thumbnailFile.exists())
        assertNull(tab.wvStateFileName)
        assertNull(tab.thumbnailHash)
    }

    @Test
    fun loadThumbnailReadsFromTheGivenCacheDirectory() {
        val cacheDir = temp.newFolder("cache")
        val thumbnailsDir = File(cacheDir, WebTabState.TAB_THUMBNAILS_DIR).apply { mkdirs() }
        // A decodable PNG, produced through the framework under Robolectric.
        FileOutputStream(File(thumbnailsDir, "thumb1.png")).use { out ->
            Bitmap
                .createBitmap(2, 2, Bitmap.Config.ARGB_8888)
                .compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val tab = WebTabState(url = "https://example/", thumbnailHash = "thumb1")
        assertNotNull("thumbnail found in the injected cache dir", tab.loadThumbnail(cacheDir))

        // A different directory means "no thumbnail": the lookup never falls back to a global.
        val elsewhere = WebTabState(url = "https://example/", thumbnailHash = "thumb1")
        assertNull(elsewhere.loadThumbnail(temp.newFolder("other")))
    }
}
