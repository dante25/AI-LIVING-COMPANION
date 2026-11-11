package io.androllm.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Soft Rose — design tokens (feminine pink theme).
 * Light: blush grounds, plum ink, rose #E0457B accents.
 * Dark/AMOLED: deep plum grounds (never pure black), blush ink.
 */
// ── The black canvas ─────────────────────────────────────────────────────────
val DeskNight = Color(0xFF2A1420)          // the page — app canvas
val DeskNightRaised = Color(0xFF341A28)    // raised ground / layout wash
val DeskWalnut = Color(0xFF3D1F2F)         // card surface
val DeskWalnutRaised = Color(0xFF4A2638)   // elevated card / hover surface
val DeskWalnutDeep = Color(0xFF22101A)     // inset well / pressed surface
val DeskPaper = Color(0xFFFFF0F5)          // ink — primary text & headings
val DeskPaperDim = Color(0xFFF8D7E3)       // strong ink (status figures)
val DeskInk = Color(0xFFD9AFC2)            // muted ink — secondary text
val DeskInkFaint = Color(0xFFA87C92)       // faint ink — tertiary / marginalia
val DeskHairline = Color(0xFF5C3448)       // the rule
val DeskHairlineSoft = Color(0xFF4E2B3C)   // softer rule

// ── The single accent (aurora violet) ────────────────────────────────────────
val LampAmber = Color(0xFFF06A95)          // primary actions — aurora violet
val LampGlow = Color(0xFFF8BBD0)           // highlight / glow — light lavender
val LampHalo = Color(0x40F06A95)           // soft halo — violet at 25%
val LampDeep = Color(0xFFE0457B)           // pressed / focus — deep lavender
val InkOnLamp = Color(0xFF3A0A1E)          // text on primary — white on violet

// ── Status red, readable on black ────────────────────────────────────────────
val EmberRed = Color(0xFFF2928D)
val EmberRedSoft = Color(0xFF4A1D22)
val EmberRedHard = Color(0xFFFFB4AB)
val EmberOnRed = Color(0xFF410002)

// ── Wash container ───────────────────────────────────────────────────────────
val TerracottaSoft = Color(0xFF6B2D4A)     // wash container
val TerracottaDeep = Color(0xFFFFD9E6)     // text on wash

val DarkCanvas = Color(0xFF2A1420)
val DarkCanvasRaised = Color(0xFF341A28)
val DarkSurface = Color(0xFF3D1F2F)
val DarkText = Color(0xFFFFF0F5)
val DarkTextSecondary = Color(0xFFD9AFC2)
val DarkTextFaint = Color(0xFFA87C92)
val DarkHairline = Color(0xFF5C3448)
val DarkHairlineSoft = Color(0xFF4E2B3C)
val DarkPrimary = Color(0xFFF06A95)
val DarkPrimaryHover = Color(0xFFF8BBD0)
val DarkPrimaryDeep = Color(0xFFE0457B)
val DarkOnPrimary = Color(0xFF3A0A1E)

// ── Material surface-container roles (blackout) ──────────────────────────────
val ParchmentLowest = Color(0xFFFFFFFF)
val ParchmentLow = Color(0xFFFFF5F8)
val ParchmentContainer = Color(0xFFFFEEF3)
val ParchmentHigh = Color(0xFFFCE4EC)
val ParchmentHighest = Color(0xFFF8D7E3)
val ParchmentBright = Color(0xFFFFFFFF)
val ParchmentDim = Color(0xFFF5DCE6)
val ParchmentInverse = Color(0xFF4A2C3A)
val OnParchmentInverse = Color(0xFFFFF0F5)

// ── Material surface-container roles (dark variant) ──────────────────────────
val DarkContainerLowest = Color(0xFF22101A)
val DarkContainerLow = Color(0xFF2F1724)
val DarkContainer = Color(0xFF3D1F2F)
val DarkContainerHigh = Color(0xFF4A2638)
val DarkContainerHighest = Color(0xFF573043)
val DarkSurfaceBright = Color(0xFF4A2638)
val DarkSurfaceDim = Color(0xFF22101A)
val DarkInverse = Color(0xFFFFF0F5)
val OnDarkInverse = Color(0xFF3A0A1E)

