/**
 * Lunara Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 */

package com.lunara.app.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil3.compose.AsyncImage
import com.lunara.app.R
import com.lunara.app.constants.DarkModeKey
import com.lunara.app.constants.ShowHomeSearchBarKey
import com.lunara.app.ui.screens.settings.DarkMode
import com.lunara.app.ui.theme.LunaraGradientEnd
import com.lunara.app.ui.theme.LunaraThemeColor
import com.lunara.app.utils.rememberEnumPreference
import com.lunara.app.utils.rememberPreference
import java.util.Calendar

/**
 * Lunara home header, ported from the original Flutter app:
 * top row (logo + wordmark | settings), greeting card with the
 * hero image overflowing above the card, and a rounded search bar.
 */
@Composable
fun LunaraHomeHeader(
    onSettingsClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    // Two ways to start music from the card itself, each showing the cover of the
    // song it starts with. Null hides the button; with neither, the card keeps its
    // old "Enjoy the music" line instead.
    onForYouClick: (() -> Unit)? = null,
    forYouArt: String? = null,
    onSpeedDialClick: (() -> Unit)? = null,
    speedDialArt: String? = null,
) {
    val darkMode by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
    val isDark = if (darkMode == DarkMode.AUTO) isSystemInDarkTheme() else darkMode == DarkMode.ON
    // The search bar is optional — some people want a compact home (Look & Feel → Home).
    val showSearchBar by rememberPreference(ShowHomeSearchBarKey, defaultValue = true)
    val iconTint = if (isDark) Color.White else Color(0xDE000000)

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header row: logo + "Lunara" | settings. The account button that used
        // to sit on the left is gone with the rest of the account surface; the
        // spacer keeps the wordmark centred against the settings button.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
        ) {
            Spacer(Modifier.size(48.dp))
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Image(
                    painter = painterResource(
                        if (isDark) R.drawable.lunara_logo_white else R.drawable.lunara_logo,
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(42.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Lunara",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                    maxLines = 1,
                    style = androidx.compose.ui.text.TextStyle(
                        brush = if (isDark) {
                            Brush.linearGradient(listOf(Color.White, Color.White))
                        } else {
                            Brush.linearGradient(listOf(LunaraThemeColor, LunaraGradientEnd))
                        },
                    ),
                )
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    painter = painterResource(R.drawable.settings),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Search bar
        if (showSearchBar) Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(if (isDark) Color.White.copy(alpha = 0.1f) else Color(0xFFEEEEEE))
                .clickable(onClick = onSearchClick)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.search),
                contentDescription = null,
                tint = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0x8A000000),
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            // Keep the placeholder on one line: on narrow screens it used to wrap and
            // grow the search bar's height, so ellipsize instead.
            Text(
                text = stringResource(R.string.home_search_hint),
                color = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0x8A000000),
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}
