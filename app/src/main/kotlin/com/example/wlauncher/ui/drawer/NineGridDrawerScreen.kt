package com.flue.launcher.ui.drawer

import android.annotation.SuppressLint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import com.flue.launcher.data.model.AppInfo
import com.flue.launcher.data.model.iconForDisplay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NineGridDrawerScreen(
    apps: List<AppInfo>,
    iconSize: Dp = 97.dp,
    appListScalePercent: Int = 100,
    blurEnabled: Boolean = true,
    twoToneIconsEnabled: Boolean = false,
    onAppClick: (AppInfo, Offset) -> Unit,
    onExcludeApp: (AppInfo) -> Unit = {},
    onRemoveShortcut: (AppInfo) -> Unit = {},
    onRenameFolder: (AppInfo, String) -> Unit = { _, _ -> },
    onDissolveFolder: (AppInfo) -> Unit = {},
    onScrollToTop: () -> Unit = {},
    active: Boolean = true,
    initialScrollResetKey: Int = 0
) {
    val gridState = rememberLazyGridState()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var longPressedApp by remember { mutableStateOf<AppInfo?>(null) }

    val overscroll = remember { Animatable(0f) }
    val overscrollLimitPx = with(density) { configuration.screenHeightDp.dp.toPx() / 2f }
    val topReturnThresholdPx = with(density) { 72.dp.toPx() }
    var returnTriggered by remember { mutableStateOf(false) }

    val appListScale = appListScalePercent.coerceIn(50, 200) / 100f
    val scaledIconSize = (iconSize * appListScale).coerceIn(24.dp, 128.dp)

    LaunchedEffect(active) {
        if (!active) {
            if (!returnTriggered) overscroll.snapTo(0f)
        } else {
            returnTriggered = false
        }
    }

    LaunchedEffect(initialScrollResetKey, active, apps.size) {
        if (initialScrollResetKey > 0 && active && apps.isNotEmpty()) {
            overscroll.stop()
            overscroll.snapTo(0f)
            gridState.scrollToItem(0)
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(
                remember(gridState) {
                    object : NestedScrollConnection {
                        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                            if (source != NestedScrollSource.UserInput) return Offset.Zero
                            return consumeGridOverscroll(
                                availableY = available.y,
                                gridState = gridState,
                                overscroll = overscroll,
                                scope = scope,
                                overscrollLimitPx = overscrollLimitPx
                            )
                        }

                        override fun onPostScroll(
                            consumed: Offset,
                            available: Offset,
                            source: NestedScrollSource
                        ): Offset {
                            if (source != NestedScrollSource.UserInput) return Offset.Zero
                            return consumeGridOverscroll(
                                availableY = available.y,
                                gridState = gridState,
                                overscroll = overscroll,
                                scope = scope,
                                overscrollLimitPx = overscrollLimitPx
                            )
                        }

                        override suspend fun onPreFling(available: Velocity): Velocity {
                            val atTop = !gridState.canScrollBackward
                            if (overscroll.value >= topReturnThresholdPx && atTop) {
                                if (!returnTriggered) {
                                    returnTriggered = true
                                    onScrollToTop()
                                }
                                overscroll.stop()
                                overscroll.animateTo(0f, spring(dampingRatio = 0.78f, stiffness = 420f))
                                returnTriggered = false
                                return available
                            }
                            if (overscroll.value != 0f) {
                                overscroll.stop()
                                overscroll.animateTo(0f, spring(dampingRatio = 0.78f, stiffness = 420f))
                                return available
                            }
                            return Velocity.Zero
                        }
                    }
                }
            )
            .pointerInput(onScrollToTop) {
                awaitEachGesture {
                    awaitPrimaryDown()
                    do {
                        val event = awaitPointerEvent()
                    } while (event.changes.any { it.pressed })

                    if (overscroll.value >= topReturnThresholdPx) {
                        if (!returnTriggered) {
                            returnTriggered = true
                            onScrollToTop()
                        }
                    }
                    scope.launch {
                        overscroll.stop()
                        overscroll.animateTo(0f, spring(dampingRatio = 0.78f, stiffness = 420f))
                        returnTriggered = false
                    }
                }
            }
            .graphicsLayer {
                translationY = overscroll.value
            },
        contentPadding = PaddingValues(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(
            items = apps,
            key = { _, app -> app.componentKey }
        ) { _, app ->
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()

            val pressedScale by animateFloatAsState(
                targetValue = if (isPressed) 0.92f else 1f,
                animationSpec = tween(120),
                label = "press"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onAppClick(app, Offset(0.5f, 0.5f)) },
                        onLongClick = {
                            longPressedApp = app
                            vibrateHaptic(context)
                        }
                    )
                    .graphicsLayer {
                        scaleX = pressedScale
                        scaleY = pressedScale
                    }
                    .clip(CircleShape)
            )
            {
                Image(
                    bitmap = app.iconForDisplay(
                        useTwoTone = twoToneIconsEnabled,
                        blurred = false
                    ),
                    contentDescription = app.label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }

    longPressedApp?.let { app ->
        AppShortcutOverlay(
            app = app,
            blurEnabled = blurEnabled,
            onExcludeApp = { onExcludeApp(app) },
            onRemoveShortcut = if (app.isAppListShortcut) { { onRemoveShortcut(app) } } else null,
            onRenameFolder = if (app.isFolder) { name -> onRenameFolder(app, name) } else null,
            onDissolveFolder = if (app.isFolder) { { onDissolveFolder(app) } } else null,
            onDismiss = { longPressedApp = null }
        )
    }
}

/**
 * 处理网格的 overscroll 逻辑（与 List 版本功能相同）
 */
private fun consumeGridOverscroll(
    availableY: Float,
    gridState: LazyGridState,
    overscroll: Animatable<Float, AnimationVector1D>,
    scope: CoroutineScope,
    overscrollLimitPx: Float
): Offset {
    val atTop = !gridState.canScrollBackward
    val atBottom = !gridState.canScrollForward
    val current = overscroll.value
    val next = when {
        availableY > 0f && atTop -> (current + availableY).coerceAtMost(overscrollLimitPx)
        availableY < 0f && atBottom -> (current + availableY).coerceAtLeast(-overscrollLimitPx)
        current > 0f && availableY < 0f -> (current + availableY).coerceAtLeast(0f)
        current < 0f && availableY > 0f -> (current + availableY).coerceAtMost(0f)
        else -> current
    }
    if (next == current) return Offset.Zero
    scope.launch {
        overscroll.stop()
        overscroll.snapTo(next)
    }
    return Offset(0f, availableY)
}

/**
 * 从 awaitPointerEventScope 中获取首个按下事件
 */
private suspend fun AwaitPointerEventScope.awaitPrimaryDown():
        PointerInputChange {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.pressed }
        if (change != null) return change
    }
}