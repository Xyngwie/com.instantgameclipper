package com.instantgameclipper.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import com.instantgameclipper.service.ClipperForegroundService
import kotlin.math.abs

class ClipOverlay(private val context: Context) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var button: Button? = null
    private var params: WindowManager.LayoutParams? = null

    fun show() {
        if (button != null) return
        if (!Settings.canDrawOverlays(context)) return

        val slop = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            12f,
            context.resources.displayMetrics,
        )

        val view = Button(context).apply {
            text = "CLIP"
            alpha = 0.88f
            textSize = 14f
        }

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = context.resources.displayMetrics.heightPixels / 3
        }

        @SuppressLint("ClickableViewAccessibility")
        view.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var touchRawX = 0f
            private var touchRawY = 0f
            private var dragged = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val lp = params ?: return false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = lp.x
                        startY = lp.y
                        touchRawX = event.rawX
                        touchRawY = event.rawY
                        dragged = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - touchRawX
                        val dy = event.rawY - touchRawY
                        if (abs(dx) > slop || abs(dy) > slop) dragged = true
                        lp.x = startX + dx.toInt()
                        lp.y = (startY + dy.toInt()).coerceAtLeast(0)
                        windowManager.updateViewLayout(v, lp)
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (!dragged) {
                            ClipperForegroundService.export(context)
                        } else {
                            snapToEdge(v, lp)
                        }
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(view, layoutParams)
        button = view
        params = layoutParams
    }

    fun hide() {
        val view = button ?: return
        runCatching { windowManager.removeView(view) }
        button = null
        params = null
    }

    private fun snapToEdge(view: View, lp: WindowManager.LayoutParams) {
        val screenW = context.resources.displayMetrics.widthPixels
        val viewW = if (view.width > 0) view.width else 160
        val mid = lp.x + viewW / 2
        lp.x = if (mid < screenW / 2) 24 else (screenW - viewW - 24).coerceAtLeast(0)
        windowManager.updateViewLayout(view, lp)
    }
}
