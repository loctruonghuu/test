package com.zek.mictsplus.ui

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.zek.mictsplus.Prefs
import com.zek.mictsplus.R
import com.zek.mictsplus.honor.HonorTranslateTrigger

class TriggerHonorTranslateActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val delay = Prefs.getTriggerDelayMs(this)
        Handler(Looper.getMainLooper()).postDelayed({
            val ok = HonorTranslateTrigger.trigger(applicationContext)
            if (!ok) {
                Toast.makeText(applicationContext, R.string.honor_translate_failed, Toast.LENGTH_LONG).show()
            }
            finish()
        }, delay)
    }
}
