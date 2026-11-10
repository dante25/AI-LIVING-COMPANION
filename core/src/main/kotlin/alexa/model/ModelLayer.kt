package alexa.model

import java.security.MessageDigest

/**
 * Model sloj. LLM je zamjenjiv inference engine, a ne Alexa.
 *
 * Pravila (iz plana i razgovora):
 *  - dva nivoa: slabi (brz, lokalni) i jaki (oblak ili lokalni);
 *  - lokalno/oblak bira korisnik ručno u postavkama;
 *  - eskalacija sa slabog na jaki samo kad odgovor ne prođe provjeru;
 *  - dnevni budžet tokena: pri 80% upozorenje, pri 100% oblak se ne koristi;
 *  - brisanje modela briše samo fajlove, ne memoriju;
 *  - preuzeti model se provjerava SHA-256 sažetkom prije instalacije.
 */
enum class Tier { WEAK, STRONG }

enum class Location { LOCAL, CLOUD }

data class Request(val system: String, val messages: List<String>, val maxTokens: Int = 256)

data class Reply(val text: String, val intent: String?, val tokensUsed: Int)

class ModelException(message: String) : RuntimeException(message)

interface LlmAdapter {
    val tier: Tier
    val location: Location

    /** Baca ModelException ako model ne može odgovoriti. */
    fun generate(request: Request): Reply
}

/** Namjere koje Alexa smije izvršiti na avataru (faza 11). Ništa drugo ne prolazi. */
val ALLOWED_INTENTS: Set<String> = setOf("pogledaj", "klimni", "pokaži", "okreni_se", "priđi", "sjedni")

fun isValidReply(reply: Reply): Boolean {
    if (reply.text.isBlank()) return false
    val intent = reply.intent ?: return true
    return intent in ALLOWED_INTENTS
}

data class ModelSettings(val weakLocation: Location, val strongLocation: Location)

class BudgetGuard(private val dailyLimitTokens: Long) {
    enum class Status { OK, WARNING, EXHAUSTED }

    private var used = 0L

    fun record(tokens: Int) {
        used += tokens
    }

    fun used(): Long = used

    fun status(): Status = when {
        used >= dailyLimitTokens -> Status.EXHAUSTED
        used >= (dailyLimitTokens * 0.8).toLong() -> Status.WARNING
        else -> Status.OK
    }

    fun reset() {
        used = 0
    }
}

class ModelRouter(
    private val adapters: List<LlmAdapter>,
    private val settings: ModelSettings,
    private val budget: BudgetGuard,
) {
    /** Odgovara počevši od slabog nivoa; eskalira na jaki samo ako je potrebno. */
    fun answer(request: Request): Reply {
        val weak = call(Tier.WEAK, request)
        if (weak != null && isValidReply(weak)) return weak

        val strong = call(Tier.STRONG, request)
        if (strong != null && isValidReply(strong)) return strong

        throw ModelException("nijedan dostupan model nije dao ispravan odgovor")
    }

    private fun call(tier: Tier, request: Request): Reply? {
        val location = resolveLocation(tier) ?: return null
        val adapter = adapters.firstOrNull { it.tier == tier && it.location == location } ?: return null
        return try {
            adapter.generate(request).also {
                if (location == Location.CLOUD) budget.record(it.tokensUsed)
            }
        } catch (e: ModelException) {
            null
        }
    }

    private fun resolveLocation(tier: Tier): Location? {
        val wanted = if (tier == Tier.WEAK) settings.weakLocation else settings.strongLocation
        // kad je budžet potrošen, oblak se preskače i koristi se lokalni model
        if (wanted == Location.CLOUD && budget.status() == BudgetGuard.Status.EXHAUSTED) {
            return Location.LOCAL
        }
        return wanted
    }
}

interface FileStore {
    fun delete(path: String)
}

data class InstalledModel(val id: String, val tier: Tier, val path: String, val sizeBytes: Long)

class ModelRegistry(private val files: FileStore) {
    private val models = linkedMapOf<String, InstalledModel>()

    fun list(): List<InstalledModel> = models.values.toList()

    /** Instalira model tek kad se sažetak preuzetih bajtova poklapa s očekivanim. */
    fun installVerified(model: InstalledModel, bytes: ByteArray, expectedSha256: String) {
        require(Integrity.sha256Hex(bytes) == expectedSha256.lowercase()) {
            "Sažetak se ne poklapa, model nije instaliran"
        }
        models[model.id] = model
    }

    /** Briše fajl modela i zapis o njemu. Memorija i razgovori se ne diraju. */
    fun delete(id: String) {
        val model = models.remove(id) ?: throw IllegalArgumentException("Nepoznat model: $id")
        files.delete(model.path)
    }
}

object Integrity {
    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
