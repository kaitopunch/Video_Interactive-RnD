package com.pion.psremote.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.pion.psremote.feature.demo.DemoRoute
import com.pion.psremote.feature.home.HomeRoute

/**
 * The single NavHost. Every Route's callbacks become `navController` calls here and nowhere else, so
 * neither feature names the other (LLM.md §7). Back from Home leaves the app; back from a demo returns
 * to Home, exactly as the demo's own Exit does.
 */
@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Route.Home, modifier = modifier) {
        composable<Route.Home> { entry ->
            HomeRoute(
                onOpenDemo = { demoId ->
                    // Only from a settled Home: a second tap during the transition would stack a
                    // second demo — two players — behind the first.
                    if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) {
                        navController.navigate(Route.Demo(demoId))
                    }
                },
            )
        }
        composable<Route.Demo> { entry ->
            DemoRoute(
                demoId = entry.toRoute<Route.Demo>().demoId,
                // Pops this demo only, so a second Exit raised before the transition ends is a no-op
                // rather than a pop of Home onto an empty host.
                onExit = { navController.popBackStack<Route.Demo>(inclusive = true) },
            )
        }
    }
}
