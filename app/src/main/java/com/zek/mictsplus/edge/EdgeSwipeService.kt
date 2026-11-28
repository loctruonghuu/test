package com.zek.mictsplus.edge

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.zek.mictsplus.Prefs
import com.zek.mictsplus.R
import com.zek.mictsplus.cts.CircleToSearchTrigger
import com.zek.mictsplus.honor.HonorTranslateTrigger
import com.zek.mictsplus.ui.MainActivity
import kotlin.math.abs

/**
 * Lightweight single foreground overlay service managing:
 *  1) Bottom Horizontal Navigation Bar Pill (Tap x1, Hold, Tap x2, Rainbow/White/Cyan/Transparent skin)
 *  2) Vertical Edge Screen Swipe Handle (10..150% vertical position, Swipe In/Up/Down)
 *
 * Works 100% WITHOUT Accessibility Service or Developer Options.
 */
class EdgeSwipeService : Service() {

    companion object {
        const val ACTION_START = "com.zek.mictsplus.edge.START"
        const val ACTION_RELOAD = "com.zek.mictsplus.edge.RELOAD"
        const val ACTION_STOP = "com.zek.mictsplus.edge.STOP"

        private const val CHANNEL_ID = "edge_swipe_channel"
        private const val NOTIF_ID = 1001
        private const val DOUBLE_TAP_WINDOW_MS = 230L

        fun start(context: Context) {
            val intent = Intent(context, EdgeSwipeService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun reload(context: Context) {
            if (!Prefs.isAnyOverlayEnabled(context)) {
                stop(context)
                return
            }
            val intent = Intent(context, EdgeSwipeService::class.java).apply {
                action = ACTION_RELOAD
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, EdgeSwipeService::class.java))
        }
    }

    private lateinit var windowManager: WindowManager
    private val activeViews = mutableListOf<View>()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || !Prefs.isAnyOverlayEnabled(this)) {
            removeAllOverlays()
            stopSelf()
            return START_NOT_STICKY
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.overlay_permission_required, Toast.LENGTH_SHORT).show()
            Prefs.setEdgeEnabled(this, false)
            Prefs.setNavBarEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }

        val notif = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIF_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIF_ID, notif)
        }

        setupOverlays()
        return START_STICKY
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        removeAllOverlays()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun getRealScreenHeight(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.height()
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(dm)
            dm.heightPixels
        }
    }

    private fun setupOverlays() {
        removeAllOverlays()
        if (Prefs.isNavBarEnabled(this)) {
            addBottomNavBarPill()
        }
        if (Prefs.isEdgeEnabled(this)) {
            val side = Prefs.getEdgeSide(this)
            if (side == Prefs.SIDE_RIGHT || side == Prefs.SIDE_BOTH) {
                addEdgeHandle(isRight = true)
            }
            if (side == Prefs.SIDE_LEFT || side == Prefs.SIDE_BOTH) {
                addEdgeHandle(isRight = false)
            }
        }
    }

    /**
     * Bottom Horizontal Navigation Bar Pill (Easy Tap style)
     * Supports Tap x1, Hold, and Tap x2 with configurable Width, Thickness, Height, Offset, Skin & Opacity.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun addBottomNavBarPill() {
        val density = resources.displayMetrics.density
        val widthPx = (Prefs.getNavWidthDp(this) * density).toInt()
        val thicknessPx = (Prefs.getNavThicknessDp(this) * density).toInt()
        val touchHeightPx = maxOf(thicknessPx + (12 * density).toInt(), (24 * density).toInt())
        val bottomMarginPx = (Prefs.getNavBottomHeightDp(this) * density).toInt()
        val offsetXPx = (Prefs.getNavOffsetXDp(this) * density).toInt()
        val alphaRatio = Prefs.getNavAlpha(this) / 100f
        val skin = Prefs.getNavSkin(this)

        val container = FrameLayout(this)

        val pillDrawable = when (skin) {
            Prefs.SKIN_RAINBOW -> GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    Color.parseColor("#00D2FF"),
                    Color.parseColor("#38EF7D"),
                    Color.parseColor("#FFD200"),
                    Color.parseColor("#FF512F"),
                    Color.parseColor("#DD2476")
                )
            )
            Prefs.SKIN_CYAN -> GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    Color.parseColor("#2193B0"),
                    Color.parseColor("#6DD5ED")
                )
            )
            Prefs.SKIN_TRANSPARENT -> GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
            }
            else -> GradientDrawable().apply {
                setColor(Color.WHITE)
            }
        }.apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = thicknessPx.toFloat()
        }

        val pill = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                widthPx,
                thicknessPx,
                Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            ).apply {
                bottomMargin = (4 * density).toInt()
            }
            background = pillDrawable
            alpha = if (skin == Prefs.SKIN_TRANSPARENT) 0f else alphaRatio
        }
        container.addView(pill)

        val params = WindowManager.LayoutParams(
            widthPx + (16 * density).toInt(),
            touchHeightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            x = offsetXPx
            y = bottomMarginPx
        }

        val moveSlopPx = 22 * density
        var downX = 0f
        var downY = 0f
        var movedTooFar = false
        var holdTriggered = false
        var pendingHoldRunnable: Runnable? = null
        var pendingSingleTapRunnable: Runnable? = null

        fun cancelHold() {
            pendingHoldRunnable?.let { mainHandler.removeCallbacks(it) }
            pendingHoldRunnable = null
        }

        fun cancelSingleTap() {
            pendingSingleTapRunnable?.let { mainHandler.removeCallbacks(it) }
            pendingSingleTapRunnable = null
        }

        container.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    movedTooFar = false
                    holdTriggered = false
                    cancelHold()

                    if (skin != Prefs.SKIN_TRANSPARENT) {
                        pill.animate().scaleY(1.25f).alpha(1f).setDuration(80).start()
                    }

                    val holdAction = Prefs.getNavHoldAction(this)
                    if (holdAction != Prefs.ACTION_NONE) {
                        val r = Runnable {
                            if (!movedTooFar) {
                                holdTriggered = true
                                cancelSingleTap()
                                scheduleActionWithDelay(holdAction)
                            }
                        }
                        pendingHoldRunnable = r
                        mainHandler.postDelayed(r, Prefs.getNavHoldDelayMs(this))
                    }
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = abs(event.rawX - downX)
                    val dy = abs(event.rawY - downY)
                    if (dx > moveSlopPx || dy > moveSlopPx) {
                        movedTooFar = true
                        cancelHold()
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    cancelHold()
                    if (skin != Prefs.SKIN_TRANSPARENT) {
                        pill.animate().scaleY(1f).alpha(alphaRatio).setDuration(120).start()
                    }

                    if (!movedTooFar && !holdTriggered) {
                        val tap1Action = Prefs.getNavTap1Action(this)
                        val tap2Action = Prefs.getNavTap2Action(this)

                        if (tap2Action == Prefs.ACTION_NONE) {
                            // No double-tap configured -> fire single tap immediately
                            if (tap1Action != Prefs.ACTION_NONE) {
                                scheduleActionWithDelay(tap1Action)
                            }
                        } else {
                            if (pendingSingleTapRunnable != null) {
                                // Second tap arrived within window -> Double Tap!
                                cancelSingleTap()
                                scheduleActionWithDelay(tap2Action)
                            } else {
                                // Wait briefly to see if a second tap comes
                                val r = Runnable {
                                    pendingSingleTapRunnable = null
                                    if (tap1Action != Prefs.ACTION_NONE) {
                                        scheduleActionWithDelay(tap1Action)
                                    }
                                }
                                pendingSingleTapRunnable = r
                                mainHandler.postDelayed(r, DOUBLE_TAP_WINDOW_MS)
                            }
                        }
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    cancelHold()
                    if (skin != Prefs.SKIN_TRANSPARENT) {
                        pill.animate().scaleY(1f).alpha(alphaRatio).setDuration(120).start()
                    }
                    true
                }

                else -> false
            }
        }

        try {
            windowManager.addView(container, params)
            activeViews.add(container)
        } catch (_: Throwable) {
        }
    }

    /**
     * Vertical Edge Screen Swipe Handle (supports 10..150% vertical position)
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun addEdgeHandle(isRight: Boolean) {
        val density = resources.displayMetrics.density
        val fullHeight = getRealScreenHeight()
        val widthPx = (Prefs.getEdgeWidthDp(this) * density).toInt()
        val heightPx = (Prefs.getEdgeHeightDp(this) * density).toInt()
        val yPercent = Prefs.getEdgeYPercent(this)

        val baseBottomY = (fullHeight - heightPx).coerceAtLeast(0)
        val yPx = if (yPercent <= 100) {
            (baseBottomY * (yPercent / 100f)).toInt()
        } else {
            val extraRatio = (yPercent - 100) / 50f
            baseBottomY + (extraRatio * heightPx * 0.65f).toInt()
        }

        val alphaRatio = Prefs.getEdgeAlpha(this) / 100f
        val container = FrameLayout(this)

        val pill = View(this).apply {
            val pillWidth = (4 * density).toInt().coerceAtLeast(4)
            val pillHeight = (heightPx * 0.58f).toInt()
            layoutParams = FrameLayout.LayoutParams(
                pillWidth,
                pillHeight,
                (if (isRight) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
            )
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 100f
                setColor(Color.WHITE)
            }
            alpha = alphaRatio * 0.75f
        }
        container.addView(pill)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            container.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                if (v.width > 0 && v.height > 0) {
                    v.systemGestureExclusionRects = listOf(Rect(0, 0, v.width, v.height))
                }
            }
        }

        val params = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = (if (isRight) Gravity.END else Gravity.START) or Gravity.TOP
            x = 0
            y = yPx
        }

        val swipeThresholdPx = 36 * density
        var downX = 0f
        var downY = 0f
        var triggered = false
        var pendingHoldRunnable: Runnable? = null

        fun cancelPendingHold() {
            pendingHoldRunnable?.let { mainHandler.removeCallbacks(it) }
            pendingHoldRunnable = null
        }

        container.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    triggered = false
                    cancelPendingHold()
                    pill.animate().alpha((alphaRatio + 0.45f).coerceAtMost(1f)).scaleX(1.7f).setDuration(90).start()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!triggered && pendingHoldRunnable == null) {
                        val dx = event.rawX - downX
                        val dy = event.rawY - downY
                        val inwardDx = if (isRight) -dx else dx

                        val matchedAction = when {
                            inwardDx > swipeThresholdPx && inwardDx > abs(dy) * 1.1f ->
                                Prefs.getSwipeInAction(this)
                            -dy > swipeThresholdPx * 1.15f && abs(dy) > abs(dx) * 1.2f ->
                                Prefs.getSwipeUpAction(this)
                            dy > swipeThresholdPx * 1.15f && abs(dy) > abs(dx) * 1.2f ->
                                Prefs.getSwipeDownAction(this)
                            else -> null
                        }

                        if (matchedAction != null && matchedAction != Prefs.ACTION_NONE) {
                            val holdMs = Prefs.getHoldDelayMs(this)
                            if (holdMs <= 0L) {
                                triggered = true
                                scheduleActionWithDelay(matchedAction)
                            } else {
                                val r = Runnable {
                                    triggered = true
                                    pendingHoldRunnable = null
                                    scheduleActionWithDelay(matchedAction)
                                }
                                pendingHoldRunnable = r
                                mainHandler.postDelayed(r, holdMs)
                            }
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    cancelPendingHold()
                    pill.animate().alpha(alphaRatio * 0.75f).scaleX(1f).setDuration(140).start()
                    true
                }

                else -> false
            }
        }

        try {
            windowManager.addView(container, params)
            activeViews.add(container)
        } catch (_: Throwable) {
        }
    }

    private fun scheduleActionWithDelay(action: String) {
        val delayMs = Prefs.getTriggerDelayMs(this)
        mainHandler.postDelayed({
            when (action) {
                Prefs.ACTION_CTS -> {
                    val ok = CircleToSearchTrigger.trigger(this)
                    if (!ok) {
                        Toast.makeText(this, R.string.cts_trigger_failed, Toast.LENGTH_SHORT).show()
                    }
                }

                Prefs.ACTION_HONOR_TRANSLATE -> {
                    val res = HonorTranslateTrigger.triggerWithResult(this)
                    if (!res.success) {
                        Toast.makeText(this, "Screen Translate: ${res.detail}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }, delayMs)
    }

    private fun removeAllOverlays() {
        for (v in activeViews) {
            try {
                windowManager.removeView(v)
            } catch (_: Throwable) {
            }
        }
        activeViews.clear()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.edge_channel_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cts)
            .setContentTitle(getString(R.string.edge_notif_title))
            .setContentText(getString(R.string.edge_notif_text))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
