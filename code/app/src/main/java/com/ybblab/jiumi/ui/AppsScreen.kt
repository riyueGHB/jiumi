// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.ybblab.jiumi.ui.theme.Ink
import com.ybblab.jiumi.MonitorController
import com.ybblab.jiumi.MonitorController.AppState
import com.ybblab.jiumi.MonitorController.ListedApp
import com.ybblab.jiumi.R
import com.ybblab.jiumi.data.AppKey
import com.ybblab.jiumi.data.AppSource
import com.ybblab.jiumi.data.ManageAction
import com.ybblab.jiumi.ui.icons.JiumiIcons
import com.ybblab.jiumi.ui.theme.AppShapes

private enum class AppsFilter(val label: Int, val state: AppState?) {
    ALL(R.string.apps_filter_all, null),
    DISABLED(R.string.apps_filter_disabled, AppState.DISABLED),
    DELETED(R.string.state_deleted, AppState.DELETED)
}

/** Groups in their display order. */
internal enum class AppGroup(val label: Int, val source: AppSource?, val tint: Color) {
    STORE(R.string.apps_stat_store, AppSource.STORE, Color(0xFF4F8CF0)),
    SIDELOADED(R.string.apps_stat_sideloaded, AppSource.SIDELOADED, Color(0xFFE5A23B)),
    PREINSTALLED(R.string.apps_stat_preinstalled, AppSource.PREINSTALLED, Color(0xFF3FB28C)),
    COMPONENT(R.string.apps_stat_component, null, Color(0xFF9A9AA4)),
    MODULE(R.string.apps_stat_module, null, Color(0xFF8E79BE)),
    HISTORICAL(R.string.apps_group_historical, null, Color(0xFF9A9AA4));

    companion object {
        val inventoryGroups = entries.filter { it != HISTORICAL }

        fun of(app: ListedApp) = when {
            app.module -> MODULE
            app.state == AppState.HISTORICAL -> HISTORICAL
            app.component -> COMPONENT
            else -> entries.first { it.source == app.source }
        }
    }
}

/**
 * Every app on the phone, system components included, grouped by where it came from. Above the
 * list, in a bar that slides away while scrolling down: the total, filters, search and shortcuts
 * to each group.
 */
