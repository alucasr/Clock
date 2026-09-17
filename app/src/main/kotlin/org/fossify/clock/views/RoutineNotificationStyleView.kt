package org.fossify.clock.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import org.fossify.clock.models.ROUTINE_STYLE_CONTINUOUS
import org.fossify.clock.models.ROUTINE_STYLE_DISCREET

/**
 * Custom square-waveform ("digital signal") toggle used to pick a Routine's notification style,
 * per the design agreed with the user:
 * - resting state (no alert) = the line stays DOWN.
 * - when it alerts, the line goes UP.
 * - CONTINUA (default): the line goes up briefly then quickly back down -- a short,
 *   non-blocking pulse the user doesn't have to act on.
 * - DISCRETA: the line goes up and STAYS up longer -- until the user explicitly stops it.
 *
 * Tapping the view toggles [notificationStyle] and redraws.
 */
class RoutineNotificationStyleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var notificationStyle: Int = ROUTINE_STYLE_CONTINUOUS
        set(value) {
            field = value
            invalidate()
        }

    var onStyleChanged: ((Int) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }

    init {
        setOnClickListener {
            notificationStyle = if (notificationStyle == ROUTINE_STYLE_CONTINUOUS) {
                ROUTINE_STYLE_DISCREET
            } else {
                ROUTINE_STYLE_CONTINUOUS
            }
            onStyleChanged?.invoke(notificationStyle)
        }
    }

    fun setLineColor(color: Int) {
        paint.color = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val margin = h * 0.2f
        val lowY = h - margin
        val highY = margin

        // proportions along the width, as fractions of total width
        val restFraction: Float = if (notificationStyle == ROUTINE_STYLE_DISCREET) 0.2f else 0.25f
        val riseStartFraction = restFraction

        val restEndX = w * restFraction
        val riseStartX = w * riseStartFraction

        val path = android.graphics.Path()
        path.moveTo(0f, lowY)
        path.lineTo(restEndX, lowY)
        path.lineTo(riseStartX, highY)

        if (notificationStyle == ROUTINE_STYLE_DISCREET) {
            // stays up until the end
            path.lineTo(w, highY)
        } else {
            // brief pulse: goes back down shortly after rising, then stays down
            val fallStartX = w * 0.55f
            val fallEndX = w * 0.63f
            path.lineTo(fallStartX, highY)
            path.lineTo(fallEndX, lowY)
            path.lineTo(w, lowY)
        }

        canvas.drawPath(path, paint)
    }
}
