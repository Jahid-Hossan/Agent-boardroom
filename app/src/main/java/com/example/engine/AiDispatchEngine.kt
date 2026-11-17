package com.example.engine

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.example.data.AgentEntity
import com.example.data.PendingAttachment
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

private const val TAG = "AiDispatchEngine"

class AiDispatchEngine {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val transcriptionModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel(modelName = "gemini-3.8-flash")
    }

    private fun normalizeGeminiModel(rawModel: String): String {
        val m = rawModel.trim()
        return when {
            m.isBlank() -> "gemini-3.8-flash"
            m.equals("gemini-4-argon", ignoreCase = true) -> "gemini-4-argon"
            m.equals("gemini-3.5-flash-lite", ignoreCase = true) -> "gemini-3.5-flash-lite"
            m.startsWith("gemini-3.", ignoreCase = true) -> m
            else -> "gemini-3.8-flash"
        }
    }

    suspend fun callGeminiStream(
        agent: AgentEntity,
        promptText: String,
        attachment: PendingAttachment?,
        onPartial: suspend (String) -> Unit
    ): String {
        val modelName = normalizeGeminiModel(agent.model)
        val systemInstruction = agent.systemPrompt.trim().takeIf { it.isNotEmpty() }?.let { sys ->
            content { text(sys) }
        }
        val model = Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel(
                modelName = modelName,
                systemInstruction = systemInstruction
            )

        val requestContent = withContext(Dispatchers.IO) {
            content {
                text(promptText)
                if (attachment != null) {
                    val file = File(attachment.localFilePath)
                    if (file.exists()) {
                        when {
                            attachment.isImage -> {
                                val bmp = BitmapFactory.decodeFile(file.absolutePath)
                                if (bmp != null) {
                                    image(bmp)
                                } else {
                                    text("[Attached image: ${attachment.filename}]")
                                }
                            }
                            isTextAttachment(attachment) -> {
                                val textContent = file.readText(Charsets.UTF_8).take(16000)
                                text("[Attached file: ${attachment.filename}]\n$textContent")
                            }
                            else -> {
                                val bytes = file.readBytes()
                                if (bytes.size <= 18 * 1024 * 1024) {
                                    inlineData(bytes, attachment.mimeType.ifBlank { "application/octet-stream" })
                                } else {
                                    text("[Attached file too large for inline upload: ${attachment.filename}]")
                                }
                            }
                        }
                    }
                }
            }
        }

        val accumulated = StringBuilder()
        try {
            withTimeout(60_000L) {
                model.generateContentStream(requestContent).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        onPartial(accumulated.toString())
                    }
                }
            }
            val finalText = accumulated.toString().trim()
            return if (finalText.isNotEmpty()) finalText else "No response received from Gemini."
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini stream timed out", e)
            throw IllegalStateException("Gemini timed out after 60 seconds. Please try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini generation failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Gemini request failed.")
        }
    }

    suspend fun transcribeAudio(audioBytes: ByteArray, mimeType: String): String {
        if (audioBytes.size < 256) {
            throw IllegalArgumentException("Recording was too short. Please speak a bit longer.")
        }
        return try {
            withTimeout(60_000L) {
                val prompt = content {
                    inlineData(audioBytes, mimeType.ifBlank { "audio/mp4" })
                    text(
                        "Transcribe the spoken audio accurately into clean prompt text ready to send to an AI assistant. " +
                            "Preserve @mentions if spoken. Return only the transcribed text with no surrounding quotes or commentary."
                    )
                }
                val response = transcriptionModel.generateContent(prompt)
                val transcript = response.text?.trim().orEmpty()
                if (transcript.isEmpty()) {
                    throw IllegalStateException("No speech detected in the recording. Please speak clearly and try again.")
                }
                transcript
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Audio transcription timed out", e)
            throw IllegalStateException("Voice transcription timed out. Please try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Audio transcription failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Voice transcription failed.")
        }
    }

    suspend fun callOpenAiCompatible(
        agent: AgentEntity,
        promptText: String,
        attachment: PendingAttachment?
    ): String = withContext(Dispatchers.IO) {
        val endpoint = agent.endpoint.trim().ifEmpty { "https://api.openai.com/v1/chat/completions" }
        val modelName = agent.model.trim().ifEmpty { "gpt-4o-mini" }
        val apiKey = agent.apiKey.trim()

        if (apiKey.isEmpty() && endpoint.contains("openai.com", ignoreCase = true)) {
            throw IllegalStateException("Missing API Key for ${agent.name}. Add your key in Manage Agents.")
        }

        val userContent: Any = if (attachment != null && File(attachment.localFilePath).exists()) {
            val file = File(attachment.localFilePath)
            when {
                attachment.isImage -> {
                    val b64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                    val dataUrl = "data:${attachment.mimeType};base64,$b64"
                    JSONArray().apply {
                        put(JSONObject().put("type", "text").put("text", promptText))
                        put(
                            JSONObject()
                                .put("type", "image_url")
                                .put("image_url", JSONObject().put("url", dataUrl))
                        )
                    }
                }
                isTextAttachment(attachment) -> {
                    val contentText = file.readText(Charsets.UTF_8).take(16000)
                    "$promptText\n\n[Attached file: ${attachment.filename}]\n$contentText"
                }
                else -> "$promptText\n\n[Attached file: ${attachment.filename} (${attachment.mimeType})]"
            }
        } else {
            promptText
        }

        val messagesArray = JSONArray().apply {
            put(
                JSONObject()
                    .put("role", "system")
                    .put("content", agent.systemPrompt.ifBlank { "You are a helpful AI assistant." })
            )
            put(
                JSONObject()
                    .put("role", "user")
                    .put("content", userContent)
            )
        }

        val bodyJson = JSONObject()
            .put("model", modelName)
            .put("messages", messagesArray)
            .put("max_tokens", 1200)

        val reqBuilder = Request.Builder()
            .url(endpoint)
            .post(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Content-Type", "application/json")

        if (apiKey.isNotEmpty()) {
            reqBuilder.header("Authorization", "Bearer $apiKey")
        }

        httpClient.newCall(reqBuilder.build()).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("API error (${resp.code}): ${raw.take(180)}")
            }
            val parsed = JSONObject(raw)
            val reply = parsed.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.trim()
            reply?.takeIf { it.isNotEmpty() } ?: "No response received."
        }
    }

    suspend fun callDockerWebhook(
        agent: AgentEntity,
        promptText: String
    ): String = withContext(Dispatchers.IO) {
        val baseUrl = normalizeHttpUrl(agent.customUrl).trimEnd('/')
        if (baseUrl.isEmpty()) {
            return@withContext "**${agent.name} is not configured yet.**\n\nOpen **Manage Agents** and enter a public `https://` endpoint URL for this agent."
        }

        val attempts = listOf(
            "$baseUrl/v1/chat/completions" to JSONObject().apply {
                put("model", agent.model.ifBlank { "default" })
                put("messages", JSONArray().apply {
                    if (agent.systemPrompt.isNotBlank()) {
                        put(JSONObject().put("role", "system").put("content", agent.systemPrompt))
                    }
                    put(JSONObject().put("role", "user").put("content", promptText))
                })
            },
            "$baseUrl/api/chat" to JSONObject().put("message", promptText).put("prompt", promptText).put("input", promptText),
            "$baseUrl/chat" to JSONObject().put("message", promptText).put("prompt", promptText).put("input", promptText),
            baseUrl to JSONObject().put("message", promptText).put("prompt", promptText).put("input", promptText)
        )

        var lastError = "No chat API endpoint responded"
        for ((url, payload) in attempts) {
            try {
                val reqBuilder = Request.Builder()
                    .url(url)
                    .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .header("Content-Type", "application/json")
                if (agent.apiKey.isNotBlank()) {
                    reqBuilder.header("Authorization", "Bearer ${agent.apiKey.trim()}")
                }
                httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        val json = JSONObject(raw)
                        val text = json.optString("reply").ifBlank {
                            json.optString("response").ifBlank {
                                json.optString("message").ifBlank {
                                    json.optJSONArray("choices")
                                        ?.optJSONObject(0)
                                        ?.optJSONObject("message")
                                        ?.optString("content")
                                        .orEmpty()
                                }
                            }
                        }
                        if (text.isNotBlank()) return@withContext text
                    } else {
                        lastError = "HTTP ${resp.code} from $url"
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e.localizedMessage ?: "Request failed"
            }
        }

        "Could not reach **${agent.name}** at `$baseUrl` ($lastError). Ensure the URL is publicly accessible and exposes `/v1/chat/completions` or `/api/chat`."
    }

    suspend fun testWebhookUrl(rawUrl: String): Result<Int> = withContext(Dispatchers.IO) {
        val url = normalizeHttpUrl(rawUrl)
        if (url.isBlank()) return@withContext Result.failure(IllegalArgumentException("Enter a URL first."))
        try {
            val req = Request.Builder().url(url).get().build()
            httpClient.newCall(req).execute().use { resp ->
                Result.success(resp.code)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun generateDemoResponse(agent: AgentEntity, promptText: String): String {
        val clean = promptText.replace(Regex("^@[\\w.-]+\\s*"), "").trim()
        val roleStr = if (agent.role.isNotBlank()) " (${agent.role})" else ""
        if (Regex("^(hello|hi|hey|greetings|good\\s*(morning|afternoon|evening)|hola|salam)\\b", RegexOption.IGNORE_CASE).containsMatchIn(clean)) {
            return "Hello! **${agent.name}**$roleStr is running in **Offline Demo** mode. Switch to **In-App Browser**, **Google Gemini**, or **OpenAI / Groq** in **Manage Agents** for live AI responses."
        }
        return "**[Offline Demo — ${agent.name}$roleStr]** Received: \"$clean\".\n\nSwitch this agent to **In-App Browser**, **Google Gemini (Firebase AI)**, or **OpenAI / Groq API** in **Manage Agents** to get real AI output."
    }

    fun appendAttachmentTextFallback(promptText: String, attachment: PendingAttachment?): String {
        if (attachment == null) return promptText
        val file = File(attachment.localFilePath)
        if (isTextAttachment(attachment) && file.exists()) {
            val contentText = runCatching { file.readText(Charsets.UTF_8).take(12000) }.getOrDefault("")
            if (contentText.isNotEmpty()) {
                return "$promptText\n\n[Attached file: ${attachment.filename}]\n$contentText"
            }
        }
        return "$promptText\n\n[Attached file: ${attachment.filename} (${attachment.mimeType})]"
    }

    private fun isTextAttachment(attachment: PendingAttachment): Boolean {
        val mime = attachment.mimeType.lowercase()
        val name = attachment.filename.lowercase()
        return mime.startsWith("text/") ||
            mime == "application/json" ||
            name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".csv") ||
            name.endsWith(".json") || name.endsWith(".js") || name.endsWith(".ts") ||
            name.endsWith(".py") || name.endsWith(".html") || name.endsWith(".css") ||
            name.endsWith(".kt") || name.endsWith(".log")
    }
}

fun normalizeHttpUrl(raw: String): String {
    val s = raw.trim()
    if (s.isEmpty()) return ""
    return if (s.startsWith("http://", ignoreCase = true) || s.startsWith("https://", ignoreCase = true)) {
        s
    } else {
        "https://$s"
    }
}

fun extractHostname(rawUrl: String): String {
    return try {
        val normalized = normalizeHttpUrl(rawUrl)
        if (normalized.isEmpty()) return ""
        java.net.URI(normalized).host?.removePrefix("www.")?.lowercase().orEmpty()
    } catch (_: Exception) {
        ""
    }
}
