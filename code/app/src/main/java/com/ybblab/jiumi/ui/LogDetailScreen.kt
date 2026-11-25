// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.ui

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ybblab.jiumi.ui.theme.Ink
import com.ybblab.jiumi.MonitorController
import com.ybblab.jiumi.R
import com.ybblab.jiumi.data.AppKey
import com.ybblab.jiumi.data.AppKind
import com.ybblab.jiumi.data.LogEntry
import com.ybblab.jiumi.data.Segments
import com.ybblab.jiumi.data.TimelineItem
import com.ybblab.jiumi.data.TimelinePages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ybblab.jiumi.ui.icons.JiumiIcons
import com.ybblab.jiumi.ui.theme.AppIconSize
import com.ybblab.jiumi.ui.theme.AppShapes
import com.ybblab.jiumi.ui.theme.StatusWarning

/** A labelled value of a log page; [code] values are identifiers shown in a fixed-width face. */
private data class Field(val label: String, val value: String, val code: Boolean = false, val color: Color? = null)

/**
 * One record, or one behavior segment, laid out as a structured log: when, who called, what was
 * reached and how, then the calls counted with it and, for a segment, each of its records.
 */
@Composable
fun LogDetailScreen(
    controller: MonitorController,
    labels: AppLabels,
    entry: LogEntry,
    onBack: () -> Unit,
    onOpenApp: (AppKey) -> Unit,
    onOpenLog: (LogEntry) -> Unit
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val ledger = state.ledger
    val summary = ledger?.apps?.firstOrNull { it.key == entry.key }
    val routine = summary?.kind?.id in ledger?.routineKinds.orEmpty()
    val item = entry.item
    // A run of network activity is told by its totals; its records below name each destination.
    val head = netView(item) ?: headOf(item)
    val destinations = destinationsOf(item)
    val segment = item as? TimelineItem.Segment
    val list = rememberLazyListState()
    val restoringScroll = remember { list.firstVisibleItemIndex > 0 }
    if (segment != null) ObserveTimelineScroll(controller, list)
    val members by produceState<TimelinePages<TimelineItem>?>(null, entry) {
        value = withContext(Dispatchers.Default) { segment?.pages }
    }

    SubScreen(recordTitle(item), onBack) { padding ->
        // The saved position can be in the member rows. Wait for them before measuring that list.
        if (segment != null && members == null && restoringScroll) return@SubScreen
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp)
        ) {
            // Who and what at a glance, with the verdict badges.
            item {
                Card {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(44.dp), shape = AppShapes.Control, color = Ink.fill) {
                            Icon(recordIcon(item, ledger?.groupOf.orEmpty(), ledger?.groupIcons.orEmpty()), null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(11.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(recordTitle(item), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(fullTime(segment?.first ?: item.t, item.t), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            categoryOf(item)?.let { categoryDescription(it) }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(top = 4.dp))
                            }
                            RecordBadges(head, routine)
                        }
                    }
                    InsetDivider(start = 16.dp)
                    ListRow(
                        title = labels.label(entry.key),
                        subtitle = listOfNotNull(summary?.let { kindName(it.kind) }, entry.key.pkg).joinToString(" · "),
                        leading = { AppIcon(labels, entry.key.pkg, 32) },
                        onClick = { onOpenApp(entry.key) }
                    ) { Chevron() }
                }
            }

            fieldSection(R.string.log_section_time) { timeFields(item) }
            fieldSection(R.string.log_section_caller) { callerFields(entry.key, head, routine) }
            fieldSection(R.string.log_section_behavior) { behaviorFields(head, ledger?.groupOf.orEmpty(), destinations.size) }
            // Method calls the system noted as this same access, counted with it rather than again.
            fieldSection(R.string.log_section_same_action) {
                val counts = segment?.sameActionCalls ?: (item as? TimelineItem.AccessItem)?.calls.orEmpty()
                    .groupingBy { "${it.iface}.${it.method}" }.fold(0) { total, call -> total + call.count }
                counts.map { (name, count) -> Field(name, "×${formatCount(count.toLong())}", code = true) }
            }

            if (segment != null) {
                item {
                    GroupTitle(stringResource(R.string.log_section_records, formatCount(segment.members.size.toLong())),
                        Modifier.padding(top = 24.dp))
                }
                // Its records on a time axis, newest first, each told as the timeline tells it ("连接
                // api.example.com"); where the app was and the counts are on each record's own page.
                val groupOf = ledger?.groupOf.orEmpty()
                val groupIcons = ledger?.groupIcons.orEmpty()
                members?.let {
                    recordPageItems(it, 0, it.size, "member") { member, first, last ->
                        LogRow(member, routine, recordIcon(member, groupOf, groupIcons), app = null,
                            first = first, last = last, onClick = { onOpenLog(LogEntry(entry.key, member)) })
                    }
                }
            }
        }
    }
}

