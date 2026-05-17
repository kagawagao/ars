package com.kagawagao.ars.demo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import com.kagawagao.ars.ArsSkinEngine
import com.kagawagao.ars.SkinChangeListener
import com.kagawagao.ars.SkinPackage

/**
 * Custom View that demonstrates skin-aware rendering.
 *
 * Draws a colored circle + label using colors from [Context.getResources].
 * Since [ArsActivity] wraps the Context with [SkinContextWrapper], every
 * call to `context.resources.getColor()` returns the skin-aware value.
 *
 * Registers itself as a [SkinChangeListener] to auto-invalidate on skin switch.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class DemoCustomView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    private var currentAccentColor = 0
    private var currentTextColor = 0

    private val skinListener = object : SkinChangeListener {
        override fun onSkinChanged(previous: SkinPackage?, current: SkinPackage?) {
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ArsSkinEngine.registerSkinChangeListener(skinListener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ArsSkinEngine.unregisterSkinChangeListener(skinListener)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Always read colors from resources — never cache across skin switches
        val accent = context.resources.getColor(R.color.theme_accent, null)
        val text = context.resources.getColor(R.color.theme_text, null)

        val cx = width / 2f
        val cy = height / 2f - 10f
        val radius = 30f.coerceAtMost(width / 3f).coerceAtMost(height / 3f)

        // Draw accent circle
        paint.color = accent
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, radius, paint)

        // Draw label
        paint.color = text
        paint.style = Paint.Style.FILL
        canvas.drawText("皮肤感知 View", cx, cy + radius + 40f, paint)
    }
}
