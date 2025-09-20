package com.example.aifraudguard
import com.example.aifraudguard.OverlayService
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.N)
class FraudGuardScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        // We only care about active calls for this prototype
        if (callDetails.callDirection == Call.Details.DIRECTION_INCOMING) {

            // Start the OverlayService to show the button
            val intent = Intent(this, OverlayService::class.java)
            startService(intent)

            // For now, we don't block or disallow the call.
            // In a real app, you might check a number against a database here.
            val response = CallResponse.Builder().build()
            respondToCall(callDetails, response)
        }
    }
}