/** A titled card of fields; nothing is drawn when [fields] yields none. */
private fun androidx.compose.foundation.lazy.LazyListScope.fieldSection(title: Int, fields: @Composable () -> List<Field>) {
    item(key = "section-$title") {
        val list = fields()
        if (list.isEmpty()) return@item
        Column {
            GroupTitle(stringResource(title), Modifier.padding(top = 24.dp))
            Card {
                list.forEachIndexed { index, field ->
                    if (index > 0) InsetDivider(start = 16.dp)
                    FieldRow(field)
                }
            }
        }
    }
}

@Composable
private fun FieldRow(field: Field) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp)) {
        Text(field.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(112.dp))
        Text(
            field.value,
            style = if (field.code) MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            else MaterialTheme.typography.bodyMedium,
            color = field.color ?: MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(top = if (field.code) 2.dp else 0.dp)
        )
    }
}

@Composable
private fun timeFields(item: TimelineItem): List<Field> = buildList {
    val segment = item as? TimelineItem.Segment
    val head = headOf(item)
    if (segment != null) {
        add(Field(stringResource(R.string.key_first), fullTime(segment.first, segment.first)))
        add(Field(stringResource(R.string.key_last), fullTime(segment.t, segment.t)))
    } else {
        add(Field(stringResource(R.string.key_time), fullTime(item.t, item.t)))
    }
    val (t, end) = when (head) {
        is TimelineItem.AccessItem -> head.access.t to head.access.end
        is TimelineItem.CallItem -> head.call.t to head.call.end
        is TimelineItem.NetItem -> head.connection.t to head.connection.end
        else -> item.t to item.t
    }
    if (segment == null && end > t + 1_000) add(Field(stringResource(R.string.key_duration), DateUtils.formatElapsedTime((end - t) / 1000)))
    val count = Segments.count(item)
    if (head !is TimelineItem.ProcessItem) add(Field(stringResource(R.string.key_count), formatCount(count.toLong())))
}

@Composable
private fun callerFields(key: AppKey, head: TimelineItem, routine: Boolean): List<Field> = buildList {
    key.pkg?.let { add(Field(stringResource(R.string.key_package), it, code = true)) }
    add(Field("UID", key.uid.toString(), code = true))
    when (head) {
        is TimelineItem.CallItem -> {
            head.call.process?.let { add(Field(stringResource(R.string.key_process), it, code = true)) }
            if (head.call.pid > 0) add(Field("PID", head.call.pid.toString(), code = true))
            head.call.thread?.let { add(Field(stringResource(R.string.key_thread), it, code = true)) }
        }
        is TimelineItem.AccessItem -> head.access.attr?.let { add(Field(stringResource(R.string.key_attribution), it, code = true)) }
        is TimelineItem.ProcessItem -> {
            add(Field(stringResource(R.string.key_process), head.start.process, code = true))
            add(Field("PID", head.start.pid.toString(), code = true))
        }
        else -> Unit
    }
    val state = when (head) {
        is TimelineItem.AccessItem -> head.access.state to head.access.screen
        is TimelineItem.CallItem -> head.call.state to head.call.screen
        is TimelineItem.NetItem -> head.connection.state to head.connection.screen
        else -> null
    }
    // System services and hardware daemons have no app state; what the system reports for them means nothing here.
    if (state != null && !routine) {
        stateName(state.first)?.let {
            add(Field(stringResource(R.string.key_state), it, color = if (state.first in BACKGROUND_STATES) StatusWarning else null))
        }
        state.second?.let {
            add(Field(stringResource(R.string.key_screen), stringResource(if (it) R.string.screen_on else R.string.screen_off),
                color = if (!it) StatusWarning else null))
        }
    }
}

