package com.example.aifraudguard

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telecom.TelecomManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.aifraudguard.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    // Using View Binding for safe and easy access to your layout's views
    private lateinit var binding: ActivityMainBinding
    private var userName: String = ""
    private var userPhone: String = ""

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
        // Inflate the layout using View Binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get user data from LoginActivity's intent
        userName = intent.getStringExtra("USER_NAME") ?: "User"
        userPhone = intent.getStringExtra("USER_PHONE") ?: ""

        // Set the welcome text and make the profile icon clickable
        binding.welcomeText.text = "Welcome, $userName"
        binding.profileIcon.setOnClickListener {
            showProfileDialog()
        }

        // Set up all button clicks
        setupButtons()

        // Request necessary permissions when the activity starts
        requestRequiredPermissions()
    }

    private fun setupButtons() {
        // Button 1: For CallScreeningService (Caller ID)
        binding.btnEnableCallerId.setOnClickListener {
            requestScreeningRole()
        }

        // Button 2: For OverlayService
        binding.btnEnableOverlay.setOnClickListener {
            requestOverlayPermission()
        }

        // Button 3: For Call Management (InCallService)
        binding.btnEnableIncallService.setOnClickListener {
            requestDialerRole()
        }

        // Button 4: For navigating to the "More" screen with safety tips
        binding.btnMore.setOnClickListener {
            val intent = Intent(this, MoreActivity::class.java)
            startActivity(intent)
        }
    }

    private fun requestRequiredPermissions() {
        val requiredPermissions = arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS
        )

        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            permissionsLauncher.launch(permissionsToRequest)
        }
    }

    private fun requestScreeningRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                settingsLauncher.launch(intent)
            }
        }
    }

    private fun requestDialerRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                Toast.makeText(this, "App is already the default phone app.", Toast.LENGTH_SHORT).show()
                return
            }
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
            settingsLauncher.launch(intent)
        } else {
            val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
            intent.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
            startActivity(intent)
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            settingsLauncher.launch(intent)
        }
    }

    private fun showProfileDialog() {
        // Inflate the custom dialog layout
        val dialogView = layoutInflater.inflate(R.layout.dialog_profile, null)

        val tvProfileName = dialogView.findViewById<TextView>(R.id.tvProfileName)
        val tvProfilePhone = dialogView.findViewById<TextView>(R.id.tvProfilePhone)

        // Set user data in the dialog's text views
        tvProfileName.text = userName
        tvProfilePhone.text = if (userPhone.isNotEmpty()) "+91 $userPhone" else "Not Provided"

        // Create and show the alert dialog
        MaterialAlertDialogBuilder(this)
            .setTitle("User Profile")
            .setView(dialogView)
            .setPositiveButton("Close") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}