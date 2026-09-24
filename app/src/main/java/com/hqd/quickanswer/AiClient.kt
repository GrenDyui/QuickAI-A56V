package com.hqd.quickanswer

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URI

object AiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/"

    const val SYSTEM_PROMPT = """
You are Quick AI, a fast and careful academic answer extractor.
The user will send one or more questions, often multiple-choice A/B/C/D or a True/False passage.
Read the full input first. Determine the question boundaries and options, then solve each item.
Return ONLY the final answers in a compact format:
- Multiple choice: C1: B
- Multiple questions: C1: B | C2: D | C3: A
- True/False: C1: a Đúng; b Sai; c Đúng; d Sai
If the input has no clear question numbering, preserve the order as C1, C2, C3...
Do not invent missing options. If an item is genuinely ambiguous, append "(không chắc)" to that item.
Do not provide chain-of-thought or long explanations. A one-line note is allowed only when the input is malformed.
""".trimIndent()

    class RetryableException(message: String) : IOException(message)
    class PermanentException(message: String) : IOException(message)

    fun generateAnswer(apiKey: String, model: String, userText: String): String {
        require(apiKey.isNotBlank()) { "Chưa có Gemini API key." }
        require(userText.isNotBlank()) { "Nội dung câu hỏi đang trống." }

        val safeModel = model.trim().ifBlank { SecurePrefs.DEFAULT_MODEL }
        val encodedModel = URLEncoder.encode(safeModel, Charsets.UTF_8.name())
        val url = URI.create("$BASE_URL$encodedModel:generateContent?key=${URLEncoder.encode(apiKey, Charsets.UTF_8.name())}").toURL()
        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", org.json.JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT))))
            .put("contents", org.json.JSONArray().put(
                JSONObject()
                    .put("role", "user")
                    .put("parts", org.json.JSONArray().put(JSONObject().put("text", userText.trim())))
            ))
            .put("generationConfig", JSONObject()
                .put("temperature", 0.1)
                .put("maxOutputTokens", 1024)
            )
            .toString()

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 25_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
        }

        try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (status == 429 || status >= 500) {
                throw RetryableException("Gemini HTTP $status")
            }
            if (status !in 200..299) {
                val message = runCatching {
                    JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty()
                }.getOrDefault("")
                throw PermanentException(
                    if (message.isNotBlank()) message else "Gemini HTTP $status"
                )
            }

            val json = JSONObject(raw)
            val candidates = json.optJSONArray("candidates")
                ?: throw PermanentException("Gemini không trả về candidate.")
            if (candidates.length() == 0) throw PermanentException("Gemini không trả về đáp án.")

            val parts = candidates.getJSONObject(0)
                .optJSONObject("content")
                ?.optJSONArray("parts")
                ?: throw PermanentException("Gemini trả về dữ liệu không hợp lệ.")

            buildString {
                for (i in 0 until parts.length()) {
                    val t = parts.optJSONObject(i)?.optString("text").orEmpty()
                    if (t.isNotBlank()) append(t).append('\n')
                }
            }.trim().ifBlank { throw PermanentException("Gemini trả về câu trả lời trống.") }
        } catch (e: java.net.SocketTimeoutException) {
            throw RetryableException("Kết nối Gemini quá thời gian.")
        } catch (e: java.net.UnknownHostException) {
            throw RetryableException("Không có Internet hoặc DNS chưa sẵn sàng.")
        } finally {
            connection.disconnect()
        }
    }
}
