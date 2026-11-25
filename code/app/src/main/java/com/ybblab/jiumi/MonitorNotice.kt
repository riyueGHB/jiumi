// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ybblab.jiumi.data.AppKey
import com.ybblab.jiumi.data.Attention
import com.ybblab.jiumi.data.NoticeSummary
import com.ybblab.jiumi.ui.AppLabels
import com.ybblab.jiumi.ui.attentionNameRes
import com.ybblab.jiumi.ui.formatCount

/**
 * The notifications about monitoring: the persistent one while it runs (how long, the session's
 * counts when the user wants them, and a stop button at its end), one when it stopped under an open
 * session, and alerts for apps newly raised for attention. Each says only what was recorded; the
 * lock screen shows none of the apps.
 */
object MonitorNotice {
    const val RUNNING_ID = 2001
    private const val INTERRUPTED_ID = 2002
    private const val ALERT_ID = 2003
    private const val CHANNEL_MONITORING = "monitoring"
    private const val CHANNEL_ATTENTION = "attention"
    private const val ACTION_STOP = "com.ybblab.jiumi.STOP_MONITORING"
    /** A page to open, as MainActivity names its pages; only attention pages are opened from here. */
    const val EXTRA_ROUTE = "com.ybblab.jiumi.ROUTE"
    const val ROUTE_MONITOR = "monitor"
    const val ROUTE_ATTENTION = "attention:"
    /** Apps named in one line before the rest are counted. */
    private const val NAMED_APPS = 3

    /** The persistent notification for a session that began at [since]. */
    data class Running(val since: Long, val chronometerBase: Long, val summary: NoticeSummary?)

    fun isStop(intent: Intent?): Boolean = intent?.action == ACTION_STOP

    fun running(context: Context, running: Running): Notification {
        val text = ContextCompat.getContextForLanguage(context)
        ensureChannels(context, text)
        val stop = PendingIntent.getService(context, 1, Intent(context, MonitorNotificationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE)
        val title = text.getString(R.string.notification_monitoring)
        val summary = running.summary
        // One level only: the status and running time, then the session's two figures (or when it
        // began, without a summary), short enough to show whole.
        val line = summary?.let { summaryLine(text, it) }
            ?: text.getString(R.string.notification_started, android.text.format.DateFormat.getTimeFormat(text).format(running.since))
        val content = RemoteViews(context.packageName, R.layout.notification_monitoring).apply {
            setTextViewText(R.id.notification_title, title)
            setChronometer(R.id.notification_elapsed, running.chronometerBase, null, true)
            setOnClickPendingIntent(R.id.notification_stop, stop)
            setContentDescription(R.id.notification_stop, text.getString(R.string.action_stop_monitoring))
            setTextViewText(R.id.notification_text, line)
        }
        return NotificationCompat.Builder(context, CHANNEL_MONITORING)
            .setSmallIcon(R.drawable.ic_stat_monitoring)
            .setContentTitle(title)
            .setContentText(line)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(content)
            // The same view unfolded, so pulling it down shows everything at once and there is nothing more to open.
            .setCustomBigContentView(content)
            .setWhen(running.since)
            .setShowWhen(false)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            // Only how long and how much: no app is named, so the lock screen may show it as it is.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open(context, null))
            .build()
    }

    /** Posts the persistent notification again with new content; its service keeps it in the foreground. */
    fun update(context: Context, running: Running) = post(context, null, RUNNING_ID, running(context, running))

    /** The monitor stopped while the session it was shown running for is still open. */
    fun postInterrupted(context: Context, message: Int = R.string.notification_interrupted_text): Boolean {
        val text = ContextCompat.getContextForLanguage(context)
        ensureChannels(context, text)
        val content = text.getString(message)
        return post(context, null, INTERRUPTED_ID, NotificationCompat.Builder(context, CHANNEL_MONITORING)
            .setSmallIcon(R.drawable.ic_stat_monitoring)
            .setContentTitle(text.getString(R.string.monitoring_interrupted))
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(open(context, ROUTE_MONITOR))
            .build())
    }

    fun cancelInterrupted(context: Context) = NotificationManagerCompat.from(context).cancel(INTERRUPTED_ID)

    /**
     * An alert for [item], whose apps in [fresh] were newly raised; it names every app raised under the
     * item, the new ones first, and replaces the earlier alert for the same item.
     */
    fun postAlert(context: Context, item: Attention, fresh: List<AppKey>) {
        val text = ContextCompat.getContextForLanguage(context)
        ensureChannels(context, text)
        val name = text.getString(attentionNameRes(item.id))
        val apps = (fresh + item.apps.filter { it !in fresh })
        val public = NotificationCompat.Builder(context, CHANNEL_ATTENTION)
            .setSmallIcon(R.drawable.ic_stat_monitoring)
            .setContentTitle(text.getString(R.string.attention_title))
            .build()
        post(context, item.id, ALERT_ID, NotificationCompat.Builder(context, CHANNEL_ATTENTION)
            .setSmallIcon(R.drawable.ic_stat_monitoring)
            .setContentTitle(name)
            .setContentText(appList(text, AppLabels(text), apps))
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setContentIntent(open(context, ROUTE_ATTENTION + item.id))
            .build())
    }

    fun cancelAlerts(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        manager.activeNotifications.filter { it.id == ALERT_ID }.forEach { manager.cancel(it.tag, it.id) }
    }

    /** Whether notifications from this app can be shown at all. */
    fun allowed(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    @SuppressLint("MissingPermission")
    private fun post(context: Context, tag: String?, id: Int, notification: Notification): Boolean {
        if (!allowed(context)) return false
        return runCatching { NotificationManagerCompat.from(context).notify(tag, id, notification) }.isSuccess
    }

    /** The session's total and its background part, in words short enough for one line in either language. */
    private fun summaryLine(text: Context, summary: NoticeSummary): String = text.getString(R.string.notification_summary,
        formatCount(summary.accesses.toLong()), formatCount(summary.background.toLong()))

    private fun appList(text: Context, labels: AppLabels, apps: List<AppKey>): String {
        val separator = text.getString(R.string.list_separator)
        if (apps.size <= NAMED_APPS) return apps.joinToString(separator) { labels.label(it) }
        val named = apps.take(NAMED_APPS - 1).joinToString(separator) { labels.label(it) }
        return text.getString(R.string.notification_apps_more, named, formatCount(apps.size.toLong()))
    }

    private fun open(context: Context, route: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        route?.let { intent.putExtra(EXTRA_ROUTE, it) }
        return PendingIntent.getActivity(context, route?.hashCode() ?: 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun ensureChannels(context: Context, text: Context) {
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_MONITORING, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(text.getString(R.string.notification_channel_monitoring))
                .setShowBadge(false)
                .build()
        )
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ATTENTION, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(text.getString(R.string.attention_title))
                .build()
        )
    }
}
