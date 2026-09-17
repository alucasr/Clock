package org.fossify.clock.dialogs

import android.view.LayoutInflater
import org.fossify.clock.R
import org.fossify.clock.activities.SimpleActivity
import org.fossify.clock.databinding.DialogManageGroupsBinding
import org.fossify.clock.databinding.ItemAlarmGroupBinding
import org.fossify.clock.extensions.routineGroupHelper
import org.fossify.clock.models.RoutineEvent
import org.fossify.clock.models.RoutineGroup
import org.fossify.commons.dialogs.ConfirmationAdvancedDialog
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.greenrobot.eventbus.EventBus

/**
 * Lets the user create, rename, enable/disable, and delete Routine groups. Fully independent
 * from [ManageGroupsDialog] (alarm groups) -- routines have their own group list. Mirrors its
 * structure closely for UI consistency, but operates on [RoutineGroup] / [routineGroupHelper]
 * instead of [org.fossify.clock.models.AlarmGroup] / [org.fossify.clock.helpers.DBHelper].
 */
class ManageRoutineGroupsDialog(val activity: SimpleActivity, val onDismiss: () -> Unit = {}) {
    private val binding = DialogManageGroupsBinding.inflate(activity.layoutInflater)
    private var dialog: androidx.appcompat.app.AlertDialog? = null

    init {
        setupGroupsList()

        binding.manageGroupsAddNew.setOnClickListener {
            GroupTitleDialog(
                activity = activity,
                currentTitle = null,
                existingTitles = currentGroups().map { it.title },
            ) { newTitle ->
                activity.routineGroupHelper.insertOrUpdateRoutineGroup(RoutineGroup(id = 0, title = newTitle, isEnabled = true)) {
                    activity.runOnUiThread {
                        EventBus.getDefault().post(RoutineEvent.Refresh)
                        setupGroupsList()
                    }
                }
            }
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setOnDismissListener { onDismiss() }
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.manage_routine_groups) { alertDialog ->
                    dialog = alertDialog
                }
            }
    }

    private var cachedGroups = ArrayList<RoutineGroup>()

    private fun currentGroups() = cachedGroups

    private fun setupGroupsList() {
        activity.routineGroupHelper.getRoutineGroups { groups ->
            activity.runOnUiThread {
                cachedGroups = ArrayList(groups.sortedBy { it.title.lowercase() })
                renderGroupsList()
            }
        }
    }

    private fun renderGroupsList() {
        binding.manageGroupsHolder.removeAllViews()
        binding.manageGroupsPlaceholder.beVisibleIf(cachedGroups.isEmpty())

        cachedGroups.forEach { group ->
            val rowBinding = ItemAlarmGroupBinding.inflate(
                LayoutInflater.from(activity), binding.manageGroupsHolder, false
            )
            bindGroupRow(rowBinding, group)
            binding.manageGroupsHolder.addView(rowBinding.root)
        }
    }

    private fun bindGroupRow(rowBinding: ItemAlarmGroupBinding, group: RoutineGroup) {
        rowBinding.apply {
            groupTitle.text = group.title
            groupAlarmCount.text = ""

            activity.routineGroupHelper.getRoutineCountForGroup(group.id) { routineCount ->
                activity.runOnUiThread {
                    groupAlarmCount.text = activity.resources.getQuantityString(
                        R.plurals.routine_group_count, routineCount, routineCount
                    )
                }

                groupDelete.setOnClickListener {
                    if (routineCount == 0) {
                        activity.routineGroupHelper.deleteRoutineGroup(group.id, deleteRoutinesInGroup = false) {
                            activity.runOnUiThread {
                                EventBus.getDefault().post(RoutineEvent.Refresh)
                                setupGroupsList()
                            }
                        }
                    } else {
                        activity.runOnUiThread {
                            ConfirmationAdvancedDialog(
                                activity = activity,
                                message = activity.getString(R.string.delete_routine_group_confirmation, routineCount),
                                positive = R.string.delete_group_and_routines,
                                negative = R.string.delete_group_keep_routines,
                            ) { deleteRoutinesToo ->
                                activity.routineGroupHelper.deleteRoutineGroup(group.id, deleteRoutinesInGroup = deleteRoutinesToo) {
                                    activity.runOnUiThread {
                                        EventBus.getDefault().post(RoutineEvent.Refresh)
                                        setupGroupsList()
                                    }
                                }
                            }
                        }
                    }
                }
            }

            groupSwitch.isChecked = group.isEnabled
            groupSwitch.setOnClickListener {
                val newState = groupSwitch.isChecked
                activity.routineGroupHelper.updateRoutineGroupEnabledState(group.id, newState) {
                    activity.runOnUiThread { EventBus.getDefault().post(RoutineEvent.Refresh) }
                }
            }

            groupRename.setOnClickListener {
                GroupTitleDialog(
                    activity = activity,
                    currentTitle = group.title,
                    existingTitles = currentGroups().map { it.title },
                ) { newTitle ->
                    activity.routineGroupHelper.insertOrUpdateRoutineGroup(group.copy(title = newTitle)) {
                        activity.runOnUiThread {
                            EventBus.getDefault().post(RoutineEvent.Refresh)
                            setupGroupsList()
                        }
                    }
                }
            }
        }
    }
}
