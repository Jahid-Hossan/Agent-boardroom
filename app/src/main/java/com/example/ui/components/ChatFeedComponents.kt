package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.AgentEntity
import com.example.data.AgentProvider
import com.example.data.MessageEntity
import com.example.ui.ChatViewMode
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentOnAmber
import com.example.ui.theme.BgCanvas
import com.example.ui.theme.BgElevated
import com.example.ui.theme.BgSurface
import com.example.ui.theme.BorderStrong
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.parseHexColor
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ContextBannerBar(
    viewMode: ChatViewMode,
    directAgent: AgentEntity?,
    onReturnToBoardroom: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = BgSurface,
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val text = if (viewMode == ChatViewMode.BOARDROOM || directAgent == null) {
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = TextPrimary)) {
                        append(stringResource(R.string.boardroom_broadcast))
                    }
                    withStyle(SpanStyle(color = TextSecondary)) {
                        append(" · ${stringResource(R.string.boardroom_subtitle)}")
                    }
                }
            } else {
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = AccentAmber)) {
                        append(stringResource(R.string.direct_chat))
                    }
                    withStyle(SpanStyle(color = TextSecondary)) {
                        append(" · ${directAgent.name} (${AgentProvider.fromId(directAgent.provider).label})")
                    }
                }
            }

            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )

            if (viewMode == ChatViewMode.DIRECT) {
                IconButton(
                    onClick = onReturnToBoardroom,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = BgElevated,
                        contentColor = TextPrimary
                    ),
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("return_boardroom_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Return to Boardroom",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun OnboardingHeroCard(
    onOpenInAppBrowser: () -> Unit,
    onOpenManageAgents: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = BgSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderStrong),
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 680.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_boardroom_hero),
                    contentDescription = "Muse Boardroom command center artwork",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, BgSurface.copy(alpha = 0.92f))
                            )
                        )
                )
                Text(
                    text = stringResource(R.string.onboarding_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.onboarding_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onOpenInAppBrowser,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentAmber,
                            contentColor = AccentOnAmber
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("onboarding_browser_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Public,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(8.dp))
                        Text("In-App Browser", maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = onOpenManageAgents,
                        border = BorderStroke(1.dp, BorderStrong),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("onboarding_manage_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ManageAccounts,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(8.dp))
                        Text("Manage Agents", color = TextPrimary, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun MessageCardItem(
    message: MessageEntity,
    modifier: Modifier = Modifier
) {
    val isUser = message.sender == "ceo"
    val isSystem = message.sender == "system"
    val accent = when {
        isUser -> AccentAmber
        isSystem -> TextMuted
        else -> parseHexColor(message.colorHex)
    }

    val containerColor = when {
        isUser -> Color(0xFF1A1F2E)
        isSystem -> BgSurface.copy(alpha = 0.75f)
        else -> BgSurface
    }

    val borderColor = when {
        isUser -> AccentAmber.copy(alpha = 0.45f)
        isSystem -> BorderSubtle
        else -> BorderSubtle
    }

    val formattedTime = remember(message.timestampMs) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestampMs))
    }

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.2f))
                            .border(1.dp, accent.copy(alpha = 0.65f), CircleShape)
                    ) {
                        Text(
                            text = when {
                                isUser -> "Y"
                                isSystem -> "S"
                                else -> message.avatar.ifBlank { message.senderName.take(1) }.uppercase()
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                    }

                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isUser) AccentAmber else TextPrimary
                    )

                    val metaParts = buildList {
                        if (message.role.isNotBlank() && message.role != message.senderName) add(message.role)
                        if (message.mode == "direct") add("Direct")
                    }
                    if (metaParts.isNotEmpty()) {
                        Text(
                            text = metaParts.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }

                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = TextMuted
                )
            }

            Spacer(Modifier.height(8.dp))

            FormattedMarkdownText(
                text = message.text,
                color = if (isSystem) TextSecondary else TextPrimary
            )

            if (!message.attachmentPath.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                val file = File(message.attachmentPath)
                val isImage = message.attachmentMimeType?.startsWith("image/") == true
                if (isImage && file.exists()) {
                    AsyncImage(
                        model = file,
                        contentDescription = message.attachmentName ?: "Attached image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                    )
                } else {
                    Surface(
                        color = BgElevated,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AttachFile,
                                contentDescription = null,
                                tint = AccentAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = message.attachmentName ?: "Attached file",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StreamingReplyCard(
    agent: AgentEntity?,
    partialText: String,
    modifier: Modifier = Modifier
) {
    val accent = parseHexColor(agent?.colorHex ?: "#8B5CF6")
    Surface(
        color = BgSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = accent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "${agent?.name ?: "Agent"} · Streaming...",
                    style = MaterialTheme.typography.titleSmall,
                    color = accent
                )
            }
            Spacer(Modifier.height(8.dp))
            FormattedMarkdownText(text = partialText)
        }
    }
}

@Composable
fun ThinkingStatusCard(
    pendingAgentNames: List<String>,
    modifier: Modifier = Modifier
) {
    Surface(
        color = BgCanvas,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = AccentAmber,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = if (pendingAgentNames.isEmpty()) {
                    "Dispatching command..."
                } else {
                    "Waiting for ${pendingAgentNames.joinToString(", ")}…"
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}
