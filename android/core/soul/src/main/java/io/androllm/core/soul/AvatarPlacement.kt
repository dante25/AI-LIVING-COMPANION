package io.androllm.core.soul

/** Pravougaonik na ekranu u pikselima. */
data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun intersects(o: Rect): Boolean =
        left < o.right && right > o.left && top < o.bottom && bottom > o.top

    fun movedTo(x: Float, y: Float): Rect = Rect(x, y, x + width, y + height)
}

/** Naredbe koje korisnik daje liku na ekranu. */
enum class AvatarCommand {
    MOVE_AWAY, MOVE_LEFT, MOVE_RIGHT, MOVE_UP, MOVE_DOWN, COME_BACK,
    /** "Sjedni": lik hoda do najbližeg sjedišta i sjeda. */
    SIT_DOWN,
    /** "Ustani": lik ustaje na istom mjestu. */
    STAND_UP,
    /** "Nasloni se": lik hoda do najbližeg ruba ekrana i naslanja se. */
    LEAN,
}

/**
 * Prepoznaje naredbe poput "Alexa, miči se" ili "Alexa, miči guzicu" iz poruke.
 * Vraća null kad poruka nije naredba za lik, pa ide dalje kao obični razgovor.
 */
fun parseAvatarCommand(text: String): AvatarCommand? {
    val t = text.lowercase()
        .replace('č', 'c').replace('ć', 'c').replace('š', 's').replace('ž', 'z').replace('đ', 'd')
    fun has(vararg words: String) = words.any { t.contains(it) }
    // Cijele riječi, da "stani" ne prepozna "stanica".
    fun hasWord(vararg words: String) = words.any {
        Regex("(^|[^a-z])" + Regex.escape(it) + "($|[^a-z])").containsMatchIn(t)
    }
    return when {
        hasWord("sjedni", "sjedi", "sedni") -> AvatarCommand.SIT_DOWN
        hasWord("ustani", "digni se", "stani") -> AvatarCommand.STAND_UP
        hasWord("nasloni se", "osloni se") -> AvatarCommand.LEAN
        has("vrati se", "dodji", "dodji ovamo", "vrati se na") -> AvatarCommand.COME_BACK
        has("mici guz", "mici se", "skloni se", "makni se", "pomjeri se", "odmakni se", "smetas", "smeta mi") ->
            AvatarCommand.MOVE_AWAY
        has("idi lijevo", "pomjeri ulijevo", "ulijevo") -> AvatarCommand.MOVE_LEFT
        has("idi desno", "pomjeri udesno", "udesno") -> AvatarCommand.MOVE_RIGHT
        has("idi gore", "popni se", "pomjeri gore") -> AvatarCommand.MOVE_UP
        has("idi dolje", "spusti se", "pomjeri dolje") -> AvatarCommand.MOVE_DOWN
        else -> null
    }
}

/**
 * Bira novo mjesto za lik tako da ne zaklanja tastaturu ni polje za unos.
 * Čista logika bez Androida: ekran, dimenzije lika i zone koje treba izbjegavati dolaze kao parametri.
 */
object AvatarPlacement {

    /** Dio ekrana na kojem može stajati: uglovi, sredine strana i sredina. */
    private fun slots(screen: Rect, w: Float, h: Float, margin: Float): List<Rect> {
        val xs = listOf(screen.left + margin, screen.centerX - w / 2f, screen.right - w - margin)
        val ys = listOf(screen.top + margin, screen.centerY - h / 2f, screen.bottom - h - margin)
        return ys.flatMap { y -> xs.map { x -> Rect(x, y, x + w, y + h) } }
    }

    /**
     * "Miči se": prvo mjesto koje ne preklapa zone za izbjegavanje, a nije na istom mjestu kao sada.
     * Bira ono najdalje od trenutnog, da se vidi da se pomjerila.
     */
    fun moveAway(screen: Rect, current: Rect, avoid: List<Rect>, margin: Float = 16f): Rect {
        val free = slots(screen, current.width, current.height, margin)
            .filter { slot -> avoid.none { it.intersects(slot) } }
            .filter { !it.intersects(current) }
        val best = free.maxByOrNull { dist(it, current) }
        if (best != null) return best
        // Nema slobodnog mjesta: ostani na vrhu ekrana, daleko od tastature.
        val topSlots = slots(screen, current.width, current.height, margin).take(3)
        return topSlots.maxByOrNull { dist(it, current) } ?: current
    }

    /** Pomjeranje u smjeru, ograničeno na ekran, uz izbjegavanje zabranjenih zona. */
    fun nudge(screen: Rect, current: Rect, command: AvatarCommand, avoid: List<Rect>, step: Float = 240f): Rect {
        val (dx, dy) = when (command) {
            AvatarCommand.MOVE_LEFT -> -step to 0f
            AvatarCommand.MOVE_RIGHT -> step to 0f
            AvatarCommand.MOVE_UP -> 0f to -step
            AvatarCommand.MOVE_DOWN -> 0f to step
            else -> 0f to 0f
        }
        val x = (current.left + dx).coerceIn(screen.left, screen.right - current.width)
        val y = (current.top + dy).coerceIn(screen.top, screen.bottom - current.height)
        val target = current.movedTo(x, y)
        return if (avoid.any { it.intersects(target) }) current else target
    }

    private fun dist(a: Rect, b: Rect): Float {
        val dx = a.centerX - b.centerX
        val dy = a.centerY - b.centerY
        return dx * dx + dy * dy
    }
}
