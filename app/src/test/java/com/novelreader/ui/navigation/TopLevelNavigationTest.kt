package com.novelreader.ui.navigation

import com.google.common.truth.Truth.assertThat
import com.novelreader.ui.components.AppDestination
import org.junit.Test

class TopLevelNavigationTest {

    @Test
    fun `topLevelRoute maps each destination`() {
        assertThat(topLevelRoute(AppDestination.LIBRARY)).isEqualTo(Routes.LIBRARY)
        assertThat(topLevelRoute(AppDestination.COLLECTIONS)).isEqualTo(Routes.LIBRARY)
        assertThat(topLevelRoute(AppDestination.FAVORITES)).isEqualTo(Routes.FAVORITES)
        assertThat(topLevelRoute(AppDestination.SETTINGS)).isEqualTo(Routes.SETTINGS)
    }

    @Test
    fun `libraryTabRequest maps LIBRARY to 0 and COLLECTIONS to 1 and the others to null`() {
        assertThat(libraryTabRequest(AppDestination.LIBRARY)).isEqualTo(0)
        assertThat(libraryTabRequest(AppDestination.COLLECTIONS)).isEqualTo(1)
        assertThat(libraryTabRequest(AppDestination.FAVORITES)).isNull()
        assertThat(libraryTabRequest(AppDestination.SETTINGS)).isNull()
    }

    @Test
    fun `isTopLevelRoute is true for library, favorites and settings and false for reader, import and about`() {
        assertThat(isTopLevelRoute(Routes.LIBRARY)).isTrue()
        assertThat(isTopLevelRoute(Routes.LIBRARY_WITH_SELECTION)).isTrue()
        assertThat(isTopLevelRoute(Routes.FAVORITES)).isTrue()
        assertThat(isTopLevelRoute(Routes.SETTINGS)).isTrue()
        assertThat(isTopLevelRoute(Routes.READER)).isFalse()
        assertThat(isTopLevelRoute(Routes.IMPORT)).isFalse()
        assertThat(isTopLevelRoute(Routes.ABOUT)).isFalse()
        assertThat(isTopLevelRoute(null)).isFalse()
    }

    @Test
    fun `destinationFor library tab 0 without novel is LIBRARY`() {
        assertThat(destinationFor(Routes.LIBRARY, selectedTab = 0, hasSelectedNovel = false))
            .isEqualTo(AppDestination.LIBRARY)
    }

    @Test
    fun `destinationFor library tab 1 without novel is COLLECTIONS`() {
        assertThat(destinationFor(Routes.LIBRARY, selectedTab = 1, hasSelectedNovel = false))
            .isEqualTo(AppDestination.COLLECTIONS)
    }

    @Test
    fun `destinationFor library with a selected novel is null`() {
        assertThat(destinationFor(Routes.LIBRARY, selectedTab = 1, hasSelectedNovel = true)).isNull()
        assertThat(destinationFor(Routes.LIBRARY, selectedTab = 2, hasSelectedNovel = true)).isNull()
    }

    @Test
    fun `destinationFor favorites route is FAVORITES`() {
        assertThat(destinationFor(Routes.FAVORITES, selectedTab = 0, hasSelectedNovel = false))
            .isEqualTo(AppDestination.FAVORITES)
    }

    @Test
    fun `destinationFor settings route is SETTINGS`() {
        assertThat(destinationFor(Routes.SETTINGS, selectedTab = 0, hasSelectedNovel = false))
            .isEqualTo(AppDestination.SETTINGS)
    }

    @Test
    fun `destinationFor a full screen route is null`() {
        assertThat(destinationFor(Routes.READER, selectedTab = 0, hasSelectedNovel = false)).isNull()
        assertThat(destinationFor(Routes.IMPORT, selectedTab = 0, hasSelectedNovel = false)).isNull()
        assertThat(destinationFor(Routes.ABOUT, selectedTab = 0, hasSelectedNovel = false)).isNull()
        assertThat(destinationFor(null, selectedTab = 0, hasSelectedNovel = false)).isNull()
    }
}
