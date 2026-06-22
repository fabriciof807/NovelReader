package com.novelreader.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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
    const val LIBRARY_WITH_SELECTION = "library?${LibraryViewModel.ARG_SELECTED_NOVEL_ID}={${LibraryViewModel.ARG_SELECTED_NOVEL_ID}}"
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
        return "library?${LibraryViewModel.ARG_SELECTED_NOVEL_ID}=$novelId"
    }
}

@Composable
fun NovelReaderNavGraph(
    navController: NavHostController,
    deepLinkBus: DeepLinkBus
) {
    LaunchedEffect(Unit) {
        deepLinkBus.events.collect { action ->
            when (action) {
                is DeepLinkAction.ViewNovel -> {
                    navController.navigate(Routes.libraryWithSelectedNovel(action.novelId)) {
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
        composable(
            route = Routes.LIBRARY_WITH_SELECTION,
            arguments = listOf(
                navArgument(LibraryViewModel.ARG_SELECTED_NOVEL_ID) {
                    type = NavType.LongType
                    defaultValue = -1L
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

        composable(Routes.IMPORT) {
            ImportScreen(
                onBack = { navController.popBackStack() },
                onImportComplete = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.READER,
            arguments = listOf(
                navArgument("novelId") { type = NavType.LongType },
                navArgument("chapterId") { type = NavType.LongType },
                navArgument("searchQuery") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val novelId = backStackEntry.arguments?.getLong("novelId", -1L)?.takeIf { it != -1L } ?: return@composable
            val chapterId = backStackEntry.arguments?.getLong("chapterId", -1L)?.takeIf { it != -1L } ?: return@composable
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

        composable(Routes.FAVORITES) {
            FavoritesScreen(
                onChapterClick = { novelId, chapterId ->
                    navController.navigate(Routes.reader(novelId, chapterId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onAboutClick = { navController.navigate(Routes.ABOUT) }
            )
        }

        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}
