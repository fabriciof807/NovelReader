package com.novelreader.ui.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.novelreader.ui.about.AboutScreen
import com.novelreader.ui.favorites.FavoritesScreen
import com.novelreader.ui.import_novel.ImportScreen
import com.novelreader.ui.library.LibraryScreen
import com.novelreader.ui.library.LibraryViewModel
import com.novelreader.ui.reader.ReaderScreen
import com.novelreader.ui.settings.SettingsScreen

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

@Composable
fun NovelReaderNavGraph(
    navController: NavHostController,
    deepLinkBus: DeepLinkBus
) {
    val haptic = LocalHapticFeedback.current
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

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

    LaunchedEffect(currentRoute) {
        if (currentRoute != null && !currentRoute.startsWith(Routes.LIBRARY)) {
            haptic?.performHapticFeedback(HapticFeedbackType.LongPress)
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
            )
        ) {
            LibraryScreen(
                onImportClick = {
                    navController.navigate(Routes.IMPORT)
                },
                onFavoritesClick = {
                    navController.navigate(Routes.FAVORITES)
                },
                onSettingsClick = {
                    navController.navigate(Routes.SETTINGS)
                },
                onChapterClick = { novelId, chapterId ->
                    navController.navigate(Routes.reader(novelId, chapterId))
                }
            )
        }

        animatedComposable(Routes.IMPORT) {
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
            )
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

        animatedComposable(Routes.FAVORITES) {
            FavoritesScreen(
                onChapterClick = { novelId, chapterId ->
                    navController.navigate(Routes.reader(novelId, chapterId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        animatedComposable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onAboutClick = { navController.navigate(Routes.ABOUT) }
            )
        }

        animatedComposable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}

private fun NavGraphBuilder.animatedComposable(
    route: String,
    arguments: List<androidx.navigation.NamedNavArgument> = emptyList(),
    content: @Composable (androidx.navigation.NavBackStackEntry) -> Unit
) {
    composable(
        route = route,
        arguments = arguments,
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(220)) },
        exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(220)) },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(220)) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(220)) }
    ) { entry -> content(entry) }
}
