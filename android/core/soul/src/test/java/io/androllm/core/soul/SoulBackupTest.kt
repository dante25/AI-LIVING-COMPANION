package io.androllm.core.soul

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

private class FakeStore : SoulStore {
    var saved: SoulSnapshot? = null
    override fun load() = saved
    override fun save(snapshot: SoulSnapshot) { saved = snapshot }
}

class SoulBackupTest {
    @Test
    fun exportThenImportRestoresState() = runBlocking {
        val source = SoulEngine(FakeStore(), clock = { 1_000L })
        source.restore(SoulSnapshot(SoulState(mood = 11.0, energy = 22.0, boredom = 33.0, anger = 44.0, attachment = 55.0), 777L, "tajna poruka"))
        val json = SoulBackupSection(source).export()
        assertFalse(json.contains("tajna poruka"))

        val store = FakeStore()
        val target = SoulEngine(store, clock = { 5_000L })
        SoulBackupSection(target).import(json)
        assertEquals(SoulState(mood = 11.0, energy = 22.0, boredom = 33.0, anger = 44.0, attachment = 55.0), target.snapshot())
        assertEquals(777L, store.saved?.lastContactMillis)
    }

    @Test
    fun importClampsOutOfRangeValues() = runBlocking {
        val engine = SoulEngine(FakeStore(), clock = { 1L })
        SoulBackupSection(engine).import("{\"mood\":500,\"anger\":-5}")
        assertEquals(100.0, engine.snapshot().mood, 0.0)
        assertEquals(0.0, engine.snapshot().anger, 0.0)
    }
}
