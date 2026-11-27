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
import com.zek.mictsplus.translate.LiveTranslateService
import com.zek.mictsplus.ui.MainActivity
import com.zek.mictsplus.ui.TranslateConsentActivity
import kotlin.math.abs

/**
 * Persistent edge swipe handle using SYSTEM_ALERT_WINDOW only.
 * Works completely WITHOUT Accessibility Service or Developer Options.
 *
 * Gestures:
 *  - Swipe Inward -> Configurable (default: Circle to Search)
 *  - Swipe Up along edge -> Configurable (default: Live Continuous Translate)
 *  - Swipe Down along edge -> Configurable (default: Single-Snap Screen Translate)
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

        // Subtle pill visual indicator tucked flush against the edge
        val pill = View(this).apply {
            val pillWidth = (4 * density).toInt().coerceAtLeast(4)
            val pillHeight = (heightPx * 0.55f).toInt()
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
            alpha = alphaRatio * 0.7f
        }
        container.addView(pill)

        // Request Android 10+ to exclude this small handle rect from the system Back gesture
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

        val swipeThresholdPx = 38 * density
        var downX = 0f
        var downY = 0f
        var triggered = false

        container.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    triggered = false
                    pill.animate().alpha((alphaRatio + 0.45f).coerceAtMost(1f)).scaleX(1.6f).setDuration(90).start()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!triggered) {
                        val dx = event.rawX - downX
                        val dy = event.rawY - downY
                        val inwardDx = if (isRight) -dx else dx

                        if (inwardDx > swipeThresholdPx && inwardDx > abs(dy) * 1.1f) {
                            triggered = true
                            executeAction(Prefs.getSwipeInAction(this))
                        } else if (-dy > swipeThresholdPx * 1.15f && abs(dy) > abs(dx) * 1.2f) {
                            triggered = true
                            executeAction(Prefs.getSwipeUpAction(this))
                        } else if (dy > swipeThresholdPx * 1.15f && abs(dy) > abs(dx) * 1.2f) {
                            triggered = true
                            executeAction(Prefs.getSwipeDownAction(this))
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    pill.animate().alpha(alphaRatio * 0.7f).scaleX(1f).setDuration(140).start()
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

    private fun executeAction(action: String) {
        when (action) {
            Prefs.ACTION_CTS -> {
                val delay = Prefs.getTriggerDelayMs(this)
                mainHandler.postDelayed({
                    val ok = CircleToSearchTrigger.trigger(this)
                    if (!ok) {
                        Toast.makeText(this, R.string.cts_trigger_failed, Toast.LENGTH_SHORT).show()
                    }
                }, delay)
            }

            Prefs.ACTION_LIVE_TRANSLATE -> {
                if (LiveTranslateService.isRunning) {
                    LiveTranslateService.stop(this)
                } else {
                    TranslateConsentActivity.launch(this, continuous = true)
                }
            }

            Prefs.ACTION_SNAP_TRANSLATE -> {
                if (LiveTranslateService.isRunning) {
                    LiveTranslateService.requestSingleSnap(this)
                } else {
                    TranslateConsentActivity.launch(this, continuous = false)
                }
            }
        }
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
