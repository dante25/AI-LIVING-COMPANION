package alexa.soul

private val KEYWORDS: List<Pair<String, List<String>>> = listOf(
    "insult" to listOf("glup", "idiot", "mrzim", "dosadna"),
    "praise" to listOf("bravo", "super", "hvala", "odlična"),
    "joke" to listOf("haha", "šala", "vic", "smij"),
    "greeting" to listOf("bok", "hej", "zdravo", "dobar dan"),
)

/** Gruba klasifikacija poruke, da bi se znalo koji događaj primijeniti na dušu. */
fun classify(text: String): String {
    val lowered = text.lowercase()
    for ((kind, words) in KEYWORDS) {
        if (words.any { lowered.contains(it) }) return kind
    }
    return "chat"
}
