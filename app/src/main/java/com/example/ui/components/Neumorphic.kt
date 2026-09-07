package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalNeuColors
import com.example.ui.theme.neumorphicRaised

/**
 * A soft, raised neumorphic card. Optional click / long-press with a subtle
 * press-scale micro interaction.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NeuCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    cornerRadius: Dp = 24.dp,
    color: Color = MaterialTheme.colorScheme.surface,
    elevation: Dp = 8.dp,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val neu = LocalNeuColors.current
    val shape = RoundedCornerShape(cornerRadius)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.975f else 1f, label = "neuCardScale")

    val clickable = if (onClick != null || onLongClick != null) {
        Modifier.combinedClickable(
            interactionSource = interaction,
            indication = null,
            onClick = { onClick?.invoke() },
            onLongClick = onLongClick,
        )
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .neumorphicRaised(cornerRadius, neu, elevation)
            .clip(shape)
            .background(color)
            .then(clickable)
            .padding(contentPadding),
        content = content,
    )
}

/** A simple raised neumorphic container without click handling. */
@Composable
fun NeuSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    color: Color = MaterialTheme.colorScheme.surface,
    elevation: Dp = 8.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val neu = LocalNeuColors.current
    Column(
        modifier = modifier
            .neumorphicRaised(cornerRadius, neu, elevation)
            .clip(RoundedCornerShape(cornerRadius))
            .background(color)
            .padding(contentPadding),
        content = content,
    )
}

/** Circular, softly-raised icon button with a press micro-interaction. */
@Composable
fun NeuIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    iconSize: Dp = 22.dp,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    container: Color = MaterialTheme.colorScheme.surface,
    elevation: Dp = 6.dp,
) {
    val neu = LocalNeuColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "neuIconScale")

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .neumorphicRaised(size / 2, neu, elevation)
            .clip(CircleShape)
            .background(container)
            .combinedClickableCircle(interaction, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCircle(
    interaction: MutableInteractionSource,
    onClick: () -> Unit,
): Modifier = this.combinedClickable(
    interactionSource = interaction,
    indication = null,
    onClick = onClick,
)
