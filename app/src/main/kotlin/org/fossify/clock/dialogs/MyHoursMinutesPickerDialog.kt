package org.fossify.clock.dialogs

import org.fossify.clock.activities.SimpleActivity
import org.fossify.clock.databinding.DialogMyHoursMinutesPickerBinding
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setupDialogStuff

/**
 * Like [MyTimePickerDialogDialog] but without a seconds picker -- used for durations that only
 * make sense in whole minutes (e.g. the upcoming-alarm notification lead time).
 */
class MyHoursMinutesPickerDialog(
    val activity: SimpleActivity,
    val initialMinutes: Int,
    val callback: (resultMinutes: Int) -> Unit,
) {
    private val binding = DialogMyHoursMinutesPickerBinding.inflate(activity.layoutInflater)

    init {
        binding.apply {
            val textColor = activity.getProperTextColor()
            arrayOf(myHoursMinutesPickerHours, myHoursMinutesPickerMinutes).forEach {
                it.textColor = textColor
                it.selectedTextColor = textColor
                it.dividerColor = textColor
            }

            myHoursMinutesPickerHours.value = initialMinutes / 60
            myHoursMinutesPickerMinutes.value = initialMinutes % 60
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ -> dialogConfirmed() }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this)
            }
    }

    private fun dialogConfirmed() {
        binding.apply {
            val hours = myHoursMinutesPickerHours.value
            val minutes = myHoursMinutesPickerMinutes.value
            callback(hours * 60 + minutes)
        }
    }
}
