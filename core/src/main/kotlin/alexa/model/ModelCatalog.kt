package alexa.model

/**
 * Katalog modela za preuzimanje. Sažetak (SHA-256) je null dok ne bude provjeren
 * na stvarnom fajlu; aplikacija tada upozorava korisnika prije instalacije.
 *
 * Napomena: ovi modeli su u GGUF formatu. Da bi se pokrenuli na telefonu, treba
 * runtime (llama.cpp), koji je još u planu. Preuzimanje radi i prije toga.
 */
data class CatalogEntry(
    val id: String,
    val name: String,
    val tier: Tier,
    val sizeMb: Int,
    val url: String,
    val fileName: String,
    val sha256: String?,
    val note: String,
)

object ModelCatalog {
    val entries: List<CatalogEntry> = listOf(
        CatalogEntry(
            id = "llama-3.2-1b-q4",
            name = "Lagani: Llama 3.2 1B (Q4)",
            tier = Tier.WEAK,
            sizeMb = 800,
            url = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            fileName = "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            sha256 = null,
            note = "Brz, za kratke odgovore. Bosanski slabije.",
        ),
        CatalogEntry(
            id = "qwen2.5-1.5b-q4",
            name = "Srednji: Qwen 2.5 1.5B (Q4)",
            tier = Tier.WEAK,
            sizeMb = 1100,
            url = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            sha256 = null,
            note = "Dobar balans brzine i kvaliteta na telefonu.",
        ),
        CatalogEntry(
            id = "llama-3.2-3b-q4",
            name = "Jači: Llama 3.2 3B (Q4)",
            tier = Tier.STRONG,
            sizeMb = 2000,
            url = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            fileName = "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            sha256 = null,
            note = "Bolji razgovor, sporije na telefonu.",
        ),
    )
}