// ── True AMOLED palette — pure black, minimal glow ───────────────────────────
val AmoledNight = Color(0xFF1A0A12)          // canvas — pure black
val AmoledNightRaised = Color(0xFF22101A)    // raised ground / layout wash
val AmoledWalnut = Color(0xFF2A1420)         // card surface
val AmoledWalnutRaised = Color(0xFF341A28)   // elevated card / hover surface
val AmoledWalnutDeep = Color(0xFF14070D)     // inset well / pressed surface
val AmoledPaper = Color(0xFFFFF0F5)          // ink — primary text
val AmoledPaperDim = Color(0xFFF8D7E3)       // strong secondary ink
val AmoledInk = Color(0xFFD9AFC2)            // muted ink — secondary text
val AmoledInkFaint = Color(0xFFA87C92)       // faint ink — tertiary
val AmoledHairline = Color(0xFF4E2B3C)       // rule
val AmoledHairlineSoft = Color(0xFF3D1F2F)   // softer rule

// ── AMOLED surface-container roles ───────────────────────────────────────────
val AmoledContainerLowest = Color(0xFF14070D)
val AmoledContainerLow = Color(0xFF1A0A12)
val AmoledContainer = Color(0xFF2A1420)
val AmoledContainerHigh = Color(0xFF341A28)
val AmoledContainerHighest = Color(0xFF3D1F2F)
val AmoledSurfaceBright = Color(0xFF341A28)
val AmoledSurfaceDim = Color(0xFF14070D)

// ── Legacy aliases mapped into the blackout palette ────────────────────────────
val DeepMidnightBlue = DeskNight
val DarkAtmosphere = DeskNightRaised
val TwilightNavy = DeskWalnut
val CloudShadowIndigo = DeskWalnutDeep

val SkyBlue = LampAmber
val AzureBlue = LampDeep
val CloudWhite = Color(0xFFFFFFFF)
val MoonSilver = DeskInkFaint
val SoftCyan = LampGlow.copy(alpha = 0.9f)

val SunsetCloudPeach = LampAmber
val SunsetCloudOrange = LampDeep
val SunsetCloudDeepOrange = Color(0xFFB82D5F)
val SunsetGlowAmber = LampGlow
val CrescentMoonGold = LampGlow

val RevolutNeonEmerald = Color(0xFF4ADE80) // success green, readable on black
val RevolutPlatinum = Color(0xFFE8C9D6)
val RevolutUltraViolet = Color(0xFFC2185B)
val RevolutGoldTier = Color(0xFFE3B7C6)
val RevolutRoseGold = Color(0xFFE0457B)
val RevolutCyberCyan = LampGlow
val RevolutTitanium = DeskInkFaint
val RevolutDarkCardBackground = DeskWalnut

val LavenderGlow = Color(0xFFF8BBD0)
val DeepIndigo = Color(0xFF3D1F2F)
val PurpleGlow = Color(0xFFE0457B)
val ElectricBlue = LampAmber
val AuroraCyan = LampGlow
val MoonlightWhite = CloudWhite

// Frosted aliases — black glass
val CloudGlassSurface = Color(0xCC2A1420)
val CloudGlassSurfaceVariant = Color(0xE63D1F2F)
val CloudGlassBorder = Color(0x595C3448)
val CloudGlassBorderHighlight = Color(0x33F8BBD0)
val CloudMoonGlow = Color(0x40F06A95)
val CloudParticleTint = Color(0x40F06A95)

// Material role mappings
val brandPrimary = LampAmber
val brandAccent = LampAmber
val brandOnPrimary = InkOnLamp

val brandBackground = DeskNight
val brandSurface = DeskWalnut
val brandSurfaceVariant = DeskWalnutDeep
val brandOnSurface = DeskPaper
val brandOnSurfaceVariant = DeskInk
val brandOutline = DeskHairline
val brandOutlineVariant = DeskHairlineSoft

val brandError = EmberRed
val brandOnError = EmberOnRed
val brandErrorContainer = EmberRedSoft
val brandOnErrorContainer = EmberRedHard

val onSurface = brandOnSurface

// ── Light scheme palette (soft rose) ───────────────────────────────────────────
val LightPrimary = Color(0xFFE0457B)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightBackground = Color(0xFFFFF0F5)
val LightSurface = Color(0xFFFFF5F8)
val LightOnSurface = Color(0xFF4A2C3A)
val LightSurfaceVariant = Color(0xFFFCE4EC)
val LightOnSurfaceVariant = Color(0xFF8A6070)
val LightOutline = Color(0xFFF3C9D8)
