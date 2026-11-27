package com.zek.mictsplus

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val FILE_NAME = "micts_plus_prefs"

    const val ACTION_CTS = "cts"
    const val ACTION_LIVE_TRANSLATE = "live_translate"
    const val ACTION_SNAP_TRANSLATE = "snap_translate"
    const val ACTION_NONE = "none"

    const val SIDE_RIGHT = 0
    const val SIDE_LEFT = 1
    const val SIDE_BOTH = 2

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var edgeEnabled: Boolean
        get() = _prefs?.getBoolean("edge_enabled", false) ?: false
        set(v) { _prefs?.edit()?.putBoolean("edge_enabled", v)?.apply() }

    private var _prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (_prefs == null) _prefs = prefs(context.applicationContext)
    }

    fun isEdgeEnabled(context: Context): Boolean =
        prefs(context).getBoolean("edge_enabled", false)

    fun setEdgeEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("edge_enabled", enabled).apply()
    }

    fun getEdgeSide(context: Context): Int =
        prefs(context).getInt("edge_side", SIDE_RIGHT)

    fun setEdgeSide(context: Context, side: Int) {
        prefs(context).edit().putInt("edge_side", side).apply()
    }

    fun getEdgeYPercent(context: Context): Int =
        prefs(context).getInt("edge_y_percent", 35) // Upper-mid edge avoids system Back gesture zone

    fun setEdgeYPercent(context: Context, percent: Int) {
        prefs(context).edit().putInt("edge_y_percent", percent.coerceIn(10, 85)).apply()
    }

    fun getEdgeHeightDp(context: Context): Int =
        prefs(context).getInt("edge_height_dp", 140)

    fun setEdgeHeightDp(context: Context, dp: Int) {
        prefs(context).edit().putInt("edge_height_dp", dp.coerceIn(60, 280)).apply()
    }

    fun getEdgeWidthDp(context: Context): Int =
        prefs(context).getInt("edge_width_dp", 18)

    fun setEdgeWidthDp(context: Context, dp: Int) {
        prefs(context).edit().putInt("edge_width_dp", dp.coerceIn(10, 36)).apply()
    }

    fun getEdgeAlpha(context: Context): Int =
        prefs(context).getInt("edge_alpha", 35) // 0..100

    fun setEdgeAlpha(context: Context, alpha: Int) {
        prefs(context).edit().putInt("edge_alpha", alpha.coerceIn(0, 100)).apply()
    }

    fun getSwipeInAction(context: Context): String =
        prefs(context).getString("action_swipe_in", ACTION_CTS) ?: ACTION_CTS

    fun setSwipeInAction(context: Context, action: String) {
        prefs(context).edit().putString("action_swipe_in", action).apply()
    }

    fun getSwipeUpAction(context: Context): String =
        prefs(context).getString("action_swipe_up", ACTION_LIVE_TRANSLATE) ?: ACTION_LIVE_TRANSLATE

    fun setSwipeUpAction(context: Context, action: String) {
        prefs(context).edit().putString("action_swipe_up", action).apply()
    }

    fun getSwipeDownAction(context: Context): String =
        prefs(context).getString("action_swipe_down", ACTION_SNAP_TRANSLATE) ?: ACTION_SNAP_TRANSLATE

    fun setSwipeDownAction(context: Context, action: String) {
        prefs(context).edit().putString("action_swipe_down", action).apply()
    }

    fun getTriggerDelayMs(context: Context): Long =
        prefs(context).getLong("trigger_delay_ms", 100L)

    fun setTriggerDelayMs(context: Context, delayMs: Long) {
        prefs(context).edit().putLong("trigger_delay_ms", delayMs.coerceIn(0L, 1000L)).apply()
    }

    fun isVibrateEnabled(context: Context): Boolean =
        prefs(context).getBoolean("vibrate_enabled", true)

    fun setVibrateEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("vibrate_enabled", enabled).apply()
    }

    fun getTargetLang(context: Context): String =
        prefs(context).getString("target_lang", "en") ?: "en"

    fun setTargetLang(context: Context, langCode: String) {
        prefs(context).edit().putString("target_lang", langCode).apply()
    }

    fun getOcrScript(context: Context): String =
        prefs(context).getString("ocr_script", "chinese") ?: "chinese" // Chinese recognizer also recognizes Latin/English!

    fun setOcrScript(context: Context, script: String) {
        prefs(context).edit().putString("ocr_script", script).apply()
    }

    fun getLiveIntervalMs(context: Context): Long =
        prefs(context).getLong("live_interval_ms", 1400L)

    fun setLiveIntervalMs(context: Context, intervalMs: Long) {
        prefs(context).edit().putLong("live_interval_ms", intervalMs.coerceIn(700L, 4000L)).apply()
    }
}
