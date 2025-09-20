package com.example.aifraudguard

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class ScamDetectionAccessibilityService : AccessibilityService() {

    private val TAG = "ScamDetectionService"
    private val client = OkHttpClient()
    private var lastProcessedText: String? = null

    // --- We define our threshold for messaging scams here ---
    private val FRAUD_THRESHOLD = 0.40

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val rootNode: AccessibilityNodeInfo = rootInActiveWindow ?: return
            val stringBuilder = StringBuilder()
            extractTextFromNode(rootNode, stringBuilder)

            val fullText = stringBuilder.toString()

            if (fullText.isNotBlank() && fullText != lastProcessedText) {
                lastProcessedText = fullText
                Log.d(TAG, "Screen Content Changed: $fullText")
                analyzeText(fullText)
            }

            rootNode.recycle()
        }
    }

    private fun analyzeText(text: String) {
        // --- ⚠️ CRITICAL ⚠️ ---
        // MAKE SURE THIS NGROK URL IS CORRECT AND CURRENT!
        val ngrokHost = "42da9636895c.ngrok-free.app" // e.g., "0451bcdd597e.ngrok-free.app"

        val serverUrl = "https://$ngrokHost/predict"

        val json = JSONObject()
        json.put("text", text)
        val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(serverUrl)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to connect to backend server.", e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        try {
                            val scoreJson = JSONObject(responseBody)
                            val score = scoreJson.getDouble("score")
                            Log.d(TAG, "SUCCESS! Received score from server: $score")

                            // --- FINAL LOGIC ADDED HERE ---
                            // 1. Check if the score is above our threshold
                            if (score > FRAUD_THRESHOLD) {
                                Log.d(TAG, "FRAUD DETECTED! Score ($score) is above threshold ($FRAUD_THRESHOLD).")

                                // 2. Create an Intent to start our OverlayService
                                val overlayIntent = Intent(applicationContext, OverlayService::class.java).apply {
                                    // 3. Pass the reason for the alert to the overlay
                                    putExtra("fraud_alert_reason", "Suspicious message detected!")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                // 4. Start the service to show the alert
                                startService(overlayIntent)
                            }
                            // --- END OF FINAL LOGIC ---

                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to parse JSON from server response.", e)
                        }
                    }
                } else {
                    Log.e(TAG, "Server returned an error: ${response.code} ${response.message}")
                }
            }
        })
    }

    private fun extractTextFromNode(node: AccessibilityNodeInfo?, builder: StringBuilder) {
        if (node == null) return
        if (node.text != null && node.text.isNotEmpty()) {
            builder.append(node.text).append("\n")
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            extractTextFromNode(child, builder)
            child?.recycle()
        }
    }

    override fun onInterrupt() {
        Log.e(TAG, "onInterrupt: service was interrupted.")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "onServiceConnected: Accessibility Service has been connected.")
    }
}