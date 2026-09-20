package org.fossify.clock.dialogs

import org.fossify.clock.R
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.ConfirmationAdvancedDialog

/**
 * Shown when the user taps the switch to disable a *recurring* alarm. Asks whether they want to
 * skip just the next occurrence (the alarm keeps repeating normally afterwards) or disable the
 * alarm completely (turned off until manually re-enabled).
 *
 * "Skip only next time" is backed by [org.fossify.clock.models.Alarm.isNextExecutionCancelled]:
 * the same flag set when tapping "Cancel" on the upcoming-alarm notification.
 */
class DisableAlarmDialog(
    activity: BaseSimpleActivity,
    val onResult: (skipNextOnly: Boolean) -> Unit,
) {
    init {
        ConfirmationAdvancedDialog(
            activity = activity,
            messageId = R.string.disable_alarm_skip_next_only_description,
            positive = R.string.disable_alarm_completely,
            negative = R.string.disable_alarm_skip_next_only,
            cancelOnTouchOutside = true
        ) { disableCompletely ->
            onResult(!disableCompletely)
        }
    }
}
