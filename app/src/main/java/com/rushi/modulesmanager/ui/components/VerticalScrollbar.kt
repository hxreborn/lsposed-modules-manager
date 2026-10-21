package com.rushi.modulesmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun VerticalScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
) {
    val layoutInfo = state.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo.size
    if (totalItems == 0 || visibleItems == 0 || totalItems <= visibleItems) return

    val firstIndex = state.firstVisibleItemIndex
    val maxFirstIndex = (totalItems - visibleItems).coerceAtLeast(1)
    val progress = (firstIndex.toFloat() / maxFirstIndex).coerceIn(0f, 1f)
    val thumbFraction = (visibleItems.toFloat() / totalItems).coerceIn(0.06f, 1f)
    val scope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = modifier
            .width(12.dp)
            .pointerInput(totalItems, maxFirstIndex) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val fraction = (change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                    val target = (fraction * maxFirstIndex).roundToInt().coerceIn(0, maxFirstIndex)
                    scope.launch { state.scrollToItem(target) }
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
                .width(4.dp)
                .height(with(density) { thumbHeightPx.toDp() })
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)),
        )
    }
}
