package com.drishyopot.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OtaManifestTest {
    private fun resource(name: String) = javaClass.classLoader!!.getResourceAsStream("ota/$name")!!.readBytes()
    private val manifest get() = resource("manifest.json")
    private val signature get() = String(resource("manifest.json.sig"))
    private val publicKey get() = String(resource("public.b64"))

    @Test fun genuineManifestVerifies() = assertTrue(OtaVerifier.verify(manifest, signature, publicKey))

    @Test fun tamperedManifestIsRejected() {
        val bad = manifest.copyOf().also { it[10] = (it[10].toInt() xor 1).toByte() }
        assertFalse(OtaVerifier.verify(bad, signature, publicKey))
    }

    @Test fun wrongSignatureIsRejected() =
        assertFalse(OtaVerifier.verify(manifest, "MEUCIQCZBXiKGC9+81CgYZ2ABjirf2iOEnxHYeusVErdwpM2TgIgVV4j6Br9Y8RUM3mWQrd8mW15eUU7RwrMHlojWLzs86d=", publicKey))

    @Test fun garbageKeyOrSignatureIsRejected() {
        assertFalse(OtaVerifier.verify(manifest, signature, "not a key"))
        assertFalse(OtaVerifier.verify(manifest, "###", publicKey))
        assertFalse(OtaVerifier.verify(manifest, signature, ""))
    }

    @Test fun manifestParses() {
        val m = OtaManifestParser.parse(manifest)
        assertEquals(7, m.sequence)
        assertEquals(1, m.engineApi)
        assertEquals("NewPipe Extractor v0.26.5", m.engineVersion)
        assertEquals("engine-7.jar", m.engine.file)
        assertEquals(setOf("potoken.html", "inject.js"), m.assets.map { it.name }.toSet())
    }

    private fun json(file: String = "engine-1.jar", asset: String = "inject.js", sha: String = "a".repeat(64), seq: Int = 1) = """
        {"schema":1,"sequence":$seq,"engineApi":1,"engine":{"version":"v","file":"$file","sha256":"$sha"},
         "assets":[{"name":"$asset","file":"x.js","sha256":"$sha"}]}
    """.trimIndent().toByteArray()

    private fun assertRejected(bytes: ByteArray) {
        try { OtaManifestParser.parse(bytes); fail("should have been rejected") } catch (_: ManifestException) { }
    }

    @Test fun pathTraversalIsRejected() = assertRejected(json(file = "../../evil.jar"))
    @Test fun unknownAssetIsRejected() = assertRejected(json(asset = "classes.dex"))
    @Test fun badChecksumIsRejected() = assertRejected(json(sha = "xyz"))
    @Test fun zeroSequenceIsRejected() = assertRejected(json(seq = 0))
    @Test fun notJsonIsRejected() = assertRejected("nope".toByteArray())
}
