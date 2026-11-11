package io.androllm.core.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private class StubFeature(
    override val id: String,
    override val requires: Set<String> = emptySet(),
) : Feature {
    override val displayName: String = id
}

class ExtensionRegistryTest {
    @Test
    fun featureWithoutRequirementsIsActive() {
        val registry = ExtensionRegistry(setOf(StubFeature("chat")))
        assertTrue(registry.isActive("chat") { false })
    }

    @Test
    fun featureNeedingMissingPermissionStaysInactive() {
        val registry = ExtensionRegistry()
        registry.register(StubFeature("pretraga", setOf("internet")))
        assertFalse(registry.isActive("pretraga") { false })
        assertTrue(registry.isActive("pretraga") { it == "internet" })
    }

    @Test
    fun allRequirementsMustBeGranted() {
        val registry = ExtensionRegistry(setOf(StubFeature("f", setOf("a", "b"))))
        assertTrue(registry.active { it == "a" }.isEmpty())
        assertEquals(listOf("f"), registry.active { true }.map { it.id })
    }

    @Test
    fun duplicateIdIsRejected() {
        val registry = ExtensionRegistry()
        registry.register(StubFeature("chat"))
        assertThrows(IllegalArgumentException::class.java) { registry.register(StubFeature("chat")) }
    }

    @Test
    fun duplicateIdInInitialSetIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ExtensionRegistry(setOf(StubFeature("x"), StubFeature("x", setOf("p"))))
        }
    }

    @Test
    fun newFeatureCanBeAddedAndRemovedAtRuntime() {
        val registry = ExtensionRegistry(setOf(StubFeature("chat")))
        registry.register(StubFeature("nova-funkcija"))
        assertEquals(listOf("chat", "nova-funkcija"), registry.all().map { it.id })
        registry.unregister("nova-funkcija")
        assertNull(registry.get("nova-funkcija"))
        assertFalse(registry.isActive("nova-funkcija") { true })
    }
}
