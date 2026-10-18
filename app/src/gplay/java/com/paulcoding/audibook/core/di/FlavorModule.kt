package com.paulcoding.audibook.core.di

import com.paulcoding.audibook.core.update.AppUpdater
import com.paulcoding.audibook.core.update.PlayStoreUpdater
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val flavorModule = module {
    single<AppUpdater> { PlayStoreUpdater(androidContext()) }
}
