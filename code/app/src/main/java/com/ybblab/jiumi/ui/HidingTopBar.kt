// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * A tab page under a top bar of fixed height: the page's [title] and, beneath it, anything else that
 * belongs on top ([extra], such as filters and search). Scrolling down slides the bar away to give the
 * page the room; scrolling up, or a tap at the top of the page, brings it back. When a scroll stops
 * halfway, the bar settles shown or hidden, whichever is nearer.
 *
 * [content] lays out the page and is told how much to keep clear at its top for the bar; [scrolled]
 * says whether the page has moved from its top, which draws a hairline under the bar.
 */
@Composable
fun HidingTopBar(
    title: String,
    scrolled: Boolean,
    trailing: (@Composable () -> Unit)? = null,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
    pinned: Boolean = false,
    content: @Composable (top: Dp) -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var height by remember { mutableIntStateOf(0) }
    // 0 when shown, -height when hidden.
    var offset by remember { mutableFloatStateOf(0f) }
    fun settle(to: Float) = scope.launch {
        animate(offset, to, animationSpec = spring(dampingRatio = 0.9f, stiffness = 500f)) { v, _ -> offset = v }
    }
    val connection = remember {
        object : NestedScrollConnection {
            // The bar moves with the finger before the page does, so neither jumps.
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (height > 0) offset = (offset + available.y).coerceIn(-height.toFloat(), 0f)
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (offset != 0f && offset != -height.toFloat()) settle(if (offset < -height / 2f) -height.toFloat() else 0f)
                return Velocity.Zero
            }
        }
    }
    val topDp = with(density) { height.toDp() }
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars).clipToBounds()
            .then(if (pinned) Modifier else Modifier.nestedScroll(connection))
    ) {
        content(topDp)
        Column(
            Modifier.fillMaxWidth().onSizeChanged { height = it.height }
                .graphicsLayer { translationY = offset }
                .background(MaterialTheme.colorScheme.background)
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = BAR_TITLE_HEIGHT).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground)
                trailing?.invoke()
            }
            extra?.invoke(this)
            HorizontalDivider(thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (scrolled) 1f else 0f))
        }
        // While the bar is away, a tap along the top of the page calls it back.
        if (offset < 0f) {
            Box(
                Modifier.fillMaxWidth().height(20.dp).clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null
                ) { settle(0f) }
            )
        }
    }
}

private val BAR_TITLE_HEIGHT: Dp = 52.dp

/** The room every tab page leaves between its title (or what [HidingTopBar] adds under it) and its first row. */
val PageTopGap: Dp = 8.dp
