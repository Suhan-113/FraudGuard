package com.example.aifraudguard

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val permissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (!allGranted) {
                Toast.makeText(this, "Some permissions were not granted.", Toast.LENGTH_LONG).show()
            }
        }

    private val settingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            Toast.makeText(this, "Please check if the setting was enabled.", Toast.LENGTH_SHORT).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestRequiredPermissions()

        val btnEnableCallerId = findViewById<Button>(R.id.btn_enable_caller_id)
        btnEnableCallerId.setOnClickListener {
            requestRole()
        }

        val btnEnableOverlay = findViewById<Button>(R.id.btn_enable_overlay)
        btnEnableOverlay.setOnClickListener {
            requestOverlayPermission()
        }

        // Inside MainActivity.kt, for example in onCreate()

        val btnEnableAccessibility = findViewById<Button>(R.id.btn_enable_accessibility) // Make sure you have this button in your layout
        btnEnableAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Please find 'AI Fraud Guard' and enable the service.", Toast.LENGTH_LONG).show()
        }
        // Inside your MainActivity.kt's onCreate() method
        val btnEnableInCall = findViewById<Button>(R.id.btn_enable_incall_service)
        btnEnableInCall.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = getSystemService(RoleManager::class.java)
                // Request the role of a Call Redirection app, which allows InCallService
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_REDIRECTION)
                settingsLauncher.launch(intent)
            } else {
                Toast.makeText(this, "Feature requires Android 10 or higher", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestRequiredPermissions() {
        val requiredPermissions = arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.RECORD_AUDIO
        )

        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            permissionsLauncher.launch(permissionsToRequest)
        }
    }

    private fun requestRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                settingsLauncher.launch(intent)
            }
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            settingsLauncher.launch(intent)
        }
    }
}