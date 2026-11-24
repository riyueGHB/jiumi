// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.ui

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.sp
import com.ybblab.jiumi.ui.theme.LocalAppIsDark
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ybblab.jiumi.ui.theme.Ink
import com.ybblab.jiumi.MonitorController
import com.ybblab.jiumi.R
import com.ybblab.jiumi.ui.icons.JiumiIcons
import com.ybblab.jiumi.ui.theme.AppIconSize
import com.ybblab.jiumi.ui.theme.AppShapes
import com.ybblab.jiumi.ui.theme.StatusSuccess
import java.text.NumberFormat

// Components follow Droidance: small grey group titles outside the cards, flat cards, rows with a
// tinted icon tile, inset dividers and blue text actions.

@Composable
fun GroupTitle(title: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = Ink.secondary,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}

@Composable
fun Card(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = AppShapes.Card, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 4.dp), content = content)
    }
}

/** Divider between rows that start with an icon tile; it begins where the text begins. */
@Composable
fun InsetDivider(start: Dp = 60.dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = start, end = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

/** A neutral grey tile for a drawn symbol; status is told in words and badges, never by tinting the symbol. */
@Composable
fun IconTile(icon: ImageVector) {
    Surface(
        modifier = Modifier.size(LEADING_SIZE.dp),
        shape = AppShapes.Control,
        color = Ink.fill
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(AppIconSize.Leading))
        }
    }
}

/**
 * A card row: leading icon tile or app icon, title with an optional subtitle, then [trailing].
 * Rows with [onClick] are tappable as a whole.
 */
@Composable
fun ListRow(
    title: String,
    subtitle: String? = null,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = subtitleColor,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
            extra?.invoke(this)
        }
        trailing()
    }
}

@Composable
fun Chevron() {
    Spacer(Modifier.width(8.dp))
    Icon(JiumiIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(AppIconSize.Inline))
}

@Composable
fun TrailingValue(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 1,
        modifier = Modifier.widthIn(max = 144.dp).padding(start = 8.dp))
}

@Composable
fun ActionText(text: String, enabled: Boolean = true, color: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge,
            color = if (enabled) color else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun JiumiSwitch(checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    // Drawn at three quarters size and laid out at that size too, so a row with a switch is no
    // taller than its neighbours; the whole row stays the touch target.
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.material3.LocalMinimumInteractiveComponentSize provides Dp.Unspecified
    ) {
    Box(Modifier.size(width = 40.dp, height = 24.dp), contentAlignment = Alignment.Center) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = Modifier.requiredSize(width = 52.dp, height = 32.dp).scale(0.75f),
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.surface,
            checkedTrackColor = MaterialTheme.colorScheme.onSurface,
            uncheckedThumbColor = MaterialTheme.colorScheme.surface,
            uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
            uncheckedBorderColor = Color.Transparent
        )
    )
    }
    }
}

@Composable
fun SelectionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(JiumiIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(AppIconSize.Leading))
        }
    }
}

