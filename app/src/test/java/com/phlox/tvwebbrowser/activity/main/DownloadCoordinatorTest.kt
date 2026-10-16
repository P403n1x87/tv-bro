package com.phlox.tvwebbrowser.activity.main

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config as RobolectricConfig

/**
 * Tests for [DownloadCoordinator]'s permission walk and service binding, driven through a
 * [FakeDownloadHost] so no Activity (or real service) is needed.
 */
@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [26], application = Application::class)
class DownloadCoordinatorTest {

    private lateinit var host: FakeDownloadHost
    private var startedFileName: String? = null

    @Before
    fun setUp() {
        host = FakeDownloadHost()
    }

    private fun coordinator() = DownloadCoordinator(host) { startedFileName = it }

    @Test
    fun theStoragePermissionIsRequestedBeforeTheDownloadStarts() {
        coordinator().requestDownload("https://file.example/a.apk", "", "a.apk", null, stream = null)

        assertEquals(1, host.requested.size)
        assertEquals(Manifest.permission.WRITE_EXTERNAL_STORAGE, host.requested.single().first)
        assertEquals(
            DownloadCoordinator.MY_PERMISSIONS_REQUEST_EXTERNAL_STORAGE_ACCESS,
            host.requested.single().second
        )
        assertNull("the download waits for the permission", startedFileName)
    }

    @Test
    fun anAlreadyGrantedPermissionStartsTheDownloadRightAway() {
        host.granted += Manifest.permission.WRITE_EXTERNAL_STORAGE

        coordinator().requestDownload(
            url = "https://example.com/file.apk", referer = "", originalDownloadFileName = "file.apk",
            userAgent = null, stream = null
        )

        assertTrue(host.requested.isEmpty())
        assertEquals("file.apk", startedFileName)
    }

    @Test
    fun grantingThePermissionStartsThePendingDownload() {
        val coordinator = coordinator()
        coordinator.requestDownload("https://example.com/a.apk", "", "a.apk", null, stream = null)

        val handled = coordinator.onPermissionsResult(
            DownloadCoordinator.MY_PERMISSIONS_REQUEST_EXTERNAL_STORAGE_ACCESS,
            intArrayOf(PackageManager.PERMISSION_GRANTED),
        )

        assertTrue(handled)
        assertEquals("a.apk", startedFileName)
    }

    @Test
    fun aDeniedPermissionDoesNotStartTheDownload() {
        val coordinator = coordinator()
        coordinator.requestDownload("https://example.com/a.apk", "", "a.apk", null, stream = null)

        val handled = coordinator.onPermissionsResult(
            DownloadCoordinator.MY_PERMISSIONS_REQUEST_EXTERNAL_STORAGE_ACCESS,
            intArrayOf(PackageManager.PERMISSION_DENIED),
        )

        assertTrue(handled)
        assertNull(startedFileName)
    }

    @Test
    fun anUnrelatedRequestCodeIsNotClaimed() {
        assertTrue(!coordinator().onPermissionsResult(12345, intArrayOf(PackageManager.PERMISSION_GRANTED)))
    }

    @Test
    fun bindingFollowsTheScreenLifecycle() {
        val coordinator = coordinator()

        coordinator.bind()
        coordinator.unbind()

        assertEquals(1, host.bindCalls)
        assertEquals(1, host.unbindCalls)
    }
}
