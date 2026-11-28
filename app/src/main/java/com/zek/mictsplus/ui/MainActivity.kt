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
        "Honor AI Screen Translate" to Prefs.ACTION_HONOR_TRANSLATE,
        "None" to Prefs.ACTION_NONE
    )

    private val skinEntries = listOf(
        "Rainbow Pill (Easy Tap)" to Prefs.SKIN_RAINBOW,
        "White Pill" to Prefs.SKIN_WHITE,
        "Cyan-Blue Gradient" to Prefs.SKIN_CYAN,
        "Invisible (Transparent)" to Prefs.SKIN_TRANSPARENT
    )

    private val compTypeEntries = listOf(
        "Service" to Prefs.COMP_TYPE_SERVICE,
        "Activity" to Prefs.COMP_TYPE_ACTIVITY,
        "Broadcast" to Prefs.COMP_TYPE_BROADCAST
    )

    private var updatingSwitches = false

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
        updatingSwitches = true
        binding.switchNavBar.isChecked = hasOverlay && Prefs.isNavBarEnabled(this)
        binding.switchEdgeSwipe.isChecked = hasOverlay && Prefs.isEdgeEnabled(this)
        updatingSwitches = false
    }

    private fun setupSpinners() {
        val actionLabels = actionEntries.map { it.first }
        val actionAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, actionLabels)

        // Nav Bar actions
        binding.spinnerNavTap1.adapter = actionAdapter
        binding.spinnerNavHold.adapter = actionAdapter
        binding.spinnerNavTap2.adapter = actionAdapter
        binding.spinnerNavTap1.setSelection(
            actionEntries.indexOfFirst { it.second == Prefs.getNavTap1Action(this) }.coerceAtLeast(0)
        )
        binding.spinnerNavHold.setSelection(
            actionEntries.indexOfFirst { it.second == Prefs.getNavHoldAction(this) }.coerceAtLeast(0)
        )
        binding.spinnerNavTap2.setSelection(
            actionEntries.indexOfFirst { it.second == Prefs.getNavTap2Action(this) }.coerceAtLeast(0)
        )

        // Nav Bar skin
        binding.spinnerNavSkin.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            skinEntries.map { it.first }
        )
        binding.spinnerNavSkin.setSelection(
            skinEntries.indexOfFirst { it.second == Prefs.getNavSkin(this) }.coerceAtLeast(0)
        )

        // Edge Swipe side & actions
        binding.spinnerEdgeSide.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            sideEntries.map { it.first }
        )
        binding.spinnerEdgeSide.setSelection(
            sideEntries.indexOfFirst { it.second == Prefs.getEdgeSide(this) }.coerceAtLeast(0)
        )

        binding.spinnerSwipeIn.adapter = actionAdapter
        binding.spinnerSwipeUp.adapter = actionAdapter
        binding.spinnerSwipeDown.adapter = actionAdapter
        binding.spinnerSwipeIn.setSelection(
            actionEntries.indexOfFirst { it.second == Prefs.getSwipeInAction(this) }.coerceAtLeast(0)
        )
        binding.spinnerSwipeUp.setSelection(
            actionEntries.indexOfFirst { it.second == Prefs.getSwipeUpAction(this) }.coerceAtLeast(0)
        )
        binding.spinnerSwipeDown.setSelection(
            actionEntries.indexOfFirst { it.second == Prefs.getSwipeDownAction(this) }.coerceAtLeast(0)
        )

        // Honor Component Type
        binding.spinnerCompType.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            compTypeEntries.map { it.first }
        )
        binding.spinnerCompType.setSelection(
            compTypeEntries.indexOfFirst { it.second == Prefs.getHonorTranslateType(this) }.coerceAtLeast(0)
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
            val res = HonorTranslateTrigger.triggerWithResult(this)
            val prefix = if (res.success) "✓ " else "✕ "
            Toast.makeText(this, "$prefix${res.detail}", Toast.LENGTH_LONG).show()
        }

        binding.btnOverlayPermission.setOnClickListener { openOverlaySettings() }

        binding.btnAssistantSettings.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
            } catch (_: Throwable) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }

        binding.editHonorPkg.setText(Prefs.getHonorTranslatePkg(this))
        binding.editHonorAct.setText(Prefs.getHonorTranslateClass(this))

        binding.btnScanHonorActivities.setOnClickListener {
            val candidates = HonorTranslateTrigger.scanHonorScreenTranslateComponents(this)
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
                    binding.editHonorAct.setText(chosen.className)
                    val typeIdx = compTypeEntries.indexOfFirst { it.second == chosen.type }.coerceAtLeast(0)
                    binding.spinnerCompType.setSelection(typeIdx)
                    saveAllPreferences()
                    val res = HonorTranslateTrigger.launchCandidateWithResult(
                        this,
                        chosen.packageName,
                        chosen.className,
                        chosen.type
                    )
                    val prefix = if (res.success) "✓ " else "✕ "
                    Toast.makeText(this, "$prefix${res.detail}", Toast.LENGTH_LONG).show()
                }
                .setNeutralButton(R.string.reset_default_quick_service) { _, _ ->
                    binding.editHonorPkg.setText(Prefs.DEFAULT_HONOR_PKG)
                    binding.editHonorAct.setText(Prefs.DEFAULT_HONOR_QUICK_SERVICE)
                    binding.spinnerCompType.setSelection(0) // Service
                    saveAllPreferences()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        // Nav Bar Sliders
        binding.sliderNavWidth.value = Prefs.getNavWidthDp(this).toFloat().coerceIn(40f, 320f)
        binding.sliderNavThickness.value = Prefs.getNavThicknessDp(this).toFloat().coerceIn(4f, 36f)
        binding.sliderNavHeight.value = Prefs.getNavBottomHeightDp(this).toFloat().coerceIn(0f, 120f)
        binding.sliderNavOffsetX.value = Prefs.getNavOffsetXDp(this).toFloat().coerceIn(-150f, 150f)
        binding.sliderNavHoldDelay.value = Prefs.getNavHoldDelayMs(this).toFloat().coerceIn(150f, 1000f)
        binding.sliderNavAlpha.value = Prefs.getNavAlpha(this).toFloat().coerceIn(0f, 100f)

        // Edge Swipe Sliders
        binding.sliderYPos.value = Prefs.getEdgeYPercent(this).toFloat().coerceIn(10f, 150f)
        binding.sliderHeight.value = Prefs.getEdgeHeightDp(this).toFloat().coerceIn(60f, 260f)
        binding.sliderAlpha.value = Prefs.getEdgeAlpha(this).toFloat().coerceIn(0f, 100f)

        // Shared Delay Sliders
        binding.sliderTriggerDelay.value = Prefs.getTriggerDelayMs(this).toFloat().coerceIn(0f, 1500f)
        binding.sliderHoldDelay.value = Prefs.getHoldDelayMs(this).toFloat().coerceIn(0f, 1000f)
        binding.switchVibrate.isChecked = Prefs.isVibrateEnabled(this)

        fun updateSliderLabels() {
            binding.tvNavWidthLabel.text = getString(R.string.nav_width_fmt, binding.sliderNavWidth.value.toInt())
            binding.tvNavThicknessLabel.text = getString(R.string.nav_thickness_fmt, binding.sliderNavThickness.value.toInt())
            binding.tvNavHeightLabel.text = getString(R.string.nav_height_fmt, binding.sliderNavHeight.value.toInt())
            binding.tvNavOffsetXLabel.text = getString(R.string.nav_offset_x_fmt, binding.sliderNavOffsetX.value.toInt())
            binding.tvNavHoldDelayLabel.text = getString(R.string.nav_hold_delay_fmt, binding.sliderNavHoldDelay.value.toInt())
            binding.tvNavAlphaLabel.text = getString(R.string.nav_alpha_fmt, binding.sliderNavAlpha.value.toInt())

            binding.tvYPosLabel.text = getString(R.string.edge_y_pos_fmt, binding.sliderYPos.value.toInt())
            binding.tvHeightLabel.text = getString(R.string.edge_height_fmt, binding.sliderHeight.value.toInt())
            binding.tvAlphaLabel.text = getString(R.string.edge_opacity_fmt, binding.sliderAlpha.value.toInt())

            binding.tvTriggerDelayLabel.text = getString(R.string.trigger_delay_fmt, binding.sliderTriggerDelay.value.toInt())
            binding.tvHoldDelayLabel.text = getString(R.string.hold_delay_fmt, binding.sliderHoldDelay.value.toInt())
        }
        updateSliderLabels()

        binding.sliderNavWidth.addOnChangeListener { _, _, _ -> updateSliderLabels() }
        binding.sliderNavThickness.addOnChangeListener { _, _, _ -> updateSliderLabels() }
        binding.sliderNavHeight.addOnChangeListener { _, _, _ -> updateSliderLabels() }
        binding.sliderNavOffsetX.addOnChangeListener { _, _, _ -> updateSliderLabels() }
        binding.sliderNavHoldDelay.addOnChangeListener { _, _, _ -> updateSliderLabels() }
        binding.sliderNavAlpha.addOnChangeListener { _, _, _ -> updateSliderLabels() }
        binding.sliderYPos.addOnChangeListener { _, _, _ -> updateSliderLabels() }
        binding.sliderHeight.addOnChangeListener { _, _, _ -> updateSliderLabels() }
        binding.sliderAlpha.addOnChangeListener { _, _, _ -> updateSliderLabels() }
        binding.sliderTriggerDelay.addOnChangeListener { _, _, _ -> updateSliderLabels() }
        binding.sliderHoldDelay.addOnChangeListener { _, _, _ -> updateSliderLabels() }

        binding.switchNavBar.setOnCheckedChangeListener { _, isChecked ->
            if (updatingSwitches) return@setOnCheckedChangeListener
            if (isChecked && !Settings.canDrawOverlays(this)) {
                updatingSwitches = true
                binding.switchNavBar.isChecked = false
                updatingSwitches = false
                openOverlaySettings()
                return@setOnCheckedChangeListener
            }
            saveAllPreferences()
            Prefs.setNavBarEnabled(this, isChecked)
            EdgeSwipeService.reload(this)
        }

        binding.switchEdgeSwipe.setOnCheckedChangeListener { _, isChecked ->
            if (updatingSwitches) return@setOnCheckedChangeListener
            if (isChecked && !Settings.canDrawOverlays(this)) {
                updatingSwitches = true
                binding.switchEdgeSwipe.isChecked = false
                updatingSwitches = false
                openOverlaySettings()
                return@setOnCheckedChangeListener
            }
            saveAllPreferences()
            Prefs.setEdgeEnabled(this, isChecked)
            EdgeSwipeService.reload(this)
        }

        binding.btnApplySettings.setOnClickListener {
            saveAllPreferences()
            EdgeSwipeService.reload(this)
            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveAllPreferences() {
        Prefs.setNavTap1Action(this, actionEntries[binding.spinnerNavTap1.selectedItemPosition].second)
        Prefs.setNavHoldAction(this, actionEntries[binding.spinnerNavHold.selectedItemPosition].second)
        Prefs.setNavTap2Action(this, actionEntries[binding.spinnerNavTap2.selectedItemPosition].second)
        Prefs.setNavSkin(this, skinEntries[binding.spinnerNavSkin.selectedItemPosition].second)
        Prefs.setNavWidthDp(this, binding.sliderNavWidth.value.toInt())
        Prefs.setNavThicknessDp(this, binding.sliderNavThickness.value.toInt())
        Prefs.setNavBottomHeightDp(this, binding.sliderNavHeight.value.toInt())
        Prefs.setNavOffsetXDp(this, binding.sliderNavOffsetX.value.toInt())
        Prefs.setNavHoldDelayMs(this, binding.sliderNavHoldDelay.value.toLong())
        Prefs.setNavAlpha(this, binding.sliderNavAlpha.value.toInt())

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
        Prefs.setHonorTranslateClass(this, binding.editHonorAct.text?.toString() ?: "")
        Prefs.setHonorTranslateType(this, compTypeEntries[binding.spinnerCompType.selectedItemPosition].second)
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }
}
