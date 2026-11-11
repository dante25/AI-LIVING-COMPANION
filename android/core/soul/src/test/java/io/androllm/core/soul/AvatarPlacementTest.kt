package io.androllm.core.soul

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarPlacementTest {
    private val screen = Rect(0f, 0f, 1080f, 2400f)
    private val keyboard = Rect(0f, 1500f, 1080f, 2400f)
    private val inputField = Rect(0f, 1380f, 1080f, 1500f)
    private val avoid = listOf(keyboard, inputField)

    @Test fun recognizesMoveAwayInBosnian() {
        assertEquals(AvatarCommand.MOVE_AWAY, parseAvatarCommand("Alexa, miči guzicu"))
        assertEquals(AvatarCommand.MOVE_AWAY, parseAvatarCommand("makni se molim te"))
        assertEquals(AvatarCommand.MOVE_AWAY, parseAvatarCommand("smetaš mi"))
        assertEquals(AvatarCommand.COME_BACK, parseAvatarCommand("vrati se"))
        assertEquals(AvatarCommand.MOVE_LEFT, parseAvatarCommand("idi lijevo"))
    }

    @Test fun ordinaryChatIsNotACommand() {
        assertNull(parseAvatarCommand("kako si danas"))
        assertNull(parseAvatarCommand("koliko je sati"))
    }

    @Test fun moveAwayNeverCoversKeyboardOrInput() {
        // lik sjedi na tastaturi
        val current = Rect(400f, 1600f, 700f, 2000f)
        val target = AvatarPlacement.moveAway(screen, current, avoid)
        assertTrue(avoid.none { it.intersects(target) })
        assertFalse(target.intersects(current))
    }

    @Test fun moveAwayActuallyMoves() {
        val current = Rect(16f, 16f, 316f, 416f)
        val target = AvatarPlacement.moveAway(screen, current, avoid)
        assertNotEquals(current, target)
    }

    @Test fun staysInsideScreen() {
        val current = Rect(400f, 1600f, 700f, 2000f)
        val target = AvatarPlacement.moveAway(screen, current, avoid)
        assertTrue(target.left >= 0f && target.top >= 0f && target.right <= 1080f && target.bottom <= 2400f)
    }

    @Test fun nudgeRefusesToStepOntoKeyboard() {
        val current = Rect(400f, 1000f, 700f, 1380f)
        // pomjeranje dolje bi ušlo u polje za unos
        val result = AvatarPlacement.nudge(screen, current, AvatarCommand.MOVE_DOWN, avoid, step = 240f)
        assertEquals(current, result)
    }

    @Test fun nudgeMovesSideways() {
        val current = Rect(400f, 200f, 700f, 600f)
        val result = AvatarPlacement.nudge(screen, current, AvatarCommand.MOVE_LEFT, avoid, step = 240f)
        assertEquals(160f, result.left, 0.001f)
    }

    @Test fun recognizesSitStandAndLeanCommands() {
        assertEquals(AvatarCommand.SIT_DOWN, parseAvatarCommand("Alexa, sjedni"))
        assertEquals(AvatarCommand.SIT_DOWN, parseAvatarCommand("sjedi"))
        assertEquals(AvatarCommand.SIT_DOWN, parseAvatarCommand("sjedni malo"))
        assertEquals(AvatarCommand.STAND_UP, parseAvatarCommand("ustani"))
        assertEquals(AvatarCommand.STAND_UP, parseAvatarCommand("digni se"))
        assertEquals(AvatarCommand.STAND_UP, parseAvatarCommand("Alexa, stani!"))
        assertEquals(AvatarCommand.LEAN, parseAvatarCommand("nasloni se"))
        assertEquals(AvatarCommand.LEAN, parseAvatarCommand("osloni se na zid"))
    }

    @Test fun newCommandsMatchWholeWordsOnly() {
        assertNull(parseAvatarCommand("idemo na autobusku stanicu"))
        assertNull(parseAvatarCommand("kako si"))
    }
}
