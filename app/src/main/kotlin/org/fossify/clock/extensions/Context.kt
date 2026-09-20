package org.fossify.clock.extensions

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager.STREAM_ALARM
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.widget.Toast
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import org.fossify.clock.R
import org.fossify.clock.activities.SnoozeReminderActivity
import org.fossify.clock.activities.SplashActivity
import org.fossify.clock.databases.AppDatabase
import org.fossify.clock.helpers.ALARM_ID
import org.fossify.clock.helpers.AlarmController
import org.fossify.clock.helpers.Config
import org.fossify.clock.helpers.DBHelper
import org.fossify.clock.helpers.EDITED_TIME_ZONE_SEPARATOR
import org.fossify.clock.helpers.FORMAT_12H
import org.fossify.clock.helpers.FORMAT_24H
import org.fossify.clock.helpers.MyAnalogueTimeWidgetProvider
import org.fossify.clock.helpers.MyDigitalTimeWidgetProvider
import org.fossify.clock.helpers.NOTIFICATION_ID
import org.fossify.clock.helpers.OPEN_ALARMS_TAB_INTENT_ID
import org.fossify.clock.helpers.OPEN_ROUTINE_TAB_INTENT_ID
import org.fossify.clock.helpers.OPEN_STOPWATCH_TAB_INTENT_ID
import org.fossify.clock.helpers.OPEN_TAB
import org.fossify.clock.helpers.ROUTINE_ID
import org.fossify.clock.helpers.ROUTINE_NOTIFICATION_ID_BASE
import org.fossify.clock.helpers.ROUTINE_STOP_INTENT_ID_BASE
import org.fossify.clock.helpers.TAB_ALARM
import org.fossify.clock.helpers.TAB_ROUTINE
import org.fossify.clock.helpers.TAB_STOPWATCH
import org.fossify.clock.helpers.TAB_TIMER
import org.fossify.clock.helpers.TIMER_ID
import org.fossify.clock.helpers.TODAY_BIT
import org.fossify.clock.helpers.TOMORROW_BIT
import org.fossify.clock.helpers.TimerHelper
import org.fossify.clock.helpers.UPCOMING_ALARM_INTENT_ID_BASE
import org.fossify.clock.helpers.formatTime
import org.fossify.clock.helpers.getAllTimeZones
import org.fossify.clock.helpers.getDefaultTimeZoneTitle
import org.fossify.clock.helpers.getTimeOfNextAlarm
import org.fossify.clock.interfaces.TimerDao
import org.fossify.clock.models.Alarm
import org.fossify.clock.models.MyTimeZone
import org.fossify.clock.models.Timer
import org.fossify.clock.models.TimerState
import org.fossify.clock.receivers.AlarmReceiver
import org.fossify.clock.receivers.HideTimerReceiver
import org.fossify.clock.receivers.SkipUpcomingAlarmReceiver
import org.fossify.clock.receivers.StopAlarmReceiver
import org.fossify.clock.receivers.UpcomingAlarmReceiver
import org.fossify.clock.services.SnoozeService
import org.fossify.commons.extensions.formatMinutesToTimeString
import org.fossify.commons.extensions.formatSecondsToTimeString
import org.fossify.commons.extensions.getDefaultAlarmSound
import org.fossify.commons.extensions.getLaunchIntent
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.grantReadUriPermission
import org.fossify.commons.extensions.notificationManager
import org.fossify.commons.extensions.rotateLeft
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toInt
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.FRIDAY_BIT
import org.fossify.commons.helpers.MINUTE_SECONDS
import org.fossify.commons.helpers.MONDAY_BIT
import org.fossify.commons.helpers.SATURDAY_BIT
import org.fossify.commons.helpers.SILENT
import org.fossify.commons.helpers.SUNDAY_BIT
import org.fossify.commons.helpers.THURSDAY_BIT
import org.fossify.commons.helpers.TUESDAY_BIT
import org.fossify.commons.helpers.WEDNESDAY_BIT
import org.fossify.commons.helpers.ensureBackgroundThread
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.ceil
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

