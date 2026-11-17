package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AgentProvider(val id: String, val label: String) {
    INAPP_BROWSER("inapp-browser", "In-App Browser + Connector"),
    GEMINI("gemini", "Google Gemini (Firebase AI)"),
    OPENAI("openai", "OpenAI / Groq / Custom API"),
    DOCKER_WEB("docker-web", "Webhook / Docker URL"),
    SIMULATION("simulation", "Offline Demo");

    companion object {
        fun fromId(raw: String): AgentProvider {
            return when (raw) {
                "inapp-browser" -> INAPP_BROWSER
                "gemini" -> GEMINI
                "openai", "custom-api" -> OPENAI
                "docker-web" -> DOCKER_WEB
                else -> SIMULATION
            }
        }
    }
}

@Entity(tableName = "agents")
data class AgentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val role: String,
    val avatar: String,
    val colorHex: String,
    val provider: String,
    val customUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val endpoint: String = "",
    val systemPrompt: String = "",
    val sortOrder: Int = 0
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val sender: String,
    val role: String,
    val senderName: String,
    val avatar: String = "",
    val colorHex: String = "#3B82F6",
    val text: String,
    val attachmentName: String? = null,
    val attachmentMimeType: String? = null,
    val attachmentPath: String? = null,
    val mode: String = "broadcast",
    val targetAgentId: String = "all",
    val timestampMs: Long = System.currentTimeMillis()
)

data class PendingAttachment(
    val filename: String,
    val mimeType: String,
    val localFilePath: String,
    val sizeBytes: Long
) {
    val isImage: Boolean
        get() = mimeType.startsWith("image/", ignoreCase = true)

    val isAudio: Boolean
        get() = mimeType.startsWith("audio/", ignoreCase = true) ||
            filename.lowercase().let {
                it.endsWith(".mp3") || it.endsWith(".wav") || it.endsWith(".m4a") ||
                    it.endsWith(".ogg") || it.endsWith(".webm") || it.endsWith(".flac") ||
                    it.endsWith(".aac")
            }
}

data class InAppBrowserStatus(
    val agentId: String,
    val open: Boolean = false,
    val url: String = "",
    val title: String = "",
    val ready: Boolean = false,
    val needsLogin: Boolean = false,
    val hasSavedSession: Boolean = false,
    val isLoading: Boolean = false
)

data class AiPreset(
    val label: String,
    val url: String,
    val role: String,
    val avatar: String,
    val colorHex: String
)

val DEFAULT_AI_PRESETS = listOf(
    AiPreset("Muse AI", "https://muse.ai", "Muse AI", "M", "#3B82F6"),
    AiPreset("Gemini", "https://gemini.google.com/app", "Google Gemini", "G", "#8B5CF6"),
    AiPreset("ChatGPT", "https://chatgpt.com", "OpenAI ChatGPT", "C", "#10B981"),
    AiPreset("Grok", "https://grok.com", "xAI Grok", "X", "#E2E8F0"),
    AiPreset("Claude", "https://claude.ai", "Anthropic Claude", "A", "#F59E0B")
)
