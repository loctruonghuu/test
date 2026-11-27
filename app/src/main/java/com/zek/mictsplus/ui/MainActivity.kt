package com.zek.mictsplus.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.zek.mictsplus.Prefs
import com.zek.mictsplus.R
import com.zek.mictsplus.cts.CircleToSearchTrigger
import com.zek.mictsplus.databinding.ActivityMainBinding
import com.zek.mictsplus.edge.EdgeSwipeService
import com.zek.mictsplus.translate.LiveTranslateService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val langEntries = listOf(
        "English" to "en",
        "Vietnamese" to "vi",
        "Chinese (Simplified)" to "zh",
        "Japanese" to "ja",
        "Korean" to "ko",
        "Spanish" to "es",
        "French" to "fr",
        "German" to "de",
        "Thai" to "th"
    )

    private val scriptEntries = listOf(
        "Chinese + Latin (Best for CN ROM)" to "chinese",
        "Japanese + Latin" to "japanese",
        "Korean + Latin" to "korean",
        "Latin Only (Fastest)" to "latin"
    )

    private val sideEntries = listOf(
        "Right Edge" to Prefs.SIDE_RIGHT,
        "Left Edge" to Prefs.SIDE_LEFT,
        "Both Edges" to Prefs.SIDE_BOTH
    )

    private val actionEntries = listOf(
        "Circle to Search" to Prefs.ACTION_CTS,
        "Live Translate (Continuous)" to Prefs.ACTION_LIVE_TRANSLATE,
        "Snap Translate (Once)" to Prefs.ACTION_SNAP_TRANSLATE,
        "None" to Prefs.ACTION_NONE
    )

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestNotificationPermissionIfNeeded()
        setupSpinners()
        setupControls()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun refreshPermissionStatus() {
        val hasOverlay = Settings.canDrawOverlays(this)
        binding.btnOverlayPermission.text = if (hasOverlay) {
            getString(R.string.overlay_granted)
        } else {
            getString(R.string.grant_overlay)
        }
        binding.switchEdgeSwipe.isChecked = hasOverlay && Prefs.isEdgeEnabled(this)
    }

    private fun setupSpinners() {
        // Target language
        binding.spinnerTargetLang.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            langEntries.map { it.first }
        )
        val curLangIdx = langEntries.indexOfFirst { it.second == Prefs.getTargetLang(this) }.coerceAtLeast(0)
        binding.spinnerTargetLang.setSelection(curLangIdx)

        // OCR Script
        binding.spinnerOcrScript.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            scriptEntries.map { it.first }
        )
        val curScriptIdx = scriptEntries.indexOfFirst { it.second == Prefs.getOcrScript(this) }.coerceAtLeast(0)
        binding.spinnerOcrScript.setSelection(curScriptIdx)

        // Edge Side
        binding.spinnerEdgeSide.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            sideEntries.map { it.first }
        )
        val curSideIdx = sideEntries.indexOfFirst { it.second == Prefs.getEdgeSide(this) }.coerceAtLeast(0)
        binding.spinnerEdgeSide.setSelection(curSideIdx)

        // Gesture actions
        val actionLabels = actionEntries.map { it.first }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, actionLabels)
        binding.spinnerSwipeIn.adapter = adapter
        binding.spinnerSwipeUp.adapter = adapter
        binding.spinnerSwipeDown.adapter = adapter

        binding.spinnerSwipeIn.setSelection(
            actionEntries.indexOfFirst { it.second == Prefs.getSwipeInAction(this) }.coerceAtLeast(0)
        )
        binding.spinnerSwipeUp.setSelection(
            actionEntries.indexOfFirst { it.second == Prefs.getSwipeUpAction(this) }.coerceAtLeast(0)
        )
        binding.spinnerSwipeDown.setSelection(
            actionEntries.indexOfFirst { it.second == Prefs.getSwipeDownAction(this) }.coerceAtLeast(0)
        )
    }

    private fun setupControls() {
        binding.btnTriggerCts.setOnClickListener {
            val ok = CircleToSearchTrigger.trigger(this)
            if (!ok) {
                Toast.makeText(this, R.string.cts_trigger_failed, Toast.LENGTH_LONG).show()
            }
        }

        binding.btnStartLiveTranslate.setOnClickListener {
            if (LiveTranslateService.isRunning) {
                LiveTranslateService.stop(this)
                Toast.makeText(this, "Live Translate stopped", Toast.LENGTH_SHORT).show()
            } else {
                if (!Settings.canDrawOverlays(this)) {
                    openOverlaySettings()
                    return@setOnClickListener
                }
                TranslateConsentActivity.launch(this, continuous = true)
            }
        }

        binding.btnOverlayPermission.setOnClickListener {
            openOverlaySettings()
        }

        binding.btnAssistantSettings.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
            } catch (_: Throwable) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }

        binding.sliderYPos.value = Prefs.getEdgeYPercent(this).toFloat()
        binding.sliderHeight.value = Prefs.getEdgeHeightDp(this).toFloat()
        binding.sliderAlpha.value = Prefs.getEdgeAlpha(this).toFloat()
        binding.switchVibrate.isChecked = Prefs.isVibrateEnabled(this)

        binding.switchEdgeSwipe.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!Settings.canDrawOverlays(this)) {
                    binding.switchEdgeSwipe.isChecked = false
                    openOverlaySettings()
                    return@setOnCheckedChangeListener
                }
                saveAllPreferences()
                Prefs.setEdgeEnabled(this, true)
                EdgeSwipeService.start(this)
            } else {
                Prefs.setEdgeEnabled(this, false)
                EdgeSwipeService.stop(this)
            }
        }

        binding.btnApplySettings.setOnClickListener {
            saveAllPreferences()
            if (Prefs.isEdgeEnabled(this)) {
                EdgeSwipeService.reload(this)
            }
            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveAllPreferences() {
        Prefs.setTargetLang(this, langEntries[binding.spinnerTargetLang.selectedItemPosition].second)
        Prefs.setOcrScript(this, scriptEntries[binding.spinnerOcrScript.selectedItemPosition].second)
        Prefs.setEdgeSide(this, sideEntries[binding.spinnerEdgeSide.selectedItemPosition].second)
        Prefs.setSwipeInAction(this, actionEntries[binding.spinnerSwipeIn.selectedItemPosition].second)
        Prefs.setSwipeUpAction(this, actionEntries[binding.spinnerSwipeUp.selectedItemPosition].second)
        Prefs.setSwipeDownAction(this, actionEntries[binding.spinnerSwipeDown.selectedItemPosition].second)
        Prefs.setEdgeYPercent(this, binding.sliderYPos.value.toInt())
        Prefs.setEdgeHeightDp(this, binding.sliderHeight.value.toInt())
        Prefs.setEdgeAlpha(this, binding.sliderAlpha.value.toInt())
        Prefs.setVibrateEnabled(this, binding.switchVibrate.isChecked)
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }
}
