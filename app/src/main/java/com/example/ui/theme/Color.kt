package com.example.ui.theme

import androidx.compose.ui.graphics.Color

val BgCanvas = Color(0xFF0B0E14)
val BgSurface = Color(0xFF121722)
val BgElevated = Color(0xFF182030)
val BgHover = Color(0xFF1F293D)
val BorderSubtle = Color(0xFF232D42)
val BorderStrong = Color(0xFF33415C)

val TextPrimary = Color(0xFFF1F5F9)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)

val AccentAmber = Color(0xFFF59E0B)
val AccentAmberBright = Color(0xFFFBBF24)
val AccentOnAmber = Color(0xFF090D16)

val StatusOk = Color(0xFF10B981)
val StatusWarn = Color(0xFFF59E0B)
val StatusErr = Color(0xFFEF4444)

val AgentBlue = Color(0xFF3B82F6)
val AgentPurple = Color(0xFF8B5CF6)
val AgentEmerald = Color(0xFF10B981)
val AgentSlate = Color(0xFFE2E8F0)

fun parseHexColor(hex: String, fallback: Color = AgentBlue): Color {
    return try {
        val clean = hex.trim().removePrefix("#")
        when (clean.length) {
            6 -> Color(("FF$clean").toLong(16))
            8 -> Color(clean.toLong(16))
            else -> fallback
        }
    } catch (_: Exception) {
        fallback
    }
}
