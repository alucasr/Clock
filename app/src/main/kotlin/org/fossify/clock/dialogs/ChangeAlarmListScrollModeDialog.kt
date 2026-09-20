package org.fossify.clock.dialogs

import org.fossify.clock.R
import org.fossify.clock.databinding.DialogChangeAlarmListScrollModeBinding
import org.fossify.clock.extensions.config
import org.fossify.clock.helpers.ALARM_LIST_SCROLL_FROM_BEGINNING
import org.fossify.clock.helpers.ALARM_LIST_SCROLL_FROM_CURRENT_TIME
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff

/**
 * Lets the user choose whether the "All" alarms list opens scrolled to the very beginning, or
 * auto-scrolled to the current hour (see [org.fossify.clock.fragments.AlarmFragment.scrollToCurrentTimeIfNeeded]).
 */
class ChangeAlarmListScrollModeDialog(val activity: BaseSimpleActivity, val callback: () -> Unit) {
    private val binding = DialogChangeAlarmListScrollModeBinding.inflate(activity.layoutInflater).apply {
        val activeRadioButton = when (activity.config.alarmListScrollMode) {
            ALARM_LIST_SCROLL_FROM_BEGINNING -> alarmListScrollModeDialogRadioBeginning
            else -> alarmListScrollModeDialogRadioCurrentTime
        }
        activeRadioButton.isChecked = true
    }

    init {
        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ -> dialogConfirmed() }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.alarm_list_scroll_mode)
            }
    }

    private fun dialogConfirmed() {
        val mode = when (binding.alarmListScrollModeDialogRadio.checkedRadioButtonId) {
            R.id.alarm_list_scroll_mode_dialog_radio_beginning -> ALARM_LIST_SCROLL_FROM_BEGINNING
            else -> ALARM_LIST_SCROLL_FROM_CURRENT_TIME
        }

        activity.config.alarmListScrollMode = mode
        callback()
    }
}
