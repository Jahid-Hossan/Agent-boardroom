package com.example.ui.components

import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.AgentEntity
import com.example.data.AgentProvider
import com.example.data.DEFAULT_AI_PRESETS
import com.example.data.InAppBrowserStatus
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentOnAmber
import com.example.ui.theme.BgCanvas
import com.example.ui.theme.BgElevated
import com.example.ui.theme.BgSurface
import com.example.ui.theme.BorderStrong
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.StatusErr
import com.example.ui.theme.StatusOk
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun InAppBrowserDialog(
    agents: List<AgentEntity>,
    activeAgentId: String?,
    browserStatuses: Map<String, InAppBrowserStatus>,
    getWebView: suspend (String) -> WebView?,
    onSwitchAgent: (String) -> Unit,
    onCreateNewAccount: () -> Unit,
    onNavigateUrl: (String) -> Unit,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onCheckReady: () -> Unit,
    onCloseTab: () -> Unit,
    onSendQuickText: (String) -> Unit,
    onPressEnter: () -> Unit,
    onDismiss: () -> Unit
) {
    val browserAgents = remember(agents, activeAgentId) {
        agents.filter { it.provider == AgentProvider.INAPP_BROWSER.id || it.id == activeAgentId }
    }
    val activeAgent = browserAgents.find { it.id == activeAgentId } ?: browserAgents.firstOrNull()
    val status = activeAgent?.let { browserStatuses[it.id] }

    var urlText by remember(activeAgent?.id) {
        mutableStateOf(status?.url?.ifBlank { activeAgent?.customUrl } ?: activeAgent?.customUrl ?: "https://muse.ai")
    }
    LaunchedEffect(status?.url) {
        val u = status?.url.orEmpty()
        if (u.isNotBlank()) urlText = u
    }

    var quickText by remember { mutableStateOf("") }
    var currentWebView by remember(activeAgent?.id) { mutableStateOf<WebView?>(null) }

    LaunchedEffect(activeAgent?.id) {
        val id = activeAgent?.id
        if (id != null) {
            currentWebView = getWebView(id)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            color = BgCanvas,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Modal Header
                Surface(color = BgSurface, border = BorderStroke(1.dp, BorderSubtle)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "In-App Browser & Connector",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val (badgeText, badgeColor) = when {
                                status == null || !status.open -> "Connector Attaching…" to TextSecondary
                                status.ready -> "Connector Attached · Chat Ready" to StatusOk
                                status.needsLogin -> "Connector Attached · Sign In" to AccentAmber
                                else -> "Connector Attached · Active" to TextSecondary
                            }
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = badgeColor
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = BgElevated,
                                contentColor = TextPrimary
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("close_browser_modal")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close In-App Browser"
                            )
                        }
                    }
                }

                // Agent Tabs Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgSurface)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    browserAgents.forEach { agent ->
                        val st = browserStatuses[agent.id]
                        val dot = when {
                            st?.open == true && st.ready -> " ●"
                            st?.open == true -> " ○"
                            else -> ""
                        }
                        val isSelected = agent.id == activeAgent?.id
                        Surface(
                            onClick = { onSwitchAgent(agent.id) },
                            color = if (isSelected) AccentAmber else BgElevated,
                            contentColor = if (isSelected) AccentOnAmber else TextPrimary,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (isSelected) AccentAmber else BorderSubtle)
                        ) {
                            Text(
                                text = "${agent.name}$dot",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onCreateNewAccount,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = AccentAmber,
                            contentColor = AccentOnAmber
                        ),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Create New Isolated Account",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // URL & Navigation Toolbar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgElevated)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = onBack,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = BgSurface,
                                contentColor = TextPrimary
                            ),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(18.dp))
                        }

                        IconButton(
                            onClick = onReload,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = BgSurface,
                                contentColor = TextPrimary
                            ),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Reload", modifier = Modifier.size(18.dp))
                        }

                        OutlinedTextField(
                            value = urlText,
                            onValueChange = { urlText = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFamily,
                                color = TextPrimary
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { onNavigateUrl(urlText) }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = BgCanvas,
                                unfocusedContainerColor = BgCanvas,
                                focusedBorderColor = AccentAmber,
                                unfocusedBorderColor = BorderSubtle
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = { onNavigateUrl(urlText) },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = AccentAmber,
                                contentColor = AccentOnAmber
                            ),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Go to URL", modifier = Modifier.size(18.dp))
                        }

                        IconButton(
                            onClick = onCheckReady,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = BgSurface,
                                contentColor = StatusOk
                            ),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = "Verify Login Ready", modifier = Modifier.size(18.dp))
                        }

                        IconButton(
                            onClick = onCloseTab,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = BgSurface,
                                contentColor = StatusErr
                            ),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Close Tab", modifier = Modifier.size(18.dp))
                        }
                    }

                    // Quick AI Site Chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        DEFAULT_AI_PRESETS.forEach { preset ->
                            Surface(
                                onClick = {
                                    urlText = preset.url
                                    onNavigateUrl(preset.url)
                                },
                                color = BgSurface,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, BorderStrong)
                            ) {
                                Text(
                                    text = preset.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                if (status?.isLoading == true) {
                    LinearProgressIndicator(
                        color = AccentAmber,
                        trackColor = BgSurface,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // WebView Viewport
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(BgCanvas)
                ) {
                    val wv = currentWebView
                    if (wv != null && status?.open != false) {
                        AndroidView(
                            factory = { ctx ->
                                FrameLayout(ctx).apply {
                                    (wv.parent as? ViewGroup)?.removeView(wv)
                                    addView(
                                        wv,
                                        FrameLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                    )
                                }
                            },
                            update = { container ->
                                if (wv.parent !== container) {
                                    (wv.parent as? ViewGroup)?.removeView(wv)
                                    container.removeAllViews()
                                    container.addView(
                                        wv,
                                        FrameLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(color = AccentAmber)
                            Text(
                                text = "Opening browser session for ${activeAgent?.name ?: "Agent"}…",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }

                // Quick Text / OTP Injection Bar
                Surface(
                    color = BgSurface,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        OutlinedTextField(
                            value = quickText,
                            onValueChange = { quickText = it },
                            placeholder = {
                                Text(
                                    "Paste email, password, or OTP into focused field…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = BgCanvas,
                                unfocusedContainerColor = BgCanvas,
                                focusedBorderColor = AccentAmber,
                                unfocusedBorderColor = BorderSubtle,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = {
                                if (quickText.isNotEmpty()) {
                                    onSendQuickText(quickText)
                                    quickText = ""
                                }
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = AccentAmber,
                                contentColor = AccentOnAmber
                            ),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send text to WebView")
                        }

                        IconButton(
                            onClick = onPressEnter,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = BgElevated,
                                contentColor = TextPrimary
                            ),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardReturn, contentDescription = "Press Enter in WebView")
                        }
                    }
                }
            }
        }
    }
}
