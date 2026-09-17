package org.fossify.clock.dialogs

import android.app.TimePickerDialog
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.media.RingtoneManager
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import org.fossify.clock.R
import org.fossify.clock.activities.SimpleActivity
import org.fossify.clock.databinding.DialogEditRoutineBinding
import org.fossify.clock.extensions.checkAlarmsWithDeletedSoundUri
import org.fossify.clock.extensions.colorCompoundDrawable
import org.fossify.clock.extensions.config
import org.fossify.clock.extensions.routineGroupHelper
import org.fossify.clock.extensions.getFormattedTime
import org.fossify.clock.extensions.handleFullScreenNotificationsPermission
import org.fossify.clock.extensions.rotateWeekdays
import org.fossify.clock.extensions.routineController
import org.fossify.clock.extensions.routineHelper
import org.fossify.clock.helpers.PICK_AUDIO_FILE_INTENT_ID
import org.fossify.clock.models.ROUTINE_STYLE_CONTINUOUS
import org.fossify.clock.models.ROUTINE_STYLE_DISCREET
import org.fossify.clock.models.Routine
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.dialogs.SelectAlarmSoundDialog
import org.fossify.commons.extensions.addBit
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getDefaultAlarmSound
import org.fossify.commons.extensions.getFormattedDuration
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.getTimePickerDialogTheme
import org.fossify.commons.extensions.isDynamicTheme
import org.fossify.commons.extensions.removeBit
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.value
import org.fossify.commons.models.AlarmSound
import org.fossify.commons.models.RadioItem

