package com.paulcoding.audibook

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.paulcoding.audibook.core.data.BookRepository
import com.paulcoding.audibook.core.data.ExternalImports
import com.paulcoding.audibook.core.ui.Routes
import com.paulcoding.audibook.feature.library.LibraryScreen
import com.paulcoding.audibook.feature.reader.ReaderScreen
import com.paulcoding.audibook.feature.settings.LicensesScreen
import com.paulcoding.audibook.feature.settings.ModelsScreen
import com.paulcoding.audibook.feature.settings.SettingsScreen
import com.paulcoding.audibook.ui.theme.AudiBookTheme
import kotlinx.coroutines.CancellationException
import org.koin.compose.koinInject

@Composable
fun AudiBookApp(openLastRead: Boolean) {
    AudiBookTheme {
        MainNavigation(openLastRead)
    }
}

@Composable
fun MainNavigation(
    openLastRead: Boolean,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val books: BookRepository = koinInject()
    LaunchedEffect(Unit) {
        if (openLastRead) books.lastReadBookId()?.let { navController.navigate(Routes.Reader(it)) }
    }
    ExternalImportHandler(books, onOpen = { id ->
        navController.navigate(Routes.Reader(id)) { popUpTo<Routes.Library>() }
    })

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
                onOpenLicenses = { navController.navigate(Routes.Licenses) },
            )
        }
        composable<Routes.Models> {
            ModelsScreen(onBack = navController::navigateUp)
        }
        composable<Routes.Licenses> {
            LicensesScreen(onBack = navController::navigateUp)
        }
    }
}

/** Imports EPUBs opened from other apps or the OS, then opens each in the reader. */
@Composable
private fun ExternalImportHandler(books: BookRepository, onOpen: (bookId: String) -> Unit) {
    val imports: ExternalImports = koinInject()
    var importing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        imports.requests.collect { open ->
            importing = true
            try {
                onOpen(books.import(open).book.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Couldn't open this book: ${e.message ?: "invalid EPUB"}"
            }
            importing = false
        }
    }
    if (importing) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Adding book…") },
            text = { LinearProgressIndicator(Modifier.fillMaxWidth()) },
            confirmButton = {},
        )
    }
    error?.let { message ->
        AlertDialog(
            onDismissRequest = { error = null },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } },
        )
    }
}
