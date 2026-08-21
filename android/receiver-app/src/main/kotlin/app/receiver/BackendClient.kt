package app.receiver

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object BackendClient {
    private val baseUrl get() = BuildConfig.BACKEND_BASE_URL
    private fun usable() = baseUrl.startsWith("https://") && !baseUrl.contains("replace-with-your-backend")
    fun isConfigured() = usable()
    fun endpointLabel() = if (usable()) baseUrl else "Backend URL not configured"
    fun get(path: String): JSONObject {
        check(usable()) { "A reachable HTTPS backend URL has not been configured." }
        val connection = URL("$baseUrl$path").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"; connection.connectTimeout = 15_000; connection.readTimeout = 15_000
            val source = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val response = JSONObject(source.bufferedReader().use { it.readText() })
            if (connection.responseCode !in 200..299 || !response.optBoolean("success", false)) throw IOException(response.optString("error", "Backend request failed"))
            response
        } finally { connection.disconnect() }
    }
    fun post(path: String, body: JSONObject): JSONObject {
        check(usable()) { "Set BACKEND_BASE_URL to the deployed HTTPS backend URL before live testing." }
        val connection = URL("$baseUrl$path").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"; connection.connectTimeout = 15_000; connection.readTimeout = 15_000; connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            val source = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val response = JSONObject(source.bufferedReader().use { it.readText() })
            if (connection.responseCode !in 200..299 || !response.optBoolean("success", false)) throw IOException(response.optString("error", "Backend request failed"))
            response
        } finally { connection.disconnect() }
    }
}
