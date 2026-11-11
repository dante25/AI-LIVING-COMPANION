package alexa.features

import alexa.permissions.Capability
import alexa.permissions.Settings
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private class StubFeature(override val id: String, override val requires: Set<Capability>) : Feature

class FeatureRegistryTest {
    @Test
    fun featureWithoutRequirementsIsActive() {
        val registry = FeatureRegistry(Settings())
        registry.register(StubFeature("chat", emptySet()))
        assertTrue(registry.isActive("chat"))
    }

    @Test
    fun featureNeedingDisabledCapabilityStaysInactive() {
        val registry = FeatureRegistry(Settings())
        registry.register(StubFeature("pretraga", setOf(Capability.WEB_SEARCH)))
        assertFalse(registry.isActive("pretraga"))
    }

    @Test
    fun featureActivatesWhenCapabilityTurnedOn() {
        val settings = Settings()
        val registry = FeatureRegistry(settings)
        registry.register(StubFeature("pretraga", setOf(Capability.WEB_SEARCH)))
        settings.set(Capability.WEB_SEARCH, true)
        assertTrue(registry.isActive("pretraga"))
    }

    @Test
    fun duplicateIdIsRejected() {
        val registry = FeatureRegistry(Settings())
        registry.register(StubFeature("chat", emptySet()))
        assertFailsWith<IllegalArgumentException> { registry.register(StubFeature("chat", emptySet())) }
    }

    @Test
    fun newFeatureCanBeAddedWithoutTouchingOthers() {
        val registry = FeatureRegistry(Settings())
        registry.register(StubFeature("chat", emptySet()))
        registry.register(StubFeature("nova-funkcija", emptySet()))
        assertTrue(registry.isActive("chat"))
        assertTrue(registry.isActive("nova-funkcija"))
    }
}
