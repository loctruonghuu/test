package com.zek.mictsplus

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val FILE_NAME = "micts_honor_prefs"

    const val ACTION_CTS = "cts"
    const val ACTION_HONOR_TRANSLATE = "honor_translate"
    const val ACTION_NONE = "none"

    const val SIDE_RIGHT = 0
    const val SIDE_LEFT = 1
    const val SIDE_BOTH = 2

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun isEdgeEnabled(context: Context): Boolean =
        prefs(context).getBoolean("edge_enabled", false)

    fun setEdgeEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("edge_enabled", enabled).apply()
    }

    // Default: Right Edge
    fun getEdgeSide(context: Context): Int =
        prefs(context).getInt("edge_side", SIDE_RIGHT)

    fun setEdgeSide(context: Context, side: Int) {
        prefs(context).edit().putInt("edge_side", side).apply()
    }

    // Default: 90% (Bottom of the right screen)
    fun getEdgeYPercent(context: Context): Int =
        prefs(context).getInt("edge_y_percent", 90)

    fun setEdgeYPercent(context: Context, percent: Int) {
        prefs(context).edit().putInt("edge_y_percent", percent.coerceIn(10, 98)).apply()
    }

    fun getEdgeHeightDp(context: Context): Int =
        prefs(context).getInt("edge_height_dp", 120)

    fun setEdgeHeightDp(context: Context, dp: Int) {
        prefs(context).edit().putInt("edge_height_dp", dp.coerceIn(60, 260)).apply()
    }

    fun getEdgeWidthDp(context: Context): Int =
        prefs(context).getInt("edge_width_dp", 20)

    fun setEdgeWidthDp(context: Context, dp: Int) {
        prefs(context).edit().putInt("edge_width_dp", dp.coerceIn(10, 36)).apply()
    }

    fun getEdgeAlpha(context: Context): Int =
        prefs(context).getInt("edge_alpha", 35)

    fun setEdgeAlpha(context: Context, alpha: Int) {
        prefs(context).edit().putInt("edge_alpha", alpha.coerceIn(0, 100)).apply()
    }

    // Default Gestures:
    // Swipe Inward -> Circle to Search
    // Swipe Up -> Honor MagicOS AI Translate
    // Swipe Down -> Honor MagicOS AI Translate
    fun getSwipeInAction(context: Context): String =
        prefs(context).getString("action_swipe_in", ACTION_CTS) ?: ACTION_CTS

    fun setSwipeInAction(context: Context, action: String) {
        prefs(context).edit().putString("action_swipe_in", action).apply()
    }

    fun getSwipeUpAction(context: Context): String =
        prefs(context).getString("action_swipe_up", ACTION_HONOR_TRANSLATE) ?: ACTION_HONOR_TRANSLATE

    fun setSwipeUpAction(context: Context, action: String) {
        prefs(context).edit().putString("action_swipe_up", action).apply()
    }

    fun getSwipeDownAction(context: Context): String =
        prefs(context).getString("action_swipe_down", ACTION_HONOR_TRANSLATE) ?: ACTION_HONOR_TRANSLATE

    fun setSwipeDownAction(context: Context, action: String) {
        prefs(context).edit().putString("action_swipe_down", action).apply()
    }

    // Custom time delay BEFORE firing the action after gesture is detected (0..1500 ms)
    fun getTriggerDelayMs(context: Context): Long =
        prefs(context).getLong("trigger_delay_ms", 100L)

    fun setTriggerDelayMs(context: Context, delayMs: Long) {
        prefs(context).edit().putLong("trigger_delay_ms", delayMs.coerceIn(0L, 1500L)).apply()
    }

    // Custom swipe-and-hold delay (0 = instant on swipe, >0 = must hold finger after swipe for X ms)
    fun getHoldDelayMs(context: Context): Long =
        prefs(context).getLong("hold_delay_ms", 0L)

    fun setHoldDelayMs(context: Context, delayMs: Long) {
        prefs(context).edit().putLong("hold_delay_ms", delayMs.coerceIn(0L, 1000L)).apply()
    }

    fun isVibrateEnabled(context: Context): Boolean =
        prefs(context).getBoolean("vibrate_enabled", true)

    fun setVibrateEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("vibrate_enabled", enabled).apply()
    }

    // Optional custom Honor AI Translate package & activity (auto-detected if blank)
    fun getHonorTranslatePkg(context: Context): String =
        prefs(context).getString("honor_translate_pkg", "") ?: ""

    fun setHonorTranslatePkg(context: Context, pkgName: String) {
        prefs(context).edit().putString("honor_translate_pkg", pkgName.trim()).apply()
    }

    fun getHonorTranslateActivity(context: Context): String =
        prefs(context).getString("honor_translate_activity", "") ?: ""

    fun setHonorTranslateActivity(context: Context, actName: String) {
        prefs(context).edit().putString("honor_translate_activity", actName.trim()).apply()
    }
}
