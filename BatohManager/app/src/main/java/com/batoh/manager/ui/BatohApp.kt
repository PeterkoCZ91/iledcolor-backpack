package com.batoh.manager.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.batoh.core.common.UiThemeMode
import com.batoh.core.ui.navigation.Screen
import com.batoh.feature.convert.ConvertRoute
import com.batoh.feature.detail.DetailRoute
import com.batoh.feature.home.HomeRoute
import com.batoh.feature.library.LibraryRoute
import com.batoh.feature.search.CategoriesRoute
import com.batoh.feature.search.SearchRoute

@Composable
fun BatohApp(
    incomingGif: com.batoh.manager.IncomingGifState = com.batoh.manager.IncomingGifState(),
    onIncomingNavigationHandled: (Long, String?) -> Unit = { _, _ -> },
    onDismissIncomingMessage: () -> Unit = {},
    themeMode: UiThemeMode,
    gridColumns: Int,
    appLanguage: String,
    onAppLanguageChanged: (String) -> Unit,
    onThemeModeChanged: (UiThemeMode) -> Unit,
    onGridColumnsChanged: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        val navController = rememberNavController()
        // MainActivity is singleTop: a deep link to the running instance arrives via onNewIntent,
        // which NavController does not observe on its own (it only reads the launch intent).
        val activity = androidx.compose.ui.platform.LocalContext.current as? androidx.activity.ComponentActivity
        androidx.compose.runtime.DisposableEffect(activity, navController) {
            val listener = androidx.core.util.Consumer<android.content.Intent> { newIntent ->
                // Only VIEW intents can carry a deep link; share intents are handled by MainActivity.
                if (newIntent.action == android.content.Intent.ACTION_VIEW) navController.handleDeepLink(newIntent)
            }
            activity?.addOnNewIntentListener(listener)
            onDispose { activity?.removeOnNewIntentListener(listener) }
        }
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route.orEmpty()
        val isOnSearchRoute = currentRoute.startsWith("search")
        LaunchedEffect(incomingGif.event, incomingGif.copiedUri) {
            if (incomingGif.event != 0L) {
                val route = incomingGif.copiedUri?.let { Screen.Detail.createRoute(it) } ?: Screen.Library.route
                navController.navigate(route) { launchSingleTop = true }
                onIncomingNavigationHandled(incomingGif.event, incomingGif.copiedUri)
            }
        }

        val bottomItems = listOf(
            Triple(Screen.Home.route, stringResource(com.batoh.manager.R.string.navigation_home), Icons.Default.Home),
            Triple("search", stringResource(com.batoh.manager.R.string.navigation_search), Icons.Default.Search),
            Triple(Screen.Categories.route, stringResource(com.batoh.manager.R.string.navigation_categories), Icons.Default.List),
            Triple(Screen.Library.route, stringResource(com.batoh.manager.R.string.navigation_library), Icons.Default.Star)
        )

        Scaffold(
            snackbarHost = {
                incomingGif.messageRes?.let { messageRes ->
                    androidx.compose.material3.Snackbar(action = {
                        androidx.compose.material3.TextButton(onClick = onDismissIncomingMessage) {
                            androidx.compose.material3.Text(stringResource(com.batoh.manager.R.string.common_close))
                        }
                    }) { androidx.compose.material3.Text(stringResource(messageRes)) }
                }
            },
            bottomBar = {
                val showBottomBar = currentRoute.startsWith(Screen.Home.route) ||
                    currentRoute.startsWith("search") ||
                    currentRoute.startsWith(Screen.Categories.route) ||
                    currentRoute.startsWith(Screen.Library.route)

                if (showBottomBar) {
                    NavigationBar {
                        bottomItems.forEach { (route, label, icon) ->
                            val selected = when (route) {
                                "search" -> isOnSearchRoute
                                else -> currentRoute.startsWith(route)
                            }
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (selected) return@NavigationBarItem

                                    if (route == Screen.Home.route) {
                                        val poppedToHome = navController.popBackStack(
                                            route = Screen.Home.route,
                                            inclusive = false
                                        )
                                        if (!poppedToHome) {
                                            navController.navigate(Screen.Home.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    inclusive = false
                                                }
                                                launchSingleTop = true
                                            }
                                        }
                                        return@NavigationBarItem
                                    }

                                    navController.navigate(route) {
                                        // Keep only root + current top-level destination for predictable behavior.
                                        popUpTo(Screen.Home.route) { inclusive = false }
                                        launchSingleTop = true
                                    }
                                },
                                icon = { Icon(imageVector = icon, contentDescription = label) },
                                label = { androidx.compose.material3.Text(label) }
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                composable(Screen.Home.route) {
            HomeRoute(
                themeMode = themeMode,
                gridColumns = gridColumns,
                appLanguage = appLanguage,
                onAppLanguageChanged = onAppLanguageChanged,
                onThemeModeChanged = onThemeModeChanged,
                        onGridColumnsChanged = onGridColumnsChanged,
                        onNavigateToSearch = { navController.navigate(Screen.Search.createRoute()) },
                        onNavigateToLibrary = { navController.navigate(Screen.Library.route) },
                        onNavigateToDetail = { url -> navController.navigate(Screen.Detail.createRoute(url)) },
                        onNavigateToConvert = { navController.navigate(Screen.Convert.route) },
                        onNavigateToBackpack = { navController.navigate(Screen.Backpack.createRoute()) },
                        onNavigateToCategories = { navController.navigate(Screen.Categories.route) },
                        onNavigateToTextBanner = { navController.navigate(Screen.TextBanner.route) }
                    )
                }
                composable(Screen.TextBanner.route) {
                    com.batoh.feature.backpack.TextBannerScreen(
                        onBack = { navController.popBackStack() },
                        onOpenBackpack = { navController.navigate(Screen.Backpack.createRoute()) }
                    )
                }
                composable(Screen.Convert.route) {
                    ConvertRoute(
                        onBack = { navController.popBackStack() },
                        onNavigateToLibrary = {
                            navController.navigate(Screen.Library.route) {
                                popUpTo(Screen.Convert.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    )
                }
                composable(
                    route = Screen.Backpack.route,
                    arguments = listOf(
                        navArgument(Screen.Backpack.ARG_AUTO_TEST) {
                            type = NavType.BoolType
                            defaultValue = false
                        },
                        navArgument(Screen.Backpack.ARG_GIF_URI) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    ),
                    deepLinks = listOf(
                        androidx.navigation.navDeepLink { uriPattern = "gifpack://backpack?auto_test={auto_test}" }
                    )
                ) { backStackEntry ->
                    // The auto-upload test hook is honoured only in debuggable builds (smoke harness).
                    val debuggable = (androidx.compose.ui.platform.LocalContext.current.applicationInfo.flags and
                        android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
                    val autoTest = debuggable && (backStackEntry.arguments?.getBoolean(Screen.Backpack.ARG_AUTO_TEST) ?: false)
                    com.batoh.feature.backpack.BackpackScreen(
                        onBack = { navController.popBackStack() },
                        autoTest = autoTest
                    )
                }
                composable(
                    route = Screen.Search.route,
                    arguments = listOf(
                        navArgument(Screen.Search.ARG_INITIAL_QUERY) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) {
                    SearchRoute(
                        gridColumns = gridColumns,
                        onNavigateToDetail = { gifUrl, mp4Url ->
                            navController.navigate(Screen.Detail.createRoute(gifUrl, mp4Url))
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.Library.route) {
                    LibraryRoute(
                        gridColumns = gridColumns,
                        onSendToBackpack = { uri -> navController.navigate(Screen.Backpack.uploadRoute(uri)) },
                        onEditGif = { uri -> navController.navigate(Screen.GifEditor.createRoute(uri)) },
                        onChainGifs = { uris -> navController.navigate(Screen.GifChain.createRoute(uris)) },
                        onNavigateToDetail = { url ->
                            navController.navigate(Screen.Detail.createRoute(url))
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(
                    route = Screen.GifChain.route,
                    arguments = listOf(navArgument(Screen.GifChain.ARG_GIF_URIS) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    })
                ) { entry ->
                    com.batoh.feature.backpack.GifChainScreen(
                        gifUris = entry.arguments?.getString(Screen.GifChain.ARG_GIF_URIS),
                        onBack = { navController.popBackStack() },
                        onSendToBackpack = { uri -> navController.navigate(Screen.Backpack.uploadRoute(uri)) },
                        onSequenceStaged = { navController.navigate(Screen.Backpack.createRoute()) }
                    )
                }
                composable(Screen.Categories.route) {
                    CategoriesRoute(
                        gridColumns = gridColumns,
                        onCategoryClick = { categoryName ->
                            navController.navigate(Screen.Search.createRoute(initialQuery = categoryName))
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.GifEditor.route,
                    arguments = listOf(navArgument(Screen.GifEditor.ARG_GIF_URI) { type = NavType.StringType })
                ) { entry ->
                    com.batoh.feature.backpack.GifEditorScreen(
                        gifUri = requireNotNull(entry.arguments?.getString(Screen.GifEditor.ARG_GIF_URI)),
                        onBack = { navController.popBackStack() },
                        onSendToBackpack = { uri -> navController.navigate(Screen.Backpack.uploadRoute(uri)) }
                    )
                }
                composable(
                    route = Screen.Detail.route + "?${Screen.Detail.ARG_MP4_URL}={${Screen.Detail.ARG_MP4_URL}}",
                    arguments = listOf(
                        navArgument(Screen.Detail.ARG_MP4_URL) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) {
                    DetailRoute(onBack = { navController.popBackStack() },
                        onSendToBackpack = { uri -> navController.navigate(Screen.Backpack.uploadRoute(uri)) },
                        onEditGif = { uri -> navController.navigate(Screen.GifEditor.createRoute(uri)) })
                }
            }
        }
    }
}
