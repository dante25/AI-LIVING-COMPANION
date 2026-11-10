package alexa.packs

import alexa.model.Integrity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

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

class PackValidatorTest {
    @Test
    fun validOutfitIsAccepted() {
        assertIs<Verdict.Accepted>(PackValidator.validate(validOutfit()))
    }

    @Test
    fun executableFileIsRejected() {
        val exe = "kod".toByteArray()
        val entries = mapOf(
            "manifest.json" to manifestOf("outfit", mapOf("model.vrm" to sha("x".toByteArray()), "run.dex" to sha(exe))),
            "model.vrm" to "x".toByteArray(),
            "run.dex" to exe,
        )
        val verdict = PackValidator.validate(entries)
        assertIs<Verdict.Rejected>(verdict)
        assertTrue(verdict.reason.contains("izvršni"))
    }

    @Test
    fun fileNotListedInManifestIsRejected() {
        val entries = validOutfit() + ("skriveno.txt" to "tajna".toByteArray())
        assertIs<Verdict.Rejected>(PackValidator.validate(entries))
    }

    @Test
    fun wrongChecksumIsRejected() {
        val entries = mapOf(
            "manifest.json" to manifestOf("outfit", mapOf("model.vrm" to "0000")),
            "model.vrm" to "vrm-podaci".toByteArray(),
        )
        assertIs<Verdict.Rejected>(PackValidator.validate(entries))
    }

    @Test
    fun pathTraversalIsRejected() {
        val model = "vrm".toByteArray()
        val entries = mapOf(
            "manifest.json" to manifestOf("outfit", mapOf("../model.vrm" to sha(model))),
            "../model.vrm" to model,
        )
        assertIs<Verdict.Rejected>(PackValidator.validate(entries))
    }

    @Test
    fun memoryCannotBePackaged() {
        val entries = mapOf("manifest.json" to manifestOf("memory", emptyMap()))
        assertIs<Verdict.Rejected>(PackValidator.validate(entries))
    }

    @Test
    fun newerFormatIsRejected() {
        val entries = mapOf("manifest.json" to manifestOf("outfit", emptyMap(), format = 2))
        assertIs<Verdict.Rejected>(PackValidator.validate(entries))
    }

    @Test
    fun voiceNeedsKnownOrigin() {
        val onnx = "model".toByteArray()
        fun voice(origin: String?) = mapOf(
            "manifest.json" to manifestOf("voice", mapOf("glas.onnx" to sha(onnx)), origin = origin),
            "glas.onnx" to onnx,
        )
        assertIs<Verdict.Rejected>(PackValidator.validate(voice(null)))
        assertIs<Verdict.Rejected>(PackValidator.validate(voice("cloned")))
        assertIs<Verdict.Accepted>(PackValidator.validate(voice("licensed")))
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
        assertIs<Verdict.Accepted>(PackValidator.validate(goodEntries))
        assertIs<Verdict.Rejected>(PackValidator.validate(badEntries))
    }

    @Test
    fun unknownTypeIsPreservedButInactive() {
        val data = "x".toByteArray()
        val entries = mapOf(
            "manifest.json" to manifestOf("dance", mapOf("dance.bin" to sha(data))),
            "dance.bin" to data,
        )
        val store = ContentStore()
        assertIs<Verdict.Preserved>(store.importEntries(entries))
        val id = store.list().single().id
        assertFalse(store.list().single().active)
        assertFailsWith<IllegalStateException> { store.setActive(id, true) }
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
        assertIs<Verdict.Accepted>(store.importZip(zip))
        assertEquals("Plava haljina", store.list().single().manifest.name)
    }

    @Test
    fun corruptZipIsRejectedWithoutCrash() {
        val verdict = ContentStore().importZip("nije zip".toByteArray())
        assertIs<Verdict.Rejected>(verdict)
    }

    @Test
    fun builderRefusesIdentityExport() {
        assertFailsWith<IllegalArgumentException> {
            PackBuilder.build("memory", "moji razgovori", "Viktor", "1", "private", null, emptyMap())
        }
    }
}

class MiniJsonTest {
    @Test
    fun parsesNestedValuesAndEscapes() {
        val obj = MiniJson.parse("{\"a\":\"x\\\"y\\n\",\"b\":[1,2.5,true,null],\"c\":{\"d\":\"\\u0041\"}}")
        val map = obj as Map<*, *>
        assertEquals("x\"y\n", map["a"])
        assertEquals(listOf(1.0, 2.5, true, null), map["b"])
        assertEquals("A", (map["c"] as Map<*, *>)["d"])
    }

    @Test
    fun rejectsTrailingGarbage() {
        assertFailsWith<IllegalArgumentException> { MiniJson.parse("{} x") }
    }
}
