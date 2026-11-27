package com.zek.mictsplus.honor

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.zek.mictsplus.Prefs
import com.zek.mictsplus.cts.CircleToSearchTrigger

data class HonorActivityCandidate(
    val packageName: String,
    val activityName: String,
    val label: String
)

/**
 * Triggers Honor MagicOS AI Translate (com.hihonor.magictranslate / com.hihonor.magicvoice / com.hihonor.lens)
 * without Accessibility Services, Developer Options, or Root.
 */
object HonorTranslateTrigger {
    private const val TAG = "HonorTranslateTrigger"

    private val KNOWN_PACKAGES = listOf(
        "com.hihonor.magictranslate",
        "com.hihonor.translate",
        "com.hihonor.aitranslate",
        "com.hihonor.aisubtitle",
        "com.hihonor.magicvoice",
        "com.hihonor.lens",
        "com.hihonor.scanner",
        "com.hihonor.vassistant"
    )

    private val KEYWORDS = listOf(
        "translate",
        "subtitle",
        "caption",
        "facetoface",
        "interpret",
        "screen"
    )

    fun trigger(context: Context): Boolean {
        if (Prefs.isVibrateEnabled(context)) {
            CircleToSearchTrigger.vibrate(context)
        }

        // 1. Try user-selected / custom package + activity first
        val customPkg = Prefs.getHonorTranslatePkg(context)
        val customAct = Prefs.getHonorTranslateActivity(context)
        if (customPkg.isNotEmpty()) {
            if (customAct.isNotEmpty()) {
                if (launchComponent(context, customPkg, customAct)) return true
            } else {
                if (launchPackageDefault(context, customPkg)) return true
            }
        }

        // 2. Try com.hihonor.magictranslate default launcher or best exported activity
        for (pkg in listOf("com.hihonor.magictranslate", "com.hihonor.translate", "com.hihonor.aitranslate")) {
            if (launchPackageDefault(context, pkg)) return true
        }

        // 3. Scan exported activities across Honor AI packages and launch best match
        val candidates = scanHonorTranslateActivities(context)
        for (cand in candidates) {
            if (launchComponent(context, cand.packageName, cand.activityName)) {
                Log.d(TAG, "Launched ${cand.packageName}/${cand.activityName}")
                return true
            }
        }

        return false
    }

    fun launchComponent(context: Context, pkg: String, activity: String): Boolean {
        return try {
            val intent = Intent().apply {
                component = ComponentName(pkg, activity)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Cannot launch $pkg/$activity: ${t.message}")
            false
        }
    }

    private fun launchPackageDefault(context: Context, pkg: String): Boolean {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(launchIntent)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Cannot launch default for $pkg: ${t.message}")
            false
        }
    }

    /**
     * Scans installed Honor system apps for exported Translate / AI Subtitle / Screen Translate activities
     * so the user can pick the exact MagicOS 11 activity directly in the UI without ADB.
     */
    fun scanHonorTranslateActivities(context: Context): List<HonorActivityCandidate> {
        val pm = context.packageManager
        val results = mutableListOf<HonorActivityCandidate>()

        val installedPackages = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(PackageManager.GET_ACTIVITIES)
            }
        } catch (_: Throwable) {
            emptyList()
        }

        val targetInfos = installedPackages.filter { info ->
            val p = info.packageName.lowercase()
            p in KNOWN_PACKAGES || (p.contains("hihonor") && (p.contains("translat") || p.contains("subtitle") || p.contains("voice") || p.contains("lens") || p.contains("ai")))
        }

        for (pkgInfo in targetInfos) {
            val activities = pkgInfo.activities ?: continue
            val isPrimaryTranslatePkg = pkgInfo.packageName.contains("translate", ignoreCase = true)

            for (act in activities) {
                if (!act.exported || !act.enabled) continue
                // Skip activities guarded by system-only permissions
                if (!act.permission.isNullOrBlank()) continue

                val lowerName = act.name.lowercase()
                val matchesKeyword = isPrimaryTranslatePkg || KEYWORDS.any { lowerName.contains(it) }
                if (!matchesKeyword) continue

                val simpleName = act.name.substringAfterLast('.')
                val appLabel = try {
                    act.loadLabel(pm).toString().ifBlank { simpleName }
                } catch (_: Throwable) {
                    simpleName
                }

                results.add(
                    HonorActivityCandidate(
                        packageName = pkgInfo.packageName,
                        activityName = act.name,
                        label = "$appLabel ($simpleName) — ${pkgInfo.packageName}"
                    )
                )
            }
        }

        // Prioritize magictranslate & screen/translate activities first
        return results.sortedWith(
            compareByDescending<HonorActivityCandidate> { it.packageName == "com.hihonor.magictranslate" }
                .thenByDescending { it.activityName.lowercase().contains("screen") }
                .thenByDescending { it.activityName.lowercase().contains("translate") }
                .thenBy { it.label }
        )
    }
}
