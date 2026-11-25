// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.ybblab.jiumi.MonitorController.Range
import com.ybblab.jiumi.data.AppKind
import com.ybblab.jiumi.data.LogEntry
import com.ybblab.jiumi.data.TimelineItem
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ybblab.jiumi.MonitorController
import com.ybblab.jiumi.R
import com.ybblab.jiumi.data.AppKey
import androidx.compose.foundation.layout.fillMaxWidth
import com.ybblab.jiumi.ui.theme.StatusWarning

/**
 * One kind of data over the chosen range, as a timeline of its records across apps. The network
 * ([NETWORK_GROUP]) opens by app instead, the ones that sent most in the background first, each
 * opening the app's own destinations; its timeline is the other view.
 */
@Composable
internal fun GroupScreen(
    controller: MonitorController,
    labels: AppLabels,
    groupId: String,
    timelineSnapshot: TimelineSnapshot<LogEntry>,
    onBack: () -> Unit,
    onOpenLog: (LogEntry) -> Unit,
    onOpenApp: (AppKey) -> Unit = {}
) {
    val network = groupId == NETWORK_GROUP
    var byApp by rememberSaveable(groupId) { mutableStateOf(network) }
    val state by controller.state.collectAsStateWithLifecycle()
    val ledger = state.ledger
    val title = if (groupId == NETWORK_GROUP) stringResource(R.string.section_network) else groupName(groupId)
    val routineKinds = ledger?.routineKinds.orEmpty()
    val routineKeys = remember(ledger) { ledger?.apps.orEmpty().filter { it.kind.id in routineKinds }.map { it.key }.toSet() }
    val list = rememberLazyListState()
    val restoringScroll = remember { list.firstVisibleItemIndex > 0 }
    val toggled = rememberToggledDays(groupId, state.range)
    val timeline = if ((network && byApp) || ledger == null) null else {
        rememberTimeline(controller, ledger.timelineSource, state.shownRange to groupId, list, timelineSnapshot) {
            if (network) headOf(it.item) is TimelineItem.NetItem
            else categoryOf(it.item)?.let(ledger.groupOf::get) == groupId
        }
    }
    val days = rememberTimelineDays(timeline?.days.orEmpty(), toggled.value)

    SubScreen(title, onBack) { padding ->
        if (!(network && byApp) && timeline == null && timelineSnapshot.index == null && restoringScroll) return@SubScreen
        Box(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp)
        ) {
            item {
                Segmented(
                    Range.entries.map { rangeLabel(it, state.session) },
                    selected = state.range.ordinal,
                    onSelect = { controller.setRange(Range.entries[it]) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (network) item {
                ViewTabs(listOf(stringResource(R.string.view_by_app), stringResource(R.string.view_timeline)),
                    selected = if (byApp) 0 else 1, onSelect = { byApp = it == 0 }, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            }
            if (network && byApp) {
                val apps = ledger?.networkApps.orEmpty()
                if (apps.isEmpty()) item { Column(Modifier.padding(top = 12.dp)) { EmptyCard(stringResource(R.string.record_empty_running)) } }
                else item {
                    Column(Modifier.padding(top = 12.dp)) { Card {
                        apps.forEachIndexed { index, app ->
                            if (index > 0) InsetDivider(start = TIME_COLUMN_WIDTH + 60.dp)
                            NetworkAppRow(app, labels, app.key in routineKeys) { onOpenApp(app.key) }
                        }
                    } }
                }
                return@LazyColumn
            }
            // Each row names the app and what it did; the app's own page holds its summary.
            if (timeline?.days?.isEmpty() == true) item { Column(Modifier.padding(top = 24.dp)) { EmptyCard(stringResource(R.string.record_empty_running)) } }
            timelineItems(days, timeline?.records, "group", toggled::toggle) { entry, first, last ->
                LogRow(entry.item, entry.key in routineKeys, recordIcon(entry.item, ledger?.groupOf.orEmpty(), ledger?.groupIcons.orEmpty()),
                    app = entry.key to labels, first = first, last = last, onClick = { onOpenLog(entry) })
            }
        }
        if (!(network && byApp)) TimelineScrubber(list, days, Modifier.align(Alignment.CenterEnd).padding(vertical = 16.dp))
        }
    }
}

/** An app's network activity in one line: where to, how much, and what it sent in the background. */
@Composable
private fun NetworkAppRow(app: com.ybblab.jiumi.data.AppSummary, labels: AppLabels, routine: Boolean, onClick: () -> Unit) {
    val parts = buildList {
        if (app.destinations > 0) add(androidx.compose.ui.res.pluralStringResource(R.plurals.day_addresses, app.destinations,
            formatCount(app.destinations.toLong())))
        app.traffic?.takeIf { it.total > 0 }?.let { add(stringResource(R.string.net_totals, formatBytes(it.tx), formatBytes(it.rx))) }
            ?: app.uploaded.takeIf { it > 0 }?.let { add(stringResource(R.string.uploaded, formatBytes(it))) }
    }
    ListRow(
        title = labels.label(app.key),
        subtitle = parts.joinToString(" · "),
        leading = { TimeColumn(app.last); AppIcon(labels, app.key.pkg, LEADING_SIZE) },
        onClick = onClick,
        extra = if (app.sentInBackground > 0 && !routine) {
            { Badges { Badge(stringResource(R.string.badge_sent_in_background, formatCount(app.sentInBackground.toLong())), StatusWarning) } }
        } else null
    ) { Chevron() }
}
