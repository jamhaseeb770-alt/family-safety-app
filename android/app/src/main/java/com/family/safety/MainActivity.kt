package com.family.safety

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import android.Manifest
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.LinearLayout
import android.view.Gravity
import android.view.ViewGroup

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    private val SERVER_BASE_URL = "https://freehbsolution.free.nf/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("family_safety", Context.MODE_PRIVATE)

        val token = prefs.getString("device_token", null)
        if (token != null) {
            showMonitoringScreen()
        } else {
            showPairingScreen()
        }
    }

    private fun showPairingScreen() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 160, 60, 60)
            gravity = Gravity.CENTER
        }

        val title = TextView(this).apply {
            text = "Family Safety"
            textSize = 24f
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "Enter the pairing code shown on your parent's dashboard."
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 40)
        }
        val codeInput = EditText(this).apply {
            hint = "6-digit pairing code"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val pairButton = Button(this).apply {
            text = "Pair This Device"
            setOnClickListener {
                val code = codeInput.text.toString().trim()
                if (code.length < 4) {
                    Toast.makeText(this@MainActivity, "Enter a valid pairing code", Toast.LENGTH_SHORT).show()
                } else {
                    ApiClient.pairDevice(SERVER_BASE_URL, code) { ok, token, error ->
                        runOnUiThread {
                            if (ok && token != null) {
                                prefs.edit().putString("device_token", token)
                                    .putString("server_base_url", SERVER_BASE_URL).apply()
                                Toast.makeText(this@MainActivity, "Paired successfully", Toast.LENGTH_SHORT).show()
                                requestPermissionsThenStart()
                            } else {
                                Toast.makeText(this@MainActivity, error ?: "Pairing failed", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }

        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(codeInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        layout.addView(pairButton)
        setContentView(layout)
    }

    private fun showMonitoringScreen() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 160, 60, 60)
            gravity = Gravity.CENTER
        }
        val status = TextView(this).apply {
            text = "Family Safety is active.\nThis device is being monitored by a parent/guardian."
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 60)
        }
        val sosButton = Button(this).apply {
            text = "SOS - Get Help Now"
            setOnClickListener { triggerSos() }
        }
        layout.addView(status)
        layout.addView(sosButton)
        setContentView(layout)

        startMonitorService()
    }

    private fun requestPermissionsThenStart() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            100
        )
        Toast.makeText(
            this,
            "Please also enable Usage Access for Family Safety in the next screen.",
            Toast.LENGTH_LONG
        ).show()
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        showMonitoringScreen()
    }

    private fun startMonitorService() {
        val intent = Intent(this, MonitorService::class.java)
        startForegroundService(intent)
    }

    private fun triggerSos() {
        val token = prefs.getString("device_token", null) ?: return
        val base = prefs.getString("server_base_url", SERVER_BASE_URL) ?: SERVER_BASE_URL
        ApiClient.sendSos(base, token) { ok, error ->
            runOnUiThread {
                Toast.makeText(
                    this,
                    if (ok) "SOS sent to parent" else (error ?: "Failed to send SOS"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
