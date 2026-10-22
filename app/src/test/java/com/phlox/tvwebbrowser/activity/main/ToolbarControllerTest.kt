package com.phlox.tvwebbrowser.activity.main

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Looper
import android.view.View
import com.phlox.tvwebbrowser.Config
import com.phlox.tvwebbrowser.databinding.ActivityMainBinding
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config as RobolectricConfig

@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [26], application = Application::class)
class ToolbarControllerTest {
    private lateinit var activity: Activity
    private lateinit var binding: ActivityMainBinding
    private lateinit var toolbarController: ToolbarController
    private var focusCalled = 0

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        binding = ActivityMainBinding.inflate(activity.layoutInflater)
        binding.flWebViewContainer.config =
            Config(activity.getSharedPreferences("toolbar_test", Context.MODE_PRIVATE))
        activity.setContentView(binding.root)
        shadowOf(Looper.getMainLooper()).idle()

        toolbarController =
            ToolbarController(
                activity = activity,
                vb = binding,
                keepScreenOn = { false },
                focusEngine = { focusCalled++ },
            )
        toolbarController.applyOverlayStyle()
        binding.rlActionBar.visibility = View.VISIBLE
    }

    @Test
    fun hideTopBarHidesActionBar() {
        assertEquals(View.VISIBLE, binding.rlActionBar.visibility)
        toolbarController.hideTopBar()
        assertEquals(View.GONE, binding.rlActionBar.visibility)
    }

    @Test
    fun showTopBarShowsActionBar() {
        binding.rlActionBar.visibility = View.GONE
        toolbarController.showTopBar()
        assertEquals(View.VISIBLE, binding.rlActionBar.visibility)
    }

    @Test
    fun hideTopBarDoesNothingInFullscreen() {
        toolbarController.enterFullscreen()
        assertEquals(View.GONE, binding.rlActionBar.visibility)
        toolbarController.hideTopBar()
        assertEquals(View.GONE, binding.rlActionBar.visibility)
    }

    @Test
    fun showTopBarDoesNothingInFullscreen() {
        toolbarController.enterFullscreen()
        assertEquals(View.GONE, binding.rlActionBar.visibility)
        toolbarController.showTopBar()
        assertEquals(View.GONE, binding.rlActionBar.visibility)
    }
}
