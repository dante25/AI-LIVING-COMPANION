package io.androllm.core.packs

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private fun sha(bytes: ByteArray) = Integrity.sha256Hex(bytes)

private fun manifestOf(
    type: String,
    files: Map<String, String>,
    origin: String? = null,
    format: Int = 1,
): ByteArray {
    val fileJson = files.entries.joinToString(",") { "\"${it.key}\":\"${it.value}\"" }
    val originJson = origin?.let { ",\"voiceOrigin\":\"$it\"" } ?: ""
    return ("{\"formatVersion\":$format,\"type\":\"$type\",\"name\":\"Plava\",\"author\":\"Viktor\"," +
        "\"version\":\"1.0\",\"license\":\"CC0\"$originJson,\"files\":{$fileJson}}").toByteArray()
}

private fun validOutfit(): Map<String, ByteArray> {
    val model = "vrm-podaci".toByteArray()
    return mapOf(
        "manifest.json" to manifestOf("outfit", mapOf("model.vrm" to sha(model))),
        "model.vrm" to model,
    )
}

private fun rejected(v: Verdict): Verdict.Rejected {
    assertTrue("očekivano odbijanje, a dobijeno $v", v is Verdict.Rejected)
    return v as Verdict.Rejected
}

class PackValidatorTest {
    @Test
    fun validOutfitIsAccepted() {
        assertTrue(PackValidator.validate(validOutfit()) is Verdict.Accepted)
    }

    @Test
    fun executableFileIsRejected() {
        val exe = "kod".toByteArray()
        val entries = mapOf(
            "manifest.json" to manifestOf("outfit", mapOf("model.vrm" to sha("x".toByteArray()), "run.dex" to sha(exe))),
            "model.vrm" to "x".toByteArray(),
            "run.dex" to exe,
        )
        assertTrue(rejected(PackValidator.validate(entries)).reason.contains("izvršni"))
    }

    @Test
    fun uppercaseExecutableExtensionIsRejected() {
        val exe = "kod".toByteArray()
        val entries = mapOf(
            "manifest.json" to manifestOf("outfit", mapOf("model.vrm" to sha("x".toByteArray()), "Run.SO" to sha(exe))),
            "model.vrm" to "x".toByteArray(),
            "Run.SO" to exe,
        )
        rejected(PackValidator.validate(entries))
    }

    @Test
    fun fileNotListedInManifestIsRejected() {
        rejected(PackValidator.validate(validOutfit() + ("skriveno.txt" to "tajna".toByteArray())))
    }

    @Test
    fun wrongChecksumIsRejected() {
        val entries = mapOf(
            "manifest.json" to manifestOf("outfit", mapOf("model.vrm" to "0000")),
            "model.vrm" to "vrm-podaci".toByteArray(),
        )
        rejected(PackValidator.validate(entries))
    }

    @Test
    fun missingListedFileIsRejected() {
        val entries = mapOf("manifest.json" to manifestOf("outfit", mapOf("model.vrm" to sha("x".toByteArray()))))
        rejected(PackValidator.validate(entries))
    }

    @Test
    fun pathTraversalIsRejected() {
        val model = "vrm".toByteArray()
        val entries = mapOf(
            "manifest.json" to manifestOf("outfit", mapOf("../model.vrm" to sha(model))),
            "../model.vrm" to model,
        )
        rejected(PackValidator.validate(entries))
    }

    @Test
    fun unsafePathsAreDetected() {
        assertFalse(isSafePath("/abs"))
        assertFalse(isSafePath("a/../b"))
        assertFalse(isSafePath("a\\b"))
        assertFalse(isSafePath("c:/x"))
        assertFalse(isSafePath("a//b"))
        assertTrue(isSafePath("modeli/lik.vrm"))
    }

    @Test
    fun memoryCannotBePackaged() {
        rejected(PackValidator.validate(mapOf("manifest.json" to manifestOf("memory", emptyMap()))))
        rejected(PackValidator.validate(mapOf("manifest.json" to manifestOf("conversation", emptyMap()))))
        rejected(PackValidator.validate(mapOf("manifest.json" to manifestOf("identity", emptyMap()))))
    }

    @Test
    fun backupPartsCannotHideInsidePack() {
        val data = "{}".toByteArray()
        val entries = mapOf(
            "manifest.json" to manifestOf("outfit", mapOf("model.vrm" to sha(data), "memory.json" to sha(data))),
            "model.vrm" to data,
            "memory.json" to data,
        )
        rejected(PackValidator.validate(entries))
    }

    @Test
    fun newerFormatIsRejected() {
        rejected(PackValidator.validate(mapOf("manifest.json" to manifestOf("outfit", emptyMap(), format = 2))))
    }

