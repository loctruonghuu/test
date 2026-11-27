package com.zek.mictsplus.translate

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.zek.mictsplus.Prefs
import com.zek.mictsplus.R
import com.zek.mictsplus.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Live On-Screen Translator using standard Android MediaProjection + ML Kit On-Device OCR & Translation.
 * Works 100% WITHOUT Accessibility Service, Developer Options, ADB, or Root.
 */
class LiveTranslateService : Service() {

    companion object {
        private const val TAG = "LiveTranslateService"
        private const val CHANNEL_ID = "live_translate_channel"
        private const val NOTIF_ID = 1002

        const val ACTION_START = "com.zek.mictsplus.translate.START"
        const val ACTION_SNAP = "com.zek.mictsplus.translate.SNAP"
        const val ACTION_STOP = "com.zek.mictsplus.translate.STOP"

        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        const val EXTRA_CONTINUOUS = "continuous"

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context, resultCode: Int, data: Intent, continuous: Boolean) {
            val intent = Intent(context, LiveTranslateService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA, data)
                putExtra(EXTRA_CONTINUOUS, continuous)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun requestSingleSnap(context: Context) {
            val intent = Intent(context, LiveTranslateService::class.java).apply {
                action = ACTION_SNAP
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LiveTranslateService::class.java))
        }
    }

    private lateinit var windowManager: WindowManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var overlayView: LiveTranslateOverlayView? = null
    private var hudView: View? = null
    private var statusTextView: TextView? = null
    private var modeButtonView: TextView? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null
    private var isContinuous = true
    private var isPaused = false
    private var isBusy = false

    private var screenWidth = 1080
    private var screenHeight = 2400
    private var screenDensity = 420

    private var recognizer: TextRecognizer? = null
    private val languageIdentifier = LanguageIdentification.getClient()
    private val translatorCache = mutableMapOf<String, Translator>()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        initRecognizer()
    }

    private fun initRecognizer() {
        recognizer?.close()
        recognizer = when (Prefs.getOcrScript(this)) {
            "japanese" -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            "korean" -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            "latin" -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            else -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_SNAP -> {
                if (isRunning) {
                    scope.launch { captureAndTranslateOnce() }
                }
                return START_STICKY
            }

            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                @Suppress("DEPRECATION")
                val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)
                isContinuous = intent.getBooleanExtra(EXTRA_CONTINUOUS, true)

                if (resultCode != Activity.RESULT_OK || data == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                // Android 14+ REQUIRES calling startForeground(MEDIA_PROJECTION) BEFORE getMediaProjection()
                val notif = buildNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceCompat.startForeground(
                        this,
                        NOTIF_ID,
                        notif,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    )
                } else {
                    startForeground(NOTIF_ID, notif)
                }

                isRunning = true
                setupProjection(resultCode, data)
                setupOverlays()
                startCaptureLoop()
            }
        }
        return START_STICKY
    }

    private fun setupProjection(resultCode: Int, data: Intent) {
        cleanupProjection()
        val dm = resources.displayMetrics
        screenWidth = dm.widthPixels
        screenHeight = dm.heightPixels
        screenDensity = dm.densityDpi

        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = mpm.getMediaProjection(resultCode, data)
        if (projection == null) {
            Toast.makeText(this, R.string.translate_projection_failed, Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }

        // Required on Android 14+ before createVirtualDisplay()
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                mainHandler.post { stopSelf() }
            }
        }, mainHandler)

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay = projection.createVirtualDisplay(
            "MiCTS_LiveTranslate",
            screenWidth,
            screenHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null,
            mainHandler
        )
        mediaProjection = projection
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupOverlays() {
        removeOverlays()

        // 1. Full-screen pass-through translation canvas
        val overlay = LiveTranslateOverlayView(this)
        val overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(overlay, overlayParams)
        overlayView = overlay

        // 2. Compact draggable floating HUD bar (Pause/Live, Snap, Clear, Close)
        val density = resources.displayMetrics.density
        val hud = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 100f
                setColor(Color.argb(230, 20, 24, 32))
                setStroke((1 * density).toInt(), Color.argb(90, 255, 255, 255))
            }
        }

        fun makeBtn(label: String, textColor: Int = Color.WHITE, onClick: () -> Unit): TextView {
            return TextView(this).apply {
                text = label
                setTextColor(textColor)
                textSize = 12f
                setPadding((8 * density).toInt(), (4 * density).toInt(), (8 * density).toInt(), (4 * density).toInt())
                setOnClickListener { onClick() }
            }
        }

        val statusTv = TextView(this).apply {
            text = "● ${Prefs.getTargetLang(this@LiveTranslateService).uppercase()}"
            setTextColor(Color.parseColor("#7DD3FC"))
            textSize = 12f
            setPadding((4 * density).toInt(), 0, (6 * density).toInt(), 0)
        }
        statusTextView = statusTv

        val modeBtn = makeBtn(if (isContinuous) "LIVE" else "SNAP", Color.parseColor("#86EFAC")) {
            if (isContinuous) {
                isPaused = !isPaused
                modeButtonView?.text = if (isPaused) "PAUSED" else "LIVE"
                if (isPaused) overlayView?.clear()
            } else {
                scope.launch { captureAndTranslateOnce() }
            }
        }
        modeButtonView = modeBtn

        val snapBtn = makeBtn("↻") {
            scope.launch { captureAndTranslateOnce() }
        }

        val closeBtn = makeBtn("✕", Color.parseColor("#FCA5A5")) {
            stopSelf()
        }

        hud.addView(statusTv)
        hud.addView(modeBtn)
        hud.addView(snapBtn)
        hud.addView(closeBtn)

        val hudParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (46 * density).toInt()
        }

        // Allow dragging the HUD pill out of the way
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        hud.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = hudParams.x
                    startY = hudParams.y
                    touchX = e.rawX
                    touchY = e.rawY
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    hudParams.x = startX + (e.rawX - touchX).toInt()
                    hudParams.y = startY + (e.rawY - touchY).toInt()
                    windowManager.updateViewLayout(hud, hudParams)
                    true
                }
                else -> false
            }
        }

        windowManager.addView(hud, hudParams)
        hudView = hud
    }

    private fun startCaptureLoop() {
        loopJob?.cancel()
        loopJob = scope.launch {
            delay(350) // Let consent dialog finish dismissing
            if (!isContinuous) {
                captureAndTranslateOnce()
                return@launch
            }
            while (isActive) {
                if (!isPaused) {
                    captureAndTranslateOnce()
                }
                delay(Prefs.getLiveIntervalMs(this@LiveTranslateService))
            }
        }
    }

    private suspend fun captureAndTranslateOnce() {
        if (isBusy) return
        isBusy = true
        try {
            // Temporarily hide our own translated text so OCR reads the underlying app cleanly
            withContext(Dispatchers.Main) {
                overlayView?.visibility = View.INVISIBLE
            }
            delay(60)

            val bitmap = grabFrameBitmap()
            withContext(Dispatchers.Main) {
                overlayView?.visibility = View.VISIBLE
            }
            if (bitmap == null) return

            val targetLang = TranslateLanguage.fromLanguageTag(Prefs.getTargetLang(this))
                ?: TranslateLanguage.ENGLISH

            val visionImage = InputImage.fromBitmap(bitmap, 0)
            val ocrResult = recognizer?.process(visionImage)?.await()
            bitmap.recycle()

            if (ocrResult == null || ocrResult.textBlocks.isEmpty()) {
                withContext(Dispatchers.Main) { overlayView?.clear() }
                return
            }

            val translatedBlocks = mutableListOf<TranslatedBlock>()
            for (block in ocrResult.textBlocks) {
                val rect = block.boundingBox ?: continue
                val text = block.text.trim()
                if (text.length < 2) continue

                val detectedCode = try {
                    languageIdentifier.identifyLanguage(text).await()
                } catch (_: Throwable) {
                    "und"
                }

                val sourceLang = if (detectedCode != "und") {
                    TranslateLanguage.fromLanguageTag(detectedCode)
                } else {
                    null
                } ?: continue

                if (sourceLang == targetLang) continue

                val translator = getOrCreateTranslator(sourceLang, targetLang)
                val translated = try {
                    translator.translate(text).await()
                } catch (e: Throwable) {
                    Log.w(TAG, "Translate failed for $sourceLang->$targetLang", e)
                    continue
                }

                if (translated.isNotBlank() && !translated.equals(text, ignoreCase = true)) {
                    translatedBlocks.add(TranslatedBlock(rect, text, translated))
                }
            }

            withContext(Dispatchers.Main) {
                overlayView?.updateBlocks(translatedBlocks)
                statusTextView?.text = "● ${targetLang.uppercase()} (${translatedBlocks.size})"
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Live translate pass error", t)
        } finally {
            isBusy = false
        }
    }

    private suspend fun getOrCreateTranslator(source: String, target: String): Translator {
        val key = "$source->$target"
        translatorCache[key]?.let { return it }

        withContext(Dispatchers.Main) {
            statusTextView?.text = "↓ $source→$target"
        }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(source)
            .setTargetLanguage(target)
            .build()
        val client = Translation.getClient(options)
        val conditions = DownloadConditions.Builder().build()
        client.downloadModelIfNeeded(conditions).await()
        translatorCache[key] = client
        return client
    }

    private fun grabFrameBitmap(): Bitmap? {
        val reader = imageReader ?: return null
        val image = reader.acquireLatestImage() ?: return null
        return try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * screenWidth
            val padded = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            padded.copyPixelsFromBuffer(buffer)
            val cropped = Bitmap.createBitmap(padded, 0, 0, screenWidth, screenHeight)
            padded.recycle()
            cropped
        } catch (t: Throwable) {
            Log.e(TAG, "Frame capture failed", t)
            null
        } finally {
            image.close()
        }
    }

    private fun removeOverlays() {
        overlayView?.let {
            try { windowManager.removeView(it) } catch (_: Throwable) {}
        }
        hudView?.let {
            try { windowManager.removeView(it) } catch (_: Throwable) {}
        }
        overlayView = null
        hudView = null
    }

    private fun cleanupProjection() {
        try { virtualDisplay?.release() } catch (_: Throwable) {}
        try { imageReader?.close() } catch (_: Throwable) {}
        try { mediaProjection?.stop() } catch (_: Throwable) {}
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
    }

    override fun onDestroy() {
        isRunning = false
        loopJob?.cancel()
        scope.cancel()
        removeOverlays()
        cleanupProjection()
        recognizer?.close()
        translatorCache.values.forEach { it.close() }
        translatorCache.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.translate_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, LiveTranslateService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_translate)
            .setContentTitle(getString(R.string.translate_notif_title))
            .setContentText(getString(R.string.translate_notif_text))
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.stop), stopIntent)
            .setOngoing(true)
            .build()
    }
}
