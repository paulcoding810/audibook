package com.paulcoding.audibook

import android.app.Application
import com.paulcoding.audibook.core.di.dataModule
import com.paulcoding.audibook.core.di.flavorModule
import com.paulcoding.audibook.core.di.ttsModule
import com.paulcoding.audibook.core.network.di.networkModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        GlobalContext.startKoin {
            androidContext(this@App)
            modules(
                listOf(
                    networkModule,
                    dataModule,
                    ttsModule,
                    flavorModule,
                    viewModelModule,
                )
            )
        }
    }
}