/**
 * A page with a title bar and a back arrow, as on every Droidance sub-page; [onBack] is null for a
 * page that is a tab and has nowhere to go back to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubScreen(title: String, onBack: (() -> Unit)?, bottomBar: @Composable () -> Unit = {}, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            // A tab is named by the bar below it; only pages above the tabs carry a title.
            if (onBack != null) TopAppBar(
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) {
                        Icon(JiumiIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = bottomBar,
        containerColor = MaterialTheme.colorScheme.background,
        content = content
    )
}

@Composable
fun AppIcon(labels: AppLabels, pkg: String?, sizeDp: Int) {
    // Read off the main thread, so a long list scrolls and opens without waiting on each icon.
    // Keyed by the package, so a row that comes to show another app never keeps the last one's icon.
    val icon by key(pkg) {
        produceState(labels.cachedIcon(pkg), pkg) {
            if (labels.iconPending(pkg)) value = withContext(Dispatchers.IO) { labels.icon(pkg) }
        }
    }
    val current = icon
    if (current != null) {
        Image(current, contentDescription = null, modifier = Modifier.size(sizeDp.dp))
    } else if (labels.iconPending(pkg)) {
        // Still reading: an empty tile of the same size, so nothing jumps when the icon arrives.
        Surface(Modifier.size(sizeDp.dp), shape = AppShapes.Control, color = Ink.fill) {}
    } else {
        // System services have no app icon.
        Surface(Modifier.size(sizeDp.dp), shape = AppShapes.Control, color = Ink.fill) {
            Box(contentAlignment = Alignment.Center) {
                Icon(JiumiIcons.Cpu, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size((sizeDp * 0.55f).dp))
            }
        }
    }
}

@Composable
fun AppIconWithBadge(controller: MonitorController, labels: AppLabels, pkg: String, sizeDp: Int,
                     badge: ImageVector, fromBackup: Boolean) {
    val backupIcon = if (fromBackup) {
        produceState<ImageBitmap?>(null, pkg) {
            value = withContext(Dispatchers.IO) { controller.backupIcon(pkg)?.asImageBitmap() }
        }.value
    } else null
    Box(Modifier.size(sizeDp.dp)) {
        if (backupIcon == null) {
            AppIcon(labels, pkg, sizeDp)
        } else {
            Image(backupIcon, contentDescription = null, modifier = Modifier.matchParentSize().clip(AppShapes.Control))
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomEnd).size((sizeDp * 0.46f).dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(badge, contentDescription = null, tint = Ink.secondary,
                    modifier = Modifier.size((sizeDp * 0.28f).dp))
            }
        }
    }
}

/** A small tinted label for a status: background use, missing from the record, data sent in background. */
@Composable
fun Badge(text: String, color: Color) {
    Surface(shape = AppShapes.Small, color = color.copy(alpha = 0.12f)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable
fun shortTime(t: Long): String {
    val context = LocalContext.current
    return if (DateUtils.isToday(t)) DateFormat.getTimeFormat(context).format(t)
    else DateUtils.formatDateTime(context, t, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_NO_YEAR)
}

fun formatCount(value: Long): String = NumberFormat.getIntegerInstance().format(value)

@Composable
fun formatBytes(bytes: Long): String = android.text.format.Formatter.formatShortFileSize(LocalContext.current, bytes)

/**
 * Equal choices sharing the width, as iOS draws them: a sunken track and a raised thumb that slides to
 * the chosen one, whose label is bold and full ink while the others stay grey.
 */
@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    activeOption: Int? = null
) {
    val activeDescription = activeOption?.let { stringResource(R.string.connection_current_method) }
    val dark = LocalAppIsDark.current
    val thumb = if (dark) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceBright
    Box(modifier.clip(SegmentTrack).background(Ink.track).padding(3.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val width = maxWidth / options.size.coerceAtLeast(1)
            val x by animateDpAsState(width * selected, spring(dampingRatio = 0.82f, stiffness = 520f), label = "segment")
            Box(
                Modifier.offset(x = x).width(width).height(SEGMENT_HEIGHT)
                    .shadow(if (dark) 0.dp else 3.dp, SegmentThumb, ambientColor = Color.Black.copy(alpha = 0.10f),
                        spotColor = Color.Black.copy(alpha = 0.14f))
                    .background(thumb, SegmentThumb)
            )
            Row(Modifier.fillMaxWidth().height(SEGMENT_HEIGHT)) {
                options.forEachIndexed { index, label ->
                    val chosen = index == selected
                    val active = index == activeOption
                    val color by animateColorAsState(if (chosen) Ink.primary else Ink.secondary, label = "segmentLabel")
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clip(SegmentThumb)
                            .selectable(selected = chosen, role = Role.Tab, interactionSource = null, indication = null) { onSelect(index) }
                            .then(if (active && activeDescription != null) Modifier.semantics {
                                stateDescription = activeDescription
                            } else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(Modifier.padding(horizontal = if (activeOption == null) 6.dp else 2.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            if (active) {
                                SelectionDot()
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(label, style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Medium,
                                color = color, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** Marks a chosen option without implying its connection or permission is ready. */
@Composable
internal fun SelectionDot() {
    Box(Modifier.size(6.dp).background(StatusSuccess, CircleShape))
}

private val SEGMENT_HEIGHT = 28.dp
private val SegmentTrack = RoundedCornerShape(10.dp)
private val SegmentThumb = RoundedCornerShape(8.dp)

/**
 * Views of one list, as a quiet row of words over a hairline: the chosen one in full ink, the others
 * faint, and a short rule on the hairline under the chosen word that slides to the next. Unlike
 * [Segmented], which filters, these change what is shown, so they read as headings, not as a control.
 */
@Composable
fun ViewTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    // Where each word sits along the row, measured once laid out, so the rule matches its width.
    val spans = remember(options.size) { mutableStateListOf<Pair<Dp, Dp>>().apply { repeat(options.size) { add(0.dp to 0.dp) } } }
    val target = spans.getOrElse(selected) { 0.dp to 0.dp }
    val motion = spring<Dp>(dampingRatio = 0.86f, stiffness = 480f)
    val x by animateDpAsState(target.first, motion, label = "viewTabX")
    val w by animateDpAsState(target.second, motion, label = "viewTabW")
    Box(modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            options.forEachIndexed { index, label ->
                val chosen = index == selected
                val color by animateColorAsState(if (chosen) Ink.primary else Ink.tertiary, label = "viewTab")
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 0.3.sp),
                    fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Medium,
                    color = color,
                    modifier = Modifier
                        .onGloballyPositioned { c ->
                            spans[index] = with(density) { c.positionInParent().x.toDp() to c.size.width.toDp() }
                        }
                        .selectable(selected = chosen, role = Role.Tab, interactionSource = null, indication = null) { onSelect(index) }
                        .padding(top = 6.dp, bottom = 11.dp)
                )
            }
        }
        HorizontalDivider(Modifier.align(Alignment.BottomStart), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        Box(
            Modifier.align(Alignment.BottomStart).padding(start = 4.dp).offset(x = x).width(w).height(1.5.dp)
                .background(Ink.primary, CircleShape)
        )
    }
}

/** Days and hours, hours and minutes, or minutes, whichever reads best. */
@Composable
fun formatDuration(ms: Long): String {
    val minutes = (ms / 60_000L).toInt()
    val days = minutes / (24 * 60)
    val hours = minutes / 60 % 24
    return when {
        days > 0 -> stringResource(R.string.duration_days_hours, days, hours)
        minutes >= 60 -> stringResource(R.string.duration_hours_minutes, minutes / 60, minutes % 60)
        else -> stringResource(R.string.duration_minutes, minutes)
    }
}

/** A session's span: its start, and its end or "in progress". */
@Composable
fun sessionSpan(start: Long, end: Long?): String =
    shortDateTime(start) + " – " + (end?.let { shortDateTime(it) } ?: stringResource(R.string.session_in_progress))

@Composable
fun shortDateTime(t: Long): String = DateUtils.formatDateTime(LocalContext.current, t,
    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_NO_YEAR)

@Composable
fun shortDate(t: Long): String = DateUtils.formatDateTime(LocalContext.current, t,
    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_NO_YEAR)

/** The one filled action of a card, full width. */
@Composable
fun PrimaryButton(text: String, busy: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !busy,
        shape = AppShapes.Large,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.onSurface,
            contentColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).height(42.dp)
    ) {
        if (busy) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.surface)
        } else {
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** The one action at the foot of a page, above the navigation bar: filled to go on, outlined to cancel. */
@Composable
fun BottomAction(text: String, filled: Boolean = true, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = AppShapes.Large,
            color = if (filled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface,
            contentColor = if (filled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) { Text(text, style = MaterialTheme.typography.titleMedium) }
        }
    }
}

/** A granted step: a small check in a grey circle where its action was. */
@Composable
fun DoneMark() {
    Surface(modifier = Modifier.size(28.dp), shape = androidx.compose.foundation.shape.CircleShape, color = Ink.fill) {
        Box(contentAlignment = Alignment.Center) {
            Icon(JiumiIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(15.dp))
        }
    }
}

/**
 * The time a list row leads with, at its left edge: the clock time for today, otherwise the date over
 * the clock time. Rows of one list line up on it.
 */
@Composable
fun TimeColumn(t: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clock = DateFormat.getTimeFormat(context).format(t)
    Column(modifier.width(TIME_COLUMN_WIDTH)) {
        if (DateUtils.isToday(t)) {
            Text(clock, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        } else {
            Text(DateUtils.formatDateTime(context, t, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NUMERIC_DATE or DateUtils.FORMAT_NO_YEAR),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(clock, style = MaterialTheme.typography.labelSmall,
                color = Ink.tertiary, maxLines = 1)
        }
    }
}

val TIME_COLUMN_WIDTH = 48.dp

/** The leading icon tile or app icon of a list row; dividers start past it. */
const val LEADING_SIZE = 32

/** A quiet pill-shaped button beside a row: grey fill, graphite label, for actions such as stopping. */
@Composable
fun PillButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = androidx.compose.foundation.shape.CircleShape,
        color = Ink.fill
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

/**
 * [text] with the app's name set in semibold and full ink wherever it appears, so the reader sees at
 * a glance which part is about Jiumi itself.
 */
@Composable
fun emphasizeAppName(text: String): androidx.compose.ui.text.AnnotatedString {
    val name = androidx.compose.ui.res.stringResource(com.ybblab.jiumi.R.string.app_name)
    val ink = MaterialTheme.colorScheme.onSurface
    return androidx.compose.ui.text.buildAnnotatedString {
        var from = 0
        while (true) {
            val at = text.indexOf(name, from)
            if (at < 0) { append(text.substring(from)); break }
            append(text.substring(from, at))
            pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold, color = ink))
            append(name)
            pop()
            from = at + name.length
        }
    }
}

/**
 * Tapping copies [text] to the clipboard. Android 13 and later confirm a copy themselves; older
 * versions get a short note.
 */
@Composable
fun Modifier.copyOnClick(text: String): Modifier {
    val context = LocalContext.current
    val label = stringResource(com.ybblab.jiumi.R.string.copied_package)
    return clickable(onClickLabel = stringResource(com.ybblab.jiumi.R.string.action_copy)) {
        context.getSystemService(android.content.ClipboardManager::class.java)
            ?.setPrimaryClip(android.content.ClipData.newPlainText(text, text))
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            android.widget.Toast.makeText(context, label, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