@Composable
private fun behaviorFields(head: TimelineItem, groupOf: Map<String, String>, destinations: Int = 1): List<Field> = buildList {
    actionName(head)?.let { add(Field(stringResource(R.string.key_action), it)) }
    categoryOf(head)?.let { c ->
        groupOf[c]?.let { add(Field(stringResource(R.string.key_group), groupName(it))) }
        add(Field(stringResource(R.string.key_category), categoryName(c)))
    }
    when (head) {
        is TimelineItem.AccessItem -> {
            add(Field(stringResource(R.string.key_operation), head.access.op.removePrefix("android:"), code = true))
        }
        is TimelineItem.CallItem -> {
            val c = head.call
            if (c.iface.startsWith(PROVIDER_PREFIX)) {
                add(Field(stringResource(R.string.key_provider), c.iface.removePrefix(PROVIDER_PREFIX), code = true))
                add(Field(stringResource(R.string.key_provider_operation), providerOperationName(c.method)))
            } else {
                add(Field(stringResource(R.string.key_interface), c.iface, code = true))
                add(Field(stringResource(R.string.key_method), c.method, code = true))
            }
        }
        is TimelineItem.NetItem -> {
            val c = head.connection
            if (destinations > 1) {
                add(Field(stringResource(R.string.key_destinations), formatCount(destinations.toLong())))
            } else {
                add(Field(stringResource(R.string.key_destination), destinationLabel(head.label)))
                if (c.addr.isNotEmpty() && c.addr != head.label) add(Field(stringResource(R.string.key_address), c.addr, code = true))
                add(Field(stringResource(R.string.key_protocol), "${c.proto.uppercase()} ${c.port}", code = true))
            }
            // Connections too short for the socket table were never counted.
            if (c.count > 0) add(Field(stringResource(R.string.key_connections), formatCount(c.count.toLong())))
            c.sent?.let { add(Field(stringResource(R.string.key_sent), formatBytes(it))) }
            c.received?.let { add(Field(stringResource(R.string.key_received), formatBytes(it))) }
            c.sentUnattended?.takeIf { it > 0 }?.let {
                add(Field(stringResource(R.string.key_sent_unattended), formatBytes(it), color = StatusWarning))
            }
        }
        is TimelineItem.ProcessItem -> {
            if (head.start.reason.isNotEmpty()) add(Field(stringResource(R.string.key_reason), startReasonName(head.start.reason)))
            if (head.start.component.isNotEmpty()) add(Field(stringResource(R.string.key_component), head.start.component, code = true))
        }
        is TimelineItem.ActionItem -> Unit
        is TimelineItem.Segment -> Unit
    }
}

/** Date and time to the second; a span when [from] and [to] differ. */
@Composable
private fun fullTime(from: Long, to: Long): String {
    val context = LocalContext.current
    val flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH
    val seconds = DateFormat.format(if (DateFormat.is24HourFormat(context)) "HH:mm:ss" else "h:mm:ss a", from).toString()
    if (from == to) return DateUtils.formatDateTime(context, from, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH) + " " + seconds
    return DateUtils.formatDateRange(context, from, to, flags)
}
