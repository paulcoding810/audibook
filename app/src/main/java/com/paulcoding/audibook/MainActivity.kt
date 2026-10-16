package com.paulcoding.audibook

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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.paulcoding.audibook.core.ui.Routes
import com.paulcoding.audibook.feature.home.navigation.homeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Scaffold { innerPadding ->
                MainNavigation(modifier = Modifier.padding(innerPadding))
            }
        }
    }
}

@Composable
fun MainNavigation(modifier: Modifier = Modifier, navController: NavHostController = rememberNavController()) {
    NavHost(
        modifier = modifier,
        navController = navController,
        startDestination = Routes.Home,
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
        homeScreen()
    }
}