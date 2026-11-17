package com.example.ui

import android.app.Application
import android.net.Uri
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AgentEntity
import com.example.data.AgentProvider
import com.example.data.AiPreset
import com.example.data.BoardroomDatabase
import com.example.data.BoardroomRepository
import com.example.data.InAppBrowserStatus
import com.example.data.MessageEntity
import com.example.data.PendingAttachment
import com.example.engine.AiDispatchEngine
import com.example.engine.InAppWebViewManager
import com.example.engine.VoiceRecorderManager
import com.example.engine.normalizeHttpUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class ChatViewMode {
    BOARDROOM,
    DIRECT
}

data class BoardroomUiState(
    val viewMode: ChatViewMode = ChatViewMode.BOARDROOM,
    val directAgentId: String? = null,
    val composerText: String = "",
    val pendingAttachment: PendingAttachment? = null,
    val isSending: Boolean = false,
    val pendingAgentNames: List<String> = emptyList(),
    val streamingReplies: Map<String, String> = emptyMap(),
    val isRecordingVoice: Boolean = false,
    val voiceSeconds: Int = 0,
    val isTranscribingVoice: Boolean = false,
    val showBrowserModal: Boolean = false,
    val activeBrowserAgentId: String? = null,
    val showManageAgentsModal: Boolean = false,
    val editingAgentId: String? = null,
    val webhookTestStatus: String? = null,
    val toastMessage: String? = null,
    val toastIsError: Boolean = false
)

class BoardroomViewModel(application: Application) : AndroidViewModel(application) {

    private val database = BoardroomDatabase.getInstance(application)
    private val repository = BoardroomRepository(application, database)
    private val aiEngine = AiDispatchEngine()
    val webViewManager = InAppWebViewManager(application)
    private val voiceRecorder = VoiceRecorderManager(application)

    val agents: StateFlow<List<AgentEntity>> = repository.agents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val messages: StateFlow<List<MessageEntity>> = repository.messages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val browserStatuses: StateFlow<Map<String, InAppBrowserStatus>> = webViewManager.statuses

    private val _uiState = MutableStateFlow(BoardroomUiState())
    val uiState: StateFlow<BoardroomUiState> = _uiState.asStateFlow()

    private var voiceTimerJob: Job? = null
    private var toastJob: Job? = null

    init {
        viewModelScope.launch {
            repository.ensureSeedData()
        }
    }

    fun showToast(message: String, isError: Boolean = false) {
        toastJob?.cancel()
        _uiState.update { it.copy(toastMessage = message, toastIsError = isError) }
        toastJob = viewModelScope.launch {
            delay(3500)
            _uiState.update { it.copy(toastMessage = null) }
        }
    }

