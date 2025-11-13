package com.example.aifraudguard

import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.N)
class FraudGuardScreeningService : CallScreeningService() {

    // --- The Default Number to use for all reports ---
    private val DEFAULT_SCAMMER_NUMBER = "+919876543210"

    companion object {
        var scammerNumber: String? = null
        // Pair<ScamType, EducationalSummary>
        var lastFraudSummary: Pair<String, String>? = null
    }

    override fun onScreenCall(callDetails: Call.Details) {
        // Clear old summary data
        lastFraudSummary = null

        // We only care about incoming calls for this feature
        if (callDetails.callDirection == Call.Details.DIRECTION_INCOMING) {

            // --- CRITICAL CHANGE: Set the hardcoded default number ---
            scammerNumber = DEFAULT_SCAMMER_NUMBER

            // Start the OverlayService to show the button
            val intent = Intent(this, OverlayService::class.java)
            startService(intent)

            // Allow the call to proceed normally
            val response = CallResponse.Builder().build()
            respondToCall(callDetails, response)
        }
    }
}