class EditRoutineDialog(
    val activity: SimpleActivity,
    val routine: Routine,
    val callback: () -> Unit,
) {
    private val binding = DialogEditRoutineBinding.inflate(activity.layoutInflater)
    private val textColor = activity.getProperTextColor()

    init {
        updateStartTime()
        updateEndTime()
        updateIntervalLabel()

        binding.apply {
            editRoutine.setText(routine.label)

            editRoutineStartTime.setOnClickListener {
                pickTime(routine.startTimeMinutes) { hours, minutes ->
                    routine.startTimeMinutes = hours * 60 + minutes
                    updateStartTime()
                }
            }

            editRoutineEndTime.setOnClickListener {
                pickTime(routine.endTimeMinutes) { hours, minutes ->
                    routine.endTimeMinutes = hours * 60 + minutes
                    updateEndTime()
                }
            }

            editRoutineInterval.setOnClickListener {
                showIntervalPickerDialog()
            }

            editRoutineSound.colorCompoundDrawable(textColor)
            editRoutineSound.text = routine.soundTitle
            editRoutineSound.setOnClickListener {
                SelectAlarmSoundDialog(
                    activity = activity,
                    currentUri = routine.soundUri,
                    audioStream = AudioManager.STREAM_ALARM,
                    pickAudioIntentId = PICK_AUDIO_FILE_INTENT_ID,
                    type = RingtoneManager.TYPE_ALARM,
                    loopAudio = true,
                    onAlarmPicked = {
                        if (it != null) {
                            updateSelectedAlarmSound(it)
                        }
                    },
                    onAlarmSoundDeleted = {
                        if (routine.soundUri == it.uri) {
                            val defaultAlarm =
                                root.context.getDefaultAlarmSound(RingtoneManager.TYPE_ALARM)
                            updateSelectedAlarmSound(defaultAlarm)
                        }
                        activity.checkAlarmsWithDeletedSoundUri(it.uri)
                    })
            }

            editRoutineVibrateIcon.setColorFilter(textColor)
            editRoutineVibrate.isChecked = routine.vibrate
            editRoutineVibrateHolder.setOnClickListener {
                editRoutineVibrate.toggle()
                routine.vibrate = editRoutineVibrate.isChecked
            }

            editRoutineLabelImage.applyColorFilter(textColor)

            val dayLetters =
                ArrayList(
                    activity.resources.getStringArray(org.fossify.commons.R.array.week_day_letters).toList()
                )
            val dayIndexes = activity.rotateWeekdays(arrayListOf(0, 1, 2, 3, 4, 5, 6))

            dayIndexes.forEach {
                val bitmask = 1 shl it
                val day = activity.layoutInflater.inflate(
                    R.layout.alarm_day, editRoutineDaysHolder, false
                ) as TextView
                day.text = dayLetters[it]

                val isDayChecked = routine.days and bitmask != 0
                day.background = getProperDayDrawable(isDayChecked)

                day.setTextColor(if (isDayChecked) root.context.getProperBackgroundColor() else textColor)
                day.setOnClickListener {
                    val selectDay = routine.days and bitmask == 0
                    if (selectDay) {
                        routine.days = routine.days.addBit(bitmask)
                    } else {
                        routine.days = routine.days.removeBit(bitmask)
                    }
                    day.background = getProperDayDrawable(selectDay)
                    day.setTextColor(if (selectDay) root.context.getProperBackgroundColor() else textColor)
                }

                editRoutineDaysHolder.addView(day)
            }

            editRoutineEnabledSwitch.isChecked = routine.isEnabled
            editRoutineEnabledSwitch.setOnCheckedChangeListener { _, isChecked ->
                routine.isEnabled = isChecked
            }

            editRoutineNotificationStyle.setLineColor(activity.getProperPrimaryColor())
            editRoutineNotificationStyle.notificationStyle = routine.notificationStyle
            editRoutineNotificationStyle.onStyleChanged = {
                routine.notificationStyle = it
            }
        }

        setupGroupSection()

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this) { alertDialog ->
                    alertDialog.window?.setSoftInputMode(
                        android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    )
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        routine.label = binding.editRoutine.value

                        if (routine.endTimeMinutes <= routine.startTimeMinutes) {
                            activity.toast(R.string.routine_invalid_time_range)
                            return@setOnClickListener
                        }

                        if (!routine.isRecurring()) {
                            activity.toast(R.string.no_days_selected)
                            return@setOnClickListener
                        }

                        activity.handleFullScreenNotificationsPermission { granted ->
                            if (granted) {
                                activity.routineHelper.insertOrUpdateRoutine(routine) { id ->
                                    if (routine.id == null || routine.id == 0) {
                                        routine.id = id.toInt()
                                    }
                                    activity.routineController.onRoutineSaved(routine)
                                    activity.runOnUiThread {
                                        callback()
                                        alertDialog.dismiss()
                                    }
                                }
                            }
                        }
                    }
                }
            }
    }

    private fun pickTime(currentMinutes: Int, onPicked: (hours: Int, minutes: Int) -> Unit) {
        if (activity.isDynamicTheme()) {
            val timeFormat = if (activity.config.use24HourFormat) {
                TimeFormat.CLOCK_24H
            } else {
                TimeFormat.CLOCK_12H
            }

            val timePicker = MaterialTimePicker.Builder()
                .setTimeFormat(timeFormat)
                .setHour(currentMinutes / 60)
                .setMinute(currentMinutes % 60)
                .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
                .build()

            timePicker.addOnPositiveButtonClickListener {
                onPicked(timePicker.hour, timePicker.minute)
            }

            timePicker.show(activity.supportFragmentManager, "")
        } else {
            TimePickerDialog(
                binding.root.context,
                binding.root.context.getTimePickerDialogTheme(),
                { _, hourOfDay, minute -> onPicked(hourOfDay, minute) },
                currentMinutes / 60,
                currentMinutes % 60,
                activity.config.use24HourFormat
            ).show()
        }
    }

    private fun updateStartTime() {
        binding.editRoutineStartTime.text = activity.getFormattedTime(
            passedSeconds = routine.startTimeMinutes * 60,
            showSeconds = false,
            makeAmPmSmaller = true
        )
    }

    private fun updateEndTime() {
        binding.editRoutineEndTime.text = activity.getFormattedTime(
            passedSeconds = routine.endTimeMinutes * 60,
            showSeconds = false,
            makeAmPmSmaller = true
        )
    }

    private fun updateIntervalLabel() {
        binding.editRoutineInterval.text = routine.intervalSeconds.getFormattedDuration(forceShowHours = true)
    }

    private fun showIntervalPickerDialog() {
        MyTimePickerDialogDialog(activity, routine.intervalSeconds) { newValue ->
            routine.intervalSeconds = if (newValue <= 0) 60 else newValue
            updateIntervalLabel()
        }
    }

    private fun getProperDayDrawable(selected: Boolean): Drawable {
        val drawableId = if (selected) {
            R.drawable.circle_background_filled
        } else {
            R.drawable.circle_background_stroke
        }

        val drawable = activity.resources.getDrawable(drawableId)
        drawable.applyColorFilter(textColor)
        return drawable
    }

    fun updateSelectedAlarmSound(alarmSound: AlarmSound) {
        routine.soundTitle = alarmSound.title
        routine.soundUri = alarmSound.uri
        binding.editRoutineSound.text = alarmSound.title
    }

    private fun setupGroupSection() {
        binding.editRoutineGroupImage.applyColorFilter(textColor)
        binding.editRoutineManageGroups.applyColorFilter(textColor)
        updateGroupLabel()

        binding.editRoutineGroupHolder.setOnClickListener {
            activity.routineGroupHelper.getRoutineGroups { groups ->
                activity.runOnUiThread {
                    val sortedGroups = groups.sortedBy { it.title.lowercase() }
                    val items = ArrayList<RadioItem>()
                    items.add(RadioItem(0, activity.getString(R.string.no_group)))
                    sortedGroups.forEach { group ->
                        items.add(RadioItem(group.id, group.title))
                    }

                    RadioGroupDialog(
                        activity = activity,
                        items = items,
                        checkedItemId = routine.groupId ?: 0,
                    ) { newValue ->
                        val selectedId = newValue as Int
                        routine.groupId = if (selectedId == 0) null else selectedId
                        updateGroupLabel()
                    }
                }
            }
        }

        binding.editRoutineManageGroups.setOnClickListener {
            ManageRoutineGroupsDialog(activity) {
                // groups may have been created/renamed/deleted while the dialog was open
                val currentGroupId = routine.groupId
                if (currentGroupId != null) {
                    activity.routineGroupHelper.getRoutineGroup(currentGroupId) { group ->
                        activity.runOnUiThread {
                            if (group == null) {
                                routine.groupId = null
                            }
                            updateGroupLabel()
                        }
                    }
                } else {
                    updateGroupLabel()
                }
            }
        }
    }

    private fun updateGroupLabel() {
        val groupId = routine.groupId
        if (groupId == null) {
            binding.editRoutineGroup.text = activity.getString(R.string.no_group)
            return
        }
        activity.routineGroupHelper.getRoutineGroups { groups ->
            activity.runOnUiThread {
                val title = groups.firstOrNull { it.id == groupId }?.title ?: activity.getString(R.string.no_group)
                binding.editRoutineGroup.text = title
            }
        }
    }
}
