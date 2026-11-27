package dev.dogetime

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.dogetime.navigation.Destination

internal val NavBarHeight = 64.dp

internal val NavBarWidth = 84.dp

internal val NavBarMargin = 16.dp

private val NavBarShape = RoundedCornerShape(NavBarHeight / 2)

private val NavRailShape = RoundedCornerShape(NavBarWidth / 2)

@OptIn(ExperimentalHazeApi::class)
@Composable
private fun rememberNavBarGlass(): GlassStyle {
    val bg = MaterialTheme.colorScheme.surface
    return remember(bg) {
        GlassStyle.regular.then {
            shape(NavBarShape)
            tint(bg.copy(alpha = 0.35f))
        }
    }
}

@OptIn(ExperimentalHazeApi::class)
@Composable
internal fun FloatingNavBar(
    root: Destination,
    onSelect: (Destination) -> Unit,
    hazeState: HazeState,
    isVertical: Boolean = false,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    if (isVertical) {
        // A plain rail: the screen's full height, no background of its own.
        Column(
            modifier = modifier
                .fillMaxHeight()
                .width(NavBarWidth)
                // Still scrolls if even the compact tabs don't fit (e.g. split screen).
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = if (compact) 4.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            tabs.forEach { tab ->
                NavBarTabItem(
                    tab = tab,
                    selected = root == tab.destination,
                    onSelect = { onSelect(tab.destination) },
                    isVertical = true,
                    compact = compact,
                )
            }
        }
    } else {
        // As wide as the screen allows (up to a phablet's width), shared evenly by the tabs, so
        // all five fit on narrow phones.
        Row(
            modifier = modifier
                .padding(horizontal = 12.dp)
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .height(NavBarHeight)
                .hazeGlass(input = HazeInput.Sources(hazeState), style = rememberNavBarGlass())
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                NavBarTabItem(
                    tab = tab,
                    selected = root == tab.destination,
                    onSelect = { onSelect(tab.destination) },
                    isVertical = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NavBarTabItem(
    tab: Tab,
    selected: Boolean,
    onSelect: () -> Unit,
    isVertical: Boolean,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val activeTheme = LocalActiveTheme.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val ghostWhite = Color(0xFFFBF9FF)
    val lavenderGrey = Color(0xFFA2A3BB)

    val iconColor by animateColorAsState(
        when {
            activeTheme == AppTheme.Midnight -> when {
                isHovered || selected -> ghostWhite
                else -> lavenderGrey
            }
            else -> when {
                isHovered || selected -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        }
    )

    val pillColor by animateColorAsState(
        when {
            selected -> (if (activeTheme == AppTheme.Midnight) ghostWhite else MaterialTheme.colorScheme.primary).copy(alpha = 0.22f)
            isPressed -> (if (activeTheme == AppTheme.Midnight) ghostWhite else MaterialTheme.colorScheme.primary).copy(alpha = 0.16f)
            isHovered -> (if (activeTheme == AppTheme.Midnight) ghostWhite else MaterialTheme.colorScheme.primary).copy(alpha = 0.10f)
            else -> Color.Transparent
        }
    )

    val shape = if (isVertical) NavRailShape else NavBarShape

    val itemModifier = if (isVertical) {
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(pillColor)
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelect,
            )
            .padding(vertical = if (compact) 6.dp else 12.dp, horizontal = 2.dp)
    } else {
        modifier
            .fillMaxHeight()
            .clip(shape)
            .background(pillColor)
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelect,
            )
            .padding(horizontal = 2.dp)
    }

    Column(
        modifier = itemModifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(tab.icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
        if (isVertical) {
            Spacer(Modifier.height(4.dp))
        }
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            color = iconColor,
            maxLines = 1,
            softWrap = false,
        )
    }
}
