package com.zek.mictsplus.ui

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.zek.mictsplus.Prefs
import com.zek.mictsplus.honor.HonorTranslateTrigger

class TriggerHonorTranslateActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val delay = Prefs.getTriggerDelayMs(this)
        Handler(Looper.getMainLooper()).postDelayed({
            val res = HonorTranslateTrigger.triggerWithResult(applicationContext)
            if (!res.success) {
                Toast.makeText(applicationContext, "Screen Translate: ${res.detail}", Toast.LENGTH_LONG).show()
            }
            finish()
        }, delay)
    }
}
