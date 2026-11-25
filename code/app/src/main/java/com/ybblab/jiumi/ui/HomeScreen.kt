// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import com.ybblab.jiumi.data.AppKind
import com.ybblab.jiumi.data.Attention
import com.ybblab.jiumi.data.AttentionRules
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.text.withStyle
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ybblab.jiumi.MonitorController
import com.ybblab.jiumi.MonitorController.Busy
import com.ybblab.jiumi.MonitorController.Problem
import com.ybblab.jiumi.MonitorController.Range
import com.ybblab.jiumi.access.RootShell
import com.ybblab.jiumi.R
import com.ybblab.jiumi.adb.AirModePairingManager.PairingState
import com.ybblab.jiumi.adb.AirModePairingManager.PairingTrustState
import com.ybblab.jiumi.adb.messageRes
import com.ybblab.jiumi.data.AppKey
import com.ybblab.jiumi.data.AppSummary
import com.ybblab.jiumi.data.Peak
import com.ybblab.jiumi.data.GroupSummary
import com.ybblab.jiumi.data.LogEntry
import com.ybblab.jiumi.data.TimelineItem
import androidx.compose.foundation.lazy.rememberLazyListState
import com.ybblab.jiumi.ui.icons.JiumiIcons
import com.ybblab.jiumi.ui.theme.AppIconSize
import com.ybblab.jiumi.ui.theme.AppShapes
import com.ybblab.jiumi.ui.theme.StatusWarning
import com.ybblab.jiumi.ui.theme.StatusSuccess
import com.ybblab.jiumi.ui.theme.Ink

