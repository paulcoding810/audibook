package com.paulcoding.audibook

import com.paulcoding.audibook.feature.home.HomesViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModelOf(::HomesViewModel)
}