package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.AgentEntity
import com.example.data.AgentProvider
import com.example.ui.components.AgentRosterSidebar
import com.example.ui.components.ComposerBar
import com.example.ui.components.ContextBannerBar
import com.example.ui.components.InAppBrowserDialog
import com.example.ui.components.ManageAgentsDialog
import com.example.ui.components.MessageCardItem
import com.example.ui.components.OnboardingHeroCard
import com.example.ui.components.StreamingReplyCard
import com.example.ui.components.ThinkingStatusCard
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentOnAmber
import com.example.ui.theme.BgCanvas
import com.example.ui.theme.BgElevated
import com.example.ui.theme.BgSurface
import com.example.ui.theme.BorderStrong
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.StatusErr
import com.example.ui.theme.StatusOk
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun BoardroomScreen(viewModel: BoardroomViewModel) {
    val agents by viewModel.agents.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val browserStatuses by viewModel.browserStatuses.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    var pendingConfirmRemoveAgent by remember { mutableStateOf<AgentEntity?>(null) }
    var showConfirmClearChat by remember { mutableStateOf(false) }

    // Zero-permission Photo Picker + SAF Document Picker (Play Policy compliant)
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.attachFromUri(uri, "image/*")
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.attachFromUri(uri)
        }
    }

    // Runtime RECORD_AUDIO permission launcher for Voice-to-Prompt
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceRecording()
        } else {
            viewModel.showToast("Microphone permission is needed for Voice-to-Prompt.", isError = true)
        }
    }

    val onToggleVoice = {
        if (uiState.isRecordingVoice) {
            viewModel.stopVoiceRecordingAndTranscribe()
        } else {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                viewModel.startVoiceRecording()
            } else {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // Handle system Back in Direct Chat view
    BackHandler(enabled = uiState.viewMode == ChatViewMode.DIRECT) {
        viewModel.switchViewMode(ChatViewMode.BOARDROOM)
    }

    val filteredMessages = remember(messages, uiState.viewMode, uiState.directAgentId) {
        if (uiState.viewMode == ChatViewMode.DIRECT && uiState.directAgentId != null) {
            messages.filter { msg ->
                if (msg.sender == "system") {
                    msg.mode == "direct" && msg.targetAgentId == uiState.directAgentId
                } else {
                    msg.targetAgentId == uiState.directAgentId || msg.sender == uiState.directAgentId
                }
            }
        } else {
            messages.filter { msg ->
                !(msg.mode == "direct" && msg.targetAgentId.isNotBlank() && msg.targetAgentId != "all")
            }
        }
    }

    val directAgent = agents.find { it.id == uiState.directAgentId }
    val readyBrowserCount = browserStatuses.values.count { it.open && it.ready }
    val openBrowserCount = browserStatuses.values.count { it.open }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 720.dp

        val sidebarContent: @Composable () -> Unit = {
            AgentRosterSidebar(
                agents = agents,
                browserStatuses = browserStatuses,
                viewMode = uiState.viewMode,
                directAgentId = uiState.directAgentId,
                onOpenBrowser = { agentId ->
                    scope.launch { drawerState.close() }
                    viewModel.openInAppBrowser(agentId)
                },
                onAddAgent = {
                    scope.launch { drawerState.close() }
                    viewModel.addNewAgent()
                },
                onSelectDirectChat = { agentId ->
                    scope.launch { drawerState.close() }
                    viewModel.switchViewMode(ChatViewMode.DIRECT, agentId)
                },
                onEditAgent = { agentId ->
                    scope.launch { drawerState.close() }
                    viewModel.openManageAgents(agentId)
                },
                onRequestRemoveAgent = { agent ->
                    pendingConfirmRemoveAgent = agent
                }
            )
        }

        val mainWorkspace: @Composable () -> Unit = {
            Scaffold(
                containerColor = BgCanvas,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    BoardroomTopBar(
                        agentCount = agents.size,
                        readyBrowserCount = readyBrowserCount,
                        openBrowserCount = openBrowserCount,
                        agents = agents,
                        viewMode = uiState.viewMode,
                        directAgentId = uiState.directAgentId,
                        showMenuButton = !isWideScreen,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onSelectBoardroom = { viewModel.switchViewMode(ChatViewMode.BOARDROOM) },
                        onSelectDirect = { agentId -> viewModel.switchViewMode(ChatViewMode.DIRECT, agentId) },
                        onOpenBrowser = { viewModel.openInAppBrowser(null) },
                        onOpenManageAgents = { viewModel.openManageAgents(null) },
                        onRequestClearChat = { showConfirmClearChat = true }
                    )
                },
                bottomBar = {
                    ComposerBar(
                        agents = agents,
                        viewMode = uiState.viewMode,
                        directAgent = directAgent,
                        composerText = uiState.composerText,
                        pendingAttachment = uiState.pendingAttachment,
                        isSending = uiState.isSending,
                        isRecordingVoice = uiState.isRecordingVoice,
                        voiceSeconds = uiState.voiceSeconds,
                        isTranscribingVoice = uiState.isTranscribingVoice,
                        onTextChange = viewModel::updateComposerText,
                        onInsertMention = viewModel::insertMention,
                        onPickImage = {
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onPickDocument = {
                            documentPickerLauncher.launch(arrayOf("*/*"))
                        },
                        onClearAttachment = viewModel::clearAttachment,
                        onTranscribeAttachment = viewModel::transcribeAudioAttachment,
                        onToggleVoice = onToggleVoice,
                        onStopVoice = viewModel::stopVoiceRecordingAndTranscribe,
                        onCancelVoice = viewModel::cancelVoiceRecording,
                        onSend = viewModel::sendCommand
                    )
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    ContextBannerBar(
                        viewMode = uiState.viewMode,
                        directAgent = directAgent,
                        onReturnToBoardroom = { viewModel.switchViewMode(ChatViewMode.BOARDROOM) }
                    )

                    val listState = rememberLazyListState()
                    val totalFeedItems = filteredMessages.size + uiState.streamingReplies.size +
                        if (uiState.isSending) 1 else 0

                    LaunchedEffect(totalFeedItems) {
                        if (totalFeedItems > 0) {
                            listState.animateScrollToItem((totalFeedItems - 1).coerceAtLeast(0))
                        }
                    }

                    val showOnboarding = uiState.viewMode == ChatViewMode.BOARDROOM &&
                        filteredMessages.size <= 2 &&
                        openBrowserCount == 0

                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("chat_feed")
                    ) {
                        if (showOnboarding) {
                            item(key = "onboarding_hero") {
                                OnboardingHeroCard(
                                    onOpenInAppBrowser = { viewModel.openInAppBrowser(null) },
                                    onOpenManageAgents = { viewModel.openManageAgents(null) }
                                )
                            }
                        }

                        if (filteredMessages.isEmpty() && uiState.viewMode == ChatViewMode.DIRECT) {
                            item(key = "direct_empty") {
                                Text(
                                    text = "No messages in this 1-on-1 thread yet. Send a prompt below to start.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(vertical = 32.dp)
                                )
                            }
                        }

                        items(filteredMessages, key = { it.id }) { msg ->
                            MessageCardItem(message = msg)
                        }

                        items(uiState.streamingReplies.entries.toList(), key = { "stream_${it.key}" }) { (agentId, partial) ->
                            val agent = agents.find { it.id == agentId }
                            StreamingReplyCard(agent = agent, partialText = partial)
                        }

                        if (uiState.isSending) {
                            item(key = "thinking_card") {
                                ThinkingStatusCard(pendingAgentNames = uiState.pendingAgentNames)
                            }
                        }
                    }
                }
            }
        }

        if (isWideScreen) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.width(310.dp).fillMaxHeight()) {
                    sidebarContent()
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(BorderSubtle)
                )
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    mainWorkspace()
                }
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = BgSurface,
                        modifier = Modifier.width(300.dp)
                    ) {
                        sidebarContent()
                    }
                }
            ) {
                mainWorkspace()
            }
        }

        // Toast Overlay
        AnimatedVisibility(
            visible = uiState.toastMessage != null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 64.dp, start = 16.dp, end = 16.dp)
        ) {
            uiState.toastMessage?.let { msg ->
                Surface(
                    onClick = viewModel::dismissToast,
                    color = BgElevated,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        1.dp,
                        if (uiState.toastIsError) StatusErr else StatusOk
                    ),
                    shadowElevation = 6.dp
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }

    // In-App Browser Workspace Modal
    if (uiState.showBrowserModal) {
        InAppBrowserDialog(
            agents = agents,
            activeAgentId = uiState.activeBrowserAgentId,
            browserStatuses = browserStatuses,
            getWebView = viewModel::getOrCreateWebView,
            onSwitchAgent = viewModel::switchBrowserAgent,
            onCreateNewAccount = viewModel::createNewInAppAccount,
            onNavigateUrl = viewModel::navigateBrowser,
            onBack = viewModel::browserBack,
            onReload = viewModel::browserReload,
            onCheckReady = viewModel::refreshBrowserStatus,
            onCloseTab = viewModel::closeBrowserTab,
            onSendQuickText = viewModel::sendBrowserQuickText,
            onPressEnter = viewModel::pressBrowserEnter,
            onDismiss = viewModel::closeInAppBrowser
        )
    }

    // Manage Agents Modal
    if (uiState.showManageAgentsModal) {
        ManageAgentsDialog(
            agents = agents,
            editingAgentId = uiState.editingAgentId,
            webhookTestStatus = uiState.webhookTestStatus,
            onSelectAgent = viewModel::selectEditingAgent,
            onAddAgent = { viewModel.addNewAgent() },
            onDuplicateAgent = viewModel::duplicateAgent,
            onOpenInAppBrowser = { updated ->
                viewModel.saveAgent(updated)
                viewModel.openInAppBrowser(updated.id)
            },
            onTestWebhook = viewModel::testWebhook,
            onSaveAgent = viewModel::saveAgent,
            onRequestDeleteAgent = { agent ->
                pendingConfirmRemoveAgent = agent
            },
            onDismiss = viewModel::closeManageAgents
        )
    }

    // Remove Agent Confirmation
    pendingConfirmRemoveAgent?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingConfirmRemoveAgent = null },
            containerColor = BgElevated,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text(stringResource(R.string.confirm_remove_agent_title)) },
            text = { Text("Remove \"${target.name}\" from your connected agents?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAgent(target.id)
                        pendingConfirmRemoveAgent = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusErr, contentColor = TextPrimary)
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { pendingConfirmRemoveAgent = null },
                    border = BorderStroke(1.dp, BorderStrong)
                ) {
                    Text("Cancel", color = TextPrimary)
                }
            }
        )
    }

    // Clear Chat Confirmation
    if (showConfirmClearChat) {
        AlertDialog(
            onDismissRequest = { showConfirmClearChat = false },
            containerColor = BgElevated,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text(stringResource(R.string.confirm_clear_chat_title)) },
            text = { Text(stringResource(R.string.confirm_clear_chat_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearChatHistory()
                        showConfirmClearChat = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusErr, contentColor = TextPrimary)
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showConfirmClearChat = false },
                    border = BorderStroke(1.dp, BorderStrong)
                ) {
                    Text("Cancel", color = TextPrimary)
                }
            }
        )
    }
}

