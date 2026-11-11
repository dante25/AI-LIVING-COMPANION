package io.androllm.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The Soft Rose palette, resolvable per theme.
 *
 * Light: blush-pink grounds, plum ink, rose accents. Dark/AMOLED: deep plum
 * grounds (never pure black), blush ink, rose accents.
 * Components read the current palette via [MaterialTheme.ledger] instead of
 * hardcoding a token, which is what flips the whole app at once.
 */
@Immutable
data class LedgerColors(
    val deskNight: Color,
    val deskNightRaised: Color,
    val deskWalnut: Color,
    val deskWalnutRaised: Color,
    val deskWalnutDeep: Color,
    val deskPaper: Color,
    val deskPaperDim: Color,
    val deskInk: Color,
    val deskInkFaint: Color,
    val deskHairline: Color,
    val deskHairlineSoft: Color,
    val lampAmber: Color,
    val lampGlow: Color,
    val lampDeep: Color,
    val lampHalo: Color,
    val inkOnLamp: Color,
    val emberRed: Color,
    val emberRedSoft: Color,
    val emberRedHard: Color,
    val emberOnRed: Color,
    val terracottaSoft: Color,
    val terracottaDeep: Color,
    val cloudWhite: Color,
    val cloudGlassSurface: Color,
    val cloudGlassSurfaceVariant: Color,
    val cloudGlassBorder: Color,
    val cloudGlassBorderHighlight: Color,
    val cloudMoonGlow: Color,
    val cloudParticleTint: Color,
    val revolutCyberCyan: Color,
    val revolutGoldTier: Color,
    val revolutNeonEmerald: Color,
    val sunsetGlowAmber: Color
)

/** Light mode — blush pink desk with the rose accent #E0457B. */
val LightLedger = LedgerColors(
    deskNight = Color(0xFFF0F5),
    deskNightRaised = Color(0xFFF5F8),
    deskWalnut = Color(0xFFF5F8),
    deskWalnutRaised = Color(0xFFFFFF),
    deskWalnutDeep = Color(0xFCE4EC),
    deskPaper = Color(0x4A2C3A),
    deskPaperDim = Color(0x3A2030),
    deskInk = Color(0x8A6070),
    deskInkFaint = Color(0xB08A9A),
    deskHairline = Color(0xF3C9D8),
    deskHairlineSoft = Color(0xF8DCE6),
    lampAmber = Color(0xE0457B),
    lampGlow = Color(0xF8BBD0),
    lampDeep = Color(0xB82D5F),
    lampHalo = Color(0x40E0457B),
    inkOnLamp = Color(0xFFFFFF),
    emberRed = Color(0xC62828),
    emberRedSoft = Color(0xFDECEA),
    emberRedHard = Color(0x8C1D18),
    emberOnRed = Color(0xFFFFFF),
    terracottaSoft = Color(0xF8BBD0),
    terracottaDeep = Color(0x5A1634),
    cloudWhite = Color(0xFFFFFF),
    cloudGlassSurface = Color(0xCCFFF5F8),
    cloudGlassSurfaceVariant = Color(0xE6FFFFFF),
    cloudGlassBorder = Color(0x59F3C9D8),
    cloudGlassBorderHighlight = Color(0x33E0457B),
    cloudMoonGlow = Color(0x40E0457B),
    cloudParticleTint = Color(0x40E0457B),
    revolutCyberCyan = Color(0xF8BBD0),
    revolutGoldTier = Color(0xC98A9E),
    revolutNeonEmerald = Color(0x2E9E5B),
    sunsetGlowAmber = Color(0xF8BBD0)
)

/** Night mode — deep plum desk, rose accent. */
val DarkLedger = LedgerColors(
    deskNight = Color(0x2A1420),
    deskNightRaised = Color(0x341A28),
    deskWalnut = Color(0x3D1F2F),
    deskWalnutRaised = Color(0x4A2638),
    deskWalnutDeep = Color(0x22101A),
    deskPaper = Color(0xFFF0F5),
    deskPaperDim = Color(0xF8D7E3),
    deskInk = Color(0xD9AFC2),
    deskInkFaint = Color(0xA87C92),
    deskHairline = Color(0x5C3448),
    deskHairlineSoft = Color(0x4E2B3C),
    lampAmber = Color(0xF06A95),
    lampGlow = Color(0xF8BBD0),
    lampDeep = Color(0xE0457B),
    lampHalo = Color(0x40F06A95),
    inkOnLamp = Color(0x3A0A1E),
    emberRed = Color(0xF2928D),
    emberRedSoft = Color(0x4A1D22),
    emberRedHard = Color(0xFFB4AB),
    emberOnRed = Color(0x410002),
    terracottaSoft = Color(0x6B2D4A),
    terracottaDeep = Color(0xFFD9E6),
    cloudWhite = Color(0xFFFFFF),
    cloudGlassSurface = Color(0xCC2A1420),
    cloudGlassSurfaceVariant = Color(0xE63D1F2F),
    cloudGlassBorder = Color(0x595C3448),
    cloudGlassBorderHighlight = Color(0x33F8BBD0),
    cloudMoonGlow = Color(0x40F06A95),
    cloudParticleTint = Color(0x40F06A95),
    revolutCyberCyan = Color(0xF8BBD0),
    revolutGoldTier = Color(0xE3B7C6),
    revolutNeonEmerald = Color(0x4ADE80),
    sunsetGlowAmber = Color(0xF8BBD0)
)

/** AMOLED mode — darkest plum floor (not pure black), rose accent. */
val AmoledLedger = LedgerColors(
    deskNight = Color(0x1A0A12),
    deskNightRaised = Color(0x22101A),
    deskWalnut = Color(0x2A1420),
    deskWalnutRaised = Color(0x341A28),
    deskWalnutDeep = Color(0x14070D),
    deskPaper = Color(0xFFF0F5),
    deskPaperDim = Color(0xF8D7E3),
    deskInk = Color(0xD9AFC2),
    deskInkFaint = Color(0xA87C92),
    deskHairline = Color(0x4E2B3C),
    deskHairlineSoft = Color(0x3D1F2F),
    lampAmber = Color(0xF06A95),
    lampGlow = Color(0xF8BBD0),
    lampDeep = Color(0xE0457B),
    lampHalo = Color(0x40F06A95),
    inkOnLamp = Color(0x3A0A1E),
    emberRed = Color(0xF2928D),
    emberRedSoft = Color(0x4A1D22),
    emberRedHard = Color(0xFFB4AB),
    emberOnRed = Color(0x410002),
    terracottaSoft = Color(0x6B2D4A),
    terracottaDeep = Color(0xFFD9E6),
    cloudWhite = Color(0xFFFFFF),
    cloudGlassSurface = Color(0xCC1A0A12),
    cloudGlassSurfaceVariant = Color(0xE62A1420),
    cloudGlassBorder = Color(0x595C3448),
    cloudGlassBorderHighlight = Color(0x33F8BBD0),
    cloudMoonGlow = Color(0x40F06A95),
    cloudParticleTint = Color(0x40F06A95),
    revolutCyberCyan = Color(0xF8BBD0),
    revolutGoldTier = Color(0xE3B7C6),
    revolutNeonEmerald = Color(0x4ADE80),
    sunsetGlowAmber = Color(0xF8BBD0)
)