val Context.config: Config
    get() = Config.newInstance(applicationContext)

val Context.dbHelper: DBHelper
    get() = DBHelper.newInstance(applicationContext)

val Context.timerDb: TimerDao
    get() = AppDatabase.getInstance(applicationContext).TimerDao()

val Context.timerHelper: TimerHelper
    get() = TimerHelper(this)

val Context.routineDb: org.fossify.clock.interfaces.RoutineDao
    get() = AppDatabase.getInstance(applicationContext).RoutineDao()

val Context.routineHelper: org.fossify.clock.helpers.RoutineHelper
    get() = org.fossify.clock.helpers.RoutineHelper(this)

val Context.routineGroupDb: org.fossify.clock.interfaces.RoutineGroupDao
    get() = AppDatabase.getInstance(applicationContext).RoutineGroupDao()

val Context.routineGroupHelper: org.fossify.clock.helpers.RoutineGroupHelper
    get() = org.fossify.clock.helpers.RoutineGroupHelper(this)

val Context.routineController: org.fossify.clock.helpers.RoutineController
    get() = org.fossify.clock.helpers.RoutineController.getInstance(applicationContext)

val Context.alarmManager: AlarmManager
    get() = getSystemService(Context.ALARM_SERVICE) as AlarmManager

val Context.alarmController: AlarmController
    get() = AlarmController.getInstance(applicationContext)

fun Context.getFormattedDate(calendar: Calendar): String {
    val dayOfWeek = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7 // make sure index 0 means monday
    val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
    val month = calendar.get(Calendar.MONTH)

    val dayString = resources.getStringArray(org.fossify.commons.R.array.week_days_short)[dayOfWeek]
    val monthString = resources.getStringArray(org.fossify.commons.R.array.months)[month]
    return "$dayString, $dayOfMonth $monthString"
}

fun Context.getEditedTimeZonesMap(): HashMap<Int, String> {
    val editedTimeZoneTitles = config.editedTimeZoneTitles
    val editedTitlesMap = HashMap<Int, String>()
    editedTimeZoneTitles.forEach {
        val parts = it.split(EDITED_TIME_ZONE_SEPARATOR.toRegex(), 2)
        editedTitlesMap[parts[0].toInt()] = parts[1]
    }
    return editedTitlesMap
}

fun Context.getAllTimeZonesModified(): ArrayList<MyTimeZone> {
    val timeZones = getAllTimeZones()
    val editedTitlesMap = getEditedTimeZonesMap()
    timeZones.forEach {
        if (editedTitlesMap.keys.contains(it.id)) {
            it.title = editedTitlesMap[it.id]!!
        } else {
            it.title = it.title.substring(it.title.indexOf(' ')).trim()
        }
    }
    return timeZones
}

fun Context.getModifiedTimeZoneTitle(id: Int): String {
    return getAllTimeZonesModified()
        .firstOrNull { it.id == id }?.title ?: getDefaultTimeZoneTitle(id)
}

fun Context.createNewAlarm(timeInMinutes: Int, weekDays: Int): Alarm {
    val defaultAlarmSound = getDefaultAlarmSound(RingtoneManager.TYPE_ALARM)
    return Alarm(
        id = 0,
        timeInMinutes = timeInMinutes,
        days = weekDays,
        isEnabled = false,
        vibrate = false,
        soundTitle = defaultAlarmSound.title,
        soundUri = defaultAlarmSound.uri,
        label = ""
    )
}

fun Context.createNewTimer(): Timer {
    val now = System.currentTimeMillis()
    return Timer(
        id = null,
        seconds = config.timerSeconds,
        state = TimerState.Idle,
        vibrate = config.timerVibrate,
        soundUri = config.timerSoundUri,
        soundTitle = config.timerSoundTitle,
        label = config.timerLabel ?: "",
        createdAt = now,
        channelId = config.timerChannelId,
        lastUsedAt = now,
    )
}

