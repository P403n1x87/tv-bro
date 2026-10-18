package com.phlox.tvwebbrowser

import android.app.Application
import android.content.Context

class AppContext {
    companion object {
        private var instance: Application? = null
        private var config: Config? = null

        fun init(
            app: Application,
            config: Config,
        ) {
            this.instance = app
            this.config = config
        }

        fun get(): Context = instance ?: throw IllegalStateException("AppContext is not initialized")

        fun provideConfig(): Config = config ?: throw IllegalStateException("AppContext is not initialized")
    }
}
