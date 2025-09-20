package com.example.aifraudguard

import android.os.Build
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.M)
class FraudGuardInCallService : InCallService() {

    companion object {
        // This list will hold the active calls
        private val calls = mutableListOf<Call>()

        fun hangUpCall() {
            calls.find { it.state == Call.STATE_ACTIVE }?.disconnect()
            Log.d("InCallService", "Hang up command sent.")
        }
    }

    // This map will store the callbacks for each call to prevent memory leaks
    private val callCallbackMap = mutableMapOf<Call, Call.Callback>()

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        calls.add(call)
        Log.d("InCallService", "Call added. Total calls: ${calls.size}. State: ${call.state}")

        // 1. Create a new listener (callback) for this specific call
        val callback = object : Call.Callback() {
            // This function is called whenever the call's state changes
            override fun onStateChanged(call: Call, state: Int) {
                super.onStateChanged(call, state)
                Log.d("InCallService", "State changed for a call. New state: $state")

                // 2. Check if the new state is ACTIVE. This means the call is connected.
                if (state == Call.STATE_ACTIVE) {
                    // 3. Now that this call is active, we can try to merge.
                    tryMergeCalls()
                }
            }
        }

        // 4. Register the listener and store it
        call.registerCallback(callback)
        callCallbackMap[call] = callback
    }

    private fun tryMergeCalls() {
        // We only merge if there are exactly two calls
        if (calls.size == 2) {
            val call1 = calls[0]
            val call2 = calls[1]

            // We need to identify which call is active and which should be held.
            // The one that just became active should initiate the merge.
            val activeCall = if (call1.state == Call.STATE_ACTIVE) call1 else call2
            val callToHold = if (activeCall == call1) call2 else call1

            // 5. Put the OTHER call on hold
            if (callToHold.state != Call.STATE_HOLDING && callToHold.details.can(Call.Details.CAPABILITY_HOLD)) {
                callToHold.hold()
                Log.d("InCallService", "Putting the other call on hold.")
            }

            // 6. Now, perform the merge on the active call
            if (activeCall.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE)) {
                activeCall.mergeConference()
                Log.d("InCallService", "Merge command sent.")
            }
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        calls.remove(call)

        // 7. CRITICAL: Unregister the listener to prevent memory leaks
        callCallbackMap[call]?.let {
            call.unregisterCallback(it)
            callCallbackMap.remove(call)
        }
        Log.d("InCallService", "Call removed and callback unregistered. Total calls: ${calls.size}")
    }
}