@Composable
private fun BoardroomTopBar(
    agentCount: Int,
    readyBrowserCount: Int,
    openBrowserCount: Int,
    agents: List<AgentEntity>,
    viewMode: ChatViewMode,
    directAgentId: String?,
    showMenuButton: Boolean,
    onOpenDrawer: () -> Unit,
    onSelectBoardroom: () -> Unit,
    onSelectDirect: (String) -> Unit,
    onOpenBrowser: () -> Unit,
    onOpenManageAgents: () -> Unit,
    onRequestClearChat: () -> Unit
) {
    var showDirectMenu by remember { mutableStateOf(false) }

    Surface(
        color = BgSurface,
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (showMenuButton) {
                    IconButton(
                        onClick = onOpenDrawer,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = BgElevated,
                            contentColor = TextPrimary
                        ),
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("mobile_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Menu,
                            contentDescription = "Open agents sidebar",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.brand_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val statusSummary = when {
                        readyBrowserCount > 0 -> "$readyBrowserCount in-app ready"
                        openBrowserCount > 0 -> "$openBrowserCount browser open"
                        else -> "Connector ready"
                    }
                    Text(
                        text = "$agentCount ${if (agentCount == 1) "agent" else "agents"} · $statusSummary",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // View Mode Switcher (Boardroom / Direct)
                Surface(
                    color = BgCanvas,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, BorderSubtle)
                ) {
                    Row(
                        modifier = Modifier.padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = onSelectBoardroom,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (viewMode == ChatViewMode.BOARDROOM) AccentAmber else BgCanvas,
                                contentColor = if (viewMode == ChatViewMode.BOARDROOM) AccentOnAmber else TextSecondary
                            ),
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("view_boardroom_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Groups,
                                contentDescription = "Boardroom Broadcast",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { showDirectMenu = true },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (viewMode == ChatViewMode.DIRECT) AccentAmber else BgCanvas,
                                    contentColor = if (viewMode == ChatViewMode.DIRECT) AccentOnAmber else TextSecondary
                                ),
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("view_direct_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ChatBubbleOutline,
                                    contentDescription = "Direct Chat",
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showDirectMenu,
                                onDismissRequest = { showDirectMenu = false },
                                containerColor = BgElevated
                            ) {
                                if (agents.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("No agents added yet", color = TextMuted) },
                                        onClick = { showDirectMenu = false }
                                    )
                                } else {
                                    agents.forEach { agent ->
                                        val isSelected = viewMode == ChatViewMode.DIRECT && directAgentId == agent.id
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(
                                                        text = agent.name,
                                                        color = if (isSelected) AccentAmber else TextPrimary
                                                    )
                                                    Text(
                                                        text = AgentProvider.fromId(agent.provider).label,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = TextSecondary
                                                    )
                                                }
                                            },
                                            onClick = {
                                                showDirectMenu = false
                                                onSelectDirect(agent.id)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // In-App Browser Pill
                Surface(
                    onClick = onOpenBrowser,
                    color = AccentAmber,
                    contentColor = AccentOnAmber,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .height(44.dp)
                        .testTag("top_open_browser_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(horizontal = 10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (readyBrowserCount > 0 || openBrowserCount > 0) StatusOk else AccentOnAmber.copy(alpha = 0.5f))
                        )
                        Icon(
                            imageVector = Icons.Filled.Public,
                            contentDescription = "In-App Browser",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Manage Agents
                IconButton(
                    onClick = onOpenManageAgents,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = BgElevated,
                        contentColor = TextPrimary
                    ),
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("top_manage_agents_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = "Manage Agents",
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Clear Chat
                IconButton(
                    onClick = onRequestClearChat,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = BgElevated,
                        contentColor = StatusErr
                    ),
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("top_clear_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteSweep,
                        contentDescription = "Clear Chat",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
