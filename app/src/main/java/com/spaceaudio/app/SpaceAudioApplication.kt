package com.spaceaudio.app

import android.app.Application
import com.spaceaudio.app.di.AppContainer

class SpaceAudioApplication : Application() {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}
