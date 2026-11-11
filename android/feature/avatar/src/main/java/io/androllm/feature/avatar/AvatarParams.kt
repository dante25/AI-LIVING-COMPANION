package io.androllm.feature.avatar

import androidx.compose.runtime.Immutable

/**
 * Parametri lika po standardnim Live2D imenima, da se kasnije može zamijeniti
 * pravim modelom iza istog sučelja ([AvatarRenderer]).
 *
 * Uglovi su u stepenima (otprilike -30..30 za glavu, -10..10 za tijelo), otvorenost 0..1,
 * obrve i pogled -1..1.
 */
@Immutable
data class AvatarParams(
    val angleX: Float = 0f,
    val angleY: Float = 0f,
    val angleZ: Float = 0f,
    /** Otvorenost lijevog oka: 0 zatvoreno, 1 otvoreno. */
    val eyeLOpen: Float = 1f,
    val eyeROpen: Float = 1f,
    /** Pogled: -1 lijevo/dolje, 1 desno/gore. */
    val eyeBallX: Float = 0f,
    val eyeBallY: Float = 0f,
    /** Obrva: -1 spuštena (ljuta), 0 neutralno, 1 podignuta. */
    val browLY: Float = 0f,
    val browRY: Float = 0f,
    /** Otvorenost usta 0..1. */
    val mouthOpenY: Float = 0f,
    /** Oblik usta: -1 namrgođeno, 0 ravno, 1 osmijeh. */
    val mouthForm: Float = 0.2f,
    val bodyAngleX: Float = 0f,
    /** Disanje 0..1. */
    val breath: Float = 0f,
    /** Rumenilo 0..1. */
    val cheek: Float = 0.15f,
)

/** Izraz lica koji bira [AvatarController] iz stanja duše. */
enum class AvatarExpression { NEUTRAL, HAPPY, PLAYFUL, ANGRY, BORED, SLEEPY, CONFUSED, SURPRISED }
