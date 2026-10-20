package com.family.safety

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

object ApiClient {

    private val executor = Executors.newCachedThreadPool()

    private fun post(urlStr: String, params: Map<String, String>, callback: (Int, String?) -> Unit) {
        executor.execute {
            try {
                val url = URL(urlStr)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 15000
                conn.readTimeout = 15000

                val body = params.entries.joinToString("&") {
                    URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
                }
                OutputStreamWriter(conn.outputStream).use { it.write(body) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val response = BufferedReader(InputStreamReader(stream)).readText()
                callback(code, response)
            } catch (e: Exception) {
                callback(-1, null)
            }
        }
    }

    fun pairDevice(baseUrl: String, code: String, callback: (Boolean, String?, String?) -> Unit) {
        post(baseUrl + "api/pair.php", mapOf("pairing_code" to code)) { _, response ->
            try {
                val json = JSONObject(response ?: "{}")
                if (json.optBoolean("ok", false)) {
                    callback(true, json.optString("device_token"), null)
                } else {
                    callback(false, null, json.optString("error", "Pairing failed"))
                }
            } catch (e: Exception) {
                callback(false, null, "Could not reach server. Check the server URL.")
            }
        }
    }

    fun sendLocation(baseUrl: String, token: String, lat: Double, lng: Double, accuracy: Float) {
        post(
            baseUrl + "api/ingest.php",
            mapOf(
                "device_token" to token,
                "type" to "location",
                "lat" to lat.toString(),
                "lng" to lng.toString(),
                "accuracy" to accuracy.toString()
            )
        ) { _, _ -> }
    }

    fun sendUsage(baseUrl: String, token: String, appName: String, packageName: String, minutes: Int, date: String) {
        post(
            baseUrl + "api/ingest.php",
            mapOf(
                "device_token" to token,
                "type" to "usage",
                "app_name" to appName,
                "package_name" to packageName,
                "usage_minutes" to minutes.toString(),
                "usage_date" to date
            )
        ) { _, _ -> }
    }

    fun sendSos(baseUrl: String, token: String, callback: (Boolean, String?) -> Unit) {
        post(
            baseUrl + "api/ingest.php",
            mapOf("device_token" to token, "type" to "sos", "message" to "SOS triggered by child")
        ) { _, response ->
            try {
                val json = JSONObject(response ?: "{}")
                callback(json.optBoolean("ok", false), json.optString("error", null))
            } catch (e: Exception) {
                callback(false, "Could not reach server.")
            }
        }
    }
}
