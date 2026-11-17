package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.AgentEntity
import com.example.data.AgentProvider
import com.example.data.DEFAULT_AI_PRESETS
import com.example.engine.normalizeHttpUrl
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageAgentsDialog(
    agents: List<AgentEntity>,
    editingAgentId: String?,
    webhookTestStatus: String?,
    onSelectAgent: (String) -> Unit,
    onAddAgent: () -> Unit,
    onDuplicateAgent: (AgentEntity) -> Unit,
    onOpenInAppBrowser: (AgentEntity) -> Unit,
    onTestWebhook: (String) -> Unit,
    onSaveAgent: (AgentEntity) -> Unit,
    onRequestDeleteAgent: (AgentEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val activeAgent = agents.find { it.id == editingAgentId } ?: agents.firstOrNull()

    var name by remember(activeAgent?.id) { mutableStateOf(activeAgent?.name ?: "") }
    var role by remember(activeAgent?.id) { mutableStateOf(activeAgent?.role ?: "") }
    var provider by remember(activeAgent?.id) { mutableStateOf(AgentProvider.fromId(activeAgent?.provider ?: AgentProvider.INAPP_BROWSER.id)) }
    var customUrl by remember(activeAgent?.id) { mutableStateOf(activeAgent?.customUrl ?: "https://muse.ai") }
    var apiKey by remember(activeAgent?.id) { mutableStateOf(activeAgent?.apiKey ?: "") }
    var model by remember(activeAgent?.id) { mutableStateOf(activeAgent?.model ?: "gemini-3.8-flash") }
    var endpoint by remember(activeAgent?.id) { mutableStateOf(activeAgent?.endpoint ?: "") }
    var systemPrompt by remember(activeAgent?.id) { mutableStateOf(activeAgent?.systemPrompt ?: "") }
    var providerMenuExpanded by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = BgSurface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, BorderStrong),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Manage Agents",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary
                    )
                    IconButton(
                        onClick = onDismiss,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = BgElevated,
                            contentColor = TextPrimary
                        ),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close Manage Agents")
                    }
                }

                // Agent Tabs
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgCanvas)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    agents.forEach { agent ->
                        val selected = agent.id == activeAgent?.id
                        Surface(
                            onClick = { onSelectAgent(agent.id) },
                            color = if (selected) AccentAmber else BgElevated,
                            contentColor = if (selected) AccentOnAmber else TextPrimary,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (selected) AccentAmber else BorderSubtle)
                        ) {
                            Text(
                                text = agent.name,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onAddAgent,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = AccentAmber,
                            contentColor = AccentOnAmber
                        ),
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("modal_add_agent_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add Agent", modifier = Modifier.size(18.dp))
                    }
                }

                // Form Body
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (activeAgent == null) {
                        Text(
                            text = "No agent selected. Tap + above to create one.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    } else {
                        val fieldColors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = BgCanvas,
                            unfocusedContainerColor = BgCanvas,
                            focusedBorderColor = AccentAmber,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Agent Name") },
                            singleLine = true,
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = role,
                            onValueChange = { role = it },
                            label = { Text("Account / Role Label") },
                            placeholder = { Text("e.g. Muse Account 1, Work Gemini") },
                            singleLine = true,
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Connection Type + Duplicate
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ExposedDropdownMenuBox(
                                expanded = providerMenuExpanded,
                                onExpandedChange = { providerMenuExpanded = !providerMenuExpanded },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = provider.label,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Connection Type") },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerMenuExpanded)
                                    },
                                    colors = fieldColors,
                                    modifier = Modifier
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                        .fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = providerMenuExpanded,
                                    onDismissRequest = { providerMenuExpanded = false },
                                    containerColor = BgElevated
                                ) {
                                    AgentProvider.entries.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.label, color = TextPrimary) },
                                            onClick = {
                                                provider = option
                                                providerMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = { onDuplicateAgent(activeAgent) },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = BgElevated,
                                    contentColor = TextPrimary
                                ),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate for 2nd Account")
                            }
                        }

                        // Provider-specific fields
                        when (provider) {
                            AgentProvider.INAPP_BROWSER -> {
                                Surface(
                                    color = BgElevated,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, BorderSubtle),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "Quick AI Target",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = TextSecondary
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState())
                                        ) {
                                            DEFAULT_AI_PRESETS.forEach { preset ->
                                                Surface(
                                                    onClick = {
                                                        customUrl = preset.url
                                                        if (name.startsWith("Agent-")) name = preset.label
                                                        if (role.isBlank() || role == "In-App Browser AI") role = preset.role
                                                    },
                                                    color = BgSurface,
                                                    shape = RoundedCornerShape(8.dp),
                                                    border = BorderStroke(1.dp, BorderStrong)
                                                ) {
                                                    Text(
                                                        text = preset.label,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = TextPrimary,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                    )
                                                }
                                            }
                                        }

                                        OutlinedTextField(
                                            value = customUrl,
                                            onValueChange = { customUrl = it },
                                            label = { Text("Target AI Site URL") },
                                            singleLine = true,
                                            colors = fieldColors,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Button(
                                            onClick = {
                                                val updated = activeAgent.copy(
                                                    name = name.trim().ifBlank { activeAgent.name },
                                                    role = role.trim(),
                                                    provider = AgentProvider.INAPP_BROWSER.id,
                                                    customUrl = normalizeHttpUrl(customUrl.ifBlank { "https://muse.ai" })
                                                )
                                                onOpenInAppBrowser(updated)
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = AccentAmber,
                                                contentColor = AccentOnAmber
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                        ) {
                                            Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.size(8.dp))
                                            Text("Open in In-App Browser")
                                        }
                                    }
                                }
                            }

                            AgentProvider.GEMINI -> {
                                Surface(
                                    color = BgElevated,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, StatusOk.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.VerifiedUser,
                                                contentDescription = null,
                                                tint = StatusOk,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "Powered by Firebase AI Logic — no API key needed",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = StatusOk
                                            )
                                        }
                                        OutlinedTextField(
                                            value = model,
                                            onValueChange = { model = it },
                                            label = { Text("Gemini Model") },
                                            placeholder = { Text("gemini-3.8-flash") },
                                            singleLine = true,
                                            colors = fieldColors,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }

                            AgentProvider.OPENAI -> {
                                Surface(
                                    color = BgElevated,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, BorderSubtle),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = apiKey,
                                            onValueChange = { apiKey = it },
                                            label = { Text("API Key") },
                                            visualTransformation = PasswordVisualTransformation(),
                                            singleLine = true,
                                            colors = fieldColors,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedTextField(
                                            value = model,
                                            onValueChange = { model = it },
                                            label = { Text("Model") },
                                            placeholder = { Text("gpt-4o-mini") },
                                            singleLine = true,
                                            colors = fieldColors,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedTextField(
                                            value = endpoint,
                                            onValueChange = { endpoint = it },
                                            label = { Text("Endpoint (Optional)") },
                                            placeholder = { Text("https://api.openai.com/v1/chat/completions") },
                                            singleLine = true,
                                            colors = fieldColors,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }

                            AgentProvider.DOCKER_WEB -> {
                                Surface(
                                    color = BgElevated,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, BorderSubtle),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = customUrl,
                                            onValueChange = { customUrl = it },
                                            label = { Text("Public Agent Webhook URL") },
                                            placeholder = { Text("https://your-agent.example.com") },
                                            singleLine = true,
                                            colors = fieldColors,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { onTestWebhook(customUrl) },
                                                border = BorderStroke(1.dp, BorderStrong),
                                                modifier = Modifier.height(44.dp)
                                            ) {
                                                Icon(Icons.Filled.Bolt, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.size(6.dp))
                                                Text("Test Connection", color = TextPrimary)
                                            }
                                            if (!webhookTestStatus.isNullOrBlank()) {
                                                Text(
                                                    text = webhookTestStatus,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = TextSecondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            AgentProvider.SIMULATION -> {
                                Text(
                                    text = "Offline Demo responds locally without network requests.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        OutlinedTextField(
                            value = systemPrompt,
                            onValueChange = { systemPrompt = it },
                            label = { Text("System Instructions (For API Agents)") },
                            minLines = 2,
                            maxLines = 4,
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgCanvas)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (activeAgent != null) {
                        IconButton(
                            onClick = { onRequestDeleteAgent(activeAgent) },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = BgElevated,
                                contentColor = StatusErr
                            ),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove Agent")
                        }
                    } else {
                        Box(Modifier.size(48.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onDismiss,
                            border = BorderStroke(1.dp, BorderStrong),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Cancel", color = TextPrimary)
                        }

                        if (activeAgent != null) {
                            Button(
                                onClick = {
                                    val saved = activeAgent.copy(
                                        name = name.trim(),
                                        role = role.trim(),
                                        provider = provider.id,
                                        customUrl = if (provider == AgentProvider.INAPP_BROWSER || provider == AgentProvider.DOCKER_WEB) {
                                            normalizeHttpUrl(customUrl)
                                        } else {
                                            activeAgent.customUrl
                                        },
                                        apiKey = apiKey.trim(),
                                        model = model.trim(),
                                        endpoint = endpoint.trim(),
                                        systemPrompt = systemPrompt.trim()
                                    )
                                    onSaveAgent(saved)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentAmber,
                                    contentColor = AccentOnAmber
                                ),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("save_agent_button")
                            ) {
                                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("Save")
                            }
                        }
                    }
                }
            }
        }
    }
}
