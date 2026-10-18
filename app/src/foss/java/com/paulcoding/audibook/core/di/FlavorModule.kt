package com.paulcoding.audibook.core.di

import com.paulcoding.audibook.core.update.AppUpdater
import com.paulcoding.audibook.core.update.GitHubAppUpdater
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val flavorModule = module {
    single<AppUpdater> { GitHubAppUpdater(androidContext(), get()) }
}