fun Context.showRemainingTimeMessage(triggerInMillis: Long) {
    val totalSeconds = triggerInMillis.milliseconds.inWholeSeconds.toInt()
    val remainingTime = if (totalSeconds >= MINUTE_SECONDS) {
        val roundedMinutes = ceil(totalSeconds / MINUTE_SECONDS.toFloat()).toInt()
        formatMinutesToTimeString(roundedMinutes)
    } else {
        formatSecondsToTimeString(totalSeconds)
    }

    toast(
        msg = String.format(
            getString(org.fossify.commons.R.string.time_remaining), remainingTime
        ),
        length = Toast.LENGTH_LONG
    )
}

fun Context.setupAlarmClock(alarm: Alarm, triggerTimeMillis: Long) {
    val alarmManager = alarmManager
    try {
        AlarmManagerCompat.setAlarmClock(
            alarmManager,
            triggerTimeMillis,
            getOpenAlarmTabIntent(),
            getAlarmIntent(alarm)
        )

        // show a notification to allow dismissing the alarm N minutes before it actually
        // triggers (N is user-configurable in Settings, default 10)
        val leadTimeMillis = config.upcomingAlarmLeadMinutes.minutes.inWholeMilliseconds
        val dismissalTriggerTime =
            if (triggerTimeMillis - System.currentTimeMillis() < leadTimeMillis) {
                System.currentTimeMillis() + 500
            } else {
                triggerTimeMillis - leadTimeMillis
            }

        AlarmManagerCompat.setExactAndAllowWhileIdle(
            alarmManager,
            0,
            dismissalTriggerTime,
            getUpcomingAlarmPendingIntent(alarm)
        )
    } catch (e: Exception) {
        showErrorToast(e)
    }
}