@Composable
fun AppsScreen(controller: MonitorController, labels: AppLabels, onOpenApp: (AppKey) -> Unit, onOpenModule: (String) -> Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(AppsFilter.ALL.name) }
    var query by rememberSaveable { mutableStateOf("") }
    // Read again after every report, which follows each change made here. The phone's own list comes
    // first and at once (the last one shown, before that); the monitor's, which fills in apps some
    // phones hide, follows only when it is due, since it costs a round of shell commands.
    val apps by produceState(lastListed, state.ledger, state.running) {
        suspend fun named(list: List<ListedApp>) = withContext(Dispatchers.IO) {
            list.map { it to (it.backup?.label ?: if (it.uid >= 0) labels.label(AppKey(it.uid, it.pkg)) else it.pkg) }
                .sortedWith(compareBy({ AppGroup.of(it.first).ordinal }, { it.second.lowercase() }))
        }
        value = named(controller.listedApps()).also { lastListed = it }
        if (controller.shellListStale()) {
            value = named(controller.listedApps(fresh = true)).also { lastListed = it }
        }
    }
    val chosen = AppsFilter.entries.firstOrNull { it.name == filter } ?: AppsFilter.ALL
    val q = query.trim()
    // The total describes the detected inventory, including device-wide APEX modules.
    val packageCount = remember(apps) {
        apps?.count { (app, _) ->
            app.module || when (app.state) {
                AppState.ACTIVE, AppState.DISABLED, AppState.SUSPENDED -> true
                else -> false
            }
        }
    }
    val history by produceState(emptyList<Pair<ListedApp, String>>(), q, chosen) {
        value = emptyList()
        if (q.isNotEmpty() && chosen == AppsFilter.ALL) {
            delay(200)
            value = controller.historicalApps(q).map { it to it.pkg }
        }
    }
    val groups = remember(apps, history, chosen, q) {
        val live = apps.orEmpty()
        val known = live.mapNotNull { (app, _) -> app.pkg.takeUnless { app.module } }.toHashSet()
        (live + history.filter { (app, _) -> app.pkg !in known })
            .filter { (app, name) ->
                (chosen.state == null || app.state == chosen.state) &&
                    (q.isEmpty() || name.contains(q, ignoreCase = true) || app.pkg.contains(q, ignoreCase = true))
            }
            .groupBy { AppGroup.of(it.first) }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Where each group's title sits in the list: after a loading or empty notice if there is one,
    // then for every group before it a title, its rows and a gap.
    val header = if (apps == null || groups.isEmpty()) 1 else 0
    val titleIndex = remember(groups, header) {
        var i = header
        groups.mapValues { (_, list) -> i.also { i += list.size + 2 } }
    }
    // The group being read: the last one whose title is at or above the top of the list.
    val current by remember(titleIndex) {
        derivedStateOf { titleIndex.entries.lastOrNull { it.value <= listState.firstVisibleItemIndex }?.key }
    }
    val scrolled by remember { derivedStateOf { listState.canScrollBackward } }

    HidingTopBar(stringResource(R.string.tab_apps), scrolled, trailing = {
        packageCount?.let { count ->
            Text(pluralStringResource(R.plurals.apps_package_total, count, formatCount(count.toLong())),
                style = MaterialTheme.typography.labelLarge, color = Ink.secondary)
        }
    }, extra = {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = PageTopGap, bottom = 4.dp)) {
            Segmented(
                AppsFilter.entries.map { stringResource(it.label) },
                selected = chosen.ordinal,
                onSelect = { filter = AppsFilter.entries[it].name },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            SearchField(query, onChange = { query = it })
            Spacer(Modifier.height(6.dp))
            GroupStats(counts = groups.mapValues { it.value.size }, current = current, onJump = { group ->
                titleIndex[group]?.let { scope.launch { listState.animateScrollToItem(it) } }
            })
        }
    }) { top ->
    // What the fast-scroll bubble says for each item of the list: the group, and the initial of the app.
    val groupNames = AppGroup.entries.associateWith { stringResource(it.label) }
    val itemLabels = remember(groups, header, groupNames) {
        buildList {
            repeat(header) { add(null) }
            groups.forEach { (group, list) ->
                val name = groupNames.getValue(group)
                add(name)
                list.forEach { (_, appName) -> add("$name · ${appName.firstOrNull()?.uppercaseChar() ?: ""}") }
                add(name)
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + PageTopGap, bottom = 32.dp + LocalTabBarInset.current)
    ) {
        if (apps == null) {
            item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (groups.isEmpty()) {
            item(key = "empty") {
                EmptyCard(stringResource(when {
                    query.isNotBlank() -> R.string.apps_empty_search
                    chosen == AppsFilter.DISABLED -> R.string.apps_empty_disabled
                    chosen == AppsFilter.DELETED -> R.string.apps_empty_deleted
                    else -> R.string.apps_empty_search
                }))
            }
        }
        // One card per group, each a few hundred rows at most; rows are items so long lists stay light.
        groups.forEach { (group, list) ->
            item(key = "title-$group") {
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    GroupTitle(stringResource(group.label) + " · " + formatCount(list.size.toLong()))
                }
            }
            list.forEachIndexed { index, (app, name) ->
                item(key = "${if (app.module) "apex" else "app"}-${app.pkg}") {
                    AppListRow(controller, labels, app, name, first = index == 0, last = index == list.lastIndex,
                        onClick = { if (app.module) onOpenModule(app.pkg) else onOpenApp(AppKey(app.uid, app.pkg)) }) {
                        Chevron()
                    }
                }
            }
            item(key = "gap-$group") { Spacer(Modifier.height(16.dp)) }
        }
    }
    FastScroller(listState, modifier = Modifier.align(Alignment.TopEnd)
        .padding(top = top + PageTopGap, bottom = 16.dp + LocalTabBarInset.current)) { i -> itemLabels.getOrNull(i) }
    }
    }
}