@Composable
internal fun HomeScreen(
    controller: MonitorController,
    labels: AppLabels,
    timelineSnapshot: TimelineSnapshot<LogEntry>,
    onOpenGroup: (String) -> Unit,
    onOpenAttention: (String) -> Unit,
    onOpenLog: (LogEntry) -> Unit,
    onOpenApp: (AppKey) -> Unit,
    onOpenPairing: () -> Unit,
    onOpenNetwork: () -> Unit
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val pairingFlow = state.method == com.ybblab.jiumi.access.AccessMethod.WIRELESS &&
        state.pairingState in setOf(PairingState.DISCOVERING, PairingState.AWAITING_CODE, PairingState.PAIRING)
    // Ready to start by the chosen way: paired, root granted, or Shizuku allowed.
    val paired = state.ready
    val ledger = state.ledger
    var view by rememberSaveable { mutableStateOf(HomeView.TIMELINE.name) }
    val list = rememberLazyListState()
    val restoringScroll = remember { list.firstVisibleItemIndex > 0 }
    // Routine system work is listed like everything else; it is only never marked as needing a look.
    val routineKeys = remember(ledger) {
        ledger?.apps.orEmpty().filter { it.kind.id in ledger?.routineKinds.orEmpty() }.map { it.key }.toSet()
    }
    var filter by rememberSaveable { mutableStateOf(TimelineFilter.ALL.name) }
    var rankName by rememberSaveable { mutableStateOf(AppRank.ALL.name) }
    val rank = AppRank.entries.firstOrNull { it.name == rankName } ?: AppRank.ALL
    var rankAll by rememberSaveable { mutableStateOf(false) }
    var routineOpen by rememberSaveable { mutableStateOf(false) }
    val selectedFilter = TimelineFilter.entries.firstOrNull { it.name == filter } ?: TimelineFilter.ALL
    val filterQuery = state.shownRange to selectedFilter
    val timeline = if (view == HomeView.TIMELINE.name && ledger != null) {
        rememberTimeline(controller, ledger.timelineSource, filterQuery, list,
            snapshot = timelineSnapshot, reuseKey = state.shownRange) {
            selectedFilter.matches(it.item)
        }
    } else null
    val toggled = rememberToggledDays(state.range, filter)
    val activity = state.activity
    // The curve grows at its end while the monitor runs and the range reaches now.
    // The range the report was built for: the picked one runs ahead while it is rebuilt.
    val built = state.shownRange
    val live = state.running && (built != Range.SESSION || state.sessionOpen)
    var picked by remember(built, state.window.first, activity?.buckets?.stepMs) { mutableStateOf<Int?>(null) }
    val days = rememberTimelineDays(timeline?.days.orEmpty(), toggled.value)
    val bottom = 32.dp + LocalTabBarInset.current
    val density = LocalDensity.current
    var filterSpacing by rememberSaveable(built) { mutableIntStateOf(0) }
    var switchingFilter by rememberSaveable(built) { mutableStateOf(false) }
    LaunchedEffect(timeline, filterQuery) {
        if (!switchingFilter || timeline == null || timelineSnapshot.query != filterQuery) return@LaunchedEffect
        // Wait for the new rows to lay out before trimming space a short result needs below it.
        withFrameNanos {}
        withFrameNanos {}
        val layout = list.layoutInfo
        val last = layout.visibleItemsInfo.firstOrNull { it.index == layout.totalItemsCount - 2 }
        val gap = last?.let { layout.viewportEndOffset - it.offset - it.size - with(density) { bottom.roundToPx() } }
        filterSpacing = (gap ?: 0).coerceIn(0, filterSpacing)
        switchingFilter = false
    }

    val scrolled by remember { androidx.compose.runtime.derivedStateOf { list.canScrollBackward } }
    HidingTopBar(stringResource(R.string.tab_monitor), scrolled) { top ->
    if (view == HomeView.TIMELINE.name && timeline == null && timelineSnapshot.index == null && restoringScroll) {
        return@HidingTopBar
    }
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        state = list,
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + PageTopGap, bottom = bottom)
    ) {
        // The monitoring service: running, interrupted, or ready to start once Wireless debugging is paired.
        val session = state.session
        item {
            // Never empty, even in the first frame before the card is measured: the list holds its place
            // by the first row it can see, and would otherwise keep the next one there and push the card
            // (with its progress and messages) above the screen as it grows.
            Box(Modifier.fillMaxWidth().heightIn(min = 1.dp)) {
                when {
                    state.sessionOpen && state.running && !pairingFlow && (state.problem == null || state.problem == Problem.STOP) -> SessionStrip(state, controller)
                    state.sessionOpen && !state.running &&
                        (paired || state.method == com.ybblab.jiumi.access.AccessMethod.NETWORK) && !pairingFlow && session != null ->
                        InterruptedCard(state, session, controller, onOpenNetwork)
                    else -> ServiceCard(state, controller, pairingFlow, paired, onOpenPairing, onOpenNetwork)
                }
            }
        }

        item {
            Segmented(
                Range.entries.map { rangeLabel(it, session) },
                selected = state.range.ordinal,
                onSelect = { controller.setRange(Range.entries[it]) },
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
            )
        }

        if (ledger == null || ledger.apps.isEmpty()) {
            // Nothing recorded yet: the status above already says what to do, so only a running
            // monitor adds a word here.
            if (state.running) {
                item { Spacer(Modifier.height(24.dp)) }
                item { EmptyCard(stringResource(R.string.record_empty_running)) }
            }
            return@LazyColumn
        }

        if (activity != null) item {
            Surface(shape = AppShapes.Card, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().padding(top = RANGE_GAP)) {
                ActivityCard(activity, built, live, picked, { picked = it })
            }
        }

        item {
            Box(Modifier.padding(top = RANGE_GAP)) { AttentionCard(ledger.attention, onOpenAttention, activity) }
        }

        item {
            ViewTabs(
                HomeView.entries.map { stringResource(it.label) },
                selected = HomeView.valueOf(view).ordinal,
                onSelect = { view = HomeView.entries[it].name },
                modifier = Modifier.padding(top = 32.dp, bottom = 8.dp)
            )
        }

        when (HomeView.valueOf(view)) {
            HomeView.TIMELINE -> {
                item(key = "timeline-filter") {
                    FilterChips(TimelineFilter.entries.map { stringResource(it.label) }, selectedFilter.ordinal,
                        Modifier.padding(top = 4.dp)) {
                        val next = TimelineFilter.entries[it].name
                        if (filter != next) {
                            // Measure at the existing offset even when the new filter removes most of the list.
                            filterSpacing = list.layoutInfo.viewportSize.height
                            switchingFilter = true
                            filter = next
                        }
                    }
                }
                if (timeline?.days?.isEmpty() == true) item {
                    Column(Modifier.padding(top = 12.dp)) { EmptyCard(stringResource(R.string.record_empty_running)) }
                }
                timelineItems(days, timeline?.records, "log", toggled::toggle) { entry, first, last ->
                    LogRow(
                        entry.item, routine = entry.key in routineKeys,
                        icon = recordIcon(entry.item, ledger.groupOf, ledger.groupIcons),
                        app = entry.key to labels, first = first, last = last,
                        onClick = { onOpenLog(entry) }
                    )
                }
                if (filterSpacing > 0) item(key = "timeline-space") {
                    Spacer(Modifier.height(with(density) { filterSpacing.toDp() }))
                }
            }
            HomeView.APPS -> {
                item {
                    FilterChips(AppRank.entries.map { stringResource(it.label) }, rank.ordinal, Modifier.padding(top = 4.dp)) {
                        rankName = AppRank.entries[it].name; rankAll = false
                    }
                }
                item {
                    // Ranked by what was measured; routine system work is kept apart, below, folded.
                    val ranked = rank.rank(ledger.apps.filter { it.key !in routineKeys })
                    val routine = rank.rank(ledger.apps.filter { it.key in routineKeys })
                    Column(Modifier.padding(top = 12.dp)) {
                        if (ranked.isEmpty()) EmptyCard(stringResource(rank.empty))
                        else Card {
                            val top = ranked.first().second
                            ranked.take(if (rankAll) ranked.size else RANK_PAGE).forEachIndexed { index, (app, value) ->
                                if (index > 0) InsetDivider(start = RANK_DIVIDER_START)
                                RankRow(app, index + 1, value, top, labels, routine = false) { onOpenApp(app.key) }
                            }
                            if (!rankAll && ranked.size > RANK_PAGE) {
                                InsetDivider(start = 0.dp)
                                Text(stringResource(R.string.rank_show_all, formatCount(ranked.size.toLong())),
                                    style = MaterialTheme.typography.bodySmall, color = Ink.secondary, textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().clickable { rankAll = true }.padding(vertical = 12.dp))
                            }
                        }
                        if (routine.isNotEmpty()) {
                            Row(Modifier.fillMaxWidth().clip(AppShapes.Small).clickable { routineOpen = !routineOpen }
                                .padding(horizontal = 4.dp, vertical = 12.dp)) {
                                Text(stringResource(R.string.rank_routine, formatCount(routine.size.toLong())),
                                    style = MaterialTheme.typography.bodySmall, color = Ink.secondary, modifier = Modifier.weight(1f))
                                Text(stringResource(if (routineOpen) R.string.rank_routine_hide else R.string.rank_routine_show),
                                    style = MaterialTheme.typography.bodySmall, color = Ink.secondary)
                            }
                            if (routineOpen) Card {
                                val top = routine.first().second
                                routine.forEachIndexed { index, (app, value) ->
                                    if (index > 0) InsetDivider(start = RANK_DIVIDER_START)
                                    RankRow(app, null, value, top, labels, routine = true) { onOpenApp(app.key) }
                                }
                            }
                        }
                    }
                }
            }
            HomeView.TYPES -> {
                item { Spacer(Modifier.height(12.dp)) }
                item {
                    // Kinds of data and the network, the one used last first.
                    val top = ledger.groups.maxOfOrNull { it.count } ?: 1
                    val network = ledger.networkApps
                    val networkLast = ledger.latestNetwork
                        ?: network.maxOfOrNull { it.last } ?: 0L
                    val rows: List<Pair<Long, @Composable () -> Unit>> = ledger.groups.map { group ->
                        group.last to @Composable { GroupRow(group, top, onClick = { onOpenGroup(group.id) }) }
                    } + if (network.isEmpty()) emptyList() else listOf(networkLast to @Composable {
                        val bytes = network.sumOf { it.traffic?.total ?: 0L }
                        val flagged = network.sumOf { it.sentInBackground }
                        ListRow(
                            title = stringResource(R.string.section_network),
                            subtitle = listOfNotNull(
                                pluralStringResource(R.plurals.apps_count, network.size, formatCount(network.size.toLong())),
                                if (bytes > 0) formatBytes(bytes) else null
                            ).joinToString(" · "),
                            leading = { TimeColumn(networkLast); IconTile(JiumiIcons.Network) },
                            extra = if (flagged > 0) {
                                { Badges { Badge(stringResource(R.string.badge_sent_in_background, formatCount(flagged.toLong())), StatusWarning) } }
                            } else null,
                            onClick = { onOpenGroup(NETWORK_GROUP) }
                        ) { Chevron() }
                    })
                    Card {
                        rows.sortedByDescending { it.first }.forEachIndexed { index, (_, row) ->
                            if (index > 0) InsetDivider(start = TIME_COLUMN_WIDTH + 60.dp)
                            row()
                        }
                    }
                }
            }
        }
    }
    if (view == HomeView.TIMELINE.name) {
        TimelineScrubber(list, days, Modifier.align(Alignment.CenterEnd).windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = top + 8.dp, bottom = 16.dp + LocalTabBarInset.current),
            trailingItems = if (filterSpacing > 0) 1 else 0)
    }
    }
    }
}

