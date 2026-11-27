package com.zek.mictsplus.honor

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.zek.mictsplus.Prefs
import com.zek.mictsplus.cts.CircleToSearchTrigger

data class HonorComponentCandidate(
    val packageName: String,
    val className: String,
    val type: String, // activity | service | broadcast
    val label: String,
    val score: Int
)

/**
 * Triggers Honor MagicOS AI Screen Translation directly (WITHOUT opening the main Translate app).
 * Supports exported Activities, Services, and Broadcast Receivers across MagicOS AI packages.
 * Works completely WITHOUT Accessibility Services, Developer Options, or Root.
 */
object HonorTranslateTrigger {
    private const val TAG = "HonorTranslateTrigger"

    private val KNOWN_PACKAGES = listOf(
        "com.hihonor.magictranslate",
        "com.hihonor.magictext",
        "com.hihonor.hitouch",
        "com.hihonor.vision",
        "com.hihonor.scanner",
        "com.hihonor.lens",
        "com.hihonor.translate",
        "com.hihonor.aitranslate",
        "com.hihonor.aisubtitle",
        "com.hihonor.magicvoice",
        "com.hihonor.vassistant"
    )

    // Strong indicators of on-screen / full-screen / floating translation overlay
    private val SCREEN_TRANSLATE_KEYWORDS = listOf(
        "screentranslat",
        "screen_translat",
        "fullscreentranslat",
        "full_screen_translat",
        "floattranslat",
        "float_translat",
        "overlaytranslat",
        "capturetranslat",
        "transscreen",
        "screensubtitle",
        "screen"
    )

    // Words that indicate the main app home screen (avoid auto-picking these!)
    private val MAIN_APP_EXCLUDE = listOf(
        "mainactivity",
        "homeactivity",
        "splashactivity",
        "welcomeactivity",
        "launcheractivity",
        "setting",
        "about",
        "privacy",
        "agreement",
        "facetoface",
        "conversation",
        "voice",
        "call"
    )

    fun trigger(context: Context): Boolean {
        if (Prefs.isVibrateEnabled(context)) {
            CircleToSearchTrigger.vibrate(context)
        }

        // 1. If user explicitly selected a component via the Scanner, launch it directly
        val customPkg = Prefs.getHonorTranslatePkg(context)
        val customClass = Prefs.getHonorTranslateClass(context)
        val customType = Prefs.getHonorTranslateType(context)
        if (customPkg.isNotEmpty() && customClass.isNotEmpty()) {
            return launchCandidate(context, customPkg, customClass, customType)
        }

        // 2. Auto-detect ONLY dedicated Screen Translation components (skip main app launchers!)
        val candidates = scanHonorScreenTranslateComponents(context)
        val screenSpecific = candidates.filter { it.score >= 50 }
        for (cand in screenSpecific) {
            if (launchCandidate(context, cand.packageName, cand.className, cand.type)) {
                Log.d(TAG, "Auto-launched screen translate [${cand.type}] ${cand.packageName}/${cand.className}")
                return true
            }
        }

        // 3. Fallback: try known MagicOS Screen Translation intent actions
        val knownActions = listOf(
            "com.hihonor.magictranslate.action.SCREEN_TRANSLATE",
            "com.hihonor.magictranslate.SCREEN_TRANSLATE",
            "com.hihonor.action.SCREEN_TRANSLATE",
            "com.hihonor.hitouch.action.SCREEN_TRANSLATE",
            "com.hihonor.vision.action.SCREEN_TRANSLATE",
            "com.hihonor.scanner.action.SCREEN_TRANSLATE"
        )
        for (action in knownActions) {
            if (tryAction(context, action)) return true
        }

        return false
    }

    fun launchCandidate(context: Context, pkg: String, className: String, type: String): Boolean {
        return try {
            val intent = Intent().apply {
                component = ComponentName(pkg, className)
                putExtra("from", "edge_gesture")
                putExtra("mode", "screen_translate")
                putExtra("translate_mode", "screen")
            }
            when (type) {
                Prefs.COMP_TYPE_SERVICE -> {
                    try {
                        context.startService(intent)
                    } catch (_: Throwable) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        } else {
                            throw IllegalStateException("startService failed")
                        }
                    }
                    true
                }

                Prefs.COMP_TYPE_BROADCAST -> {
                    context.sendBroadcast(intent)
                    true
                }

                else -> {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    context.startActivity(intent)
                    true
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to launch [$type] $pkg/$className: ${t.message}")
            false
        }
    }