@Composable
fun SystemModuleScreen(controller: MonitorController, labels: AppLabels, pkg: String, onBack: () -> Unit, onOpenApp: (AppKey) -> Unit) {
    val info by produceState<Pair<com.ybblab.jiumi.data.SystemModule, List<AppKey>>?>(null, pkg) {
        value = controller.systemModule(pkg)
    }
    SubScreen(stringResource(R.string.apps_apex_title), onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            item {
                Card {
                    ListRow(stringResource(R.string.key_package), subtitle = pkg, leading = { IconTile(JiumiIcons.Cpu) })
                    info?.first?.version?.let {
                        InsetDivider()
                        ListRow(stringResource(R.string.apps_apex_version), subtitle = it.toString())
                    }
                    info?.first?.path?.let {
                        InsetDivider()
                        ListRow(stringResource(R.string.apps_apex_path), subtitle = it)
                    }
                }
            }
            val apps = info?.second.orEmpty()
            if (apps.isNotEmpty()) {
                item { GroupTitle(stringResource(R.string.apps_apex_packages), Modifier.padding(top = 20.dp)) }
                apps.forEachIndexed { index, key ->
                    item(key = key.pkg) {
                        CardSlice(index == 0, index == apps.lastIndex) {
                            if (index > 0) InsetDivider()
                            val name = if (key.uid >= 0) labels.label(key) else key.pkg.orEmpty()
                            ListRow(name, subtitle = key.pkg?.takeUnless { it == name },
                                leading = { AppIcon(labels, key.pkg, LEADING_SIZE) },
                                onClick = { onOpenApp(key) }) { Chevron() }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Shortcuts to the groups in the list. The group in view is shaded and empty groups are disabled.
 */
@Composable
private fun GroupStats(counts: Map<AppGroup, Int>, current: AppGroup?, onJump: (AppGroup) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, AppShapes.Card).padding(4.dp)) {
        val perRow = if (maxWidth < 300.dp || LocalDensity.current.fontScale > 1.25f) 3 else 5
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AppGroup.inventoryGroups.chunked(perRow).forEach { groups ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    groups.forEachIndexed { index, group ->
                        if (index > 0) Box(
                            Modifier.padding(vertical = 14.dp).width(0.5.dp).fillMaxHeight()
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                        val count = counts[group] ?: 0
                        val enabled = count > 0
                        val fill by animateColorAsState(
                            if (group == current && enabled) Ink.fill else Color.Transparent,
                            label = "groupHere"
                        )
                        Column(
                            Modifier.weight(1f).heightIn(min = 48.dp).clip(AppShapes.Control)
                                .background(fill).clickable(enabled = enabled) { onJump(group) }.padding(3.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(formatCount(count.toLong()), color = if (enabled) Ink.primary else Ink.tertiary,
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 12.sp, lineHeight = 16.sp,
                                    fontFeatureSettings = "tnum"))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Box(Modifier.size(5.dp).clip(CircleShape)
                                    .background(group.tint.copy(alpha = if (enabled) 1f else 0.35f)))
                                Text(stringResource(group.label), color = if (enabled) Ink.secondary else Ink.tertiary,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
                                    textAlign = TextAlign.Center, maxLines = 2)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** What a group of apps is called by where its apps came from. */
@Composable
fun sourceName(source: AppSource): String = stringResource(when (source) {
    AppSource.STORE -> R.string.source_store
    AppSource.SIDELOADED -> R.string.source_sideloaded
    AppSource.PREINSTALLED -> R.string.source_preinstalled
})

/** The list last shown, with names, so coming back to the tab shows it at once while it is read again. */
private var lastListed: List<Pair<ListedApp, String>>? = null

@Composable
private fun stateText(app: ListedApp): String? = when (app.state) {
    AppState.ACTIVE -> if (app.lapsed) stringResource(R.string.state_lapsed) else null
    AppState.DISABLED -> stringResource(R.string.state_disabled)
    AppState.SUSPENDED -> stringResource(R.string.state_suspended)
    AppState.DELETED -> stringResource(R.string.state_deleted)
    AppState.HISTORICAL, null -> null
}

/**
 * A rounded search field with its symbol: the card colour inside a hairline, so it stands off the page
 * in both themes (a bare grey wash all but vanishes on a black page).
 */
@Composable
internal fun SearchField(value: String, onChange: (String) -> Unit) {
    val dark = com.ybblab.jiumi.ui.theme.LocalAppIsDark.current
    Surface(
        shape = AppShapes.Control,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            if (dark) 0.75.dp else 0.5.dp,
            MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.22f else 0.12f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(JiumiIcons.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(stringResource(R.string.apps_search), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BasicTextField(
                    value = value, onValueChange = onChange, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (value.isNotEmpty()) {
                Icon(JiumiIcons.Close, stringResource(R.string.action_cancel), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp).clickable { onChange("") })
            }
        }
    }
}

@Composable
internal fun AppListRow(
    controller: MonitorController,
    labels: AppLabels,
    app: ListedApp,
    name: String,
    first: Boolean,
    last: Boolean,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit
) {
    CardSlice(first, last) {
        if (!first) InsetDivider()
        val badge = when (app.state) {
            AppState.DISABLED -> JiumiIcons.Snowflake
            AppState.DELETED -> JiumiIcons.Trash
            else -> null
        }
        ListRow(
            title = name,
            subtitle = listOfNotNull(
                stateText(app),
                app.backup?.let { stringResource(R.string.backup_size, formatBytes(it.bytes)) },
                app.pkg.takeIf { it != name }
            ).joinToString(" · ").ifEmpty { null },
            leading = {
                if (badge != null) {
                    AppIconWithBadge(controller, labels, app.pkg, LEADING_SIZE, badge, fromBackup = app.backup != null)
                } else if (app.module) IconTile(JiumiIcons.Cpu)
                else AppIcon(labels, app.pkg, LEADING_SIZE)
            },
            onClick = onClick,
            titleColor = if (badge != null) Ink.secondary else MaterialTheme.colorScheme.onSurface,
            trailing = trailing
        )
    }
}

/** One row of a card built from list items, so a long card scrolls lazily; [first] and [last] round its ends. */
@Composable
internal fun CardSlice(first: Boolean, last: Boolean, content: @Composable () -> Unit) {
    val r = AppShapes.CardRadius
    Surface(
        shape = RoundedCornerShape(topStart = if (first) r else 0.dp, topEnd = if (first) r else 0.dp,
            bottomStart = if (last) r else 0.dp, bottomEnd = if (last) r else 0.dp),
        color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(top = if (first) 4.dp else 0.dp, bottom = if (last) 4.dp else 0.dp)) { content() }
    }
}

/** What a button that carries out [action] says. */
fun actionLabel(action: ManageAction): Int = when (action) {
    ManageAction.DISABLE -> R.string.manage_action_disable
    ManageAction.ENABLE -> R.string.manage_action_enable
    ManageAction.UNSUSPEND -> R.string.manage_action_unsuspend
    ManageAction.UNINSTALL -> R.string.manage_action_uninstall
    ManageAction.REMOVE -> R.string.manage_action_remove
    ManageAction.RESTORE, ManageAction.REINSTALL -> R.string.manage_action_restore
}