    @Test
    fun voiceNeedsKnownOrigin() {
        val onnx = "model".toByteArray()
        fun voice(origin: String?) = mapOf(
            "manifest.json" to manifestOf("voice", mapOf("glas.onnx" to sha(onnx)), origin = origin),
            "glas.onnx" to onnx,
        )
        rejected(PackValidator.validate(voice(null)))
        rejected(PackValidator.validate(voice("cloned")))
        assertTrue(PackValidator.validate(voice("licensed")) is Verdict.Accepted)
        assertTrue(PackValidator.validate(voice("synthetic")) is Verdict.Accepted)
        assertTrue(PackValidator.validate(voice("consented")) is Verdict.Accepted)
    }

    @Test
    fun personalityMustBeJsonObject() {
        val good = "{\"ime\":\"Alexa\"}".toByteArray()
        val bad = "nije json".toByteArray()
        val goodEntries = mapOf(
            "manifest.json" to manifestOf("personality", mapOf("personality.json" to sha(good))),
            "personality.json" to good,
        )
        val badEntries = mapOf(
            "manifest.json" to manifestOf("personality", mapOf("personality.json" to sha(bad))),
            "personality.json" to bad,
        )
        assertTrue(PackValidator.validate(goodEntries) is Verdict.Accepted)
        rejected(PackValidator.validate(badEntries))
    }

    @Test
    fun animationNeedsAnimJson() {
        val data = "{}".toByteArray()
        val ok = mapOf(
            "manifest.json" to manifestOf("animation", mapOf("mahanje.anim.json" to sha(data))),
            "mahanje.anim.json" to data,
        )
        assertTrue(PackValidator.validate(ok) is Verdict.Accepted)
        val bad = mapOf(
            "manifest.json" to manifestOf("animation", mapOf("mahanje.json" to sha(data))),
            "mahanje.json" to data,
        )
        rejected(PackValidator.validate(bad))
    }

    @Test
    fun missingManifestIsRejected() {
        rejected(PackValidator.validate(mapOf("model.vrm" to "x".toByteArray())))
    }

    @Test
    fun unknownTypeIsPreservedButInactive() {
        val data = "x".toByteArray()
        val entries = mapOf(
            "manifest.json" to manifestOf("dance", mapOf("dance.bin" to sha(data))),
            "dance.bin" to data,
        )
        val store = ContentStore()
        assertTrue(store.importEntries(entries) is Verdict.Preserved)
        val id = store.list().single().id
        assertFalse(store.list().single().active)
        assertThrows(IllegalStateException::class.java) { store.setActive(id, true) }
    }
}

class ContentStoreTest {
    @Test
    fun reimportDoesNotDuplicate() {
        val store = ContentStore()
        store.importEntries(validOutfit())
        store.importEntries(validOutfit())
        assertEquals(1, store.list().size)
    }

    @Test
    fun rejectedPackChangesNothing() {
        val store = ContentStore()
        store.importEntries(mapOf("manifest.json" to manifestOf("memory", emptyMap())))
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun packsSurviveRestartOnDisk() {
        val dir = File.createTempFile("packs", "").apply { delete(); mkdirs() }
        try {
            val first = ContentStore(dir)
            first.importEntries(validOutfit())
            first.setActive(first.list().single().id, false)

            val second = ContentStore(dir)
            val installed = second.list().single()
            assertEquals("Plava", installed.manifest.name)
            assertFalse(installed.active)
            assertEquals("vrm-podaci", String(second.readFile(installed.id, "model.vrm")!!))

            second.remove(installed.id)
            assertTrue(ContentStore(dir).list().isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }
}

class PackRoundTripTest {
    @Test
    fun builtPackCanBeReadAndImported() {
        val zip = PackBuilder.build(
            type = "outfit",
            name = "Plava haljina",
            author = "Viktor",
            version = "1.0",
            license = "CC0",
            voiceOrigin = null,
            files = mapOf("model.vrm" to "vrm-podaci".toByteArray()),
        )
        val store = ContentStore()
        assertTrue(store.importZip(zip) is Verdict.Accepted)
        assertEquals("Plava haljina", store.list().single().manifest.name)
    }

    @Test
    fun corruptZipIsRejectedWithoutCrash() {
        rejected(ContentStore().importZip("nije zip".toByteArray()))
    }

    @Test
    fun builderRefusesIdentityExport() {
        assertThrows(IllegalArgumentException::class.java) {
            PackBuilder.build("memory", "moji razgovori", "Viktor", "1", "private", null, emptyMap())
        }
    }
}
