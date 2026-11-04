package com.drishyopot.app.engine

import android.app.Application
import android.os.Build
import com.drishyopot.app.diagnostics.DiagnosticLog
import com.drishyopot.app.settings.SettingsStore
import com.drishyopot.engine.api.Engine
import com.drishyopot.engine.api.EngineException
import com.drishyopot.engine.impl.EngineImpl
import java.io.File

/** Where the running engine came from. */
enum class EngineSource { BUNDLED, UPDATE }

data class EngineInfo(val source: EngineSource, val version: String, val sequence: Int)

/**
 * Owns the one active [Engine]. It is the copy compiled into the app unless a verified update has been
 * downloaded, in which case that update is loaded instead; if an update misbehaves it is marked bad and
 * the bundled engine takes over again.
 */
object EngineHost {
    private class Loaded(val engine: Engine, val info: EngineInfo)

    private lateinit var app: Application
    private lateinit var store: EngineStore
    private lateinit var http: HostHttpBridge
    lateinit var poToken: PoTokenService
        private set
    lateinit var updater: EngineUpdater
        private set

    private val lock = Any()
    @Volatile private var loaded: Loaded? = null

    fun init(application: Application, settings: SettingsStore) {
        app = application
        store = EngineStore(application)
        http = HostHttpBridge(settings)
        poToken = PoTokenService(application, settings)
        updater = EngineUpdater(application, settings, store)
    }

    fun engine(): Engine = current().engine

    fun info(): EngineInfo = current().info

    /** Path of an updated page script, or null to use the one in the APK. */
    fun updatedAsset(name: String): File? {
        if (loaded?.info?.source != EngineSource.UPDATE) return null
        return store.active()?.asset(name)?.takeIf { it.isFile }
    }

    /** Forget the loaded engine so the next call picks up a newly installed update. */
    fun reload() {
        synchronized(lock) { loaded = null }
        com.drishyopot.app.player.yt.StreamProfiles.clear()
    }

    private fun current(): Loaded = loaded ?: synchronized(lock) { loaded ?: load().also { loaded = it } }

    private fun load(): Loaded {
        val update = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) store.active() else null
        if (update != null) {
            try {
                val engine = loadUpdate(update)
                return Loaded(engine, EngineInfo(EngineSource.UPDATE, engine.version(), update.sequence))
            } catch (t: Throwable) {
                DiagnosticLog.log("Engine", "Update #${update.sequence} failed to load; using the bundled engine", t)
                store.markBad(update.sequence)
            }
        }
        return loadBundled()
    }

    private fun loadBundled(): Loaded {
        val engine = EngineImpl()
        engine.init(http, poToken)
        return Loaded(engine, EngineInfo(EngineSource.BUNDLED, engine.version(), 0))
    }

    private fun loadUpdate(update: EngineStore.Active): Engine {
        val engine = instantiate(update.jar)
        engine.init(http, poToken)
        check(engine.version().isNotBlank()) { "Engine reported no version" }
        return engine
    }

    /** Loads the engine class from [jar] without initialising it (used to vet an update before installing). */
    fun instantiate(jar: File): Engine {
        val loader = ChildFirstDexClassLoader(jar.absolutePath, app.classLoader)
        return loader.loadClass("com.drishyopot.engine.impl.EngineImpl").getDeclaredConstructor().newInstance() as Engine
    }

    /**
     * Runs [block] on the engine. When the engine itself is broken (the page structure changed, or an
     * update cannot run) it first tries to fetch a newer update, then falls back to the bundled engine.
     */
    fun <T> call(block: (Engine) -> T): T {
        try {
            return block(engine())
        } catch (e: EngineException) {
            if (e.kind != EngineException.OTHER && e.kind != EngineException.BOT_CHECK) throw e
            if (updater.updateForHealing()) return block(engine())
            throw e
        } catch (e: LinkageError) {
            return recoverFromBrokenUpdate(e, block)
        } catch (e: ReflectiveOperationException) {
            return recoverFromBrokenUpdate(e, block)
        }
    }

    private fun <T> recoverFromBrokenUpdate(error: Throwable, block: (Engine) -> T): T {
        val info = loaded?.info
        DiagnosticLog.log("Engine", "Engine call failed with ${error.javaClass.simpleName}", error)
        if (info?.source == EngineSource.UPDATE) {
            store.markBad(info.sequence)
            reload()
            return block(engine())
        }
        throw error
    }
}
