package com.novelreader.ui.navigation

import com.novelreader.ui.theme.LocalAppVisuals
import com.novelreader.ui.theme.SystemBarAppearance
import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.novelreader.ui.about.AboutScreen
import com.novelreader.ui.components.AppDestination
import com.novelreader.ui.favorites.FavoritesScreen
import com.novelreader.ui.import_novel.ImportScreen
import com.novelreader.ui.library.LibraryScreen
import com.novelreader.ui.library.LibraryViewModel
import com.novelreader.ui.reader.ReaderScreen
import com.novelreader.ui.settings.SettingsScreen
import com.novelreader.ui.theme.AppMotion
import com.novelreader.ui.theme.rememberReducedMotion

object Routes {
    const val LIBRARY = "library"
    const val LIBRARY_WITH_SELECTION = "library?${LibraryViewModel.ARG_SELECTED_NOVEL_ID}={${LibraryViewModel.ARG_SELECTED_NOVEL_ID}}&${LibraryViewModel.ARG_SHOW_FAILED}={${LibraryViewModel.ARG_SHOW_FAILED}}"
    const val IMPORT = "import"
    const val READER = "reader/{novelId}/{chapterId}?searchQuery={searchQuery}"
    const val FAVORITES = "favorites"
    const val SETTINGS = "settings"
    const val ABOUT = "about"

    fun reader(novelId: Long, chapterId: Long, searchQuery: String? = null): String {
        return if (searchQuery != null) {
            "reader/$novelId/$chapterId?searchQuery=${Uri.encode(searchQuery)}"
        } else {
            "reader/$novelId/$chapterId"
        }
    }

    fun libraryWithSelectedNovel(novelId: Long): String {
        return "library?${LibraryViewModel.ARG_SELECTED_NOVEL_ID}=$novelId&${LibraryViewModel.ARG_SHOW_FAILED}=false"
    }

    fun libraryWithFailedChapters(novelId: Long): String {
        return "library?${LibraryViewModel.ARG_SELECTED_NOVEL_ID}=$novelId&${LibraryViewModel.ARG_SHOW_FAILED}=true"
    }
}

private val topLevelRoutePrefixes = listOf(Routes.LIBRARY, Routes.FAVORITES, Routes.SETTINGS)

fun topLevelRoute(destination: AppDestination): String = when (destination) {
    AppDestination.LIBRARY, AppDestination.COLLECTIONS -> Routes.LIBRARY
    AppDestination.FAVORITES -> Routes.FAVORITES
    AppDestination.SETTINGS -> Routes.SETTINGS
}

const val LIBRARY_TAB_REQUEST_KEY = "libraryTabRequest"

fun libraryTabRequest(destination: AppDestination): Int? = when (destination) {
    AppDestination.LIBRARY -> 0
    AppDestination.COLLECTIONS -> 1
    AppDestination.FAVORITES, AppDestination.SETTINGS -> null
}

fun isTopLevelRoute(route: String?): Boolean =
    route != null && topLevelRoutePrefixes.any { route.startsWith(it) }

fun destinationFor(
    route: String?,
    selectedTab: Int,
    hasSelectedNovel: Boolean
): AppDestination? = when {
    route == null -> null
    route.startsWith(Routes.LIBRARY) ->
        if (hasSelectedNovel) null
        else if (selectedTab == 1) AppDestination.COLLECTIONS
        else AppDestination.LIBRARY
    route.startsWith(Routes.FAVORITES) -> AppDestination.FAVORITES
    route.startsWith(Routes.SETTINGS) -> AppDestination.SETTINGS
    else -> null
}