/** Between the range and the cards it governs: close enough to read as one group under it. */
private val RANGE_GAP = 8.dp

private enum class HomeView(val label: Int) {
    TIMELINE(R.string.view_timeline), APPS(R.string.view_by_app), TYPES(R.string.view_by_type)
}

/**
 * How the timeline narrows down: by circumstance, never by kind. What was done (reads, writes, calls,
 * Bluetooth, the network) is sorted in By type; here it is everything or what happened in the
 * background. What Android refused gave the app nothing, so it is marked on its row, not filtered for.
 */
private enum class TimelineFilter(val label: Int) {
    ALL(R.string.filter_all), BACKGROUND(R.string.filter_background);

    fun matches(item: TimelineItem): Boolean = when (this) {
        ALL -> true
        BACKGROUND -> (netView(item) ?: headOf(item)).let { isUnattended(it) || (it as? TimelineItem.NetItem)?.sentInBackground == true }
    }
}

/** A row of small choices, one picked: filled when picked, quiet otherwise. */
@Composable
private fun FilterChips(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Surface(onClick = { onSelect(i) }, shape = RoundedCornerShape(50),
                color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface) {
                Text(label, style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
            }
        }
    }
}

@Composable
fun rangeLabel(range: Range, session: MonitorController.SessionView?): String = when (range) {
    // A session picked from the monitoring record is named by its start.
    Range.SESSION -> if (session == null || session.latest) stringResource(R.string.range_session) else shortDate(session.session.start)
    Range.TODAY -> stringResource(R.string.range_today)
    Range.WEEK -> stringResource(R.string.range_week)
    Range.MONTH -> stringResource(R.string.range_month)
}

