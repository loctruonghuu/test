package com.zek.mictsplus.cts

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.zek.mictsplus.Prefs
import org.lsposed.hiddenapibypass.HiddenApiBypass

object CircleToSearchTrigger {
    private const val TAG = "CircleToSearchTrigger"

    @SuppressLint("PrivateApi")
    fun trigger(context: Context): Boolean {
        if (Prefs.isVibrateEnabled(context)) {
            vibrate(context)
        }

        return try {
            val serviceManager = Class.forName("android.os.ServiceManager")
            val getService = serviceManager.getMethod("getService", String::class.java)
            val binder = getService.invoke(null, "voiceinteraction") as? IBinder
                ?: throw IllegalStateException("voiceinteraction service binder is null")

            val stubClass = Class.forName("com.android.internal.app.IVoiceInteractionManagerService\$Stub")
            val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
            val vims = asInterface.invoke(null, binder)
                ?: throw IllegalStateException("IVoiceInteractionManagerService is null")

            val iVimsClass = Class.forName("com.android.internal.app.IVoiceInteractionManagerService")

            val args = Bundle().apply {
                putLong("invocation_time_ms", SystemClock.elapsedRealtime())
                putInt("omni.entry_point", 1)
                putBoolean("micts_trigger", true)
            }

            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                HiddenApiBypass.invoke(
                    iVimsClass,
                    vims,
                    "showSessionFromSession",
                    null,
                    args,
                    7,
                    context.attributionTag
                )
            } else {
                HiddenApiBypass.invoke(
                    iVimsClass,
                    vims,
                    "showSessionFromSession",
                    null,
                    args,
                    7
                )
            }
            Log.d(TAG, "showSessionFromSession result=$result")
            (result as? Boolean) ?: true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to trigger Circle to Search via VIS", t)
            false
        }
    }

    fun vibrate(context: Context, durationMs: Long = 18L) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                v.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Throwable) {
        }
    }
}
