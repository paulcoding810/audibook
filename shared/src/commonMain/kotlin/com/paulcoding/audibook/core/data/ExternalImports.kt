package com.paulcoding.audibook.core.data

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.io.InputStream

/** EPUBs handed to the app from outside (Open with, Share, a desktop file open); the app imports and opens each once. */
class ExternalImports {
    private val queue = Channel<() -> InputStream>(Channel.UNLIMITED)
    val requests: Flow<() -> InputStream> = queue.receiveAsFlow()

    fun submit(open: () -> InputStream) {
        queue.trySend(open)
    }
}
