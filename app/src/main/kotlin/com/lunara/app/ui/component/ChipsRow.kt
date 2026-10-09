package com.lunara.app.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lunara.app.R
import com.lunara.app.ui.theme.SpotifyCardSurface
import com.lunara.app.ui.theme.SpotifyGreen

@Composable
fun <E> ChipsRow(
    chips: List<Pair<E, String>>,
    currentValue: E,
    onValueUpdate: (E) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = SpotifyCardSurface,
    isLoading: (E) -> Boolean = { false },
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(12.dp))

        chips.forEach { (value, label) ->
            val selected = currentValue == value
            val pillText by animateColorAsState(
                targetValue = if (selected) Color.Black else Color.White,
                animationSpec = tween(150),
                label = "chipText",
            )
            val pillBg by animateColorAsState(
                targetValue = if (selected) SpotifyGreen else containerColor,
                animationSpec = tween(150),
                label = "chipBg",
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(34.dp)
                    .clip(CircleShape)
                    .background(pillBg)
                    .clickable { onValueUpdate(value) }
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = pillText,
                    maxLines = 1,
                )
            }
        }

        Spacer(Modifier.width(12.dp))
    }
}

@Composable
fun <Option, E> ChoiceChipsRow(
    chips: List<Pair<E, String>>,
    options: List<Pair<Option, String>>,
    selectedOption: Option,
    onSelectionChange: (Option) -> Unit,
    currentValue: E,
    onValueUpdate: (E) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = SpotifyCardSurface,
) {
    var expandIconDegree by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(12.dp))

        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .height(34.dp)
                    .clip(CircleShape)
                    .background(SpotifyGreen)
                    .clickable { expandIconDegree = true }
                    .padding(horizontal = 14.dp),
            ) {
                Text(
                    text = options.find { it.first == selectedOption }?.second.orEmpty(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                )
                Icon(
                    painter = painterResource(R.drawable.expand_more),
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp),
                )
            }

            DropdownMenu(
                expanded = expandIconDegree,
                onDismissRequest = { expandIconDegree = false },
            ) {
                options.forEach { (option, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onSelectionChange(option)
                            expandIconDegree = false
                        },
                    )
                }
            }
        }

        chips.forEach { (value, label) ->
            val selected = currentValue == value
            val pillText by animateColorAsState(
                targetValue = if (selected) Color.Black else Color.White,
                animationSpec = tween(150),
                label = "choiceChipText",
            )
            val pillBg by animateColorAsState(
                targetValue = if (selected) SpotifyGreen else containerColor,
                animationSpec = tween(150),
                label = "choiceChipBg",
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(34.dp)
                    .clip(CircleShape)
                    .background(pillBg)
                    .clickable { onValueUpdate(value) }
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = pillText,
                    maxLines = 1,
                )
            }
        }

        Spacer(Modifier.width(12.dp))
    }
}
