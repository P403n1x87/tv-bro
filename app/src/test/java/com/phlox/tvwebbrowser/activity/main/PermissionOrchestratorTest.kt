package com.phlox.tvwebbrowser.activity.main

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as RobolectricConfig

/**
 * Tests for [PermissionOrchestrator]'s routing: voice search first, then the engine, then the
 * download coordinator, and the activity results.
 */
@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [26], application = Application::class)
class PermissionOrchestratorTest {

    private val host = FakeDownloadHost()
    private val coordinator = DownloadCoordinator(host) { }

    private var voicePermissionsHandled = false
    private var voiceActivityHandled = false
    private var engineHandled = false
    private var engineCalls = 0

    private lateinit var orchestrator: PermissionOrchestrator

    @Before
    fun setUp() {
        voicePermissionsHandled = false
        voiceActivityHandled = false
        engineHandled = false
        engineCalls = 0
        orchestrator = PermissionOrchestrator(
            downloadCoordinator = coordinator,
            voiceSearchPermissionsResult = { _, _, _ -> voicePermissionsHandled },
            voiceSearchActivityResult = { _, _, _ -> voiceActivityHandled },
            enginePermissionsResult = { _, _, _ ->
                engineCalls++
                engineHandled
            },
        )
    }

    @Test
    fun voiceSearchShortCircuitsTheEngine() {
        voicePermissionsHandled = true

        val handled = orchestrator.onRequestPermissionsResult(7, emptyArray(), intArrayOf())

        assertTrue(handled)
        assertEquals("the engine must not see a voice-search result", 0, engineCalls)
    }

    @Test
    fun theEngineIsConsultedBeforeTheDownloads() {
        engineHandled = true

        val handled = orchestrator.onRequestPermissionsResult(7, emptyArray(), intArrayOf())

        assertTrue(handled)
        assertEquals(1, engineCalls)
    }

    @Test
    fun theDownloadPermissionResultsAreClaimedHere() {
        val handled = orchestrator.onRequestPermissionsResult(
            DownloadCoordinator.MY_PERMISSIONS_REQUEST_EXTERNAL_STORAGE_ACCESS,
            emptyArray(),
            intArrayOf(PackageManager.PERMISSION_DENIED),
        )

        assertTrue(handled)
        assertEquals(1, engineCalls)
    }

    @Test
    fun anUnknownRequestIsLeftToTheCaller() {
        assertFalse(orchestrator.onRequestPermissionsResult(4242, emptyArray(), intArrayOf()))
        assertEquals(1, engineCalls)
    }

    @Test
    fun activityResultsGoToVoiceSearch() {
        assertFalse(orchestrator.onActivityResult(1, 1, Intent()))

        voiceActivityHandled = true
        assertTrue(orchestrator.onActivityResult(1, 1, Intent()))
    }

    @Test
    fun requestCodesAreAllocatedMonotonically() {
        val first = orchestrator.nextRequestCode()
        val second = orchestrator.nextRequestCode()

        assertEquals(first + 1, second)
    }
}
