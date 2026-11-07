package com.paulcoding.audibook

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.paulcoding.audibook.core.data.ExternalImports
import com.paulcoding.audibook.core.playback.PlaybackService
import org.koin.android.ext.android.inject
import java.io.IOException

class MainActivity : ComponentActivity() {
    private var mediaController: ListenableFuture<MediaController>? = null
    private val imports: ExternalImports by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val openedFile = savedInstanceState == null && importFrom(intent)
        setContent {
            AudiBookApp(openLastRead = savedInstanceState == null && !openedFile)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        importFrom(intent)
    }

    /** Queues the EPUB from an "Open with" or "Share" intent; returns whether there was one. */
    private fun importFrom(intent: Intent): Boolean {
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        } ?: return false
        val resolver = contentResolver
        imports.submit { resolver.openInputStream(uri) ?: throw IOException("Can't open the file") }
        return true
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        mediaController = MediaController.Builder(this, token).buildAsync()
    }

    override fun onStop() {
        mediaController?.let(MediaController::releaseFuture)
        mediaController = null
        super.onStop()
    }
}
