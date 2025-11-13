package com.example.aifraudguard

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.aifraudguard.databinding.ActivityLoginBinding
import com.google.android.material.snackbar.Snackbar

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViews()
    }

    private fun setupViews() {
        // Set up text watcher for phone number input
        binding.phoneNumberEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Clear any previous status message when user types
                binding.statusText.text = ""
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // Set up login button click listener
        binding.loginButton.setOnClickListener {
            val name = binding.nameEditText.text.toString().trim()
            val phoneNumber = binding.phoneNumberEditText.text.toString().trim()

            when {
                name.isEmpty() -> {
                    showError("Please enter your name")
                    binding.nameInputLayout.requestFocus()
                }
                phoneNumber.length != 10 -> {
                    showError("Please enter a valid 10-digit phone number")
                    binding.phoneInputLayout.requestFocus()
                }
                else -> {
                    // Proceed with login
                    onLoginSuccess(name, phoneNumber)
                }
            }
        }
    }

    private fun onLoginSuccess(name: String, phoneNumber: String) {
        // Show success message with the user's name
        binding.statusText.text = "Welcome, $name!"
        binding.statusText.setTextColor(getColor(android.R.color.holo_green_dark))

        // Disable the login button to prevent multiple clicks
        binding.loginButton.isEnabled = false

        // Show a toast message
        Toast.makeText(this, "Welcome, $name!", Toast.LENGTH_SHORT).show()

        // Navigate to MainActivity with user data
        binding.root.postDelayed({
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("USER_NAME", name)
                putExtra("USER_PHONE", phoneNumber)
            }
            startActivity(intent)
            finish()
        }, 1000) // 1 second delay
    }

    private fun showError(message: String) {
        binding.statusText.text = message
        binding.statusText.setTextColor(getColor(android.R.color.holo_red_dark))

        // Show a snackbar for better visibility
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }
}
