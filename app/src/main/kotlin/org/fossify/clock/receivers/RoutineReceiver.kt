package org.fossify.clock.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.fossify.clock.extensions.goAsync
import org.fossify.clock.extensions.routineController
import org.fossify.clock.helpers.INVALID_ROUTINE_ID
import org.fossify.clock.helpers.ROUTINE_ID

/**
 * Receiver responsible for firing a Routine's periodic reminder. Mirrors [AlarmReceiver]'s
 * pattern but with the loop logic described in the Routine feature: on each trigger it decides
 * whether to notify + reschedule +intervalMinutes, or skip ahead to the next valid day's start
 * time, or stop entirely if the routine was disabled in the meantime.
 */
class RoutineReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(ROUTINE_ID, INVALID_ROUTINE_ID)
        if (id == INVALID_ROUTINE_ID) return

        goAsync {
            context.routineController.onRoutineTriggered(id)
        }
    }
}