/**
 * Where monitoring stands, as one card: a dot, the state or the running clock, and at the right the one
 * thing to do. Beneath, inside the card, what goes with the state ([detail]: the last hour's activity
 * while monitoring, the last session or what was covered otherwise), or [message], an error.
 */
@Composable
private fun MonitorCapsule(
    tone: Color,
    live: Boolean,
    title: String,
    clock: String? = null,
    corner: (@Composable () -> Unit)? = null,
    message: String? = null,
    detail: (@Composable ColumnScope.() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Column(Modifier.padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = if (detail != null || message != null) 14.dp else 8.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusDot(tone, live)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (clock == null) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (clock == null) Ink.primary else Ink.secondary, maxLines = 1)
                if (clock != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(clock, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.SemiBold, color = Ink.primary, maxLines = 1)
                }
                Spacer(Modifier.weight(1f))
                corner?.invoke()
            }
            Column(Modifier.padding(end = 6.dp)) {
                if (message != null) {
                    Text(message, style = MaterialTheme.typography.bodySmall, color = StatusWarning, modifier = Modifier.padding(top = 6.dp))
                } else if (detail != null) {
                    Spacer(Modifier.height(8.dp))
                    detail()
                }
            }
        }
    }
}

/** A line of quiet figures, label then value: "Apps 66 · Records 340". */
@Composable
private fun RowScope.Figures(figures: List<Pair<String, String>>) {
    val primary = Ink.primary
    val secondary = Ink.secondary
    Text(
        androidx.compose.ui.text.buildAnnotatedString {
            figures.forEachIndexed { i, (value, label) ->
                if (i > 0) withStyle(androidx.compose.ui.text.SpanStyle(color = secondary.copy(alpha = 0.5f))) { append("  ·  ") }
                withStyle(androidx.compose.ui.text.SpanStyle(color = secondary)) { append(label); append(' ') }
                withStyle(androidx.compose.ui.text.SpanStyle(color = primary, fontWeight = FontWeight.SemiBold)) { append(value) }
            }
        },
        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), maxLines = 1,
        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
    )
}

/** The one step to take, as a small graphite capsule at the panel's right. */
@Composable
private fun CornerButton(text: String, enabled: Boolean = true, busy: Boolean = false, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled && !busy, shape = CircleShape, color = Ink.primary,
        contentColor = MaterialTheme.colorScheme.surface, modifier = Modifier.height(32.dp)) {
        Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            if (busy) CircularProgressIndicator(strokeWidth = 1.5.dp, modifier = Modifier.size(14.dp), color = LocalContentColor.current)
            else Text(text, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        }
    }
}

/** The state's dot; while monitoring it breathes, a soft halo swelling and fading behind it. */
@Composable
private fun StatusDot(tone: Color, live: Boolean) {
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
    val t by pulse.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(
        androidx.compose.animation.core.tween(1800, easing = androidx.compose.animation.core.FastOutSlowInEasing)), label = "pulseT")
    Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
        if (live) Box(Modifier.size(18.dp).graphicsLayer { scaleX = 0.4f + 0.6f * t; scaleY = 0.4f + 0.6f * t; alpha = 1f - t }
            .background(tone.copy(alpha = 0.45f), CircleShape))
        Box(Modifier.size(9.dp).background(tone, CircleShape))
    }
}

/** Stopping, as a media control: a round grey button holding a small square, at the panel's corner. */
@Composable
private fun StopButton(enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = CircleShape, color = Ink.fill, modifier = Modifier.size(32.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(10.dp).background(Ink.primary.copy(alpha = if (enabled) 1f else 0.4f), RoundedCornerShape(2.5.dp)))
        }
    }
}

