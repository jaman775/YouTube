package com.drishyopot.app.engine

import dalvik.system.DexClassLoader

/**
 * Loads an engine update. The engine's own packages are taken from the update first, so a newer
 * extractor wins over the older copy compiled into the app; the shared `engine.api` package always
 * comes from the app, which is what lets the two sides talk to each other.
 */
class ChildFirstDexClassLoader(dexPath: String, parent: ClassLoader) : DexClassLoader(dexPath, null, null, parent) {
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        synchronized(this) { // getClassLoadingLock() is not part of Android's public API
            findLoadedClass(name)?.let { return it }
            if (CHILD_FIRST.any { name.startsWith(it) }) {
                try {
                    val c = findClass(name)
                    if (resolve) resolveClass(c)
                    return c
                } catch (_: ClassNotFoundException) {
                    // fall through to the app's copy
                }
            }
            return super.loadClass(name, resolve)
        }
    }

    companion object {
        val CHILD_FIRST = listOf(
            "org.schabi.newpipe.", "org.mozilla.", "org.jsoup.", "com.grack.",
            "com.google.protobuf.", "com.drishyopot.engine.impl.",
        )
    }
}
