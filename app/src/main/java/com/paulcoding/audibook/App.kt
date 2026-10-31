package com.paulcoding.audibook

import android.app.Application
import com.paulcoding.audibook.core.di.androidModule
import com.paulcoding.audibook.core.di.flavorModule
import com.paulcoding.audibook.core.di.sharedModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        GlobalContext.startKoin {
            androidContext(this@App)
            modules(sharedModules + androidModule + flavorModule)
        }
    }
}