/** A running time as a clock, hours:minutes:seconds; past a day, in days and hours. */
@Composable
private fun clock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    if (total >= 86_400) return formatDuration(ms)
    return "%d:%02d:%02d".format(total / 3600, total / 60 % 60, total % 60)
}

/**
 * Monitoring in progress: the running clock and stop, and what this session held so far: how many
 * apps, how many reads and calls, and the monitor's own memory. Its curve is the session range's, below.
 */
@Composable
private fun SessionStrip(state: MonitorController.UiState, controller: MonitorController) {
    val session = state.session ?: return
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(1000) } }
    val live = state.live
    val stopLabel = stringResource(R.string.action_stop_monitoring)
    val figures = listOfNotNull(
        formatCount(state.liveApps.toLong()) + stringResource(R.string.chart_unit_apps) to stringResource(R.string.stat_apps),
        formatCount((live?.total ?: 0).toLong()) + stringResource(R.string.chart_unit_times) to stringResource(R.string.stat_accesses),
        state.footprint?.let { "${(it.pssKb / 1024).toInt()} MB" to stringResource(R.string.stat_memory) }
    )
    MonitorCapsule(
        tone = chartColor, live = true,
        title = stringResource(R.string.monitor_live),
        clock = clock(session.coverage.coveredMs + (now - session.measuredAt).coerceAtLeast(0L)),
        corner = {
            Box(Modifier.semantics { contentDescription = stopLabel }) {
                StopButton(enabled = state.busy == null, onClick = controller::stopMonitor)
            }
        },
        detail = {
            Row(Modifier.fillMaxWidth().padding(top = 2.dp)) { Figures(figures) }
        },
        message = if (state.busy == Busy.STOPPING) stringResource(R.string.monitor_saving) else statusMessage(state)
    )
}

/** The session is open but the monitor stopped (a reboot, Wireless debugging off): resume it, or end it. */
@Composable
private fun InterruptedCard(
    state: MonitorController.UiState, session: MonitorController.SessionView, controller: MonitorController,
    onOpenNetwork: () -> Unit
) {
    val network = state.method == com.ybblab.jiumi.access.AccessMethod.NETWORK
    // Looking for a monitor that may still run, starting one, or connecting to start one.
    val recovering = state.awaitingMonitor || state.busy == Busy.STARTING || state.pairingState == PairingState.CONNECTING
    Column {
        MonitorCapsule(
            tone = StatusWarning, live = false,
            title = stringResource(if (recovering) R.string.monitor_recovering else R.string.monitoring_interrupted),
            corner = {
                CornerButton(stringResource(R.string.action_resume_short),
                    busy = recovering, onClick = {
                        if (network && state.problem == Problem.NETWORK_AUTHORIZATION) onOpenNetwork()
                        else controller.startMonitor()
                    })
            },
            detail = { Row(verticalAlignment = Alignment.CenterVertically) {
                Figures(listOf(
                    formatDuration(session.coverage.coveredMs) to stringResource(R.string.stat_covered),
                    formatCount((state.ledger?.apps?.size ?: 0).toLong()) + stringResource(R.string.chart_unit_apps) to stringResource(R.string.stat_apps)
                ))
                Text(stringResource(R.string.action_end_short), style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium, color = Ink.secondary,
                    modifier = Modifier.clip(CircleShape).clickable(enabled = state.busy == null, onClick = controller::stopMonitor)
                        .padding(horizontal = 8.dp, vertical = 4.dp))
            } },
            message = statusMessage(state)
        )
        if (network && !recovering && (state.problem != null || !state.ready)) {
            androidx.compose.material3.TextButton(onClick = onOpenNetwork) { Text(stringResource(R.string.network_adb_connection_settings)) }
        }
    }
}

/**
 * Not monitoring: start, once Wireless debugging is paired; until then the card says so and leads to
 * pairing. Beneath, when there was one, the last session.
 */
