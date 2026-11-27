package com.zek.mictsplus.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import com.zek.mictsplus.R
import com.zek.mictsplus.translate.LiveTranslateService

/**
 * Invisible helper Activity that requests standard Android MediaProjection consent
 * and launches LiveTranslateService immediately upon approval.
 */
class TranslateConsentActivity : Activity() {

    companion object {
        private const val REQ_PROJECTION = 5001
        const val EXTRA_CONTINUOUS = "continuous"

        fun launch(context: Context, continuous: Boolean) {
            val intent = Intent(context, TranslateConsentActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_CONTINUOUS, continuous)
            }
            context.startActivity(intent)
        }
    }

    private var continuous = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        continuous = intent.getBooleanExtra(EXTRA_CONTINUOUS, true)

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.overlay_permission_required, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION")
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_PROJECTION)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PROJECTION && resultCode == RESULT_OK && data != null) {
            LiveTranslateService.start(applicationContext, resultCode, data, continuous)
        }
        finish()
    }
}
