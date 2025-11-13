package com.example.aifraudguard

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.util.Log
import androidx.annotation.RequiresApi


@RequiresApi(Build.VERSION_CODES.M)
class FraudGuardInCallService : InCallService() {

    companion object {
        // Now we manage a list of calls, not just one
        private val calls = mutableListOf<Call>()
        private var instance: FraudGuardInCallService? = null

        // This function will be called by the OverlayService to start the process
        fun startConferenceFlow() {
            instance?.initiateProtection()
        }

        // This function hangs up the primary call
        fun hangUpCall() {
            // Disconnect the first call in the list, which should be the main one
            calls.firstOrNull()?.disconnect()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    // When a new call is added (either incoming or outgoing), add it to our list
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        calls.add(call)

        // When the first call is added, start the overlay service
        if (calls.size == 1) {
            val intent = Intent(this, OverlayService::class.java)
            startService(intent)
        }
    }

    // When a call is removed, remove it from our list
    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        calls.remove(call)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    // This is the core logic for the automatic merge
    private fun initiateProtection() {
        // 1. Find the original call (there should only be one at this point)
        if (calls.size != 1) return
        val originalCall = calls.first()

        // 2. Place the second call to your Twilio number
        val telecomManager = getSystemService(TelecomManager::class.java)
        val botUri = Uri.parse("tel:+12136934461") // Your Twilio number

        // This command places the call. The onCallAdded function will be triggered again
        // when this new call is created by the system.
        telecomManager.placeCall(botUri, null)

        // 3. Set up a listener to wait for the new call to become active
        registerCallbackForNewestCall()
    }

    private fun registerCallbackForNewestCall() {
        // Wait for the second call to be added to our list
        if (calls.size < 2) {
            // If the call hasn't been added yet, wait a moment and try again
            android.os.Handler(mainLooper).postDelayed({ registerCallbackForNewestCall() }, 500)
            return
        }

        val newCall = calls.last()
        newCall.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                super.onStateChanged(call, state)

                // 4. Wait until the new call to Twilio is 'Active'
                if (state == Call.STATE_ACTIVE) {
                    val originalCall = calls.firstOrNull()
                    if (originalCall != null && originalCall.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE)) {
                        // 5. THE MAGIC COMMAND: Automatically merge the two calls
                        originalCall.conference(call)
                    }
                    // We're done with this callback, so unregister it
                    call.unregisterCallback(this)
                }
            }
        })
    }
}