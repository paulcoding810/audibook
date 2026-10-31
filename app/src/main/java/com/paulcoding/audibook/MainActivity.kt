package com.paulcoding.audibook

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.paulcoding.audibook.core.playback.PlaybackService

class MainActivity : ComponentActivity() {
    private var mediaController: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AudiBookApp(openLastRead = savedInstanceState == null)
        }
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
