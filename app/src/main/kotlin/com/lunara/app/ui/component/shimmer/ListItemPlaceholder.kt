/**
 * Ankit Khatri KC (C) 2026
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0
 */

package com.lunara.app.ui.component.shimmer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lunara.app.R
import com.lunara.app.constants.ListItemHeight
import com.lunara.app.constants.ListThumbnailSize
import com.lunara.app.constants.ThumbnailCornerRadius

@Composable
fun ListItemPlaceHolder(
    modifier: Modifier = Modifier,
    thumbnailShape: Shape = RoundedCornerShape(ThumbnailCornerRadius),
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
        modifier
            .height(ListItemHeight)
            .padding(horizontal = 6.dp),
    ) {
        // The thumbnail block carries a faint Lunara mark so the loading state is
        // on-brand instead of an empty grey square.
        Box(
            modifier = Modifier
                .padding(6.dp)
                .size(ListThumbnailSize)
                .clip(thumbnailShape)
                .background(skeletonBlockColor),
            contentAlignment = Alignment.Center,
        ) {
            SkeletonLogo(size = ListThumbnailSize * 0.5f)
        }

        Column(
            modifier =
            Modifier
                .weight(1f)
                .padding(horizontal = 6.dp),
        ) {
            TextPlaceholder()
            TextPlaceholder()
        }
    }
}

/**
 * The Lunara mark drawn faintly inside a skeleton thumbnail, so loading lists and
 * grids read as Lunara rather than showing a blank grey block. Shared by the list
 * and grid placeholders.
 */
@Composable
fun SkeletonLogo(size: Dp) {
    Image(
        painter = painterResource(R.drawable.lunara_logo),
        contentDescription = null,
        modifier = Modifier.size(size).alpha(0.28f),
    )
}
