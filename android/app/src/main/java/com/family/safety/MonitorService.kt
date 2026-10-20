package com.family.safety

import android.app.*
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationServices
import java.text.SimpleDateFormat
import java.util.*

class MonitorService : Service() {

    private lateinit var fusedClient: com.google.android.gms.location.FusedLocationProviderClient
    private val handler = Handler(Looper.getMainLooper())
    private val UPDATE_INTERVAL = 15 * 60 * 1000L

    private val tickRunnable = object : Runnable {
        override fun run() {
            uploadLocation()
            uploadUsageStats()
            handler.postDelayed(this, UPDATE_INTERVAL)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        startForeground(1, buildNotification())
        handler.post(tickRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(tickRunnable)
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val channelId = "family_safety_monitor"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Family Safety Monitoring", NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Family Safety is active")
            .setContentText("This device is being monitored by a parent/guardian.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    private fun getPrefs() = getSharedPreferences("family_safety", Context.MODE_PRIVATE)

    private fun uploadLocation() {
        val prefs = getPrefs()
        val token = prefs.getString("device_token", null) ?: return
        val base = prefs.getString("server_base_url", null) ?: return

        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        fusedClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                ApiClient.sendLocation(base, token, loc.latitude, loc.longitude, loc.accuracy)
            }
        }
    }

    private fun uploadUsageStats() {
        val prefs = getPrefs()
        val token = prefs.getString("device_token", null) ?: return
        val base = prefs.getString("server_base_url", null) ?: return

        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        val startOfDay = cal.timeInMillis
        val now = System.currentTimeMillis()

        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, now)
        val pm = packageManager
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        stats?.forEach { stat ->
            val minutes = (stat.totalTimeInForeground / 60000).toInt()
            if (minutes > 0) {
                val appName = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(stat.packageName, 0)).toString()
                } catch (e: Exception) {
                    stat.packageName
                }
                ApiClient.sendUsage(base, token, appName, stat.packageName, minutes, today)
            }
        }
    }
}