fun Context.getUpcomingAlarmPendingIntent(alarm: Alarm): PendingIntent {
    val intent = Intent(this, UpcomingAlarmReceiver::class.java).apply {
        putExtra(ALARM_ID, alarm.id)
    }

    return PendingIntent.getBroadcast(
        this,
        UPCOMING_ALARM_INTENT_ID_BASE + alarm.id,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

fun Context.getOpenAlarmTabIntent(): PendingIntent {
    val intent = getLaunchIntent() ?: Intent(this, SplashActivity::class.java)
    intent.putExtra(OPEN_TAB, TAB_ALARM)
    return PendingIntent.getActivity(
        this,
        OPEN_ALARMS_TAB_INTENT_ID,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

/**
 * Same as [getOpenAlarmTabIntent] but with a request code unique to [requestCode] (typically an
 * alarm id). Needed for the upcoming-alarm notifications: when several of them are visible at
 * once and all share the exact same content PendingIntent, Android/some OEM skins treat tapping
 * one auto-cancelable notification as clearing every other notification using that identical
 * PendingIntent -- so each alarm needs its own distinct content intent to dismiss only itself.
 */
fun Context.getOpenAlarmTabIntent(requestCode: Int): PendingIntent {
    val intent = getLaunchIntent() ?: Intent(this, SplashActivity::class.java)
    intent.putExtra(OPEN_TAB, TAB_ALARM)
    return PendingIntent.getActivity(
        this,
        OPEN_ALARMS_TAB_INTENT_ID + requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

fun Context.getOpenTimerTabIntent(timerId: Int): PendingIntent {
    val intent = getLaunchIntent() ?: Intent(this, SplashActivity::class.java)
    intent.putExtra(OPEN_TAB, TAB_TIMER)
    intent.putExtra(TIMER_ID, timerId)
    return PendingIntent.getActivity(
        this,
        timerId,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

fun Context.getOpenStopwatchTabIntent(): PendingIntent {
    val intent = getLaunchIntent() ?: Intent(this, SplashActivity::class.java)
    intent.putExtra(OPEN_TAB, TAB_STOPWATCH)
    return PendingIntent.getActivity(
        this,
        OPEN_STOPWATCH_TAB_INTENT_ID,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

fun Context.getAlarmIntent(alarm: Alarm): PendingIntent {
    val intent = Intent(this, AlarmReceiver::class.java)
    intent.putExtra(ALARM_ID, alarm.id)
    return PendingIntent.getBroadcast(
        this,
        alarm.id,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

fun Context.cancelAlarmClock(alarm: Alarm) {
    val alarmManager = alarmManager
    alarmManager.cancel(getAlarmIntent(alarm))
    alarmManager.cancel(getUpcomingAlarmPendingIntent(alarm))
}

fun Context.hideNotification(id: Int) {
    notificationManager.cancel(id)
}

fun Context.hideTimerNotification(timerId: Int) = hideNotification(timerId)

fun Context.updateWidgets() {
    updateDigitalWidgets()
    updateAnalogueWidgets()
}

fun Context.updateDigitalWidgets() {
    val component = ComponentName(applicationContext, MyDigitalTimeWidgetProvider::class.java)
    val widgetIds = AppWidgetManager.getInstance(applicationContext)
        ?.getAppWidgetIds(component) ?: return

    if (widgetIds.isNotEmpty()) {
        val ids = intArrayOf(R.xml.widget_digital_clock_info)
        Intent(applicationContext, MyDigitalTimeWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            sendBroadcast(this)
        }
    }
}

fun Context.updateAnalogueWidgets() {
    val component = ComponentName(applicationContext, MyAnalogueTimeWidgetProvider::class.java)
    val widgetIds = AppWidgetManager.getInstance(applicationContext)
        ?.getAppWidgetIds(component) ?: return

    if (widgetIds.isNotEmpty()) {
        val ids = intArrayOf(R.xml.widget_analogue_clock_info)
        Intent(applicationContext, MyAnalogueTimeWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            sendBroadcast(this)
        }
    }
}

fun Context.getFormattedTime(
    passedSeconds: Int,
    showSeconds: Boolean,
    makeAmPmSmaller: Boolean,
): SpannableString {
    val use24HourFormat = config.use24HourFormat
    val hours = (passedSeconds / 3600) % 24
    val minutes = (passedSeconds / 60) % 60
    val seconds = passedSeconds % 60

    return if (use24HourFormat) {
        val formattedTime = formatTime(
            showSeconds = showSeconds,
            use24HourFormat = true,
            hours = hours,
            minutes = minutes,
            seconds = seconds
        )
        SpannableString(formattedTime)
    } else {
        val formattedTime = formatTo12HourFormat(
            showSeconds = showSeconds,
            hours = hours,
            minutes = minutes,
            seconds = seconds
        )
        val spannableTime = SpannableString(formattedTime)
        val amPmMultiplier = if (makeAmPmSmaller) 0.4f else 1f
        spannableTime.setSpan(
            RelativeSizeSpan(amPmMultiplier),
            spannableTime.length - 3,
            spannableTime.length,
            0
        )
        spannableTime
    }
}

fun Context.formatTo12HourFormat(
    showSeconds: Boolean,
    hours: Int,
    minutes: Int,
    seconds: Int,
): String {
    val appendable = getString(
        if (hours >= 12) {
            org.fossify.commons.R.string.p_m
        } else {
            org.fossify.commons.R.string.a_m
        }
    )
    val newHours = if (hours == 0 || hours == 12) 12 else hours % 12
    return "${formatTime(showSeconds, false, newHours, minutes, seconds)} $appendable"
}

fun Context.getClosestEnabledAlarmString(callback: (result: String) -> Unit) {
    getEnabledAlarms { enabledAlarms ->
        if (enabledAlarms.isNullOrEmpty()) {
            callback("")
            return@getEnabledAlarms
        }

        val now = Calendar.getInstance()
        val nextAlarmList = enabledAlarms
            .mapNotNull { alarm -> getTimeOfNextAlarm(alarm)?.let { alarm to it } }
            .filter { (_, time) -> time > now }

        val closestAlarm = nextAlarmList.minByOrNull { (_, time) -> time }
        if (closestAlarm == null) {
            callback("")
            return@getEnabledAlarms
        }

        val (alarm, closestAlarmTime) = closestAlarm
        val dayOfWeekIndex = (closestAlarmTime.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val dayOfWeek =
            resources.getStringArray(org.fossify.commons.R.array.week_days_short)[dayOfWeekIndex]
        val pattern = if (config.use24HourFormat) {
            FORMAT_24H
        } else {
            FORMAT_12H
        }

        val formattedTime =
            SimpleDateFormat(pattern, Locale.getDefault()).format(closestAlarmTime.time)
        val displayLabel = getAlarmDisplayLabel(alarm)
        val result = if (displayLabel.isEmpty()) {
            "$dayOfWeek $formattedTime"
        } else {
            "$dayOfWeek $formattedTime - $displayLabel"
        }
        callback(result)
    }
}

/**
 * Builds the "(Group) Label" text used to identify an alarm to the user -- shown in the
 * upcoming-alarm notification, the clock screen's next-alarm line, and the home screen widget.
 * Mirrors AlarmsAdapter.buildDisplayLabel(). Returns "" if the alarm has neither a label nor a
 * (title-bearing) group, so callers can fall back to just the time.
 */
fun Context.getAlarmDisplayLabel(alarm: org.fossify.clock.models.Alarm): String {
    val groupId = alarm.groupId
    val prefix = if (groupId != null) {
        val groupTitle = dbHelper.getGroupWithId(groupId)?.title
        if (groupTitle.isNullOrEmpty()) "" else "($groupTitle) "
    } else {
        ""
    }
    return prefix + alarm.label
}

fun Context.getEnabledAlarms(callback: (result: List<Alarm>?) -> Unit) {
    ensureBackgroundThread {
        val alarms = dbHelper.getEnabledAlarms()
        Handler(Looper.getMainLooper()).post {
            callback(alarms)
        }
    }
}

fun Context.getTimerNotification(timer: Timer, pendingIntent: PendingIntent): Notification {
    var soundUri = timer.soundUri
    if (soundUri == SILENT) {
        soundUri = ""
    } else {
        grantReadUriPermission(soundUri)
    }

    val channelId =
        timer.channelId ?: "simple_timer_channel_${soundUri}_${System.currentTimeMillis()}"
    timerHelper.insertOrUpdateTimer(timer.copy(channelId = channelId))

    try {
        notificationManager.deleteNotificationChannel(channelId)
    } catch (_: Exception) {
    }

    val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .setLegacyStreamType(STREAM_ALARM)
        .build()

    val name = getString(R.string.timer)
    val importance = NotificationManager.IMPORTANCE_HIGH
    NotificationChannel(channelId, name, importance).apply {
        setBypassDnd(true)
        enableLights(true)
        lightColor = getProperPrimaryColor()
        setSound(soundUri.toUri(), audioAttributes)

        if (!timer.vibrate) {
            vibrationPattern = longArrayOf(0L)
        }

        enableVibration(timer.vibrate)
        notificationManager.createNotificationChannel(this)
    }

    val title = timer.label.ifEmpty { getString(R.string.timer) }
    val builder = NotificationCompat.Builder(this, channelId)
        .setContentTitle(title)
        .setContentText(getString(R.string.time_expired))
        .setSmallIcon(R.drawable.ic_hourglass_vector)
        .setContentIntent(pendingIntent)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setDefaults(Notification.DEFAULT_LIGHTS)
        .setCategory(Notification.CATEGORY_EVENT)
        .setSound(soundUri.toUri(), STREAM_ALARM)
        .setChannelId(channelId)
        .addAction(
            org.fossify.commons.R.drawable.ic_cross_vector,
            getString(org.fossify.commons.R.string.dismiss),
            getHideTimerPendingIntent(timer.id!!)
        )

    builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

    if (timer.vibrate) {
        val vibrateArray = LongArray(2) { 500 }
        builder.setVibrate(vibrateArray)
    }

    val notification = builder.build()
    notification.flags = notification.flags or Notification.FLAG_INSISTENT
    return notification
}

fun Context.getHideTimerPendingIntent(timerId: Int): PendingIntent {
    val intent = Intent(this, HideTimerReceiver::class.java)
    intent.putExtra(TIMER_ID, timerId)
    return PendingIntent.getBroadcast(
        this,
        timerId,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

fun Context.getStopAlarmPendingIntent(alarm: Alarm): PendingIntent {
    val intent = Intent(this, StopAlarmReceiver::class.java).apply {
        putExtra(ALARM_ID, alarm.id)
    }
    return PendingIntent.getBroadcast(
        this,
        alarm.id,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

fun Context.getSkipUpcomingAlarmPendingIntent(alarmId: Int, notificationId: Int): PendingIntent {
    val intent = Intent(this, SkipUpcomingAlarmReceiver::class.java).apply {
        putExtra(ALARM_ID, alarmId)
        putExtra(NOTIFICATION_ID, notificationId)
    }
    return PendingIntent.getBroadcast(
        this,
        alarmId,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

fun Context.getSnoozePendingIntent(alarm: Alarm): PendingIntent {
    val snoozeClass = if (config.useSameSnooze) {
        SnoozeService::class.java
    } else {
        SnoozeReminderActivity::class.java
    }

    val intent = Intent(this, snoozeClass).setAction("Snooze")
    intent.putExtra(ALARM_ID, alarm.id)
    return if (config.useSameSnooze) {
        PendingIntent.getService(
            this,
            alarm.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    } else {
        PendingIntent.getActivity(
            this,
            alarm.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

fun Context.checkAlarmsWithDeletedSoundUri(uri: String) {
    val defaultAlarmSound = getDefaultAlarmSound(RingtoneManager.TYPE_ALARM)
    dbHelper.getAlarmsWithUri(uri).forEach {
        it.soundTitle = defaultAlarmSound.title
        it.soundUri = defaultAlarmSound.uri
        dbHelper.updateAlarm(it)
    }
}

fun Context.rotateWeekdays(days: List<Int>) = days.rotateLeft(config.firstDayOfWeek - 1)

fun Context.firstDayOrder(bitMask: Int): Int {
    if (bitMask == TODAY_BIT) return -2
    if (bitMask == TOMORROW_BIT) return -1

    val dayBits = rotateWeekdays(
        arrayListOf(
            MONDAY_BIT,
            TUESDAY_BIT,
            WEDNESDAY_BIT,
            THURSDAY_BIT,
            FRIDAY_BIT,
            SATURDAY_BIT,
            SUNDAY_BIT
        )
    )

    dayBits.forEachIndexed { i, bit ->
        if (bitMask and bit != 0) {
            return i
        }
    }

    return bitMask
}

// ---- Routines ----

fun Context.createNewRoutine(): org.fossify.clock.models.Routine {
    val defaultAlarmSound = getDefaultAlarmSound(RingtoneManager.TYPE_ALARM)
    return org.fossify.clock.models.Routine(
        id = null,
        label = "",
        groupId = null,
        intervalSeconds = 20 * 60,
        isEnabled = true,
        startTimeMinutes = 9 * 60,
        endTimeMinutes = 18 * 60,
        days = org.fossify.commons.helpers.EVERY_DAY_BIT,
        vibrate = true,
        soundUri = defaultAlarmSound.uri,
        soundTitle = defaultAlarmSound.title,
        notificationStyle = org.fossify.clock.models.ROUTINE_STYLE_CONTINUOUS,
    )
}

fun Context.getRoutineIntent(routine: org.fossify.clock.models.Routine): PendingIntent {
    val id = routine.id ?: 0
    val intent = Intent(this, org.fossify.clock.receivers.RoutineReceiver::class.java)
    intent.putExtra(ROUTINE_ID, id)
    return PendingIntent.getBroadcast(
        this,
        id,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

fun Context.scheduleRoutineAlarm(routine: org.fossify.clock.models.Routine, triggerTimeMillis: Long) {
    try {
        AlarmManagerCompat.setExactAndAllowWhileIdle(
            alarmManager,
            AlarmManager.RTC_WAKEUP,
            triggerTimeMillis,
            getRoutineIntent(routine)
        )
    } catch (e: Exception) {
        showErrorToast(e)
    }
}

fun Context.cancelRoutineAlarm(routine: org.fossify.clock.models.Routine) {
    alarmManager.cancel(getRoutineIntent(routine))
}

fun Context.getOpenRoutineTabIntent(): PendingIntent {
    val intent = getLaunchIntent() ?: Intent(this, SplashActivity::class.java)
    intent.putExtra(OPEN_TAB, TAB_ROUTINE)
    return PendingIntent.getActivity(
        this,
        OPEN_ROUTINE_TAB_INTENT_ID,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

fun Context.getRoutineStopPendingIntent(routine: org.fossify.clock.models.Routine): PendingIntent {
    val id = routine.id ?: 0
    val intent = Intent(this, org.fossify.clock.receivers.RoutineStopReceiver::class.java).apply {
        putExtra(ROUTINE_ID, id)
    }
    return PendingIntent.getBroadcast(
        this,
        ROUTINE_STOP_INTENT_ID_BASE + id,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

/**
 * Fires the actual user-facing reminder for a Routine trigger.
 * - CONTINUA (default): short, non-blocking notification -- auto-cancels itself, and gets
 *   replaced anyway by the next loop trigger's notification (same notification id).
 * - DISCRETA: ongoing (non-dismissable-by-swipe) notification with an explicit "Stop" action,
 *   representing the "line stays up until the user stops it" behavior from the toggle icon.
 */
fun Context.notifyRoutineFired(routine: org.fossify.clock.models.Routine) {
    val channelId = "routine_channel_${routine.id}_${routine.soundUri}"
    val isDiscreet = routine.notificationStyle == org.fossify.clock.models.ROUTINE_STYLE_DISCREET

    try {
        notificationManager.deleteNotificationChannel(channelId)
    } catch (_: Exception) {
    }

    var soundUri = routine.soundUri
    if (soundUri == SILENT) {
        soundUri = ""
    } else {
        grantReadUriPermission(soundUri)
    }

    val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .setLegacyStreamType(STREAM_ALARM)
        .build()

    val importance = if (isDiscreet) {
        NotificationManager.IMPORTANCE_HIGH
    } else {
        NotificationManager.IMPORTANCE_DEFAULT
    }

    NotificationChannel(channelId, getString(R.string.routines), importance).apply {
        setSound(soundUri.toUri(), audioAttributes)
        enableVibration(routine.vibrate)
        if (!routine.vibrate) {
            vibrationPattern = longArrayOf(0L)
        }
        notificationManager.createNotificationChannel(this)
    }

    val title = routine.label.ifEmpty { getString(R.string.routines) }
    val contentIntent = getOpenRoutineTabIntent()
    val notificationId = ROUTINE_NOTIFICATION_ID_BASE + (routine.id ?: 0)

    val builder = NotificationCompat.Builder(this, channelId)
        .setContentTitle(title)
        .setContentText(getString(R.string.routine_reminder_text))
        .setSmallIcon(R.drawable.ic_routine_vector)
        .setContentIntent(contentIntent)
        .setChannelId(channelId)
        .setSound(soundUri.toUri(), STREAM_ALARM)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

    if (routine.vibrate) {
        builder.setVibrate(LongArray(2) { 500 })
    }

    if (isDiscreet) {
        builder
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(
                org.fossify.commons.R.drawable.ic_cross_vector,
                getString(R.string.stop_routine),
                getRoutineStopPendingIntent(routine)
            )
    } else {
        builder
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(false)
            .setAutoCancel(true)
            .setTimeoutAfter(10_000L)
    }

    notificationManager.notify(notificationId, builder.build())
}
