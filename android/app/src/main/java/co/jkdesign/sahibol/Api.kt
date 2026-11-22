package co.jkdesign.sahibol

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Api {
    data class Result(val fixed: String, val reasons: List<String>)

    /** Runs on a background thread. Throws on any error. */
    fun fix(text: String, tone: String): Result {
        val url = URL(BuildConfig.SERVER_URL.trimEnd('/') + "/fix")
        val c = url.openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 15_000
            c.readTimeout = 40_000 // free servers can be slow to wake up
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("x-app-key", BuildConfig.APP_KEY)
            val body = JSONObject().put("text", text).put("tone", tone).toString()
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val ok = c.responseCode in 200..299
            val stream = if (ok) c.inputStream else c.errorStream
            val reply = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (!ok) throw IllegalStateException("Server ${c.responseCode}: $reply")

            val j = JSONObject(reply)
            val arr = j.optJSONArray("reasons") ?: JSONArray()
            val reasons = (0 until arr.length()).map {
                val r = arr.getJSONObject(it)
                "${r.optString("from")} → ${r.optString("to")}: ${r.optString("why")}"
            }
            return Result(j.getString("fixed"), reasons)
        } finally {
            c.disconnect()
        }
    }
}
