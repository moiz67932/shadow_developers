package com.example.nasconsports
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
class ApiError(val code: String, message: String): Exception(message)
class ApiClient(var base: String, var token: String = "") {
    fun call(path: String, body: JSONObject? = null, method: String = if (body == null) "GET" else "POST"): JSONObject {
        val c = URL(base.trimEnd('/') + path).openConnection() as HttpURLConnection
        c.connectTimeout = 5000; c.readTimeout = 10000; c.setRequestProperty("Accept", "application/json")
        if (token.isNotEmpty()) c.setRequestProperty("Authorization", "Bearer $token")
        try {
            c.requestMethod = method
            if (body != null) { c.doOutput = true; c.setRequestProperty("Content-Type", "application/json"); c.outputStream.use { it.write(body.toString().toByteArray()) } }
            val status = c.responseCode
            val text = (if(status in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: "{}"
            val result = JSONObject(text)
            if (status !in 200..299) { val e = result.optJSONObject("error") ?: result; throw ApiError(e.optString("code", "REQUEST_FAILED"), e.optString("message", "Request failed ($status)")) }
            return result
        } finally { c.disconnect() }
    }
}
