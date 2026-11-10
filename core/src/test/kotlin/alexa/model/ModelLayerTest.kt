package alexa.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeAdapter(
    override val tier: Tier,
    override val location: Location,
    private val behavior: (Request) -> Reply,
) : LlmAdapter {
    var calls = 0
        private set

    override fun generate(request: Request): Reply {
        calls++
        return behavior(request)
    }
}

private class FakeFiles : FileStore {
    val deleted = mutableListOf<String>()
    override fun delete(path: String) {
        deleted.add(path)
    }
}

private val request = Request(system = "Ti si Alexa", messages = listOf("bok"))
private val goodReply = Reply(text = "Evo me", intent = null, tokensUsed = 20)
private val badReply = Reply(text = "", intent = null, tokensUsed = 5)

class ReplyValidationTest {
    @Test
    fun rejectsBlankText() = assertFalse(isValidReply(Reply("  ", null, 1)))

    @Test
    fun rejectsIntentOutsideAllowlist() = assertFalse(isValidReply(Reply("ok", "skoči", 1)))

    @Test
    fun acceptsAllowedIntent() = assertTrue(isValidReply(Reply("ok", "klimni", 1)))
}

class ModelRouterTest {
    @Test
    fun weakReplyUsedWhenValid() {
        val weak = FakeAdapter(Tier.WEAK, Location.LOCAL) { goodReply }
        val strong = FakeAdapter(Tier.STRONG, Location.LOCAL) { goodReply }
        val router = ModelRouter(listOf(weak, strong), ModelSettings(Location.LOCAL, Location.LOCAL), BudgetGuard(1000))
        assertEquals("Evo me", router.answer(request).text)
        assertEquals(0, strong.calls)
    }

    @Test
    fun escalatesToStrongWhenWeakReplyInvalid() {
        val weak = FakeAdapter(Tier.WEAK, Location.LOCAL) { badReply }
        val strong = FakeAdapter(Tier.STRONG, Location.LOCAL) { goodReply }
        val router = ModelRouter(listOf(weak, strong), ModelSettings(Location.LOCAL, Location.LOCAL), BudgetGuard(1000))
        router.answer(request)
        assertEquals(1, weak.calls)
        assertEquals(1, strong.calls)
    }

    @Test
    fun escalatesWhenWeakThrows() {
        val weak = FakeAdapter(Tier.WEAK, Location.LOCAL) { throw ModelException("nema memorije") }
        val strong = FakeAdapter(Tier.STRONG, Location.LOCAL) { goodReply }
        val router = ModelRouter(listOf(weak, strong), ModelSettings(Location.LOCAL, Location.LOCAL), BudgetGuard(1000))
        assertEquals("Evo me", router.answer(request).text)
    }

    @Test
    fun failsWhenNoModelGivesValidReply() {
        val weak = FakeAdapter(Tier.WEAK, Location.LOCAL) { badReply }
        val strong = FakeAdapter(Tier.STRONG, Location.LOCAL) { badReply }
        val router = ModelRouter(listOf(weak, strong), ModelSettings(Location.LOCAL, Location.LOCAL), BudgetGuard(1000))
        assertFailsWith<ModelException> { router.answer(request) }
    }

    @Test
    fun manuallyChosenCloudIsUsedAndTokensRecorded() {
        val weak = FakeAdapter(Tier.WEAK, Location.LOCAL) { badReply }
        val strongCloud = FakeAdapter(Tier.STRONG, Location.CLOUD) { goodReply }
        val budget = BudgetGuard(1000)
        val router = ModelRouter(listOf(weak, strongCloud), ModelSettings(Location.LOCAL, Location.CLOUD), budget)
        router.answer(request)
        assertEquals(1, strongCloud.calls)
        assertEquals(20L, budget.used())
    }

    @Test
    fun exhaustedBudgetFallsBackToLocal() {
        val weak = FakeAdapter(Tier.WEAK, Location.LOCAL) { badReply }
        val strongCloud = FakeAdapter(Tier.STRONG, Location.CLOUD) { goodReply }
        val strongLocal = FakeAdapter(Tier.STRONG, Location.LOCAL) { goodReply }
        val budget = BudgetGuard(100)
        budget.record(100)
        val router = ModelRouter(
            listOf(weak, strongCloud, strongLocal),
            ModelSettings(Location.LOCAL, Location.CLOUD),
            budget,
        )
        router.answer(request)
        assertEquals(0, strongCloud.calls)
        assertEquals(1, strongLocal.calls)
    }
}

class BudgetGuardTest {
    @Test
    fun warnsAtEightyPercentAndStopsAtLimit() {
        val budget = BudgetGuard(100)
        budget.record(79)
        assertEquals(BudgetGuard.Status.OK, budget.status())
        budget.record(1)
        assertEquals(BudgetGuard.Status.WARNING, budget.status())
        budget.record(20)
        assertEquals(BudgetGuard.Status.EXHAUSTED, budget.status())
    }
}

class ModelRegistryTest {
    private val model = InstalledModel("weak-1b", Tier.WEAK, "/models/weak-1b.gguf", 1024)

    @Test
    fun deleteRemovesFileAndEntryOnly() {
        val files = FakeFiles()
        val registry = ModelRegistry(files)
        registry.installVerified(model, "abc".toByteArray(), Integrity.sha256Hex("abc".toByteArray()))
        registry.delete("weak-1b")
        assertEquals(listOf("/models/weak-1b.gguf"), files.deleted)
        assertTrue(registry.list().isEmpty())
    }

    @Test
    fun wrongChecksumIsNotInstalled() {
        val registry = ModelRegistry(FakeFiles())
        assertFailsWith<IllegalArgumentException> {
            registry.installVerified(model, "abc".toByteArray(), "0000")
        }
        assertTrue(registry.list().isEmpty())
    }

    @Test
    fun sha256MatchesKnownValue() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Integrity.sha256Hex("abc".toByteArray()),
        )
    }
}
