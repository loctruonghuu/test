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
 * Persistent bottom-right edge swipe handle using SYSTEM_ALERT_WINDOW only.
 * Works completely WITHOUT Accessibility Service or Developer Options.
 *
 * Supports:
 *  - Custom Trigger Time Delay (ms): waits X ms after gesture before executing
 *  - Custom Swipe-and-Hold Delay (ms): requires holding finger for X ms after swipe (0 = instant)
 */
class EdgeSwipeService : Service() {

    companion object {
        const val ACTION_START = "com.zek.mictsplus.edge.START"
        const val ACTION_RELOAD = "com.zek.mictsplus.edge.RELOAD"
        const val ACTION_STOP = "com.zek.mictsplus.edge.STOP"

        private const val CHANNEL_ID = "edge_swipe_channel"
        private const val NOTIF_ID = 1001

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
            if (!Prefs.isEdgeEnabled(context)) return
            val intent = Intent(context, EdgeSwipeService::class.java).apply {
                action = ACTION_RELOAD
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, EdgeSwipeService::class.java))
        }
    }

    private lateinit var windowManager: WindowManager
    private val activeHandles = mutableListOf<View>()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            removeHandles()
            stopSelf()
            return START_NOT_STICKY
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.overlay_permission_required, Toast.LENGTH_SHORT).show()
            Prefs.setEdgeEnabled(this, false)
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

        setupHandles()
        return START_STICKY
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        removeHandles()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun setupHandles() {
        removeHandles()
        val side = Prefs.getEdgeSide(this)
        if (side == Prefs.SIDE_RIGHT || side == Prefs.SIDE_BOTH) {
            addEdgeHandle(isRight = true)
        }
        if (side == Prefs.SIDE_LEFT || side == Prefs.SIDE_BOTH) {
            addEdgeHandle(isRight = false)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addEdgeHandle(isRight: Boolean) {
        val dm = resources.displayMetrics
        val density = dm.density
        val widthPx = (Prefs.getEdgeWidthDp(this) * density).toInt()
        val heightPx = (Prefs.getEdgeHeightDp(this) * density).toInt()
        val yPx = ((dm.heightPixels - heightPx) * (Prefs.getEdgeYPercent(this) / 100f)).toInt()
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

        // Exclude this edge handle rect from Android's system Back gesture
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
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
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
                                // Require holding the finger for holdMs before confirming trigger
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
            activeHandles.add(container)
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
                    val ok = HonorTranslateTrigger.trigger(this)
                    if (!ok) {
                        Toast.makeText(this, R.string.honor_translate_failed, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }, delayMs)
    }

    private fun removeHandles() {
        for (v in activeHandles) {
            try {
                windowManager.removeView(v)
            } catch (_: Throwable) {
            }
        }
        activeHandles.clear()
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
