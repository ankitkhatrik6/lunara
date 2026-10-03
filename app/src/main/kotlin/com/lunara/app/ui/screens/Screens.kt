/**
 * Lunara Project (C) 2026
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 */

package com.lunara.app.ui.screens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.lunara.app.R

@Immutable
sealed class Screens(
    @StringRes val titleId: Int,
    @DrawableRes val iconIdInactive: Int,
    @DrawableRes val iconIdActive: Int,
    val route: String,
) {
    object Home : Screens(
        titleId = R.string.home,
        iconIdInactive = R.drawable.home_outlined,
        iconIdActive = R.drawable.home_filled,
        route = "home"
    )

    object Search : Screens(
        titleId = R.string.search,
        iconIdInactive = R.drawable.search,
        iconIdActive = R.drawable.search,
        route = "search_input"
    )

    object Yours : Screens(
        titleId = R.string.yours,
        iconIdInactive = R.drawable.grid_view,
        iconIdActive = R.drawable.grid_view,
        route = "yours"
    )

    object Library : Screens(
        titleId = R.string.filter_library,
        iconIdInactive = R.drawable.library_music_outlined,
        iconIdActive = R.drawable.library_music_filled,
        route = "library"
    )

    companion object {
        val MainScreens = listOf(Home, Search, Yours, Library)
    }
}
