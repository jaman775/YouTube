package com.drishyopot.app

import android.app.Application
import com.drishyopot.app.browser.BrowserState
import com.drishyopot.app.cast.CastController
import com.drishyopot.app.diagnostics.DiagnosticLog
import com.drishyopot.app.download.DownloadCenter
import com.drishyopot.app.player.PlaybackHub
import com.drishyopot.app.settings.SettingsStore

class DrishyopotApp : Application() {
    lateinit var settings: SettingsStore
        private set

    /** Created on first use so cold start stays fast. */
    val hub: PlaybackHub by lazy { PlaybackHub(this, settings) }

    /** Created on first use; restores the saved download list. */
    val downloads: DownloadCenter by lazy { DownloadCenter(this, settings) }

    val cast: CastController by lazy { CastController(this, hub) }

    /** Tabs and their WebViews live for the whole process so they survive screen changes. */
    val browser: BrowserState by lazy { BrowserState(this, settings) }

    override fun onCreate() {
        super.onCreate()
        DiagnosticLog.init(this)
        settings = SettingsStore(this)
    }
}
