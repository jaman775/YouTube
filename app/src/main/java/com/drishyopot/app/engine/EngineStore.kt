package com.drishyopot.app.engine

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** On-disk record of the downloaded engine: files, which version is active, versions known to be bad. */
class EngineStore(context: Context) {
    class Active(val sequence: Int, val version: String, val dir: File) {
        val jar: File get() = File(dir, "engine.jar")
        fun asset(name: String): File = File(File(dir, "assets"), name)
    }

    private val root = File(context.filesDir, "engine").also { it.mkdirs() }
    private val currentFile = File(root, "current.json")
    private val badFile = File(root, "bad.json")
    private val stateFile = File(root, "state.json")

    fun finalDir(sequence: Int) = File(root, "seq-$sequence")
    fun stagingDir(sequence: Int) = File(root, "stage-$sequence")

    @Synchronized
    fun active(): Active? {
        val o = readObject(currentFile) ?: return null
        val seq = o.optInt("sequence")
        val dir = finalDir(seq)
        if (seq <= 0 || isBad(seq) || !File(dir, "engine.jar").isFile) return null
        return Active(seq, o.optString("version"), dir)
    }

    fun installedSequence(): Int = active()?.sequence ?: 0

    @Synchronized
    fun activate(sequence: Int, version: String) {
        writeAtomically(currentFile, JSONObject().put("sequence", sequence).put("version", version).toString())
    }

    @Synchronized
    fun markBad(sequence: Int) {
        val bad = badSequences() + sequence
        writeAtomically(badFile, JSONArray(bad.toList()).toString())
        // Drop the pointer so the bundled engine is used until a newer one arrives.
        if (readObject(currentFile)?.optInt("sequence") == sequence) currentFile.delete()
    }

    fun isBad(sequence: Int) = sequence in badSequences()

    private fun badSequences(): Set<Int> = try {
        val arr = JSONArray(if (badFile.exists()) badFile.readText() else "[]")
        (0 until arr.length()).map { arr.getInt(it) }.toSet()
    } catch (_: Exception) { emptySet() }

    /** Removes engines other than the ones in [keep]. */
    fun prune(keep: Set<Int>) {
        root.listFiles()?.forEach { f ->
            val seq = f.name.removePrefix("seq-").removePrefix("stage-").toIntOrNull()
            if (f.isDirectory && seq != null && (f.name.startsWith("stage-") || seq !in keep)) f.deleteRecursivelyReadOnly()
        }
    }

    var lastCheckAt: Long
        get() = readObject(stateFile)?.optLong("lastCheckAt") ?: 0L
        set(v) = saveState { it.put("lastCheckAt", v) }

    var lastMessage: String
        get() = readObject(stateFile)?.optString("lastMessage").orEmpty()
        set(v) = saveState { it.put("lastMessage", v) }

    @Synchronized
    private fun saveState(change: (JSONObject) -> Unit) {
        val o = readObject(stateFile) ?: JSONObject()
        change(o)
        writeAtomically(stateFile, o.toString())
    }

    private fun readObject(f: File): JSONObject? = try {
        if (f.exists()) JSONObject(f.readText()) else null
    } catch (_: Exception) { null }

    private fun writeAtomically(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
    }

    private fun File.deleteRecursivelyReadOnly() {
        // Engine jars are read-only (a requirement for loading dex on recent Android).
        walkBottomUp().forEach { it.setWritable(true); it.delete() }
    }
}