@Composable
fun NovelReaderNavGraph(
    navController: NavHostController,
    deepLinkBus: DeepLinkBus
) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val reducedMotion = rememberReducedMotion()

    LaunchedEffect(Unit) {
        deepLinkBus.events.collect { action ->
            when (action) {
                is DeepLinkAction.ViewNovel -> {
                    navController.navigate(Routes.libraryWithSelectedNovel(action.novelId)) {
                        launchSingleTop = true
                    }
                }
                is DeepLinkAction.OpenCloudflareSolver -> {
                    navController.navigate(Routes.IMPORT) {
                        launchSingleTop = true
                    }
                }
                is DeepLinkAction.OpenFailedChapters -> {
                    navController.navigate(Routes.libraryWithFailedChapters(action.novelId)) {
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.LIBRARY
    ) {
        animatedComposable(
            route = Routes.LIBRARY_WITH_SELECTION,
            arguments = listOf(
                navArgument(LibraryViewModel.ARG_SELECTED_NOVEL_ID) {
                    type = NavType.LongType
                    defaultValue = -1L
                },
                navArgument(LibraryViewModel.ARG_SHOW_FAILED) {
                    type = NavType.BoolType
                    defaultValue = false
                }
            ),
            reducedMotion = reducedMotion,
            appliesAppBars = false
        ) {
            LibraryScreen(
                onImportClick = {
                    navController.navigate(Routes.IMPORT)
                },
                onNavigate = { destination -> navController.navigateToTopLevel(destination) },
                onChapterClick = { novelId, chapterId ->
                    navController.navigate(Routes.reader(novelId, chapterId))
                }
            )
        }

        animatedComposable(Routes.IMPORT, reducedMotion = reducedMotion) {
            ImportScreen(
                onBack = { navController.popBackStack() },
                onImportComplete = {
                    navController.popBackStack()
                }
            )
        }

        animatedComposable(Routes.READER,
            arguments = listOf(
                navArgument("novelId") { type = NavType.LongType },
                navArgument("chapterId") { type = NavType.LongType },
                navArgument("searchQuery") { type = NavType.StringType; defaultValue = "" }
            ),
            reducedMotion = reducedMotion,
            appliesAppBars = false
        ) { backStackEntry ->
            val novelId = backStackEntry.arguments?.getLong("novelId", -1L)?.takeIf { it != -1L } ?: return@animatedComposable
            val chapterId = backStackEntry.arguments?.getLong("chapterId", -1L)?.takeIf { it != -1L } ?: return@animatedComposable
            val searchQuery = backStackEntry.arguments?.getString("searchQuery")
                ?.takeIf { it.isNotBlank() }
            ReaderScreen(
                novelId = novelId,
                chapterId = chapterId,
                initialSearchQuery = searchQuery,
                onBack = { navController.popBackStack() },
                onChapterChange = { newChapterId, query ->
                    navController.navigate(Routes.reader(novelId, newChapterId, query))
                }
            )
        }

        animatedComposable(Routes.FAVORITES, reducedMotion = reducedMotion) {
            FavoritesScreen(
                onChapterClick = { novelId, chapterId ->
                    navController.navigate(Routes.reader(novelId, chapterId))
                },
                onBack = { navController.popBackStack() },
                onNavigate = { destination -> navController.navigateToTopLevel(destination) }
            )
        }

        animatedComposable(Routes.SETTINGS, reducedMotion = reducedMotion) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onAboutClick = { navController.navigate(Routes.ABOUT) },
                onNavigate = { destination -> navController.navigateToTopLevel(destination) }
            )
        }

        animatedComposable(Routes.ABOUT, reducedMotion = reducedMotion) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}

private fun NavHostController.navigateToTopLevel(destination: AppDestination) {
    val requestedTab = libraryTabRequest(destination)
    if (requestedTab != null) {
        runCatching {
            getBackStackEntry(graph.findStartDestination().id)
                .savedStateHandle[LIBRARY_TAB_REQUEST_KEY] = requestedTab
        }
    }
    navigate(topLevelRoute(destination)) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTopLevelTransition(): Boolean =
    isTopLevelRoute(initialState.destination.route) && isTopLevelRoute(targetState.destination.route)

private fun NavGraphBuilder.animatedComposable(
    route: String,
    arguments: List<androidx.navigation.NamedNavArgument> = emptyList(),
    reducedMotion: Boolean = false,
    appliesAppBars: Boolean = true,
    content: @Composable (androidx.navigation.NavBackStackEntry) -> Unit
) {
    val fadeMillis = AppMotion.fadeMillis(reducedMotion)
    val slideMillis = AppMotion.slideMillis(reducedMotion)

    composable(
        route = route,
        arguments = arguments,
        enterTransition = {
            if (isTopLevelTransition()) fadeIn(tween(fadeMillis))
            else slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(slideMillis))
        },
        exitTransition = {
            if (isTopLevelTransition()) fadeOut(tween(fadeMillis))
            else slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(slideMillis))
        },
        popEnterTransition = {
            if (isTopLevelTransition()) fadeIn(tween(fadeMillis))
            else slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(slideMillis))
        },
        popExitTransition = {
            if (isTopLevelTransition()) fadeOut(tween(fadeMillis))
            else slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(slideMillis))
        }
    ) { entry ->
        if (appliesAppBars) SystemBarAppearance(LocalAppVisuals.current.dark)
        content(entry)
    }
}
