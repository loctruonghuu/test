package com.zek.mictsplus.ui

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.zek.mictsplus.Prefs
import com.zek.mictsplus.R
import com.zek.mictsplus.cts.CircleToSearchTrigger

class TriggerCtsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val delay = Prefs.getTriggerDelayMs(this)
        Handler(Looper.getMainLooper()).postDelayed({
            val ok = CircleToSearchTrigger.trigger(applicationContext)
            if (!ok) {
                Toast.makeText(applicationContext, R.string.cts_trigger_failed, Toast.LENGTH_SHORT).show()
            }
            finish()
        }, delay)
    }
}
