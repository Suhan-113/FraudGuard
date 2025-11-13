package com.example.aifraudguard

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView

class SummaryActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_summary)

        // Ensure the Activity appears on top of everything
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        } else {
            @Suppress("DEPRECATION")
            window.setType(WindowManager.LayoutParams.TYPE_PHONE)
        }

        // FIX: Set layout gravity to TOP, as requested
        window.attributes.gravity = android.view.Gravity.TOP

        // Ensure the activity is focused to receive input (like button clicks)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)

        val tvScamType: TextView = findViewById(R.id.tv_scam_type)
        val tvSummary: TextView = findViewById(R.id.tv_educational_summary)
        val btnDismiss: Button = findViewById(R.id.btn_dismiss_summary)

        // Retrieve data from the shared companion object
        val summaryData = FraudGuardScreeningService.lastFraudSummary

        if (summaryData != null) {
            tvScamType.text = "Scam Type: ${summaryData.first}"
            tvSummary.text = summaryData.second
        } else {
            tvScamType.text = "No Fraud Detected"
            tvSummary.text = "The call appeared safe. Stay vigilant!"
        }

        btnDismiss.setOnClickListener {
            // Clear the shared data to prevent showing old summary on next non-fraud call
            FraudGuardScreeningService.lastFraudSummary = null
            finish()
        }
    }
}
