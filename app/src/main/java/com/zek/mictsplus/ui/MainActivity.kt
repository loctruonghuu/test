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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.zek.mictsplus.Prefs
import com.zek.mictsplus.R
import com.zek.mictsplus.cts.CircleToSearchTrigger
import com.zek.mictsplus.databinding.ActivityMainBinding
import com.zek.mictsplus.edge.EdgeSwipeService
import com.zek.mictsplus.honor.HonorTranslateTrigger

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val sideEntries = listOf(
        "Right Edge (Default)" to Prefs.SIDE_RIGHT,
        "Left Edge" to Prefs.SIDE_LEFT,
        "Both Edges" to Prefs.SIDE_BOTH
    )

    private val actionEntries = listOf(
        "Circle to Search (MiCTS)" to Prefs.ACTION_CTS,
        "Honor MagicOS AI Translate" to Prefs.ACTION_HONOR_TRANSLATE,
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
        binding.spinnerEdgeSide.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            sideEntries.map { it.first }
        )
        val curSideIdx = sideEntries.indexOfFirst { it.second == Prefs.getEdgeSide(this) }.coerceAtLeast(0)
        binding.spinnerEdgeSide.setSelection(curSideIdx)

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

        binding.btnTriggerHonorTranslate.setOnClickListener {
            saveAllPreferences()
            val ok = HonorTranslateTrigger.trigger(this)
            if (!ok) {
                Toast.makeText(this, R.string.honor_translate_failed, Toast.LENGTH_LONG).show()
            }
        }

        binding.btnOverlayPermission.setOnClickListener { openOverlaySettings() }

        binding.btnAssistantSettings.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
            } catch (_: Throwable) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }

        // Honor AI Translate custom component fields + scanner
        binding.editHonorPkg.setText(Prefs.getHonorTranslatePkg(this))
        binding.editHonorAct.setText(Prefs.getHonorTranslateActivity(this))

        binding.btnScanHonorActivities.setOnClickListener {
            val candidates = HonorTranslateTrigger.scanHonorTranslateActivities(this)
            if (candidates.isEmpty()) {
                Toast.makeText(this, R.string.no_honor_activities_found, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val labels = candidates.map { it.label }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle(R.string.pick_honor_activity_title)
                .setItems(labels) { _, which ->
                    val chosen = candidates[which]
                    binding.editHonorPkg.setText(chosen.packageName)
                    binding.editHonorAct.setText(chosen.activityName)
                    saveAllPreferences()
                    Toast.makeText(this, "Selected: ${chosen.activityName.substringAfterLast('.')}", Toast.LENGTH_SHORT).show()
                }
                .setNeutralButton(R.string.reset_auto) { _, _ ->
                    binding.editHonorPkg.setText("")
                    binding.editHonorAct.setText("")
                    saveAllPreferences()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        // Sliders with live labels
        val initY = Prefs.getEdgeYPercent(this).toFloat()
        val initHeight = Prefs.getEdgeHeightDp(this).toFloat()
        val initAlpha = Prefs.getEdgeAlpha(this).toFloat()
        val initTriggerDelay = Prefs.getTriggerDelayMs(this).toFloat()
        val initHoldDelay = Prefs.getHoldDelayMs(this).toFloat()

        binding.sliderYPos.value = initY
        binding.sliderHeight.value = initHeight
        binding.sliderAlpha.value = initAlpha
        binding.sliderTriggerDelay.value = initTriggerDelay
        binding.sliderHoldDelay.value = initHoldDelay
        binding.switchVibrate.isChecked = Prefs.isVibrateEnabled(this)

        fun updateSliderLabels() {
            binding.tvYPosLabel.text = getString(R.string.edge_y_pos_fmt, binding.sliderYPos.value.toInt())
            binding.tvHeightLabel.text = getString(R.string.edge_height_fmt, binding.sliderHeight.value.toInt())
            binding.tvAlphaLabel.text = getString(R.string.edge_opacity_fmt, binding.sliderAlpha.value.toInt())
            binding.tvTriggerDelayLabel.text = getString(R.string.trigger_delay_fmt, binding.sliderTriggerDelay.value.toInt())
            binding.tvHoldDelayLabel.text = getString(R.string.hold_delay_fmt, binding.sliderHoldDelay.value.toInt())
        }
        updateSliderLabels()

        binding.sliderYPos.addOnChangeListener { _, _, _ -> updateSliderLabels() }
        binding.sliderHeight.addOnChangeListener { _, _, _ -> updateSliderLabels() }
        binding.sliderAlpha.addOnChangeListener { _, _, _ -> updateSliderLabels() }
        binding.sliderTriggerDelay.addOnChangeListener { _, _, _ -> updateSliderLabels() }
        binding.sliderHoldDelay.addOnChangeListener { _, _, _ -> updateSliderLabels() }

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
        Prefs.setEdgeSide(this, sideEntries[binding.spinnerEdgeSide.selectedItemPosition].second)
        Prefs.setSwipeInAction(this, actionEntries[binding.spinnerSwipeIn.selectedItemPosition].second)
        Prefs.setSwipeUpAction(this, actionEntries[binding.spinnerSwipeUp.selectedItemPosition].second)
        Prefs.setSwipeDownAction(this, actionEntries[binding.spinnerSwipeDown.selectedItemPosition].second)
        Prefs.setEdgeYPercent(this, binding.sliderYPos.value.toInt())
        Prefs.setEdgeHeightDp(this, binding.sliderHeight.value.toInt())
        Prefs.setEdgeAlpha(this, binding.sliderAlpha.value.toInt())
        Prefs.setTriggerDelayMs(this, binding.sliderTriggerDelay.value.toLong())
        Prefs.setHoldDelayMs(this, binding.sliderHoldDelay.value.toLong())
        Prefs.setVibrateEnabled(this, binding.switchVibrate.isChecked)
        Prefs.setHonorTranslatePkg(this, binding.editHonorPkg.text?.toString() ?: "")
        Prefs.setHonorTranslateActivity(this, binding.editHonorAct.text?.toString() ?: "")
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }
}
