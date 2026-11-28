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

    const val SKIN_RAINBOW = "rainbow"
    const val SKIN_WHITE = "white"
    const val SKIN_CYAN = "cyan"
    const val SKIN_TRANSPARENT = "transparent"

    const val COMP_TYPE_ACTIVITY = "activity"
    const val COMP_TYPE_SERVICE = "service"
    const val COMP_TYPE_BROADCAST = "broadcast"

    const val DEFAULT_HONOR_PKG = "com.hihonor.magictranslate"
    const val DEFAULT_HONOR_QUICK_SERVICE = "com.hihonor.magictranslate.screen.service.ScreenTranslateQuickService"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    // --- Service master checks ---
    fun isAnyOverlayEnabled(context: Context): Boolean =
        isNavBarEnabled(context) || isEdgeEnabled(context)

    // --- Bottom Horizontal Navigation Bar Pill (Easy Tap style) ---
    fun isNavBarEnabled(context: Context): Boolean =
        prefs(context).getBoolean("nav_bar_enabled", true)

    fun setNavBarEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("nav_bar_enabled", enabled).apply()
    }

    fun getNavTap1Action(context: Context): String =
        prefs(context).getString("nav_tap1_action", ACTION_CTS) ?: ACTION_CTS

    fun setNavTap1Action(context: Context, action: String) {
        prefs(context).edit().putString("nav_tap1_action", action).apply()
    }

    fun getNavHoldAction(context: Context): String =
        prefs(context).getString("nav_hold_action", ACTION_HONOR_TRANSLATE) ?: ACTION_HONOR_TRANSLATE

    fun setNavHoldAction(context: Context, action: String) {
        prefs(context).edit().putString("nav_hold_action", action).apply()
    }

    fun getNavTap2Action(context: Context): String =
        prefs(context).getString("nav_tap2_action", ACTION_HONOR_TRANSLATE) ?: ACTION_HONOR_TRANSLATE

    fun setNavTap2Action(context: Context, action: String) {
        prefs(context).edit().putString("nav_tap2_action", action).apply()
    }

    fun getNavWidthDp(context: Context): Int =
        prefs(context).getInt("nav_width_dp", 150).coerceIn(40, 320)

    fun setNavWidthDp(context: Context, dp: Int) {
        prefs(context).edit().putInt("nav_width_dp", dp.coerceIn(40, 320)).apply()
    }

    fun getNavThicknessDp(context: Context): Int =
        prefs(context).getInt("nav_thickness_dp", 10).coerceIn(4, 36)

    fun setNavThicknessDp(context: Context, dp: Int) {
        prefs(context).edit().putInt("nav_thickness_dp", dp.coerceIn(4, 36)).apply()
    }

    fun getNavBottomHeightDp(context: Context): Int =
        prefs(context).getInt("nav_bottom_height_dp", 0).coerceIn(0, 120)

    fun setNavBottomHeightDp(context: Context, dp: Int) {
        prefs(context).edit().putInt("nav_bottom_height_dp", dp.coerceIn(0, 120)).apply()
    }

    fun getNavOffsetXDp(context: Context): Int =
        prefs(context).getInt("nav_offset_x_dp", 0).coerceIn(-150, 150)

    fun setNavOffsetXDp(context: Context, dp: Int) {
        prefs(context).edit().putInt("nav_offset_x_dp", dp.coerceIn(-150, 150)).apply()
    }

    fun getNavHoldDelayMs(context: Context): Long =
        prefs(context).getLong("nav_hold_delay_ms", 350L).coerceIn(150L, 1000L)

    fun setNavHoldDelayMs(context: Context, ms: Long) {
        prefs(context).edit().putLong("nav_hold_delay_ms", ms.coerceIn(150L, 1000L)).apply()
    }

    fun getNavSkin(context: Context): String =
        prefs(context).getString("nav_skin", SKIN_RAINBOW) ?: SKIN_RAINBOW

    fun setNavSkin(context: Context, skin: String) {
        prefs(context).edit().putString("nav_skin", skin).apply()
    }

    fun getNavAlpha(context: Context): Int =
        prefs(context).getInt("nav_alpha", 85).coerceIn(0, 100)

    fun setNavAlpha(context: Context, alpha: Int) {
        prefs(context).edit().putInt("nav_alpha", alpha.coerceIn(0, 100)).apply()
    }

    // --- Vertical Edge Screen Swipe Handle ---
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
        prefs(context).getInt("edge_y_percent", 105).coerceIn(10, 150)

    fun setEdgeYPercent(context: Context, percent: Int) {
        prefs(context).edit().putInt("edge_y_percent", percent.coerceIn(10, 150)).apply()
    }

    fun getEdgeHeightDp(context: Context): Int =
        prefs(context).getInt("edge_height_dp", 120).coerceIn(60, 260)

    fun setEdgeHeightDp(context: Context, dp: Int) {
        prefs(context).edit().putInt("edge_height_dp", dp.coerceIn(60, 260)).apply()
    }

    fun getEdgeWidthDp(context: Context): Int =
        prefs(context).getInt("edge_width_dp", 20).coerceIn(10, 36)

    fun setEdgeWidthDp(context: Context, dp: Int) {
        prefs(context).edit().putInt("edge_width_dp", dp.coerceIn(10, 36)).apply()
    }

    fun getEdgeAlpha(context: Context): Int =
        prefs(context).getInt("edge_alpha", 35).coerceIn(0, 100)

    fun setEdgeAlpha(context: Context, alpha: Int) {
        prefs(context).edit().putInt("edge_alpha", alpha.coerceIn(0, 100)).apply()
    }

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

    // --- Shared Timing & Haptics ---
    fun getTriggerDelayMs(context: Context): Long =
        prefs(context).getLong("trigger_delay_ms", 50L).coerceIn(0L, 1500L)

    fun setTriggerDelayMs(context: Context, delayMs: Long) {
        prefs(context).edit().putLong("trigger_delay_ms", delayMs.coerceIn(0L, 1500L)).apply()
    }

    fun getHoldDelayMs(context: Context): Long =
        prefs(context).getLong("hold_delay_ms", 0L).coerceIn(0L, 1000L)

    fun setHoldDelayMs(context: Context, delayMs: Long) {
        prefs(context).edit().putLong("hold_delay_ms", delayMs.coerceIn(0L, 1000L)).apply()
    }

    fun isVibrateEnabled(context: Context): Boolean =
        prefs(context).getBoolean("vibrate_enabled", true)

    fun setVibrateEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("vibrate_enabled", enabled).apply()
    }

    // --- Honor MagicOS Screen Translation Target ---
    fun getHonorTranslatePkg(context: Context): String =
        prefs(context).getString("honor_translate_pkg", DEFAULT_HONOR_PKG) ?: DEFAULT_HONOR_PKG

    fun setHonorTranslatePkg(context: Context, pkgName: String) {
        prefs(context).edit().putString("honor_translate_pkg", pkgName.trim()).apply()
    }

    fun getHonorTranslateClass(context: Context): String =
        prefs(context).getString("honor_translate_activity", DEFAULT_HONOR_QUICK_SERVICE)
            ?: DEFAULT_HONOR_QUICK_SERVICE

    fun setHonorTranslateClass(context: Context, className: String) {
        prefs(context).edit().putString("honor_translate_activity", className.trim()).apply()
    }

    fun getHonorTranslateType(context: Context): String =
        prefs(context).getString("honor_translate_type", COMP_TYPE_SERVICE) ?: COMP_TYPE_SERVICE

    fun setHonorTranslateType(context: Context, type: String) {
        prefs(context).edit().putString("honor_translate_type", type).apply()
    }
}
