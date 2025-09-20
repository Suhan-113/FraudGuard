package com.example.aifraudguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.telecom.TelecomManager
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import okhttp3.*
import org.json.JSONObject
import java.util.*

class OverlayService : Service(), TextToSpeech.OnInitListener {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private var webSocket: WebSocket? = null

    // UI Elements
    private lateinit var statusText: TextView
    private lateinit var actionButton: Button
    private lateinit var rootLayout: LinearLayout

    // Logic for Repeating Instructions & Spoken Alerts
    private var tts: TextToSpeech? = null
    private val instructionHandler = Handler(Looper.getMainLooper())
    private var isWaitingForMerge = false

    private val alertHandler = Handler(Looper.getMainLooper())
    private var isAlerting = false

    companion object {
        private const val CHANNEL_ID = "FraudGuardChannel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())

        val reason = intent?.getStringExtra("fraud_alert_reason")
        if (reason != null) {
            if (::overlayView.isInitialized) {
                updateOverlayForAlert(reason)
            }
        }
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(WindowManager::class.java)
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null)
        tts = TextToSpeech(this, this)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
        }

        statusText = overlayView.findViewById(R.id.tv_status)
        actionButton = overlayView.findViewById(R.id.btn_action)
        rootLayout = overlayView.findViewById(R.id.overlay_root)

        // Set the initial state of the button for the call feature
        actionButton.text = "Protect this call"
        actionButton.setOnClickListener {
            actionButton.text = "Connecting..."
            actionButton.isEnabled = false
            connectWebSocketWithCallback {
                // Now that we're connected, place the call to the bot and guide the user
                Handler(Looper.getMainLooper()).post {
                    addAssistantBot()
                    showWaitingForMergeState()
                }
            }
        }

        windowManager.addView(overlayView, params)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
        } else {
            Log.e("TTS", "TextToSpeech initialization failed.")
        }
    }

    private fun showWaitingForMergeState() {
        isWaitingForMerge = true
        statusText.text = "Tap 'Merge Calls' on your screen!"
        actionButton.visibility = View.GONE

        // Start the repeating voice and visual prompt
        instructionRunnable.run()
    }

    private val instructionRunnable = object : Runnable {
        override fun run() {
            if (!isWaitingForMerge) return

            val anim = AlphaAnimation(0.2f, 1.0f).apply {
                duration = 700
                repeatMode = Animation.REVERSE
                repeatCount = 1
            }
            statusText.startAnimation(anim)

            tts?.speak("Please tap merge calls", TextToSpeech.QUEUE_FLUSH, null, "")
            instructionHandler.postDelayed(this, 3500)
        }
    }

    private fun onCallMerged() {
        isWaitingForMerge = false
        instructionHandler.removeCallbacks(instructionRunnable)

        statusText.text = "✅ Call is Protected"
        actionButton.visibility = View.GONE
        rootLayout.setBackgroundColor(Color.parseColor("#4CAF50"))
    }

    private fun updateOverlayForAlert(reason: String) {
        isWaitingForMerge = false
        instructionHandler.removeCallbacks(instructionRunnable)

        statusText.text = "⚠ High Risk: $reason"
        rootLayout.setBackgroundColor(Color.parseColor("#D32F2F"))

        isAlerting = true
        alertRunnable.run()

        actionButton.text = "Hang Up"
        actionButton.isEnabled = true
        actionButton.visibility = View.VISIBLE
        actionButton.setOnClickListener {
            FraudGuardInCallService.hangUpCall()
            stopSelf()
        }
    }

    private val alertRunnable = object : Runnable {
        override fun run() {
            if (!isAlerting) return
            val alertMessage = "Fraud detected. Please hang up."
            tts?.speak(alertMessage, TextToSpeech.QUEUE_FLUSH, null, "fraud_alert")
            alertHandler.postDelayed(this, 4000)
        }
    }

    private fun createWebSocketListener(onOpenCallback: () -> Unit) = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(applicationContext, "Connected to Guard Server!", Toast.LENGTH_SHORT).show()
            }
            webSocket.send("{\"type\": \"app_connect\"}")
            onOpenCallback()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val json = JSONObject(text)
                when (json.getString("type")) {
                    "fraud_alert" -> {
                        val reason = json.getString("reason")
                        Handler(Looper.getMainLooper()).post { updateOverlayForAlert(reason) }
                    }
                    "merge_successful" -> {
                        Handler(Looper.getMainLooper()).post { onCallMerged() }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(applicationContext, "Connection Failed: ${t.message}", Toast.LENGTH_LONG).show()
                actionButton.text = "Protect this call"
                actionButton.isEnabled = true
                actionButton.visibility = View.VISIBLE
            }
            t.printStackTrace()
        }
    }

    private fun connectWebSocketWithCallback(onOpenCallback: () -> Unit) {
        val ngrokHost = "42da9636895c.ngrok-free.app" // ⚠ UPDATE THIS URL
        val wssUrl = "wss://$ngrokHost"
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(wssUrl)
            .addHeader("ngrok-skip-browser-warning", "true")
            .build()
        this.webSocket = client.newWebSocket(request, createWebSocketListener(onOpenCallback))
    }

    private fun addAssistantBot() {
        try {
            val telecomManager = getSystemService(TelecomManager::class.java)
            val botUri = Uri.parse("tel:+12136934461")
            telecomManager.placeCall(botUri, null)
        } catch (e: SecurityException) {
            Toast.makeText(this, "CALL_PHONE permission not granted.", Toast.LENGTH_SHORT).show()
            stopSelf()
        }
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID, "AI Fraud Guard Active",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(serviceChannel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AI Guard is active")
            .setContentText("Protecting your communications.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isWaitingForMerge = false
        instructionHandler.removeCallbacks(instructionRunnable)
        isAlerting = false
        alertHandler.removeCallbacks(alertRunnable)

        tts?.stop()
        tts?.shutdown()

        webSocket?.close(1000, "Service destroyed")
        if (::overlayView.isInitialized && overlayView.isAttachedToWindow()) {
            windowManager.removeView(overlayView)
        }
    }
}