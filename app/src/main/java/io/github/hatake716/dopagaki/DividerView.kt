package io.github.hatake716.dopagaki

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.ContextCompat
import kotlin.math.abs

/**
 * 唯一のアプリ UI である境界線ハンドル（SPEC.md §2, §3, §6）。
 * ドラッグ = 比率変更 / ダブルタップ = 1:2 に戻す / 長押しして離す = 再読み込み。
 * 長押しは「800ms 以上、タッチスロップ内で動かさずに離した」ときだけ発火させ、
 * ドラッグ開始前に指を止めただけで再読み込みが走るのを防ぐ。
 */
class DividerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    interface Listener {
        /** ドラッグ中。rawY は画面座標 */
        fun onDragTo(rawY: Float)

        /** ドラッグ終了（ここで比率を保存する） */
        fun onDragEnd()

        /** ダブルタップ: 比率を 1:2 に戻す */
        fun onReset()

        /** 長押しして離した: 最後に触ったペインを再読み込み */
        fun onLongPressReload()
    }

    var listener: Listener? = null

    private val density = resources.displayMetrics.density
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.divider_line)
    }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.divider_pill)
    }
    private val pillRect = RectF()

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downTime = 0L
    private var downRawY = 0f
    private var dragging = false

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                listener?.onReset()
                return true
            }
        },
    )

    override fun onDraw(canvas: Canvas) {
        val centerY = height / 2f
        val lineHalf = LINE_HEIGHT_DP * density / 2f
        canvas.drawRect(0f, centerY - lineHalf, width.toFloat(), centerY + lineHalf, linePaint)

        val pillHalfWidth = PILL_WIDTH_DP * density / 2f
        val pillHalfHeight = PILL_HEIGHT_DP * density / 2f
        pillRect.set(
            width / 2f - pillHalfWidth,
            centerY - pillHalfHeight,
            width / 2f + pillHalfWidth,
            centerY + pillHalfHeight,
        )
        canvas.drawRoundRect(pillRect, pillHalfHeight, pillHalfHeight, pillPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downTime = SystemClock.uptimeMillis()
                downRawY = event.rawY
                dragging = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (!dragging && abs(event.rawY - downRawY) > touchSlop) {
                    dragging = true
                }
                if (dragging) {
                    listener?.onDragTo(event.rawY)
                }
            }

            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    listener?.onDragEnd()
                } else if (SystemClock.uptimeMillis() - downTime >= LONG_PRESS_MS) {
                    listener?.onLongPressReload()
                }
                dragging = false
            }

            MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    listener?.onDragEnd()
                }
                dragging = false
            }
        }
        return true
    }

    companion object {
        private const val LINE_HEIGHT_DP = 3f
        private const val PILL_WIDTH_DP = 40f
        private const val PILL_HEIGHT_DP = 5f
        private const val LONG_PRESS_MS = 800L
    }
}
