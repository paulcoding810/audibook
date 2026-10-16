package com.paulcoding.audibook

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.google.common.util.concurrent.ListenableFuture
import com.paulcoding.audibook.core.playback.PlaybackService
import com.paulcoding.audibook.core.ui.Routes
import com.paulcoding.audibook.feature.library.LibraryScreen
import com.paulcoding.audibook.feature.reader.ReaderScreen
import com.paulcoding.audibook.feature.settings.ModelsScreen
import com.paulcoding.audibook.feature.settings.SettingsScreen
import com.paulcoding.audibook.ui.theme.AudiBookTheme

class MainActivity : ComponentActivity() {
    private var mediaController: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AudiBookTheme {
                MainNavigation()
            }
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

@Composable
fun MainNavigation(modifier: Modifier = Modifier, navController: NavHostController = rememberNavController()) {
    NavHost(
        modifier = modifier,
        navController = navController,
        startDestination = Routes.Library,
        enterTransition = {
            fadeIn(
                animationSpec = tween(220, easing = LinearEasing)
            ) + slideIntoContainer(
                animationSpec = tween(220, easing = EaseIn),
                towards = AnimatedContentTransitionScope.SlideDirection.Start
            )
        },
        exitTransition = {
            fadeOut(
                animationSpec = tween(280, easing = LinearEasing)
            ) + slideOutOfContainer(
                animationSpec = tween(280, easing = EaseOut),
                towards = AnimatedContentTransitionScope.SlideDirection.End
            )
        },
        popEnterTransition = {
            fadeIn(
                animationSpec = tween(220, easing = LinearEasing)
            )
        },
    ) {
        composable<Routes.Library> {
            LibraryScreen(
                onOpenBook = { navController.navigate(Routes.Reader(it)) { launchSingleTop = true } },
                onOpenSettings = { navController.navigate(Routes.Settings) },
            )
        }
        composable<Routes.Reader> { entry ->
            ReaderScreen(
                bookId = entry.toRoute<Routes.Reader>().bookId,
                onBack = navController::navigateUp,
                onOpenSettings = { navController.navigate(Routes.Settings) },
            )
        }
        composable<Routes.Settings> {
            SettingsScreen(
                onBack = navController::navigateUp,
                onOpenModels = { navController.navigate(Routes.Models) },
            )
        }
        composable<Routes.Models> {
            ModelsScreen(onBack = navController::navigateUp)
        }
    }
}