    fun dismissToast() {
        toastJob?.cancel()
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun switchViewMode(mode: ChatViewMode, agentId: String? = null) {
        if (mode == ChatViewMode.BOARDROOM) {
            _uiState.update { it.copy(viewMode = ChatViewMode.BOARDROOM, directAgentId = null) }
        } else {
            val targetId = agentId ?: agents.value.firstOrNull()?.id
            _uiState.update { it.copy(viewMode = ChatViewMode.DIRECT, directAgentId = targetId) }
        }
    }

    fun updateComposerText(text: String) {
        _uiState.update { it.copy(composerText = text) }
    }

    fun insertMention(tag: String) {
        val current = _uiState.value.composerText.trim()
        val updated = when {
            current.isEmpty() -> "$tag "
            current.contains(tag, ignoreCase = true) -> _uiState.value.composerText
            else -> "$tag $current"
        }
        _uiState.update { it.copy(composerText = updated) }
    }

    fun attachFromUri(uri: Uri, fallbackMime: String? = null) {
        viewModelScope.launch {
            try {
                val attachment = repository.copyUriToAttachment(uri, fallbackMime)
                if (attachment != null) {
                    _uiState.update { it.copy(pendingAttachment = attachment) }
                } else {
                    showToast("Could not read the selected file.", isError = true)
                }
            } catch (e: Exception) {
                showToast(e.localizedMessage ?: "Failed to attach file.", isError = true)
            }
        }
    }

    fun clearAttachment() {
        _uiState.update { it.copy(pendingAttachment = null) }
    }

    fun transcribeAudioAttachment() {
        val attachment = _uiState.value.pendingAttachment ?: return
        if (_uiState.value.isTranscribingVoice) return
        viewModelScope.launch {
            _uiState.update { it.copy(isTranscribingVoice = true) }
            try {
                val bytes = withContext(Dispatchers.IO) {
                    File(attachment.localFilePath).readBytes()
                }
                val transcript = aiEngine.transcribeAudio(bytes, attachment.mimeType)
                appendTranscriptToComposer(transcript)
                _uiState.update { it.copy(pendingAttachment = null) }
                showToast("Prompt generated from audio.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showToast(e.localizedMessage ?: "Could not transcribe audio attachment.", isError = true)
            } finally {
                _uiState.update { it.copy(isTranscribingVoice = false) }
            }
        }
    }

    fun startVoiceRecording() {
        if (_uiState.value.isRecordingVoice || _uiState.value.isTranscribingVoice) return
        try {
            voiceRecorder.startRecording()
            _uiState.update { it.copy(isRecordingVoice = true, voiceSeconds = 0) }
            voiceTimerJob?.cancel()
            voiceTimerJob = viewModelScope.launch {
                while (true) {
                    delay(1000)
                    _uiState.update { it.copy(voiceSeconds = it.voiceSeconds + 1) }
                }
            }
        } catch (e: Exception) {
            voiceRecorder.stopAndDiscard()
            _uiState.update { it.copy(isRecordingVoice = false, voiceSeconds = 0) }
            showToast(e.localizedMessage ?: "Could not start microphone.", isError = true)
        }
    }

    fun stopVoiceRecordingAndTranscribe() {
        if (!_uiState.value.isRecordingVoice) return
        voiceTimerJob?.cancel()
        val bytes = voiceRecorder.stopAndReadBytes()
        _uiState.update { it.copy(isRecordingVoice = false, voiceSeconds = 0) }

        if (bytes == null || bytes.size < 256) {
            showToast("Recording was too short. Speak a bit longer.", isError = true)
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isTranscribingVoice = true) }
            try {
                val transcript = aiEngine.transcribeAudio(bytes, "audio/mp4")
                appendTranscriptToComposer(transcript)
                showToast("Prompt generated from voice.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showToast(e.localizedMessage ?: "Voice transcription failed.", isError = true)
            } finally {
                _uiState.update { it.copy(isTranscribingVoice = false) }
            }
        }
    }

    fun cancelVoiceRecording() {
        voiceTimerJob?.cancel()
        voiceRecorder.stopAndDiscard()
        _uiState.update { it.copy(isRecordingVoice = false, voiceSeconds = 0) }
    }

    private fun appendTranscriptToComposer(transcript: String) {
        val clean = transcript.trim()
        if (clean.isEmpty()) return
        val existing = _uiState.value.composerText.trim()
        val next = if (existing.isEmpty()) clean else "$existing $clean"
        _uiState.update { it.copy(composerText = next) }
    }

    fun sendCommand() {
        val state = _uiState.value
        if (state.isSending) return
        val rawText = state.composerText.trim()
        val attachment = state.pendingAttachment
        if (rawText.isEmpty() && attachment == null) return

        val userText = rawText.ifEmpty { "Analyze the attached file." }
        val mode = if (state.viewMode == ChatViewMode.DIRECT) "direct" else "broadcast"
        val targetAgentId = if (state.viewMode == ChatViewMode.DIRECT && state.directAgentId != null) {
            state.directAgentId
        } else {
            "all"
        }

        _uiState.update {
            it.copy(
                composerText = "",
                pendingAttachment = null,
                isSending = true,
                streamingReplies = emptyMap()
            )
        }

        viewModelScope.launch {
            try {
                val allAgents = repository.getAllAgentsSnapshot()
                val userMsg = MessageEntity(
                    id = "ceo-${System.currentTimeMillis()}",
                    sender = "ceo",
                    role = "User",
                    senderName = "You",
                    text = userText,
                    attachmentName = attachment?.filename,
                    attachmentMimeType = attachment?.mimeType,
                    attachmentPath = attachment?.localFilePath,
                    mode = mode,
                    targetAgentId = targetAgentId,
                    timestampMs = System.currentTimeMillis()
                )
                repository.insertMessage(userMsg)

                val targets = resolveTargetAgents(allAgents, userText, targetAgentId)
                if (targets.isEmpty()) {
                    repository.insertMessage(
                        MessageEntity(
                            id = "sys-${System.currentTimeMillis()}",
                            sender = "system",
                            role = "System",
                            senderName = "System",
                            text = "No agents configured yet. Tap + Add in Connected Agents to add one.",
                            mode = mode,
                            targetAgentId = targetAgentId,
                            timestampMs = System.currentTimeMillis()
                        )
                    )
                    return@launch
                }

                _uiState.update { it.copy(pendingAgentNames = targets.map { a -> a.name }) }

                coroutineScope {
                    targets.map { agent ->
                        async {
                            val replyText = try {
                                executeAgentPrompt(agent, userText, attachment)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                "**${agent.name} error:** ${e.localizedMessage ?: "Request failed."}"
                            }

                            _uiState.update { current ->
                                current.copy(
                                    pendingAgentNames = current.pendingAgentNames.filterNot { it == agent.name },
                                    streamingReplies = current.streamingReplies - agent.id
                                )
                            }

                            repository.insertMessage(
                                MessageEntity(
                                    id = "reply-${agent.id}-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(4)}",
                                    sender = agent.id,
                                    role = agent.role.ifBlank { agent.provider },
                                    senderName = agent.name,
                                    avatar = agent.avatar.ifBlank { agent.name.take(1).uppercase() },
                                    colorHex = agent.colorHex.ifBlank { "#3B82F6" },
                                    text = replyText,
                                    mode = mode,
                                    targetAgentId = agent.id,
                                    timestampMs = System.currentTimeMillis()
                                )
                            )
                        }
                    }.awaitAll()
                }
            } finally {
                _uiState.update {
                    it.copy(
                        isSending = false,
                        pendingAgentNames = emptyList(),
                        streamingReplies = emptyMap()
                    )
                }
            }
        }
    }

    private fun resolveTargetAgents(
        allAgents: List<AgentEntity>,
        command: String,
        targetAgentId: String
    ): List<AgentEntity> {
        if (targetAgentId != "all") {
            return allAgents.filter { it.id == targetAgentId }
        }
        if (Regex("@all\\b", RegexOption.IGNORE_CASE).containsMatchIn(command)) {
            return allAgents
        }
        val mentioned = allAgents.filter { agent ->
            val escaped = Regex.escape(agent.name)
            Regex("@$escaped\\b", RegexOption.IGNORE_CASE).containsMatchIn(command)
        }
        return mentioned.ifEmpty { allAgents }
    }

    private suspend fun executeAgentPrompt(
        agent: AgentEntity,
        promptText: String,
        attachment: PendingAttachment?
    ): String {
        return when (AgentProvider.fromId(agent.provider)) {
            AgentProvider.GEMINI -> aiEngine.callGeminiStream(agent, promptText, attachment) { partial ->
                _uiState.update { state ->
                    state.copy(streamingReplies = state.streamingReplies + (agent.id to partial))
                }
            }
            AgentProvider.OPENAI -> aiEngine.callOpenAiCompatible(agent, promptText, attachment)
            AgentProvider.DOCKER_WEB -> aiEngine.callDockerWebhook(
                agent,
                aiEngine.appendAttachmentTextFallback(promptText, attachment)
            )
            AgentProvider.INAPP_BROWSER -> webViewManager.sendPrompt(
                agent,
                aiEngine.appendAttachmentTextFallback(promptText, attachment)
            )
            AgentProvider.SIMULATION -> aiEngine.generateDemoResponse(
                agent,
                aiEngine.appendAttachmentTextFallback(promptText, attachment)
            )
        }
    }

    // In-App Browser Controls
    fun openInAppBrowser(agentId: String? = null) {
        viewModelScope.launch {
            val all = repository.getAllAgentsSnapshot()
            var target = agentId?.let { id -> all.find { it.id == id } }
                ?: all.firstOrNull { it.provider == AgentProvider.INAPP_BROWSER.id }
                ?: all.firstOrNull()

            if (target == null) {
                val created = AgentEntity(
                    id = "muse-${System.currentTimeMillis()}",
                    name = "Muse AI",
                    role = "Muse AI",
                    avatar = "M",
                    colorHex = "#3B82F6",
                    provider = AgentProvider.INAPP_BROWSER.id,
                    customUrl = "https://muse.ai",
                    sortOrder = 0
                )
                repository.upsertAgent(created)
                target = created
            }

            _uiState.update {
                it.copy(showBrowserModal = true, activeBrowserAgentId = target.id)
            }
            webViewManager.ensureWebView(target)
        }
    }

    fun closeInAppBrowser() {
        _uiState.update { it.copy(showBrowserModal = false) }
        val activeId = _uiState.value.activeBrowserAgentId
        if (activeId != null) {
            viewModelScope.launch { webViewManager.refreshStatus(activeId) }
        }
    }

    fun switchBrowserAgent(agentId: String) {
        val agent = agents.value.find { it.id == agentId } ?: return
        _uiState.update { it.copy(activeBrowserAgentId = agentId) }
        viewModelScope.launch {
            webViewManager.ensureWebView(agent)
        }
    }

    fun createNewInAppAccount() {
        viewModelScope.launch {
            val currentAgents = repository.getAllAgentsSnapshot()
            val base = currentAgents.find { it.id == _uiState.value.activeBrowserAgentId }
                ?: currentAgents.firstOrNull()
            val baseName = base?.name?.replace(Regex("\\s+\\d+$"), "") ?: "Muse AI"
            val newName = uniqueAgentName(baseName, currentAgents)
            val created = AgentEntity(
                id = "agent-${System.currentTimeMillis()}",
                name = newName,
                role = base?.role ?: "In-App Browser AI",
                avatar = base?.avatar ?: newName.take(1).uppercase(),
                colorHex = base?.colorHex ?: "#3B82F6",
                provider = AgentProvider.INAPP_BROWSER.id,
                customUrl = base?.customUrl?.ifBlank { "https://muse.ai" } ?: "https://muse.ai",
                sortOrder = (currentAgents.maxOfOrNull { it.sortOrder } ?: 0) + 1
            )
            repository.upsertAgent(created)
            _uiState.update { it.copy(activeBrowserAgentId = created.id) }
            webViewManager.ensureWebView(created)
            showToast("Created \"$newName\".")
        }
    }

    suspend fun getOrCreateWebView(agentId: String): WebView? {
        val agent = agents.value.find { it.id == agentId } ?: return null
        return webViewManager.ensureWebView(agent)
    }

    fun navigateBrowser(url: String) {
        val agentId = _uiState.value.activeBrowserAgentId ?: return
        val agent = agents.value.find { it.id == agentId } ?: return
        val normalized = normalizeHttpUrl(url)
        if (normalized.isBlank()) return
        viewModelScope.launch {
            repository.upsertAgent(agent.copy(customUrl = normalized))
            webViewManager.navigate(agent.copy(customUrl = normalized), normalized)
        }
    }

    fun browserBack() {
        val agentId = _uiState.value.activeBrowserAgentId ?: return
        viewModelScope.launch { webViewManager.goBack(agentId) }
    }

    fun browserReload() {
        val agentId = _uiState.value.activeBrowserAgentId ?: return
        viewModelScope.launch { webViewManager.reload(agentId) }
    }

    fun refreshBrowserStatus() {
        val agentId = _uiState.value.activeBrowserAgentId ?: return
        viewModelScope.launch {
            val st = webViewManager.refreshStatus(agentId)
            if (st?.ready == true) {
                showToast("Session connected and ready for commands!")
            } else {
                showToast("Sign in inside the browser viewport, then check again.", isError = true)
            }
        }
    }

    fun closeBrowserTab() {
        val agentId = _uiState.value.activeBrowserAgentId ?: return
        viewModelScope.launch {
            webViewManager.closeSession(agentId)
            showToast("Browser tab closed. Cookies remain saved.")
        }
    }

    fun sendBrowserQuickText(text: String) {
        val agentId = _uiState.value.activeBrowserAgentId ?: return
        if (text.isEmpty()) return
        viewModelScope.launch {
            webViewManager.injectQuickText(agentId, text)
        }
    }

    fun pressBrowserEnter() {
        val agentId = _uiState.value.activeBrowserAgentId ?: return
        viewModelScope.launch {
            webViewManager.pressEnter(agentId)
        }
    }

    // Manage Agents Modal
    fun openManageAgents(agentId: String? = null) {
        val targetId = agentId ?: agents.value.firstOrNull()?.id
        _uiState.update {
            it.copy(
                showManageAgentsModal = true,
                editingAgentId = targetId,
                webhookTestStatus = null
            )
        }
    }

    fun closeManageAgents() {
        _uiState.update {
            it.copy(showManageAgentsModal = false, webhookTestStatus = null)
        }
    }

    fun selectEditingAgent(agentId: String) {
        _uiState.update { it.copy(editingAgentId = agentId, webhookTestStatus = null) }
    }

    fun addNewAgent(preset: AiPreset? = null) {
        viewModelScope.launch {
            val current = repository.getAllAgentsSnapshot()
            val baseName = preset?.label ?: "Agent-${current.size + 1}"
            val name = uniqueAgentName(baseName, current)
            val created = AgentEntity(
                id = "agent-${System.currentTimeMillis()}",
                name = name,
                role = preset?.role ?: "In-App Browser AI",
                avatar = preset?.avatar ?: name.take(1).uppercase(),
                colorHex = preset?.colorHex ?: "#3B82F6",
                provider = AgentProvider.INAPP_BROWSER.id,
                customUrl = preset?.url ?: "https://muse.ai",
                model = "gemini-3.8-flash",
                sortOrder = (current.maxOfOrNull { it.sortOrder } ?: 0) + 1
            )
            repository.upsertAgent(created)
            _uiState.update {
                it.copy(
                    showManageAgentsModal = true,
                    editingAgentId = created.id,
                    webhookTestStatus = null
                )
            }
        }
    }

    fun duplicateAgent(agent: AgentEntity) {
        viewModelScope.launch {
            val current = repository.getAllAgentsSnapshot()
            val baseName = agent.name.replace(Regex("\\s+\\d+$"), "")
            val newName = uniqueAgentName(baseName, current)
            val clone = agent.copy(
                id = "agent-${System.currentTimeMillis()}",
                name = newName,
                sortOrder = (current.maxOfOrNull { it.sortOrder } ?: 0) + 1
            )
            repository.upsertAgent(clone)
            _uiState.update { it.copy(editingAgentId = clone.id) }
            showToast("Created \"$newName\".")
        }
    }

    fun saveAgent(updated: AgentEntity) {
        if (updated.name.isBlank()) {
            showToast("Agent name is required.", isError = true)
            return
        }
        viewModelScope.launch {
            repository.upsertAgent(updated)
            _uiState.update { it.copy(showManageAgentsModal = false, webhookTestStatus = null) }
            showToast("Saved \"${updated.name}\".")
        }
    }

    fun deleteAgent(agentId: String) {
        viewModelScope.launch {
            val existing = agents.value.find { it.id == agentId }
            webViewManager.closeSession(agentId)
            repository.deleteAgent(agentId)
            val remaining = repository.getAllAgentsSnapshot()
            _uiState.update { state ->
                val nextEditing = if (state.editingAgentId == agentId) remaining.firstOrNull()?.id else state.editingAgentId
                val nextMode = if (state.directAgentId == agentId) ChatViewMode.BOARDROOM else state.viewMode
                val nextDirect = if (state.directAgentId == agentId) null else state.directAgentId
                state.copy(
                    editingAgentId = nextEditing,
                    viewMode = nextMode,
                    directAgentId = nextDirect
                )
            }
            showToast("Removed \"${existing?.name ?: "Agent"}\".")
        }
    }

    fun testWebhook(url: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(webhookTestStatus = "Testing...") }
            val result = aiEngine.testWebhookUrl(url)
            val msg = result.fold(
                onSuccess = { code -> "Reachable (HTTP $code)" },
                onFailure = { err -> "Unreachable (${err.localizedMessage ?: "error"})" }
            )
            _uiState.update { it.copy(webhookTestStatus = msg) }
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            repository.clearChatHistory()
            showToast("Chat cleared.")
        }
    }

    private fun uniqueAgentName(baseName: String, existing: List<AgentEntity>): String {
        val names = existing.map { it.name }.toSet()
        if (!names.contains(baseName)) return baseName
        var idx = 2
        while (names.contains("$baseName $idx")) idx++
        return "$baseName $idx"
    }

    override fun onCleared() {
        super.onCleared()
        voiceRecorder.stopAndDiscard()
        webViewManager.destroyAll()
    }
}
