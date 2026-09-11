package org.fossify.clock.dialogs

import android.view.LayoutInflater
import org.fossify.clock.R
import org.fossify.clock.activities.SimpleActivity
import org.fossify.clock.databinding.DialogManageGroupsBinding
import org.fossify.clock.databinding.ItemAlarmGroupBinding
import org.fossify.clock.extensions.dbHelper
import org.fossify.clock.models.AlarmEvent
import org.fossify.clock.models.AlarmGroup
import org.fossify.commons.dialogs.ConfirmationAdvancedDialog
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.greenrobot.eventbus.EventBus

/**
 * Lets the user create, rename, enable/disable, and delete alarm groups.
 * Any change here (create/rename/toggle/delete) posts [AlarmEvent.Refresh] so the
 * alarms list (which shows groups and their alarms together) updates immediately.
 */
class ManageGroupsDialog(val activity: SimpleActivity, val onDismiss: () -> Unit = {}) {
    private val binding = DialogManageGroupsBinding.inflate(activity.layoutInflater)
    private var dialog: androidx.appcompat.app.AlertDialog? = null

    init {
        setupGroupsList()

        binding.manageGroupsAddNew.setOnClickListener {
            GroupTitleDialog(
                activity = activity,
                currentTitle = null,
                existingTitles = activity.dbHelper.getGroups().map { it.title },
            ) { newTitle ->
                activity.dbHelper.insertGroup(AlarmGroup(id = 0, title = newTitle, isEnabled = true))
                EventBus.getDefault().post(AlarmEvent.Refresh)
                setupGroupsList()
            }
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setOnDismissListener { onDismiss() }
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.manage_groups) { alertDialog ->
                    dialog = alertDialog
                }
            }
    }

    private fun setupGroupsList() {
        binding.manageGroupsHolder.removeAllViews()
        // Alphabetical order, as requested.
        val groups = activity.dbHelper.getGroups().sortedBy { it.title.lowercase() }
        binding.manageGroupsPlaceholder.beVisibleIf(groups.isEmpty())

        groups.forEach { group ->
            val rowBinding = ItemAlarmGroupBinding.inflate(
                LayoutInflater.from(activity), binding.manageGroupsHolder, false
            )
            bindGroupRow(rowBinding, group)
            binding.manageGroupsHolder.addView(rowBinding.root)
        }
    }

    private fun bindGroupRow(rowBinding: ItemAlarmGroupBinding, group: AlarmGroup) {
        rowBinding.apply {
            groupTitle.text = group.title
            val alarmCount = activity.dbHelper.getAlarmCountForGroup(group.id)
            groupAlarmCount.text = if (alarmCount == 1) "1 alarm" else "$alarmCount alarms"

            groupSwitch.isChecked = group.isEnabled
            groupSwitch.setOnClickListener {
                val newState = groupSwitch.isChecked
                activity.dbHelper.updateGroupEnabledState(group.id, newState)
                EventBus.getDefault().post(AlarmEvent.Refresh)
            }

            groupRename.setOnClickListener {
                GroupTitleDialog(
                    activity = activity,
                    currentTitle = group.title,
                    existingTitles = activity.dbHelper.getGroups().map { it.title },
                ) { newTitle ->
                    activity.dbHelper.updateGroup(group.copy(title = newTitle))
                    EventBus.getDefault().post(AlarmEvent.Refresh)
                    setupGroupsList()
                }
            }

            groupDelete.setOnClickListener {
                if (alarmCount == 0) {
                    activity.dbHelper.deleteGroup(group.id, deleteAlarmsInGroup = false)
                    EventBus.getDefault().post(AlarmEvent.Refresh)
                    setupGroupsList()
                } else {
                    ConfirmationAdvancedDialog(
                        activity = activity,
                        message = activity.getString(R.string.delete_group_confirmation, alarmCount),
                        positive = R.string.delete_group_and_alarms,
                        negative = R.string.delete_group_keep_alarms,
                    ) { deleteAlarmsToo ->
                        activity.dbHelper.deleteGroup(group.id, deleteAlarmsInGroup = deleteAlarmsToo)
                        EventBus.getDefault().post(AlarmEvent.Refresh)
                        setupGroupsList()
                    }
                }
            }
        }
    }
}
