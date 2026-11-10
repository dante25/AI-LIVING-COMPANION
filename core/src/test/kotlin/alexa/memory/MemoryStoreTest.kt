package alexa.memory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MemoryStoreTest {
    private fun storeWithMessages(): Pair<InMemoryMemoryStore, List<String>> {
        val store = InMemoryMemoryStore()
        val ids = listOf("m-a", "m-b")
        store.addMessage(Message("m-a", "c1", "user", "Zovem se Viktor", 1000))
        store.addMessage(Message("m-b", "c1", "user", "Volim kafu", 2000))
        return store to ids
    }

    @Test
    fun suggestionIsNotActiveUntilConfirmed() {
        val (store, src) = storeWithMessages()
        val s = store.suggest("Viktor voli kafu", src, 3000)
        assertEquals(MemoryStatus.SUGGESTED, s.status)
        assertFalse(store.activeMemories().any { it.id == s.id })

        store.confirm(s.id)
        assertTrue(store.activeMemories().any { it.id == s.id })
    }

    @Test
    fun confirmOnlyAppliesToSuggestions() {
        val (store, src) = storeWithMessages()
        val s = store.suggest("tvrdnja", src, 3000)
        store.confirm(s.id)
        assertFailsWith<IllegalStateException> { store.confirm(s.id) }
    }

    @Test
    fun correctionReplacesOldClaimButKeepsTrail() {
        val (store, src) = storeWithMessages()
        val old = store.suggest("Viktor ima 30 godina", src, 3000)
        store.confirm(old.id)

        val fixed = store.correct(old.id, "Viktor ima 33 godine", src, 4000)

        assertEquals(MemoryStatus.CORRECTED, store.memory(old.id)!!.status)
        assertEquals(old.id, fixed.supersedes)
        assertEquals(MemoryStatus.CONFIRMED, fixed.status)
        val active = store.activeMemories().map { it.claim }
        assertTrue("Viktor ima 33 godine" in active)
        assertFalse("Viktor ima 30 godina" in active)
    }

    @Test
    fun correctionCannotBeAppliedTwiceToSameClaim() {
        val (store, src) = storeWithMessages()
        val old = store.suggest("stara", src, 3000)
        store.correct(old.id, "nova", src, 4000)
        assertFailsWith<IllegalStateException> { store.correct(old.id, "treća", src, 5000) }
    }

    @Test
    fun memoryMustHaveExistingSources() {
        val (store, _) = storeWithMessages()
        assertFailsWith<IllegalArgumentException> { store.suggest("bez izvora", emptyList(), 1) }
        assertFailsWith<IllegalArgumentException> { store.suggest("nepoznat", listOf("x"), 1) }
    }

    @Test
    fun deletingMemoryKeepsMessages() {
        val (store, src) = storeWithMessages()
        val s = store.suggest("tvrdnja", src, 3000)
        store.confirm(s.id)
        store.delete(s.id)

        assertNull(store.memory(s.id))
        assertEquals(2, store.messages("c1").size)
    }

    @Test
    fun searchFindsConfirmedOnlyAndIgnoresCase() {
        val (store, src) = storeWithMessages()
        val confirmed = store.suggest("Viktor voli KAFU", src, 3000)
        store.confirm(confirmed.id)
        store.suggest("Viktor voli čaj", src, 3100) // prijedlog, ne ulazi u pretragu

        val found = store.search("kafu").map { it.id }
        assertEquals(listOf(confirmed.id), found)
        assertTrue(store.search("čaj").isEmpty())
    }

    @Test
    fun duplicateMessageIsNotAddedTwice() {
        val store = InMemoryMemoryStore()
        val m = Message("m-1", "c1", "user", "bok", 1000)
        store.addMessage(m)
        store.addMessage(m)
        assertEquals(1, store.messages("c1").size)
    }

    @Test
    fun messagesComeBackInTimeOrder() {
        val store = InMemoryMemoryStore()
        store.addMessage(Message("m-2", "c1", "alexa", "drugo", 2000))
        store.addMessage(Message("m-1", "c1", "user", "prvo", 1000))
        assertEquals(listOf("prvo", "drugo"), store.messages("c1").map { it.text })
    }
}
