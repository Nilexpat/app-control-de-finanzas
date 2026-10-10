package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Focused, minimal, and modern palette inspired by high-end design reference
// Dominated by Matte Charcoal Black, Electric Neon Lime (#CCF32F), and Electric Cobalt Blue (#3838E8)
val BrandDark = Color(0xFF121316)             // Matte Charcoal Black
val BrandDarkCard = Color(0xFF18191E)         // Sleek Dark Card Background
val BrandBlue = Color(0xFF3838E8)             // Electric Cobalt Blue
val BrandBlueLight = Color(0xFFEEF0FD)        // Cobalt Soft Tint
val BrandLime = Color(0xFFD4F73C)             // Electric Neon Lime
val BrandLimeLight = Color(0xFFF4FDCB)        // Lime Soft Glow
val BrandCanvas = Color(0xFFF7F8FA)           // Clean Off-white Canvas
val BrandSurface = Color(0xFFFFFFFF)          // Pure Crisp White
val BrandSurfaceMuted = Color(0xFFEFF1F4)     // Neutral Pill / Chip Gray
val BrandBorder = Color(0xFFE5E7EB)           // Subtle Outline
val BrandTextPrimary = Color(0xFF121316)      // Primary Deep Black
val BrandTextSecondary = Color(0xFF6B7280)    // Muted Secondary Text
val BrandTextMuted = Color(0xFF9CA3AF)        // Subtle Gray (Dates, Tags)

// Semantic financial colors strictly mapped to the limited palette:
val FinanceGreen = Color(0xFF166534)          // Deep readable green for text on white
val FinanceGreenBg = BrandLime                // Electric Lime badge with black text
val FinanceRed = Color(0xFFDC2626)            // Expenses & Debt (-)
val FinanceRedBg = Color(0xFFFEE2E2)          // Soft rose badge
val FinanceOrange = Color(0xFFB45309)         // Préstamos
val FinanceOrangeBg = Color(0xFFFEF3C7)       // Soft amber badge
val FinanceTeal = BrandBlue                   // Abonos (+) uses the Electric Cobalt Blue
val FinanceTealBg = BrandBlueLight

// Theme aliases for seamless compatibility
val EmeraldPrimary = BrandDark
val EmeraldOnPrimary = Color(0xFFFFFFFF)
val EmeraldPrimaryContainer = BrandLime
val EmeraldOnPrimaryContainer = BrandDark

val TealSecondary = BrandBlue
val TealOnSecondary = Color(0xFFFFFFFF)
val TealSecondaryContainer = BrandBlueLight
val TealOnSecondaryContainer = BrandBlue

val AmberTertiary = BrandLime
val AmberOnTertiary = BrandDark
val AmberTertiaryContainer = BrandLimeLight
val AmberOnTertiaryContainer = BrandDark

val BackgroundLight = BrandCanvas
val SurfaceLight = BrandSurface
val SurfaceVariantLight = BrandSurfaceMuted
val OutlineLight = BrandBorder

val DarkBackground = Color(0xFF111216)
val DarkSurface = Color(0xFF18191E)
val DarkSurfaceVariant = Color(0xFF22242B)
