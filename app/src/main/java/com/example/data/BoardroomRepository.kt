package com.example.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class BoardroomRepository(
    private val context: Context,
    private val database: BoardroomDatabase
) {
    private val agentDao = database.agentDao()
    private val messageDao = database.messageDao()

    val agents: Flow<List<AgentEntity>> = agentDao.observeAgents()
    val messages: Flow<List<MessageEntity>> = messageDao.observeMessages()

    suspend fun ensureSeedData() {
        if (agentDao.countAgents() == 0) {
            agentDao.upsertAll(
                listOf(
                    AgentEntity(
                        id = "muse",
                        name = "Muse AI",
                        role = "Muse AI",
                        avatar = "M",
                        colorHex = "#3B82F6",
                        provider = AgentProvider.INAPP_BROWSER.id,
                        customUrl = "https://muse.ai",
                        sortOrder = 0
                    ),
                    AgentEntity(
                        id = "gemini",
                        name = "Gemini",
                        role = "Google Gemini",
                        avatar = "G",
                        colorHex = "#8B5CF6",
                        provider = AgentProvider.GEMINI.id,
                        customUrl = "https://gemini.google.com/app",
                        model = "gemini-3.8-flash",
                        sortOrder = 1
                    ),
                    AgentEntity(
                        id = "chatgpt",
                        name = "ChatGPT",
                        role = "OpenAI ChatGPT",
                        avatar = "C",
                        colorHex = "#10B981",
                        provider = AgentProvider.INAPP_BROWSER.id,
                        customUrl = "https://chatgpt.com",
                        model = "gpt-4o-mini",
                        sortOrder = 2
                    ),
                    AgentEntity(
                        id = "grok",
                        name = "Grok",
                        role = "xAI Grok",
                        avatar = "X",
                        colorHex = "#E2E8F0",
                        provider = AgentProvider.INAPP_BROWSER.id,
                        customUrl = "https://grok.com",
                        sortOrder = 3
                    )
                )
            )
        }

        if (messageDao.countMessages() == 0) {
            messageDao.insertMessage(
                MessageEntity(
                    id = "init-1",
                    sender = "system",
                    role = "System",
                    senderName = "System",
                    text = "Command Center ready with **Muse AI**, **Gemini** (Firebase AI), **ChatGPT**, and **Grok**. Tap the globe icon on any browser agent to sign in inside the app, or prompt **Gemini** directly right away.",
                    mode = "broadcast",
                    targetAgentId = "all",
                    timestampMs = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun getAllAgentsSnapshot(): List<AgentEntity> = agentDao.getAllAgents()

    suspend fun upsertAgent(agent: AgentEntity) = agentDao.upsertAgent(agent)

    suspend fun deleteAgent(id: String) = agentDao.deleteAgentById(id)

    suspend fun insertMessage(message: MessageEntity) = messageDao.insertMessage(message)

    suspend fun clearChatHistory() {
        messageDao.clearAll()
        messageDao.insertMessage(
            MessageEntity(
                id = "sys-${System.currentTimeMillis()}",
                sender = "system",
                role = "System",
                senderName = "System",
                text = "Chat history cleared. Ready for your next command.",
                mode = "broadcast",
                targetAgentId = "all",
                timestampMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun copyUriToAttachment(uri: Uri, fallbackMime: String? = null): PendingAttachment? =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri) ?: fallbackMime ?: "application/octet-stream"
            var displayName = "attachment"
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        displayName = cursor.getString(idx) ?: displayName
                    }
                }
            }
            val safeName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val attachDir = File(context.filesDir, "attachments").apply { mkdirs() }
            val destFile = File(attachDir, "${UUID.randomUUID().toString().take(8)}_$safeName")
            resolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null

            val sizeBytes = destFile.length()
            if (sizeBytes > 18L * 1024L * 1024L) {
                destFile.delete()
                throw IllegalArgumentException("Attachment exceeds the 18 MB limit.")
            }

            PendingAttachment(
                filename = displayName,
                mimeType = mimeType,
                localFilePath = destFile.absolutePath,
                sizeBytes = sizeBytes
            )
        }
}
