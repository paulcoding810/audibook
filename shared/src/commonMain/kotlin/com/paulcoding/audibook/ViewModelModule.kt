package com.paulcoding.audibook

import com.paulcoding.audibook.feature.library.LibraryViewModel
import com.paulcoding.audibook.feature.reader.ReaderViewModel
import com.paulcoding.audibook.feature.settings.ModelsViewModel
import com.paulcoding.audibook.feature.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModelOf(::LibraryViewModel)
    viewModel { params -> ReaderViewModel(params.get(), get(), get()) }
    viewModelOf(::SettingsViewModel)
    viewModelOf(::ModelsViewModel)
}
