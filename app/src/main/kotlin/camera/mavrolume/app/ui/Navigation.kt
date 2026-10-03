package camera.mavrolume.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import camera.mavrolume.app.ui.viewfinder.ViewfinderScreen
import camera.mavrolume.app.ui.gallery.GalleryScreen
import camera.mavrolume.app.ui.settings.SettingsScreen

/**
 * Navigation host — this is like react-router's <Routes>.
 *
 * NavHost defines all the screens/routes in the app.
 * Each `composable("route")` is like a <Route path="..." element={...} />.
 * navController is like useNavigate() — you call navController.navigate("route").
 */
@Composable
fun MavrolumeNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "viewfinder",
    ) {
        composable("viewfinder") {
            ViewfinderScreen(
                onNavigateToGallery = { navController.navigate("gallery") },
                onNavigateToSettings = { rawCapable ->
                    navController.navigate("settings?rawCapable=$rawCapable")
                },
            )
        }
        composable("gallery") {
            GalleryScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "settings?rawCapable={rawCapable}",
            arguments = listOf(
                navArgument("rawCapable") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { backStackEntry ->
            SettingsScreen(
                onBack = { navController.popBackStack() },
                rawCapable = backStackEntry.arguments?.getBoolean("rawCapable") ?: false,
            )
        }
    }
}
