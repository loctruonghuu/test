package com.zek.mictsplus.honor

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.zek.mictsplus.Prefs
import com.zek.mictsplus.cts.CircleToSearchTrigger
import org.lsposed.hiddenapibypass.HiddenApiBypass

data class HonorComponentCandidate(
    val packageName: String,
    val className: String,
    val type: String, // activity | service | broadcast
    val label: String,
    val score: Int
)

data class TriggerResult(
    val success: Boolean,
    val detail: String
)

/**
 * Triggers Honor MagicOS AI Screen Translation using:
 *  1) Direct call to com.hihonor.magictranslate/.screen.service.ScreenTranslateQuickService
 *  2) HiddenApiBypass IStatusBarService.clickTile(ScreenTranslateQuickService)
 *  3) Exported .screen. / ScreenTranslate Activities, Services, and Receivers in com.hihonor.magictranslate
 *  4) Known MagicOS Screen Translate intent actions
 */
object HonorTranslateTrigger {
    private const val TAG = "HonorTranslateTrigger"

    private val QUICK_TILE_COMPONENT = ComponentName(
        Prefs.DEFAULT_HONOR_PKG,
        Prefs.DEFAULT_HONOR_QUICK_SERVICE
    )

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

    private val SCREEN_TRANSLATE_KEYWORDS = listOf(
        "screentranslat",
        "screen_translat",
        "fullscreentranslat",
        "floattranslat",
        "overlaytranslat",
        "capturetranslat",
        "transscreen",
        ".screen."
    )

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

    fun trigger(context: Context): Boolean = triggerWithResult(context).success

    fun triggerWithResult(context: Context): TriggerResult {
        if (Prefs.isVibrateEnabled(context)) {
            CircleToSearchTrigger.vibrate(context)
        }

        val targetPkg = Prefs.getHonorTranslatePkg(context)
        val targetClass = Prefs.getHonorTranslateClass(context)
        val targetType = Prefs.getHonorTranslateType(context)
        var lastError = ""

        // 1. Try configured target component first (defaults to ScreenTranslateQuickService)
        if (targetPkg.isNotEmpty() && targetClass.isNotEmpty()) {
            val isQuickTile = targetClass == Prefs.DEFAULT_HONOR_QUICK_SERVICE ||
                targetClass.endsWith("ScreenTranslateQuickService")

            if (isQuickTile) {
                // 1a. Try StatusBarService.clickTile(ComponentName) via HiddenApiBypass
                val tileRes = tryClickQuickTile(ComponentName(targetPkg, targetClass))
                if (tileRes.success) return tileRes
                lastError = tileRes.detail
            }

            // 1b. Try direct startService / startActivity / sendBroadcast
            val directRes = launchCandidateWithResult(context, targetPkg, targetClass, targetType)
            if (directRes.success) return directRes
            lastError = directRes.detail
        }

        // 2. Always attempt clickTile on ScreenTranslateQuickService if not already tried
        if (targetClass != Prefs.DEFAULT_HONOR_QUICK_SERVICE) {
            val tileRes = tryClickQuickTile(QUICK_TILE_COMPONENT)
            if (tileRes.success) return tileRes
        }

        // 3. Auto-detect exported (non-privileged) .screen. / ScreenTranslate components
        val candidates = scanHonorScreenTranslateComponents(context)
        val screenSpecific = candidates.filter {
            it.score >= 50 && it.className != targetClass
        }
        for (cand in screenSpecific) {
            val res = launchCandidateWithResult(context, cand.packageName, cand.className, cand.type)
            if (res.success) return res
        }

        // 4. Known MagicOS Screen Translation intent actions
        val knownActions = listOf(
            "com.hihonor.magictranslate.action.SCREEN_TRANSLATE",
            "com.hihonor.magictranslate.SCREEN_TRANSLATE",
            "com.hihonor.action.SCREEN_TRANSLATE",
            "com.hihonor.hitouch.action.SCREEN_TRANSLATE",
            "com.hihonor.vision.action.SCREEN_TRANSLATE"
        )
        for (action in knownActions) {
            if (tryAction(context, action)) {
                return TriggerResult(true, "Launched via action $action")
            }
        }

        return TriggerResult(
            false,
            lastError.ifBlank { "No callable Screen Translate component found." }
        )
    }

