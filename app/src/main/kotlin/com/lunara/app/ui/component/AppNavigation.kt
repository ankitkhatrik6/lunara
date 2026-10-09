/**
 * Lunara Project (C) 2026
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 *
 * Minimalist, Spotify-Inspired Navigation:
 * Solid deep black/elevated surface, crisp typography, clean Spotify green accents.
 * No laggy gradients, no emojis, no bounce recalculations. Ultra smooth 60/120fps.
 */

package com.lunara.app.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lunara.app.constants.NavBarStyle
import com.lunara.app.constants.NavBarStyleKey
import com.lunara.app.ui.screens.Screens
import com.lunara.app.ui.theme.SpotifyBlack
import com.lunara.app.ui.theme.SpotifyCardSurface
import com.lunara.app.ui.theme.SpotifyDeepBlack
import com.lunara.app.ui.theme.SpotifyDivider
import com.lunara.app.ui.theme.SpotifyElevatedSurface
import com.lunara.app.ui.theme.SpotifyGreen
import com.lunara.app.ui.theme.SpotifyTextMuted
import com.lunara.app.ui.theme.SpotifyTextPrimary
import com.lunara.app.ui.theme.SpotifyTextSecondary
import com.lunara.app.utils.rememberEnumPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@Immutable
private data class NavItemState(
    val isSelected: Boolean,
    val iconRes: Int,
)

@Stable
private fun isRouteSelected(currentRoute: String?, screenRoute: String, navigationItems: List<Screens>): Boolean {
    if (currentRoute == null) return false
    if (currentRoute == screenRoute) return true
    if (navigationItems.any { it.route == screenRoute } &&
        currentRoute.startsWith("$screenRoute/")
    ) return true

    if (screenRoute == "search_input" &&
        (currentRoute.startsWith("search/") || currentRoute == "search/{query}")
    ) return true

    return false
}