    private fun tryAction(context: Context, action: String): Boolean {
        return try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return true
            }
            false
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Deep-scans installed Honor system packages for exported Activities, Services, and Receivers
     * related to Screen Translation, ranking dedicated Screen Translate overlays at the very top.
     */
    fun scanHonorScreenTranslateComponents(context: Context): List<HonorComponentCandidate> {
        val pm = context.packageManager
        val results = mutableListOf<HonorComponentCandidate>()

        val flags = (PackageManager.GET_ACTIVITIES or
            PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS).toLong()

        val installedPackages = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(flags.toInt())
            }
        } catch (_: Throwable) {
            emptyList()
        }

        val targetInfos = installedPackages.filter { info ->
            val p = info.packageName.lowercase()
            p in KNOWN_PACKAGES || (p.contains("hihonor") && (
                p.contains("translat") ||
                    p.contains("magictext") ||
                    p.contains("hitouch") ||
                    p.contains("vision") ||
                    p.contains("scanner") ||
                    p.contains("subtitle") ||
                    p.contains("lens")
                ))
        }

        fun scoreComponent(pkg: String, cls: String): Int {
            val lower = cls.lowercase()
            var score = 0
            if (pkg == "com.hihonor.magictranslate") score += 25
            if (SCREEN_TRANSLATE_KEYWORDS.any { lower.contains(it) }) score += 60
            if (lower.contains("translat")) score += 20
            if (lower.contains("float") || lower.contains("overlay") || lower.contains("full") || lower.contains("capture")) score += 25
            if (lower.contains("ocr") || lower.contains("text")) score += 10
            if (MAIN_APP_EXCLUDE.any { lower.contains(it) }) score -= 60
            return score
        }

        for (pkgInfo in targetInfos) {
            val pkg = pkgInfo.packageName
            val isTranslatePkg = pkg.contains("translate", ignoreCase = true)

            // 1. Exported Activities
            pkgInfo.activities?.forEach { act ->
                if (!act.exported || !act.enabled || !act.permission.isNullOrBlank()) return@forEach
                val lower = act.name.lowercase()
                if (!isTranslatePkg && !lower.contains("translat") && !lower.contains("screen") && !lower.contains("subtitle")) return@forEach
                val simple = act.name.substringAfterLast('.')
                val sc = scoreComponent(pkg, act.name)
                results.add(
                    HonorComponentCandidate(
                        packageName = pkg,
                        className = act.name,
                        type = Prefs.COMP_TYPE_ACTIVITY,
                        label = "[ACT] $simple — $pkg",
                        score = sc
                    )
                )
            }

            // 2. Exported Services
            pkgInfo.services?.forEach { svc ->
                if (!svc.exported || !svc.enabled || !svc.permission.isNullOrBlank()) return@forEach
                val lower = svc.name.lowercase()
                if (!isTranslatePkg && !lower.contains("translat") && !lower.contains("screen")) return@forEach
                val simple = svc.name.substringAfterLast('.')
                val sc = scoreComponent(pkg, svc.name)
                results.add(
                    HonorComponentCandidate(
                        packageName = pkg,
                        className = svc.name,
                        type = Prefs.COMP_TYPE_SERVICE,
                        label = "[SVC] $simple — $pkg",
                        score = sc
                    )
                )
            }

            // 3. Exported Broadcast Receivers
            pkgInfo.receivers?.forEach { rcv ->
                if (!rcv.exported || !rcv.enabled || !rcv.permission.isNullOrBlank()) return@forEach
                val lower = rcv.name.lowercase()
                if (!isTranslatePkg && !lower.contains("translat") && !lower.contains("screen")) return@forEach
                val simple = rcv.name.substringAfterLast('.')
                val sc = scoreComponent(pkg, rcv.name)
                results.add(
                    HonorComponentCandidate(
                        packageName = pkg,
                        className = rcv.name,
                        type = Prefs.COMP_TYPE_BROADCAST,
                        label = "[RCV] $simple — $pkg",
                        score = sc
                    )
                )
            }
        }

        return results.sortedWith(
            compareByDescending<HonorComponentCandidate> { it.score }
                .thenBy { it.label }
        )
    }
}
