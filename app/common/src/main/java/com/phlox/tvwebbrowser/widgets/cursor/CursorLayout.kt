package com.phlox.tvwebbrowser.widgets.cursor

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.phlox.tvwebbrowser.Config
import com.phlox.tvwebbrowser.utils.DPADNavigationEventsAdapter


/**
 * Created by PDT on 25.08.2016.
 */
class CursorLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null):
    FrameLayout(context, attrs) {
    var cursorEnabled: Boolean
        get() = !willNotDraw()
        set(value) {
            setWillNotDraw(!value)
        }

    /**
     * The app [Config], handed by the composition root right after inflation. The cursor widgets
     * read the live cursor settings from it instead of the `AppContext` global.
     */
    lateinit var config: Config

    /** Built on first use, so it can pick up [config] set by the host after inflation. */
    val cursorDrawerDelegate: CursorDrawerDelegate by lazy {
        CursorDrawerDelegate(context, this, config).also { it.init() }
    }
    private val inputEventsAdapter = DPADNavigationEventsAdapter(
        onEmulatedKeyEvent = { keyEvent ->
            cursorDrawerDelegate.dispatchKeyEvent(keyEvent)
        },
        motionAxesTranslationEnabled = { !config.disableMotionAxesDpadNavigation },
        isSoftwareKeyboardVisible = {
            ViewCompat.getRootWindowInsets(rootView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        },
    )

    init {
        init()
    }

    private fun init() {
        if (isInEditMode) {
            return
        }
        setWillNotDraw(false)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (isInEditMode || willNotDraw()) {
            return
        }
        cursorDrawerDelegate.onSizeChanged(w, h, ow, oh)
    }

    override fun setWillNotDraw(willNotDraw: Boolean) {
        inputEventsAdapter.resetState()
        super.setWillNotDraw(willNotDraw)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        Log.d("CursorLayout", "dispatchKeyEvent: $event")

        if (willNotDraw()) return super.dispatchKeyEvent(event)

        if (inputEventsAdapter.dispatchKeyEvent(event)) {
            return true
        }

        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        Log.d("CursorLayout", "dispatchGenericMotionEvent: $event")

        if (willNotDraw()) return super.dispatchGenericMotionEvent(event)

        if (inputEventsAdapter.dispatchGenericMotionEvent(event)) {
            return true
        }

        return super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (isInEditMode || willNotDraw()) {
            return
        }

        cursorDrawerDelegate.dispatchDraw(canvas)
    }
}
