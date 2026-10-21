package com.rushi.modulesmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
fun VerticalScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
) {
    val layoutInfo = state.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo
    val viewport = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).toFloat()
    if (totalItems == 0 || visibleItems.isEmpty() || viewport <= 0f) return

    val averageStep = averageItemStep(visibleItems)
    val totalContent = (averageStep * totalItems).coerceAtLeast(viewport)
    val contentRange = (totalContent - viewport).coerceAtLeast(0f)
    if (contentRange <= 0f) return

    val current = (state.firstVisibleItemIndex * averageStep + state.firstVisibleItemScrollOffset)
        .coerceIn(0f, contentRange)
    val progress = when {
        !state.canScrollBackward -> 0f
        !state.canScrollForward -> 1f
        else -> (current / contentRange).coerceIn(0f, 1f)
    }
    val thumbFraction = (viewport / totalContent).coerceIn(0.05f, 1f)

    BoxWithConstraints(
        modifier = modifier
            .width(20.dp)
            .pointerInput(totalItems) {
                // Move the list by the same distance the finger moves on the track. Using raw
                // pixels instead of jumping to an item index keeps this smooth.
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val info = state.layoutInfo
                    val items = info.visibleItemsInfo
                    if (items.isNotEmpty()) {
                        val viewportPx = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
                        if (viewportPx > 0f) {
                            val step = averageItemStep(items)
                            val contentHeight = (step * info.totalItemsCount).coerceAtLeast(viewportPx)
                            val scrollRange = (contentHeight - viewportPx).coerceAtLeast(1f)
                            val thumbPx = (size.height * (viewportPx / contentHeight))
                                .coerceAtLeast(36.dp.toPx())
                            val trackRange = (size.height - thumbPx).coerceAtLeast(1f)
                            state.dispatchRawDelta(dragAmount.y * (scrollRange / trackRange))
                        }
                    }
                }
            },
    ) {
        val density = LocalDensity.current
        val trackHeightPx = constraints.maxHeight.toFloat()
        val thumbHeightPx = (trackHeightPx * thumbFraction)
            .coerceAtLeast(with(density) { 36.dp.toPx() })
        val maxOffsetPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(y = with(density) { (maxOffsetPx * progress).toDp() })
                .width(5.dp)
                .height(with(density) { thumbHeightPx.toDp() })
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)),
        )
    }
}

private fun averageItemStep(items: List<LazyListItemInfo>): Float {
    if (items.size > 1) {
        val first = items.first()
        val last = items.last()
        return ((last.offset - first.offset + last.size).toFloat() / items.size).coerceAtLeast(1f)
    }
    return items.first().size.toFloat().coerceAtLeast(1f)
}
