package com.ejemplo.iot

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.ejemplo.iot.databinding.ActivityPrivacyConsentBinding

class PrivacyConsentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrivacyConsentBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("privacy_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("consent_given", false)) {
            goToMain()
            return
        }

        binding = ActivityPrivacyConsentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonReadPrivacy.setOnClickListener {
            startActivity(Intent(this, PrivacyDetailActivity::class.java))
        }

        binding.checkboxConsent.setOnCheckedChangeListener { _, isChecked ->
            binding.buttonAccept.isEnabled = isChecked
        }

        binding.buttonAccept.isEnabled = false

        binding.buttonAccept.setOnClickListener {
            if (binding.checkboxConsent.isChecked) {
                prefs.edit {
                    putBoolean("consent_given", true)
                    putLong("consent_date", System.currentTimeMillis())
                    putString("consent_version", "1.0")
                }
                goToMain()
            }
        }

        binding.buttonReject.setOnClickListener {
            Toast.makeText(
                this,
                "Sin consentimiento no es posible usar la app.",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}