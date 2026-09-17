package org.fossify.clock.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.fossify.clock.extensions.hideNotification
import org.fossify.clock.helpers.INVALID_ROUTINE_ID
import org.fossify.clock.helpers.ROUTINE_ID
import org.fossify.clock.helpers.ROUTINE_NOTIFICATION_ID_BASE

/**
 * Stops a DISCREET-style routine notification when the user explicitly dismisses/stops it
 * (e.g. taps its "Stop" action or swipes it away). This is what makes the toggle icon's line
 * "go back down": the current reminder is cleared, and the loop naturally continues with its
 * next already-scheduled occurrence (scheduling is untouched -- only the visible/audible
 * reminder is cleared here).
 */
class RoutineStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(ROUTINE_ID, INVALID_ROUTINE_ID)
        if (id == INVALID_ROUTINE_ID) return

        context.hideNotification(ROUTINE_NOTIFICATION_ID_BASE + id)
    }
}