@Composable
private fun ServiceCard(
    state: MonitorController.UiState,
    controller: MonitorController,
    pairingFlow: Boolean,
    paired: Boolean,
    onOpenPairing: () -> Unit,
    onOpenNetwork: () -> Unit
) {
    val needsPairing = !paired && !pairingFlow
    val last = state.session?.takeIf { !it.running }
    val lastText = last?.let { stringResource(R.string.monitor_last, shortDateTime(it.session.start), formatDuration(it.coverage.coveredMs)) }
    MonitorCapsule(
        tone = if (needsPairing) StatusWarning else MaterialTheme.colorScheme.onSurfaceVariant,
        live = false,
        title = when {
            pairingFlow -> stringResource(R.string.pairing_in_progress)
            // A start under way says so whatever the way's own state, so a tap is never met by nothing.
            state.busy == Busy.STARTING && state.pairingState == PairingState.CONNECTING -> stringResource(R.string.pairing_connecting)
            state.busy == Busy.STARTING -> stringResource(R.string.service_starting)
            !paired && state.method == com.ybblab.jiumi.access.AccessMethod.ROOT -> stringResource(rootStatus(state.root))
            !paired && state.method == com.ybblab.jiumi.access.AccessMethod.SHIZUKU -> stringResource(R.string.service_shizuku_needed)
            !paired && state.method == com.ybblab.jiumi.access.AccessMethod.NETWORK -> stringResource(R.string.network_adb_not_configured)
            !paired -> stringResource(if (state.trust == PairingTrustState.PAIRING_REQUIRED) R.string.service_repair else R.string.service_unpaired)
            else -> stringResource(R.string.hero_idle)
        },
        corner = when {
            state.busy == Busy.STARTING -> { {
                CornerButton(stringResource(R.string.action_start_short), busy = true, onClick = {})
            } }
            !paired && state.method == com.ybblab.jiumi.access.AccessMethod.ROOT -> { {
                CornerButton(stringResource(rootAction(state.root)), busy = state.root == RootShell.State.CHECKING,
                    onClick = controller::authorizeRoot)
            } }
            !paired && state.method == com.ybblab.jiumi.access.AccessMethod.SHIZUKU -> { {
                CornerButton(stringResource(shizukuNextAction(state.shizuku))) { runShizukuNextAction(controller, state.shizuku) }
            } }
            state.method == com.ybblab.jiumi.access.AccessMethod.NETWORK && (!paired || state.problem == Problem.NETWORK_AUTHORIZATION) -> {
                { CornerButton(stringResource(R.string.network_adb_configure), onClick = onOpenNetwork) }
            }
            !paired -> { { CornerButton(stringResource(R.string.action_pair), enabled = !pairingFlow, onClick = onOpenPairing) } }
            !pairingFlow -> { {
                CornerButton(stringResource(R.string.action_start_short),
                    busy = state.busy == Busy.STARTING || state.pairingState == PairingState.CONNECTING, onClick = controller::startMonitor)
            } }
            else -> null
        },
        detail = lastText?.takeIf { paired }?.let { text -> {
            Text(text, style = MaterialTheme.typography.bodySmall, color = Ink.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } },
        message = statusMessage(state) ?: last?.takeIf { it.coverage.partialTrace }
            ?.let { stringResource(R.string.error_service_stop_partial) }
    )
}

@Composable
private fun AttentionCard(items: List<Attention>, onOpen: (String) -> Unit, activity: com.ybblab.jiumi.data.Activity? = null) {
    val raised = items.filter { it.apps.isNotEmpty() }
    Card {
        // Its title is inside, as the overview's is; then when it was raised, and what.
        val timed = activity?.takeIf { raised.isNotEmpty() && it.hits.any { hit -> hit.total > 0 } }
        // 10 and the card's own 4: the title sits as far down as the overview's.
        AttentionHeader(raised.size, key = timed != null, Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp))
        if (timed != null) {
            AttentionStrip(timed, Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp))
            InsetDivider(start = 16.dp)
        }
        if (raised.isEmpty()) {
            Text(stringResource(R.string.attention_none), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            return@Card
        }
        raised.forEachIndexed { index, item ->
            if (index > 0) InsetDivider()
            ListRow(
                title = attentionName(item.id),
                subtitle = pluralStringResource(R.plurals.apps_count, item.apps.size, formatCount(item.apps.size.toLong())),
                leading = { IconTile(attentionIcon(item.id)) },
                onClick = { onOpen(item.id) }
            ) { Chevron() }
        }
    }
}

fun attentionIcon(id: String): ImageVector = when (id) {
    AttentionRules.SENT_IN_BACKGROUND -> JiumiIcons.Network
    AttentionRules.BACKGROUND -> JiumiIcons.Layers
    AttentionRules.FREQUENT -> JiumiIcons.Activity
    AttentionRules.FREQUENT_DENIED -> JiumiIcons.Ban
    else -> JiumiIcons.Info
}

/** Route id of the network list, next to the category groups from the rules. */
const val NETWORK_GROUP = "network"

/** A first-level kind with its share of the busiest one as a bar. */
@Composable
private fun GroupRow(group: GroupSummary, top: Int, onClick: () -> Unit) {
    ListRow(
        title = groupName(group.id),
        subtitle = pluralStringResource(R.plurals.apps_count, group.apps, formatCount(group.apps.toLong())),
        leading = { TimeColumn(group.last); IconTile(groupIcon(group.icon)) },
        extra = {
            ShareBar(group.count.toFloat() / top.coerceAtLeast(1), Modifier.padding(top = 8.dp))
            if (group.background > 0) Badges {
                Badge(stringResource(R.string.badge_background, formatCount(group.background.toLong())), StatusWarning)
            }
        },
        onClick = onClick
    ) {
        TrailingValue(formatCount(group.count.toLong()))
        Chevron()
    }
}

