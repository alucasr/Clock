package org.fossify.clock.helpers

import android.app.Application
import android.content.Context
import android.content.Intent
import org.fossify.clock.extensions.cancelAlarmClock
import org.fossify.clock.extensions.dbHelper
import org.fossify.clock.extensions.setupAlarmClock
import org.fossify.clock.extensions.showRemainingTimeMessage
import org.fossify.clock.extensions.updateWidgets
import org.fossify.clock.models.Alarm
import org.fossify.clock.models.AlarmEvent
import org.fossify.clock.services.AlarmService
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.helpers.ensureBackgroundThread
import org.greenrobot.eventbus.EventBus
import java.util.Calendar

/**
 * Centralized class for handling alarm operations including dismissal, cancellation, scheduling,
 * and state management.
 */
class AlarmController(
    private val context: Application,
    private val db: DBHelper,
    private val bus: EventBus,
) {
    /**
     * Reschedules all enabled alarms.
     * Skips rescheduling one-time alarms that were set for today but whose time has already passed,
     * and potentially upcoming alarms for today depending on the logic in `scheduleNextOccurrence`.
     */
    fun rescheduleEnabledAlarms() {
        db.getEnabledAlarms().forEach {
            // TODO: Skipped upcoming alarms are being *rescheduled* here.
            if (!it.isToday() || it.timeInMinutes > getCurrentDayMinutes()) {
                scheduleNextOccurrence(it, false)
            }
        }
    }

    /**
     * Schedules the next occurrence of the given alarm based on its properties (time, repetition).
     *
     * @param alarm The alarm to schedule.
     * @param showToasts If true, a remaining time toast will be shown for the alarm.
     */
    fun scheduleNextOccurrence(alarm: Alarm, showToasts: Boolean = false) {
        ensureBackgroundThread {
            scheduleNextAlarm(alarm, showToasts)
            notifyObservers()
        }
    }

    /**
     * Marks the *next scheduled occurrence* of an alarm to be skipped, without touching its
     * schedule at all: [onAlarmTriggered] checks this flag when the alarm fires and, if set,
     * silently consumes it (clears it back to false) instead of sounding. This is much simpler
     * and more robust than the previous approach of computing "remaining days" to reschedule
     * around today, which broke for single-day alarms (see task #16) since skipping the only
     * configured day left nothing to reschedule.
     *
     * @param alarmId The ID of the upcoming alarm trigger to skip.
     */
    fun skipNextOccurrence(alarmId: Int) {
        ensureBackgroundThread {
            db.updateAlarmNextExecutionCancelled(alarmId, true)
            notifyObservers()
        }
    }

    /**
     * Handles the triggering of an alarm.
     * If the alarm is repeating, it schedules the next occurrence immediately.
     * If [Alarm.isNextExecutionCancelled] was set (user tapped "Cancel" on the upcoming-alarm
     * notification), the flag is cleared and the alarm does NOT sound this time.
     * Otherwise, it starts the service for sounding the alarm.
     *
     * @param alarmId The ID of the alarm that was triggered.
     */
    fun onAlarmTriggered(alarmId: Int) {
        ensureBackgroundThread {
            val alarm = db.getAlarmWithId(alarmId) ?: return@ensureBackgroundThread
            // Defensive guard: the group was disabled after this trigger was armed (e.g. a snooze).
            // Stay silent and leave the alarm's own state untouched.
            if (!db.isGroupEnabled(alarm.groupId)) {
                notifyObservers()
                return@ensureBackgroundThread
            }
            // Reschedule the next occurrence right away -- except for single-use alarms, which
            // must ring exactly once even when they have repeat days (e.g. "this Saturday only").
            if (alarm.isRecurring() && !alarm.oneShot) {
                scheduleNextOccurrence(alarm)
            }

            if (alarm.isNextExecutionCancelled) {
                db.updateAlarmNextExecutionCancelled(alarmId, false)
                notifyObservers()

                if (!alarm.isRecurring() || alarm.oneShot) {
                    disableOrDeleteOneTimeAlarm(alarm)
                }
                return@ensureBackgroundThread
            }

            sendIntentToService(AlarmService.ACTION_START_ALARM, alarmId)
        }
    }

    /**
     * Silences the currently ringing alarm by stopping the alarm service.
     */
    fun silenceAlarm(alarmId: Int) {
        sendIntentToService(AlarmService.ACTION_STOP_ALARM, alarmId)
    }

    /**
     * Dismisses an alarm that is currently ringing or has just finished ringing.
     *
     * - Stops the alarm sound/vibration service.
     * - If the alarm is *not* repeating, it is cancelled in the system scheduler and then
     * disabled or deleted via [disableOrDeleteOneTimeAlarm].
     *
     * @param alarmId The ID of the alarm to dismiss.
     */
    fun stopAlarm(alarmId: Int) {
        sendIntentToService(AlarmService.ACTION_STOP_ALARM, alarmId)
        bus.post(AlarmEvent.Stopped(alarmId))

        ensureBackgroundThread {
            val alarm = db.getAlarmWithId(alarmId)

            // We don't reschedule alarms here. A single-use alarm is finished after ringing even
            // if it has repeat days.
            if (alarm != null && (!alarm.isRecurring() || alarm.oneShot)) {
                context.cancelAlarmClock(alarm)
                disableOrDeleteOneTimeAlarm(alarm)
            }

            notifyObservers()
        }
    }

    /**
     * Snoozes an alarm that is currently ringing.
     *
     * - Stops the alarm sound/vibration service.
     * - Schedules the alarm to ring again after [snoozeMinutes] using [setupAlarmClock]
     *   with a calculated future trigger time.
     *
     * @param alarmId The ID of the alarm to snooze.
     * @param snoozeMinutes The number of minutes from now until the alarm should ring again.
     */
    fun snoozeAlarm(alarmId: Int, snoozeMinutes: Int) {
        sendIntentToService(AlarmService.ACTION_STOP_ALARM, alarmId)
        bus.post(AlarmEvent.Stopped(alarmId))

        ensureBackgroundThread {
            val alarm = db.getAlarmWithId(alarmId)
            // TODO: This works but it is very rudimentary. Snoozed alarms are not being tracked.
            if (alarm != null) {
                val triggerTimeMillis = Calendar.getInstance()
                    .apply { add(Calendar.MINUTE, snoozeMinutes) }
                    .timeInMillis

                context.setupAlarmClock(alarm = alarm, triggerTimeMillis = triggerTimeMillis)
            }

            notifyObservers()
        }
    }

    /**
     * Handles disabling or deleting an alarm that has finished its only run: either a
     * non-repeating alarm, or a repeating alarm flagged `oneShot` (single use).
     * This is typically called after a one-time alarm has rung and been dismissed or stopped,
     * or when it's explicitly skipped.
     *
     * @param alarm The finished alarm. Must be non-repeating or flagged `oneShot`.
     */
    private fun disableOrDeleteOneTimeAlarm(alarm: Alarm) {
        require(!alarm.isRecurring() || alarm.oneShot) {
            "Alarm ${alarm.id} is repeating but was passed to disableOrDeleteOneTimeAlarm()"
        }

        if (alarm.oneShot) {
            alarm.isEnabled = false
            db.deleteAlarms(arrayListOf(alarm))
        } else {
            db.updateAlarmEnabledState(alarm.id, false)
        }
    }

    /**
     * Applies a group's new enabled state to the system scheduler. Call AFTER the group flag has
     * been persisted. Disabling cancels the pending intent of every alarm in the group;
     * enabling schedules those alarms that are individually switched on. The alarms' own
     * [Alarm.isEnabled] flags are left untouched on purpose (see [DBHelper.getEnabledAlarms]).
     *
     * @param groupId The group whose enabled flag just changed.
     */
    fun onGroupEnabledChanged(groupId: Int) {
        ensureBackgroundThread {
            val groupEnabled = db.isGroupEnabled(groupId)
            db.getAlarms().filter { it.groupId == groupId && it.isEnabled }.forEach { alarm ->
                if (groupEnabled) {
                    scheduleNextAlarm(alarm)
                } else {
                    context.cancelAlarmClock(alarm)
                }
            }
            notifyObservers()
        }
    }

    private fun scheduleNextAlarm(alarm: Alarm, showToast: Boolean = false) {
        // Single choke point for scheduling: an alarm in a disabled group must never be armed,
        // no matter which path asked for it (toggle, edit dialog, reboot reschedule, intents).
        if (!db.isGroupEnabled(alarm.groupId)) {
            context.cancelAlarmClock(alarm)
            return
        }
        val triggerTimeMillis = getTimeOfNextAlarm(alarm)?.timeInMillis ?: return
        context.setupAlarmClock(alarm = alarm, triggerTimeMillis = triggerTimeMillis)

        if (showToast) {
            val now = Calendar.getInstance()
            val triggerInMillis = triggerTimeMillis - now.timeInMillis
            context.showRemainingTimeMessage(triggerInMillis)
        }
    }

    private fun notifyObservers() {
        context.updateWidgets()
        bus.post(AlarmEvent.Refresh)
    }

    private fun sendIntentToService(action: String, alarmId: Int) {
        try {
            val serviceIntent = Intent(context, AlarmService::class.java).apply {
                this.action = action
                putExtra(ALARM_ID, alarmId)
            }

            when (action) {
                AlarmService.ACTION_START_ALARM -> context.startForegroundService(serviceIntent)
                AlarmService.ACTION_STOP_ALARM -> context.startService(serviceIntent)
                else -> throw IllegalArgumentException("Unknown action: $action")
            }
        } catch (e: Exception) {
            context.showErrorToast(e)
        }
    }

    companion object {
        @Volatile
        private var instance: AlarmController? = null

        fun getInstance(context: Context): AlarmController {
            val appContext = context.applicationContext as Application
            return instance ?: synchronized(this) {
                instance ?: AlarmController(
                    context = appContext,
                    db = appContext.dbHelper,
                    bus = EventBus.getDefault()
                ).also { instance = it }
            }
        }
    }
}