@Composable
fun AppNavigationRail(
    navigationItems: List<Screens>,
    currentRoute: String?,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    onSearchLongClick: (() -> Unit)? = null,
) {
    val containerColor = if (pureBlack) SpotifyDeepBlack else SpotifyElevatedSurface
    val haptics = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current

    NavigationRail(
        modifier = modifier,
        containerColor = containerColor,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        navigationItems.forEach { screen ->
            val isSelected = remember(currentRoute, screen.route) {
                isRouteSelected(currentRoute, screen.route, navigationItems)
            }
            val currentIsSelected by rememberUpdatedState(isSelected)
            val iconRes = remember(isSelected, screen) {
                if (isSelected) screen.iconIdActive else screen.iconIdInactive
            }

            val isSearchItem = screen == Screens.Search && onSearchLongClick != null
            val interactionSource = remember { MutableInteractionSource() }

            if (isSearchItem) {
                LaunchedEffect(interactionSource) {
                    var isLongClick = false
                    interactionSource.interactions.collectLatest { interaction ->
                        when (interaction) {
                            is PressInteraction.Press -> {
                                isLongClick = false
                                delay(viewConfiguration.longPressTimeoutMillis)
                                isLongClick = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSearchLongClick?.invoke()
                            }
                            is PressInteraction.Release -> {
                                if (!isLongClick) {
                                    onItemClick(screen, currentIsSelected)
                                }
                            }
                            is PressInteraction.Cancel -> {
                                isLongClick = false
                            }
                        }
                    }
                }
            }

            NavigationRailItem(
                selected = isSelected,
                onClick = {
                    if (!isSearchItem) {
                        onItemClick(screen, currentIsSelected)
                    }
                },
                interactionSource = interactionSource,
                colors = NavigationRailItemDefaults.colors(
                    indicatorColor = Color.Transparent,
                    selectedIconColor = SpotifyGreen,
                    unselectedIconColor = SpotifyTextSecondary,
                    selectedTextColor = SpotifyGreen,
                    unselectedTextColor = SpotifyTextSecondary,
                ),
                icon = {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = stringResource(screen.titleId),
                        modifier = Modifier.size(24.dp),
                    )
                },
                label = {
                    Text(
                        text = stringResource(screen.titleId),
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    )
                },
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun AppNavigationBar(
    navigationItems: List<Screens>,
    currentRoute: String?,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    slimNav: Boolean = false,
    onSearchLongClick: (() -> Unit)? = null,
) {
    val containerColor = if (pureBlack) SpotifyDeepBlack else SpotifyElevatedSurface
    val haptics = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current
    val navBarStyle by rememberEnumPreference(NavBarStyleKey, NavBarStyle.PILL)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor),
    ) {
        // Minimal hairline top divider
        HorizontalDivider(
            thickness = 0.5.dp,
            color = SpotifyDivider,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = if (slimNav) 4.dp else 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navigationItems.forEach { screen ->
                val isSelected = remember(currentRoute, screen.route) {
                    isRouteSelected(currentRoute, screen.route, navigationItems)
                }
                val currentIsSelected by rememberUpdatedState(isSelected)
                val iconRes = remember(isSelected, screen) {
                    if (isSelected) screen.iconIdActive else screen.iconIdInactive
                }

                val isSearchItem = screen == Screens.Search && onSearchLongClick != null
                val interactionSource = remember { MutableInteractionSource() }

                if (isSearchItem) {
                    LaunchedEffect(interactionSource) {
                        var isLongClick = false
                        interactionSource.interactions.collectLatest { interaction ->
                            when (interaction) {
                                is PressInteraction.Press -> {
                                    isLongClick = false
                                    delay(viewConfiguration.longPressTimeoutMillis)
                                    isLongClick = true
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSearchLongClick?.invoke()
                                }
                                is PressInteraction.Release -> {
                                    if (!isLongClick) {
                                        onItemClick(screen, currentIsSelected)
                                    }
                                }
                                is PressInteraction.Cancel -> {
                                    isLongClick = false
                                }
                            }
                        }
                    }
                }

                val label = stringResource(screen.titleId)
                val activeColor = if (navBarStyle == NavBarStyle.UNDERLINE) SpotifyGreen else SpotifyTextPrimary
                val iconTint by animateColorAsState(
                    targetValue = if (isSelected) activeColor else SpotifyTextSecondary,
                    animationSpec = tween(durationMillis = 150),
                    label = "navIconTint",
                )
                val textTint by animateColorAsState(
                    targetValue = if (isSelected) activeColor else SpotifyTextSecondary,
                    animationSpec = tween(durationMillis = 150),
                    label = "navTextTint",
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = ripple(bounded = false, radius = 28.dp),
                        ) {
                            if (!isSearchItem) {
                                onItemClick(screen, currentIsSelected)
                            }
                        }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    when (navBarStyle) {
                        NavBarStyle.PILL -> {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (isSelected) SpotifyCardSurface else Color.Transparent)
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(id = iconRes),
                                    contentDescription = label,
                                    tint = iconTint,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                        NavBarStyle.OUTLINED -> {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(
                                        width = if (isSelected) 1.dp else 0.dp,
                                        color = if (isSelected) SpotifyGreen else Color.Transparent,
                                        shape = RoundedCornerShape(14.dp),
                                    )
                                    .background(if (isSelected) SpotifyCardSurface else Color.Transparent)
                                    .padding(horizontal = 14.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(id = iconRes),
                                    contentDescription = label,
                                    tint = iconTint,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                        NavBarStyle.UNDERLINE -> {
                            Icon(
                                painter = painterResource(id = iconRes),
                                contentDescription = label,
                                tint = iconTint,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        NavBarStyle.GRADIENT -> {
                            // Minimal solid pill tab (replaces old gradient)
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (isSelected) SpotifyGreen.copy(alpha = 0.18f) else Color.Transparent)
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(id = iconRes),
                                    contentDescription = label,
                                    tint = if (isSelected) SpotifyGreen else SpotifyTextSecondary,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    }

                    if (!slimNav) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = label,
                            color = textTint,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (navBarStyle == NavBarStyle.UNDERLINE) {
                        Spacer(Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .width(if (isSelected) 16.dp else 0.dp)
                                .height(2.5.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) SpotifyGreen else Color.Transparent),
                        )
                    }
                }
            }
        }
    }
}
