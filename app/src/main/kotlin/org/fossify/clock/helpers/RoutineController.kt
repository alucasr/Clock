package org.fossify.clock.helpers

import android.app.Application
import android.content.Context
import org.fossify.clock.extensions.cancelRoutineAlarm
import org.fossify.clock.extensions.isBitSet
import org.fossify.clock.extensions.notifyRoutineFired
import org.fossify.clock.extensions.routineDb
import org.fossify.clock.extensions.routineHelper
import org.fossify.clock.extensions.scheduleRoutineAlarm
import org.fossify.clock.extensions.updateWidgets
import org.fossify.clock.models.Routine
import org.fossify.clock.models.RoutineEvent
import org.fossify.commons.helpers.ensureBackgroundThread
import org.greenrobot.eventbus.EventBus
import java.util.Calendar

/**
 * Centralized class handling Routine scheduling/triggering. Mirrors [AlarmController]'s
 * responsibilities but implements the alarm+timer hybrid loop described in the feature spec:
 * - like an alarm: the first firing depends on start time / weekdays / isEnabled.
 * - like a timer: once started, it's a plain interval countdown.
 * - the loop: each firing reschedules itself +intervalMinutes using
 *   setExactAndAllowWhileIdle, until it falls outside the [Routine.endTimeMinutes] window, in
 *   which case it jumps to the next valid day's startTimeMinutes.
 */
class RoutineController(
    private val context: Application,
    private val bus: EventBus,
) {
    /**
     * (Re)schedules every enabled routine's *next* occurrence. Used at app startup / boot,
     * mirroring [AlarmController.rescheduleEnabledAlarms]. Each routine independently figures
     * out whether "now" falls inside its window (resuming the loop) or whether it must wait
     * for its next start time.
     */
    fun rescheduleEnabledRoutines() {
        ensureBackgroundThread {
            context.routineHelper.getEnabledRoutines { routines ->
                routines.forEach { scheduleNext(it, isInitial = true) }
            }
        }
    }

    /**
     * Called whenever a routine is created/edited/enabled from the UI. Cancels any pending
     * alarm for it first, then reschedules if it's enabled.
     */
    fun onRoutineSaved(routine: Routine) {
        ensureBackgroundThread {
            context.cancelRoutineAlarm(routine)
            if (routine.isEnabled) {
                scheduleNext(routine, isInitial = true)
            }
            notifyObservers()
        }
    }

    /**
     * Called when the user disables/enables a routine or deletes it.
     */
    fun onRoutineToggled(routineId: Int, isEnabled: Boolean) {
        ensureBackgroundThread {
            context.routineHelper.getRoutine(routineId) { routine ->
                if (routine == null) return@getRoutine
                routine.isEnabled = isEnabled
                context.routineHelper.insertOrUpdateRoutine(routine)
                context.cancelRoutineAlarm(routine)
                if (isEnabled) {
                    scheduleNext(routine, isInitial = true)
                }
                notifyObservers()
            }
        }
    }

    fun cancelRoutine(routine: Routine) {
        context.cancelRoutineAlarm(routine)
    }

    /**
     * Entry point invoked by [org.fossify.clock.receivers.RoutineReceiver] when a scheduled
     * alarm actually fires. Implements the 4-step logic from the spec.
     */
    fun onRoutineTriggered(routineId: Int) {
        ensureBackgroundThread {
            // fetched synchronously (we're already on a background thread here) to avoid
            // double-hopping through RoutineHelper's own async dispatch
            val current = context.routineDbSync(routineId) ?: return@ensureBackgroundThread

            // Step 1: disabled routines are inert -- no notification, no reschedule.
            if (!current.isEnabled) {
                return@ensureBackgroundThread
            }

            val now = Calendar.getInstance()
            val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

            // Step 2: is today one of the configured days?
            val isTodayValid = current.days.isBitSet(getDayNumber(now.get(Calendar.DAY_OF_WEEK)))

            // Step 3: does another repetition fit inside today's window?
            val intervalMinutesForWindowCheck = current.intervalSeconds / 60
            val fitsInWindow = isTodayValid &&
                nowMinutes >= current.startTimeMinutes &&
                nowMinutes + intervalMinutesForWindowCheck <= current.endTimeMinutes

            if (isTodayValid && fitsInWindow) {
                context.notifyRoutineFired(current)
                context.scheduleRoutineAlarm(
                    routine = current,
                    triggerTimeMillis = System.currentTimeMillis() + current.intervalSeconds * 1000L
                )
            } else {
                // Either today ran out of room in the window, or today isn't a valid day at all
                // (defensive; RoutineReceiver should only fire on valid days/windows anyway).
                // Still fire the reminder if we were inside the window (last rep of the day)
                if (isTodayValid && nowMinutes < current.endTimeMinutes) {
                    context.notifyRoutineFired(current)
                }
                scheduleNext(current, isInitial = true)
            }

            notifyObservers()
        }
    }

    /**
     * Schedules the given routine's next occurrence:
     * - if [isInitial] (app start, save, toggle-on, or "ran out of window"): find the next
     *   valid day's startTimeMinutes, UNLESS we are already inside today's active window, in
     *   which case resume immediately from "now" (rounded to the next interval boundary is not
     *   necessary -- we simply pick up right where a running loop would be, i.e. fire once now
     *   plus intervalMinutes going forward is handled by the receiver itself on next trigger).
     */
    private fun scheduleNext(routine: Routine, isInitial: Boolean) {
        val now = Calendar.getInstance()
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val isTodayValid = routine.days.isBitSet(getDayNumber(now.get(Calendar.DAY_OF_WEEK)))

        val triggerCalendar: Calendar? = if (
            isInitial && isTodayValid &&
            nowMinutes >= routine.startTimeMinutes &&
            nowMinutes < routine.endTimeMinutes
        ) {
            // We're inside an active window right now (e.g. app was restarted mid-day) --
            // resume the loop immediately.
            Calendar.getInstance()
        } else {
            getTimeOfNextRoutineStart(routine.startTimeMinutes, routine.days)
        }

        if (triggerCalendar != null) {
            context.scheduleRoutineAlarm(routine, triggerCalendar.timeInMillis)
        }
    }

    private fun notifyObservers() {
        context.updateWidgets()
        bus.post(RoutineEvent.Refresh)
    }

    companion object {
        @Volatile
        private var instance: RoutineController? = null

        fun getInstance(context: Context): RoutineController {
            val appContext = context.applicationContext as Application
            return instance ?: synchronized(this) {
                instance ?: RoutineController(
                    context = appContext,
                    bus = EventBus.getDefault(),
                ).also { instance = it }
            }
        }
    }
}

/**
 * Synchronous helper to fetch a single routine, used inside a background thread that's already
 * running (avoids double-hopping through [org.fossify.clock.helpers.RoutineHelper]'s own
 * background dispatch, which would otherwise race against the caller).
 */
private fun Context.routineDbSync(routineId: Int) =
    routineDb.getRoutine(routineId)
