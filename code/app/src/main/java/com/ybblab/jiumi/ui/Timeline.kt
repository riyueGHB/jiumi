// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ybblab.jiumi.MonitorController
import com.ybblab.jiumi.R
import com.ybblab.jiumi.data.TimelineDay
import com.ybblab.jiumi.data.TimelineIndex
import com.ybblab.jiumi.data.TimelinePages
import com.ybblab.jiumi.data.TimelineSource
import com.ybblab.jiumi.ui.icons.JiumiIcons
import com.ybblab.jiumi.ui.theme.AppIconSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Days toggled against the default: for a long range only the most recent day opens initially. */
@Composable
fun rememberToggledDays(vararg keys: Any?): MutableState<List<Long>> =
    rememberSaveable(*keys) { mutableStateOf(emptyList()) }

fun MutableState<List<Long>>.toggle(day: Long) {
    value = if (day in value) value - day else value + day
}

/** A page keeps its displayed rows while a detail is open above it. */
internal class TimelineSnapshot<T> {
    var reuseKey: Any? = null
    var query: Any? = null
    var source: TimelineSource<T>? = null
    var index: TimelineIndex<T>? = null
}

/** Keep the displayed snapshot in place while the user scrolls, even when the probe is reporting. */
@Composable
internal fun <T> rememberTimeline(
    controller: MonitorController, source: TimelineSource<T>, query: Any?, state: LazyListState,
    snapshot: TimelineSnapshot<T>? = null, reuseKey: Any? = query, matches: (T) -> Boolean
): TimelineIndex<T>? {
    ObserveTimelineScroll(controller, state)
    var index by remember(reuseKey) {
        mutableStateOf(snapshot?.index?.takeIf { snapshot.reuseKey == reuseKey })
    }
    LaunchedEffect(source, query) {
        if (index != null && snapshot?.source === source && snapshot.query == query) return@LaunchedEffect
        if (index != null) snapshotFlow { state.isScrollInProgress }.first { !it }
        val next = withContext(Dispatchers.Default) { source.select(matches) }
        if (index != null) snapshotFlow { state.isScrollInProgress }.first { !it }
        snapshot?.let {
            it.reuseKey = reuseKey
            it.query = query
            it.source = source
            it.index = next
        }
        index = next
    }
    return index
}

@Composable
internal fun ObserveTimelineScroll(controller: MonitorController, state: LazyListState) {
    LaunchedEffect(controller, state) {
        val owner = Any()
        try {
            snapshotFlow { state.isScrollInProgress }.collect { controller.timelineScrolling(owner, it) }
        } finally {
            controller.timelineScrolling(owner, false)
        }
    }
}

internal data class DisplayDay(val data: TimelineDay, val offset: Int, val open: Boolean)

@Composable
internal fun rememberTimelineDays(days: List<TimelineDay>, toggled: List<Long>): List<DisplayDay> =
    remember(days, toggled) { displayTimelineDays(days, toggled) }

internal fun displayTimelineDays(days: List<TimelineDay>, toggled: List<Long>): List<DisplayDay> {
    var offset = 0
    return days.mapIndexed { index, day ->
        val open = (days.size <= OPEN_DAYS || index == 0) != (day.time in toggled)
        DisplayDay(day, offset, open).also { offset += 1 + if (open) day.count else 0 }
    }
}

@OptIn(ExperimentalFoundationApi::class)
internal fun <T> LazyListScope.timelineItems(
    days: List<DisplayDay>,
    records: TimelinePages<T>?,
    prefix: String,
    onToggle: (Long) -> Unit,
    row: @Composable (entry: T, first: Boolean, last: Boolean) -> Unit
) {
    if (records == null) return
    for ((day, _, open) in days) {
        val dayKey = "$prefix-day-${day.time}"
        if (open) {
            stickyHeader(key = dayKey) {
                Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).clickable { onToggle(day.time) }
                    .padding(top = 20.dp)) {
                    DayTitle(day.time, day.count)
                }
            }
            recordPageItems(records, day.start, day.count, prefix, row)
        } else {
            item(key = dayKey) { FoldedDay(day, Modifier.padding(top = 12.dp)) { onToggle(day.time) } }
        }
    }
}

/** One lazy interval per day or detail, regardless of how many rows it can address. */
internal fun <T> LazyListScope.recordPageItems(
    records: TimelinePages<T>, start: Int, count: Int, prefix: String,
    row: @Composable (entry: T, first: Boolean, last: Boolean) -> Unit
) {
    items(count, key = { "$prefix-${records.keyAt(start + it)}" }, contentType = { "timeline-record" }) { index ->
        row(records.at(start + index), index == 0, index == count - 1)
    }
}

/** A day in one card: its date and count, then the three things done most, connections as one. */
@Composable
private fun FoldedDay(day: TimelineDay, modifier: Modifier, onOpen: () -> Unit) {
    val actions = LinkedHashMap<String, Int>()
    for ((item, count) in day.actions) actions.merge(rowAction(item), count, Int::plus)
    val addresses = day.destinations
    val summary = (actions.entries.sortedByDescending { it.value }.take(if (addresses > 0) 2 else 3).map { it.key } +
        listOfNotNull(addresses.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.day_addresses, it, formatCount(it.toLong())) }))
        .joinToString(" · ")
    Column(modifier) { Card {
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(dayLabel(day.time), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    Text(pluralStringResource(R.plurals.records_count, day.count, formatCount(day.count.toLong())),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (summary.isNotEmpty()) {
                    Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                }
            }
            Icon(JiumiIcons.ChevronDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp).size(AppIconSize.Inline))
        }
    } }
}

/**
 * The fast-scroll handle for a timeline: [FastScroller] over its [rows], naming the day under the
 * finger. The timeline must be the list's last section, so its rows are the last items of [state].
 */
@Composable
internal fun TimelineScrubber(
    state: LazyListState, days: List<DisplayDay>, modifier: Modifier = Modifier, trailingItems: Int = 0
) {
    val last = days.lastOrNull() ?: return
    val count = last.offset + 1 + if (last.open) last.data.count else 0
    if (count < SCRUB_MIN_ROWS) return
    val base by remember(state, count, trailingItems) {
        derivedStateOf { (state.layoutInfo.totalItemsCount - count - trailingItems).coerceAtLeast(0) }
    }
    FastScroller(state, first = base, count = count, modifier = modifier) { index ->
        dayLabel((days.lastOrNull { it.offset <= index } ?: days.first()).data.time)
    }
}

/** Up to this many days every day is open. */
private const val OPEN_DAYS = 2
/** Below this many rows a list is short enough to scroll by hand. */
private const val SCRUB_MIN_ROWS = 60

