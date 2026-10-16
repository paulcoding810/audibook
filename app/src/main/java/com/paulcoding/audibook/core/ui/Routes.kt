package com.paulcoding.audibook.core.ui

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

object Routes {
    @Serializable
    @Immutable
    object Library

    @Serializable
    @Immutable
    data class Reader(val bookId: String)

    @Serializable
    @Immutable
    object Settings

    @Serializable
    @Immutable
    object Models
}