/** Symbols named by the rules file. */
fun groupIcon(name: String): ImageVector = when (name) {
    "map_pin" -> JiumiIcons.MapPin
    "smartphone" -> JiumiIcons.Smartphone
    "person" -> JiumiIcons.Person
    "message" -> JiumiIcons.MessageCheck
    "calendar" -> JiumiIcons.Calendar
    "camera" -> JiumiIcons.Camera
    "image" -> JiumiIcons.Image
    "clipboard" -> JiumiIcons.Clipboard
    "activity" -> JiumiIcons.Activity
    "layers" -> JiumiIcons.Layers
    "notifications" -> JiumiIcons.Notifications
    "network" -> JiumiIcons.Network
    "eye" -> JiumiIcons.Eye
    else -> JiumiIcons.Info
}

/**
 * What the by-app list ranks by, named as the timeline's filters: every record (accesses and calls),
 * or the reads while not in use. Apps with none of it are left out; ties go to the one active last.
 */
private enum class AppRank(val label: Int, val empty: Int, val measure: (AppSummary) -> Long) {
    ALL(R.string.filter_all, R.string.rank_empty_all, { (it.accesses + it.calls).toLong() }),
    BACKGROUND(R.string.filter_background, R.string.rank_empty_background, { it.background.toLong() });

    fun rank(apps: List<AppSummary>): List<Pair<AppSummary, Long>> = apps.map { it to measure(it) }
        .filter { it.second > 0 }
        .sortedWith(compareByDescending<Pair<AppSummary, Long>> { it.second }.thenByDescending { it.first.last })
}

/** How many ranked apps show before "Show all". */
private const val RANK_PAGE = 10
private val RANK_WIDTH = 20.dp
/** Past the rank, the icon and their gaps, as the other lists' dividers start past the icon. */
private val RANK_DIVIDER_START = RANK_WIDTH + 8.dp + 60.dp

/** One ranked app: its place, what it touched, its share of the first one's amount, and the badges. */
@Composable
private fun RankRow(
    app: AppSummary, place: Int?, value: Long, top: Long, labels: AppLabels,
    routine: Boolean, onClick: () -> Unit
) {
    ListRow(
        title = labels.label(app.key),
        subtitle = appSummaryText(app),
        leading = {
            Text(place?.toString().orEmpty(), style = MaterialTheme.typography.labelMedium,
                fontWeight = if (place != null && place <= 3) FontWeight.Medium else FontWeight.Normal,
                color = if (place != null && place <= 3) Ink.primary else Ink.secondary,
                textAlign = TextAlign.Center, modifier = Modifier.width(RANK_WIDTH))
            Spacer(Modifier.width(8.dp))
            AppIcon(labels, app.key.pkg, LEADING_SIZE)
        },
        extra = {
            ShareBar(value.toFloat() / top.coerceAtLeast(1), Modifier.padding(top = 8.dp))
            AppBadges(app, routine)
        },
        onClick = onClick
    ) {
        TrailingValue(formatCount(value) + stringResource(R.string.chart_unit_times))
        Chevron()
    }
}

/** The kinds of information an app touched, most used first; for an app that only used the network, its traffic. */
@Composable
private fun appSummaryText(app: AppSummary): String {
    val kinds = app.groups.map { groupName(it) }
    return when {
        kinds.isNotEmpty() -> kinds.joinToString(stringResource(R.string.list_separator))
        app.traffic != null -> stringResource(R.string.traffic_total, formatBytes(app.traffic.total))
        else -> ""
    }
}

/**
 * One app in a list: what kinds of information it touched and when, and badges for what needs a
 * look. [value] replaces the time on the right, for lists measured by something else.
 */
@Composable
fun AppRow(app: AppSummary, labels: AppLabels, value: String? = null, routine: Boolean = false, onClick: () -> Unit) {
    // What it did, every kind of data it reached, most used first; the time leads the row.
    ListRow(
        title = labels.label(app.key),
        subtitle = appSummaryText(app),
        leading = { TimeColumn(app.last); AppIcon(labels, app.key.pkg, LEADING_SIZE) },
        onClick = onClick,
        extra = { AppBadges(app, routine) }
    ) {
        if (value != null) TrailingValue(value)
        Chevron()
    }
}

