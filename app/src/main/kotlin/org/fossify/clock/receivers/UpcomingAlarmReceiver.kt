package org.fossify.clock.receivers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import org.fossify.clock.R
import org.fossify.clock.extensions.dbHelper
import org.fossify.clock.extensions.getAlarmDisplayLabel
import org.fossify.clock.extensions.getFormattedTime
import org.fossify.clock.extensions.getOpenAlarmTabIntent
import org.fossify.clock.extensions.getSkipUpcomingAlarmPendingIntent
import org.fossify.clock.extensions.goAsync
import org.fossify.clock.helpers.ALARM_ID
import org.fossify.clock.helpers.UPCOMING_ALARM_CHANNEL_ID
import org.fossify.clock.helpers.UPCOMING_ALARM_NOTIFICATION_ID_BASE
import org.fossify.clock.helpers.getTimeOfNextAlarm
import org.fossify.commons.extensions.notificationManager

/**
 * Receiver responsible for showing a notification that allows users to skip an upcoming alarm.
 * This notification appears N minutes before the alarm is scheduled to trigger, where N is the
 * user-configurable "upcoming alarm lead time" setting (default 10).
 *
 * Each alarm gets its own notification (id = [UPCOMING_ALARM_NOTIFICATION_ID_BASE] + alarm.id),
 * so multiple alarms within the lead-time window each show their own entry instead of
 * overwriting one another.
 */
class UpcomingAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra(ALARM_ID, -1)
        if (alarmId == -1) {
            return
        }

        goAsync {
            showUpcomingAlarmNotification(context, alarmId)
        }
    }

    private fun showUpcomingAlarmNotification(context: Context, alarmId: Int) {
        val alarm = context.dbHelper.getAlarmWithId(alarmId) ?: return
        if (!alarm.isEnabled) return

        val notificationManager = context.notificationManager
        NotificationChannel(
            UPCOMING_ALARM_CHANNEL_ID,
            context.getString(R.string.upcoming_alarm),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            setBypassDnd(true)
            setSound(null, null)
            notificationManager.createNotificationChannel(this)
        }

        val time = context.getFormattedTime(
            passedSeconds = alarm.timeInMinutes * 60,
            showSeconds = false,
            makeAmPmSmaller = false
        )
        val displayLabel = context.getAlarmDisplayLabel(alarm)
        val contentText = if (displayLabel.isEmpty()) time.toString() else "$time - $displayLabel"

        val notificationId = UPCOMING_ALARM_NOTIFICATION_ID_BASE + alarmId
        val contentIntent = context.getOpenAlarmTabIntent(alarmId)
        val dismissIntent = context.getSkipUpcomingAlarmPendingIntent(
            alarmId = alarmId, notificationId = notificationId
        )

        // Sort ascending by actual trigger time (soonest first) within the notification shade.
        // Zero-padded so lexicographic string comparison matches numeric comparison.
        val triggerTimeMillis = getTimeOfNextAlarm(alarm)?.timeInMillis ?: Long.MAX_VALUE
        val sortKey = triggerTimeMillis.toString().padStart(19, '0')

        val notification = NotificationCompat.Builder(context, UPCOMING_ALARM_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.upcoming_alarm))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_alarm_vector)
            .setPriority(Notification.PRIORITY_LOW)
            .setSortKey(sortKey)
            .addAction(
                0,
                context.getString(org.fossify.commons.R.string.dismiss),
                dismissIntent
            )
            .setContentIntent(contentIntent)
            .setSound(null)
            .setAutoCancel(true)
            .setChannelId(UPCOMING_ALARM_CHANNEL_ID)
            .build()

        notificationManager.notify(notificationId, notification)
    }
}