    @SuppressLint("PrivateApi")
    private fun tryClickQuickTile(component: ComponentName): TriggerResult {
        return try {
            val sm = Class.forName("android.os.ServiceManager")
            val getService = sm.getMethod("getService", String::class.java)
            val binder = getService.invoke(null, "statusbar") as? IBinder
                ?: return TriggerResult(false, "statusbar binder is null")

            val stubClass = Class.forName("com.android.internal.statusbar.IStatusBarService\$Stub")
            val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
            val sbs = asInterface.invoke(null, binder)
                ?: return TriggerResult(false, "IStatusBarService is null")

            val iSbsClass = Class.forName("com.android.internal.statusbar.IStatusBarService")
            HiddenApiBypass.invoke(iSbsClass, sbs, "clickTile", component)
            Log.d(TAG, "clickTile succeeded for $component")
            TriggerResult(true, "Triggered via StatusBar clickTile")
        } catch (t: Throwable) {
            val cause = t.cause ?: t
            Log.w(TAG, "clickTile blocked: ${cause.javaClass.simpleName}: ${cause.message}")
            TriggerResult(false, "clickTile blocked (${cause.javaClass.simpleName})")
        }
    }

    fun launchCandidate(context: Context, pkg: String, className: String, type: String): Boolean =
        launchCandidateWithResult(context, pkg, className, type).success

    fun launchCandidateWithResult(
        context: Context,
        pkg: String,
        className: String,
        type: String
    ): TriggerResult {
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
                    } catch (_: IllegalStateException) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        } else {
                            throw IllegalStateException("startService failed")
                        }
                    }
                    TriggerResult(true, "Started service ${className.substringAfterLast('.')}")
                }

                Prefs.COMP_TYPE_BROADCAST -> {
                    context.sendBroadcast(intent)
                    TriggerResult(true, "Sent broadcast ${className.substringAfterLast('.')}")
                }

                else -> {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    context.startActivity(intent)
                    TriggerResult(true, "Started activity ${className.substringAfterLast('.')}")
                }
            }
        } catch (t: Throwable) {
            val msg = "${t.javaClass.simpleName}: ${t.message}"
            Log.w(TAG, "Failed [$type] $pkg/$className -> $msg")
            TriggerResult(false, msg)
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
            if (pkg == Prefs.DEFAULT_HONOR_PKG) score += 30
            if (SCREEN_TRANSLATE_KEYWORDS.any { lower.contains(it) }) score += 60
            if (lower.contains("translat")) score += 20
            if (lower.contains("float") || lower.contains("overlay") || lower.contains("full") || lower.contains("capture")) score += 25
            if (MAIN_APP_EXCLUDE.any { lower.contains(it) }) score -= 60
            return score
        }

        for (pkgInfo in targetInfos) {
            val pkg = pkgInfo.packageName
            val isTranslatePkg = pkg.contains("translate", ignoreCase = true)

            pkgInfo.activities?.forEach { act ->
                if (!act.exported || !act.enabled || !act.permission.isNullOrBlank()) return@forEach
                val lower = act.name.lowercase()
                if (!isTranslatePkg && !lower.contains("translat") && !lower.contains("screen")) return@forEach
                val simple = act.name.substringAfterLast('.')
                results.add(
                    HonorComponentCandidate(
                        packageName = pkg,
                        className = act.name,
                        type = Prefs.COMP_TYPE_ACTIVITY,
                        label = "[ACT] $simple — $pkg",
                        score = scoreComponent(pkg, act.name)
                    )
                )
            }

            pkgInfo.services?.forEach { svc ->
                if (!svc.exported || !svc.enabled || !svc.permission.isNullOrBlank()) return@forEach
                val lower = svc.name.lowercase()
                if (!isTranslatePkg && !lower.contains("translat") && !lower.contains("screen")) return@forEach
                val simple = svc.name.substringAfterLast('.')
                results.add(
                    HonorComponentCandidate(
                        packageName = pkg,
                        className = svc.name,
                        type = Prefs.COMP_TYPE_SERVICE,
                        label = "[SVC] $simple — $pkg",
                        score = scoreComponent(pkg, svc.name)
                    )
                )
            }

            pkgInfo.receivers?.forEach { rcv ->
                if (!rcv.exported || !rcv.enabled || !rcv.permission.isNullOrBlank()) return@forEach
                val lower = rcv.name.lowercase()
                if (!isTranslatePkg && !lower.contains("translat") && !lower.contains("screen")) return@forEach
                val simple = rcv.name.substringAfterLast('.')
                results.add(
                    HonorComponentCandidate(
                        packageName = pkg,
                        className = rcv.name,
                        type = Prefs.COMP_TYPE_BROADCAST,
                        label = "[RCV] $simple — $pkg",
                        score = scoreComponent(pkg, rcv.name)
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
