package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AgentEntity
import com.example.data.AgentProvider
import com.example.data.InAppBrowserStatus
import com.example.engine.extractHostname
import com.example.ui.ChatViewMode
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
import com.example.ui.theme.parseHexColor

@Composable
fun AgentRosterSidebar(
    agents: List<AgentEntity>,
    browserStatuses: Map<String, InAppBrowserStatus>,
    viewMode: ChatViewMode,
    directAgentId: String?,
    onOpenBrowser: (String?) -> Unit,
    onAddAgent: () -> Unit,
    onSelectDirectChat: (String) -> Unit,
    onEditAgent: (String) -> Unit,
    onRequestRemoveAgent: (AgentEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val openCount = browserStatuses.values.count { it.open }
    val readyCount = browserStatuses.values.count { it.open && it.ready }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(BgSurface)
            .padding(14.dp)
    ) {
        // In-App Browser Connector Summary
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.connector_title).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Surface(
                onClick = { onOpenBrowser(null) },
                color = AccentAmber,
                contentColor = AccentOnAmber,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .size(40.dp)
                    .testTag("sidebar_open_browser_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Public,
                        contentDescription = "Open In-App Browser",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Surface(
            color = BgElevated,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (readyCount > 0 || openCount > 0) StatusOk else AccentAmber)
                    )
                    Text(
                        text = when {
                            readyCount > 0 -> "Connector active · $readyCount ready"
                            openCount > 0 -> "Connector attached · $openCount open"
                            else -> "Connector ready"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Tap the globe icon on any browser agent to sign in inside this app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Connected Agents Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.connected_agents).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Surface(
                onClick = onAddAgent,
                color = AccentAmber,
                contentColor = AccentOnAmber,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .size(40.dp)
                    .testTag("sidebar_add_agent_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add Agent",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (agents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No agents connected yet.\nTap + to add an agent.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(agents, key = { it.id }) { agent ->
                    val isDirectActive = viewMode == ChatViewMode.DIRECT && directAgentId == agent.id
                    AgentRosterCard(
                        agent = agent,
                        browserStatus = browserStatuses[agent.id],
                        isDirectActive = isDirectActive,
                        onDirectChat = { onSelectDirectChat(agent.id) },
                        onOpenBrowser = { onOpenBrowser(agent.id) },
                        onEdit = { onEditAgent(agent.id) },
                        onRemove = { onRequestRemoveAgent(agent) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AgentRosterCard(
    agent: AgentEntity,
    browserStatus: InAppBrowserStatus?,
    isDirectActive: Boolean,
    onDirectChat: () -> Unit,
    onOpenBrowser: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    val accentColor = parseHexColor(agent.colorHex)
    val provider = AgentProvider.fromId(agent.provider)

    val statusText = when (provider) {
        AgentProvider.INAPP_BROWSER -> {
            val host = extractHostname(browserStatus?.url?.ifBlank { agent.customUrl } ?: agent.customUrl).ifBlank { "site" }
            when {
                browserStatus?.open == true && browserStatus.ready -> "In-App Browser · Connected ($host)"
                browserStatus?.open == true && browserStatus.needsLogin -> "In-App Browser · Sign in on $host"
                browserStatus?.open == true -> "In-App Browser · Tab open ($host)"
                else -> "In-App Browser · $host"
            }
        }
        AgentProvider.GEMINI -> "Firebase AI · ${agent.model.ifBlank { "gemini-3.8-flash" }}"
        AgentProvider.OPENAI -> "OpenAI API · ${agent.model.ifBlank { "gpt-4o-mini" }}"
        AgentProvider.DOCKER_WEB -> "Webhook · ${extractHostname(agent.customUrl).ifBlank { "Not set" }}"
        AgentProvider.SIMULATION -> "Offline Demo · Ready"
    }

    Surface(
        color = if (isDirectActive) BgCanvas else BgElevated,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (isDirectActive) 1.5.dp else 1.dp,
            color = if (isDirectActive) AccentAmber else BorderSubtle
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("agent_card_${agent.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.2f))
                        .border(1.dp, accentColor.copy(alpha = 0.7f), CircleShape)
                ) {
                    Text(
                        text = agent.avatar.ifBlank { agent.name.take(1) }.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = agent.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (browserStatus?.open == true && browserStatus.ready) StatusOk else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onDirectChat,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (isDirectActive) AccentAmber else BgSurface,
                            contentColor = if (isDirectActive) AccentOnAmber else TextPrimary
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("agent_chat_${agent.id}")
                    ) {
                        Icon(
                            imageVector = if (isDirectActive) Icons.Filled.ChatBubble else Icons.Outlined.ChatBubbleOutline,
                            contentDescription = "Chat 1-on-1 with ${agent.name}",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (provider == AgentProvider.INAPP_BROWSER) {
                        val isReady = browserStatus?.open == true && browserStatus.ready
                        IconButton(
                            onClick = onOpenBrowser,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (isReady) BgSurface else AccentAmber,
                                contentColor = if (isReady) StatusOk else AccentOnAmber
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("agent_browser_${agent.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Public,
                                contentDescription = "Open In-App Browser for ${agent.name}",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onEdit,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = BgSurface,
                            contentColor = TextSecondary
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("agent_edit_${agent.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit ${agent.name}",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onRemove,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = BgSurface,
                        contentColor = StatusErr
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("agent_remove_${agent.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Remove ${agent.name}",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
