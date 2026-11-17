package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.AgentEntity
import com.example.data.PendingAttachment
import com.example.ui.ChatViewMode
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentOnAmber
import com.example.ui.theme.BgCanvas
import com.example.ui.theme.BgElevated
import com.example.ui.theme.BgSurface
import com.example.ui.theme.BorderStrong
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.StatusErr
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.io.File

@Composable
fun ComposerBar(
    agents: List<AgentEntity>,
    viewMode: ChatViewMode,
    directAgent: AgentEntity?,
    composerText: String,
    pendingAttachment: PendingAttachment?,
    isSending: Boolean,
    isRecordingVoice: Boolean,
    voiceSeconds: Int,
    isTranscribingVoice: Boolean,
    onTextChange: (String) -> Unit,
    onInsertMention: (String) -> Unit,
    onPickImage: () -> Unit,
    onPickDocument: () -> Unit,
    onClearAttachment: () -> Unit,
    onTranscribeAttachment: () -> Unit,
    onToggleVoice: () -> Unit,
    onStopVoice: () -> Unit,
    onCancelVoice: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAttachMenu by remember { mutableStateOf(false) }

    Surface(
        color = BgSurface,
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Mention Chips
            if (viewMode == ChatViewMode.BOARDROOM && agents.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Mentions:",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    MentionChip(label = "@all", onClick = { onInsertMention("@all") })
                    agents.forEach { agent ->
                        MentionChip(
                            label = "@${agent.name}",
                            onClick = { onInsertMention("@${agent.name}") }
                        )
                    }
                }
            }

            // Attachment Preview
            AnimatedVisibility(visible = pendingAttachment != null) {
                pendingAttachment?.let { att ->
                    Surface(
                        color = BgElevated,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, BorderStrong),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            if (att.isImage) {
                                AsyncImage(
                                    model = File(att.localFilePath),
                                    contentDescription = att.filename,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                )
                            } else {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(BgSurface)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AttachFile,
                                        contentDescription = null,
                                        tint = AccentAmber,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Text(
                                text = att.filename,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            if (att.isAudio) {
                                IconButton(
                                    onClick = onTranscribeAttachment,
                                    enabled = !isTranscribingVoice,
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = AccentAmber,
                                        contentColor = AccentOnAmber
                                    ),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    if (isTranscribingVoice) {
                                        CircularProgressIndicator(
                                            strokeWidth = 2.dp,
                                            color = AccentOnAmber,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.GraphicEq,
                                            contentDescription = "Transcribe audio to prompt",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = onClearAttachment,
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = BgSurface,
                                    contentColor = StatusErr
                                ),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Remove attachment",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Voice Recording / Transcribing Bar
            AnimatedVisibility(visible = isRecordingVoice || isTranscribingVoice) {
                Surface(
                    color = BgElevated,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (isRecordingVoice) StatusErr.copy(alpha = 0.6f) else AccentAmber.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isTranscribingVoice) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = AccentAmber,
                                    modifier = Modifier.size(14.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(StatusErr)
                                )
                            }
                            val mins = voiceSeconds / 60
                            val secs = voiceSeconds % 60
                            Text(
                                text = "$mins:${secs.toString().padStart(2, '0')}",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFamily),
                                color = TextPrimary
                            )
                            Text(
                                text = if (isTranscribingVoice) "Generating prompt from voice…" else "Listening… speak your prompt",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (!isTranscribingVoice) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                IconButton(
                                    onClick = onCancelVoice,
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = BgSurface,
                                        contentColor = TextSecondary
                                    ),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Cancel voice recording",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = onStopVoice,
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = AccentAmber,
                                        contentColor = AccentOnAmber
                                    ),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Finish recording and transcribe",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Input Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box {
                    IconButton(
                        onClick = { showAttachMenu = true },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = BgElevated,
                            contentColor = TextSecondary
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("attach_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AttachFile,
                            contentDescription = "Attach image or file",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showAttachMenu,
                        onDismissRequest = { showAttachMenu = false },
                        containerColor = BgElevated
                    ) {
                        DropdownMenuItem(
                            text = { Text("Attach Photo / Image", color = TextPrimary) },
                            onClick = {
                                showAttachMenu = false
                                onPickImage()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Attach Document or Audio", color = TextPrimary) },
                            onClick = {
                                showAttachMenu = false
                                onPickDocument()
                            }
                        )
                    }
                }

                IconButton(
                    onClick = onToggleVoice,
                    enabled = !isTranscribingVoice,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (isRecordingVoice) StatusErr else BgElevated,
                        contentColor = if (isRecordingVoice) TextPrimary else TextSecondary
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("mic_button")
                ) {
                    Icon(
                        imageVector = if (isRecordingVoice) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = if (isRecordingVoice) "Stop recording" else "Voice prompt",
                        modifier = Modifier.size(20.dp)
                    )
                }

                OutlinedTextField(
                    value = composerText,
                    onValueChange = onTextChange,
                    placeholder = {
                        Text(
                            text = if (viewMode == ChatViewMode.DIRECT && directAgent != null) {
                                "Message ${directAgent.name} directly…"
                            } else {
                                "Prompt all agents (or @AgentName)…"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    singleLine = false,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = BgCanvas,
                        unfocusedContainerColor = BgCanvas,
                        focusedBorderColor = AccentAmber,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = AccentAmber
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("command_input")
                )

                IconButton(
                    onClick = onSend,
                    enabled = !isSending && (composerText.isNotBlank() || pendingAttachment != null),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = AccentAmber,
                        contentColor = AccentOnAmber,
                        disabledContainerColor = BgElevated,
                        disabledContentColor = TextMuted
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("send_button")
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = AccentAmber,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send command",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MentionChip(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = BgElevated,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
