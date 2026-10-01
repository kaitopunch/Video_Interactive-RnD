package com.pion.psremote

import android.app.Application
import com.pion.psremote.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/** The one assembly point: `startKoin` is called here and nowhere else (LLM.md §6). */
class PsRemoteApp : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@PsRemoteApp)
            modules(appModule)
        }
    }
}
