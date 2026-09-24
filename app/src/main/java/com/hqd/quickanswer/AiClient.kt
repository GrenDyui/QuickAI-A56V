package com.hqd.quickanswer

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.URI
import kotlin.random.Random

object AiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/"

    // Resilience settings.
    // - Per model: retry transient errors (503/429/timeout) with exponential backoff.
    // - Across models: if the chosen model stays unavailable, fall back to known-stable,
    //   less-loaded models so a "503 High Demand" on one endpoint doesn't kill the request.
    // Google's guidance: retry on 429/5xx, exponential backoff, add jitter, set a max.
    private const val MAX_ATTEMPTS = 3            // attempts per model
    private const val MAX_MODELS_TRIED = 3        // primary + up to 2 fallbacks
    private val BACKOFF_MS = longArrayOf(1_000, 2_000)

    // Stable, available-to-new-projects models (per ai.google.dev/gemini-api/docs/models).
    val FALLBACK_MODELS = listOf("gemini-3.5-flash", "gemini-3.6-flash")

    val SYSTEM_PROMPT = """
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

    /**
     * Calls Gemini. Retries transient errors (503 / 429 / timeout) per model with backoff,
     * and on persistent transient failure falls back to other stable models.
     * A bad API key / bad request (PermanentException) propagates immediately — no fallback.
     */
    fun generateAnswer(apiKey: String, model: String, userText: String): String {
        require(apiKey.isNotBlank()) { "Chưa có Gemini API key." }
        require(userText.isNotBlank()) { "Nội dung câu hỏi đang trống." }

        val requested = model.trim().ifBlank { SecurePrefs.DEFAULT_MODEL }
        val candidates = (listOf(requested, SecurePrefs.DEFAULT_MODEL) + FALLBACK_MODELS)
            .distinct()
            .take(MAX_MODELS_TRIED)

        var lastError: IOException? = null
        for (m in candidates) {
            try {
                return runWithRetry(apiKey, m, userText)
            } catch (e: PermanentException) {
                throw e // bad key / bad request — fallback won't help
            } catch (e: RetryableException) {
                lastError = e // transient — try next model
            }
        }
        throw lastError ?: RetryableException("Không gọi được Gemini sau khi thử ${candidates.size} model.")
    }

    private fun runWithRetry(apiKey: String, model: String, userText: String): String {
        var lastError: RetryableException? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            try {
                return doRequest(apiKey, model, userText)
            } catch (e: RetryableException) {
                lastError = e
                if (attempt < MAX_ATTEMPTS) {
                    val base = BACKOFF_MS.getOrElse(attempt - 1) { BACKOFF_MS.last() }
                    Thread.sleep(base + Random.nextLong(0, 400)) // jitter
                }
            }
        }
        throw lastError ?: RetryableException("Gemini $model không phản hồi.")
    }

    private fun doRequest(apiKey: String, model: String, userText: String): String {
        val encodedModel = URLEncoder.encode(model, Charsets.UTF_8.name())
        val url = URI.create(
            "$BASE_URL$encodedModel:generateContent?key=${URLEncoder.encode(apiKey, Charsets.UTF_8.name())}"
        ).toURL()
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

        return try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (status == 429 || status >= 500) {
                val detail = parseErrorMessage(raw)
                throw RetryableException(if (detail.isNotBlank()) "Gemini HTTP $status — $detail" else "Gemini HTTP $status")
            }
            if (status !in 200..299) {
                val message = parseErrorMessage(raw)
                throw PermanentException(if (message.isNotBlank()) message else "Gemini HTTP $status")
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
        } catch (e: SocketTimeoutException) {
            throw RetryableException("Kết nối Gemini quá thời gian.")
        } catch (e: java.net.UnknownHostException) {
            throw RetryableException("Không có Internet hoặc DNS chưa sẵn sàng.")
        } finally {
            connection.disconnect()
        }
    }

    private fun parseErrorMessage(raw: String): String =
        runCatching {
            JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty()
        }.getOrDefault("")
}