/**
 * Fixed order, in plain words: missing from the privacy record, sent while not in use, read while
 * not in use, many reads within 60 minutes, many refusals, then what it sent. Routine system work shows only what it sent.
 */
@Composable
fun AppBadges(app: AppSummary, routine: Boolean) {
    val grey = MaterialTheme.colorScheme.onSurfaceVariant
    val badges = buildList {
        if (!routine) {
            if (app.sentInBackground > 0) add(stringResource(R.string.badge_sent_in_background, formatCount(app.sentInBackground.toLong())) to StatusWarning)
            if (app.background > 0) add(stringResource(R.string.badge_background, formatCount(app.background.toLong())) to StatusWarning)
            app.frequent?.let { add(stringResource(R.string.badge_frequent, categoryName(it.category), formatCount(it.count.toLong())) to StatusWarning) }
            app.frequentDenied?.let { add(stringResource(R.string.badge_frequent_denied, categoryName(it.category), formatCount(it.count.toLong())) to StatusWarning) }
        }
        // The system's own count: in the background it needs a look; otherwise only for an app the monitor did not see.
        if (!routine && app.systemBackground > 0) add(stringResource(R.string.badge_system_background, formatCount(app.systemBackground)) to StatusWarning)
        else if (app.systemTotal > 0 && app.accesses + app.calls == 0) add(stringResource(R.string.badge_system_total, formatCount(app.systemTotal)) to grey)
        if (app.uploaded > 0) add(stringResource(R.string.uploaded, formatBytes(app.uploaded)) to grey)
    }
    if (badges.isEmpty()) return
    Badges { badges.forEach { (text, color) -> Badge(text, color) } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Badges(content: @Composable () -> Unit) {
    FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        content()
    }
}

@Composable
fun kindName(kind: AppKind): String = stringResource(
    when (kind) {
        AppKind.THIRD_PARTY -> R.string.kind_third_party
        AppKind.PREINSTALLED -> R.string.kind_preinstalled
        AppKind.SYSTEM_APP -> R.string.kind_system_app
        AppKind.SYSTEM -> R.string.kind_system
    }
)

@Composable
fun attentionName(id: String): String = stringResource(attentionNameRes(id))

/** The name of an item raised for attention; notifications use it outside Compose. */
fun attentionNameRes(id: String): Int = when (id) {
    AttentionRules.BACKGROUND -> R.string.attention_background
    AttentionRules.SENT_IN_BACKGROUND -> R.string.attention_sent_in_background
    AttentionRules.FREQUENT -> R.string.attention_frequent
    AttentionRules.FREQUENT_DENIED -> R.string.attention_frequent_denied
    else -> R.string.cap_unknown
}

/**
 * Why monitoring is not running, when something went wrong: the connection's own reason when it
 * could not be made (Wireless debugging off, no Wi-Fi), else what failed.
 */
@Composable
fun statusMessage(state: MonitorController.UiState): String? {
    if (state.method == com.ybblab.jiumi.access.AccessMethod.ROOT) {
        rootError(state.root)?.let { return stringResource(it) }
    }
    // Wireless debugging's own reasons say nothing about root or Shizuku.
    val failure = state.pairingFailure?.takeIf { state.method == com.ybblab.jiumi.access.AccessMethod.WIRELESS }
        ?.let { stringResource(it.messageRes()) }
    return when (val p = state.problem) {
        null -> failure
        Problem.CONNECT -> failure ?: problemText(p)
        else -> problemText(p)
    }
}

@Composable
private fun problemText(problem: Problem): String = stringResource(
    when (problem) {
        Problem.CONNECT -> R.string.error_connect
        Problem.START -> R.string.error_service_start
        Problem.STOP -> R.string.error_service_stop
        Problem.STOP_PARTIAL -> R.string.error_service_stop_partial
        Problem.SHIZUKU -> R.string.error_shizuku
        Problem.NETWORK_UNAVAILABLE -> R.string.network_adb_unavailable
        Problem.NETWORK_AUTHORIZATION -> R.string.network_adb_authorization_needed
        Problem.NETWORK_IDENTITY -> R.string.network_adb_wrong_identity
    }
)

@Composable
fun EmptyCard(text: String) {
    Card {
        Column(Modifier.fillMaxWidth().widthIn(min = 0.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp))
        }
    }
}

/** How an attention item is judged, in plain words. */
@Composable
fun attentionRule(id: String): String? = when (id) {
    AttentionRules.BACKGROUND -> R.string.attention_rule_background
    AttentionRules.SENT_IN_BACKGROUND -> R.string.attention_rule_sent_in_background
    AttentionRules.FREQUENT -> R.string.attention_rule_frequent
    AttentionRules.FREQUENT_DENIED -> R.string.attention_rule_frequent_denied
    else -> null
}?.let { stringResource(it) }

