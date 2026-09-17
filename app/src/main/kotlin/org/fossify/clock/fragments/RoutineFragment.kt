package org.fossify.clock.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.fossify.clock.R
import org.fossify.clock.activities.SimpleActivity
import org.fossify.clock.adapters.RoutinesAdapter
import org.fossify.clock.databinding.FragmentRoutineBinding
import org.fossify.clock.databinding.ItemGroupChipBinding
import org.fossify.clock.dialogs.EditRoutineDialog
import org.fossify.clock.dialogs.ManageRoutineGroupsDialog
import org.fossify.clock.extensions.createNewRoutine
import org.fossify.clock.extensions.routineController
import org.fossify.clock.extensions.routineGroupHelper
import org.fossify.clock.extensions.routineHelper
import org.fossify.clock.models.Routine
import org.fossify.clock.models.RoutineEvent
import org.fossify.clock.models.RoutineGroup
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.models.AlarmSound
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class RoutineFragment : Fragment() {
    private var routines = ArrayList<Routine>()
    private var currentEditRoutineDialog: EditRoutineDialog? = null

    // null = "All groups" filter selected (default)
    private var selectedGroupFilterId: Int? = null

    private lateinit var binding: FragmentRoutineBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentRoutineBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EventBus.getDefault().register(this)
    }

    override fun onDestroy() {
        EventBus.getDefault().unregister(this)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        setupViews()
    }

    private fun setupViews() {
        binding.apply {
            requireContext().updateTextColors(routineFragment)
            routineFab.setOnClickListener {
                val newRoutine = root.context.createNewRoutine()
                newRoutine.groupId = selectedGroupFilterId
                openEditRoutine(newRoutine)
            }
        }

        setupRoutines()
    }

    private fun setupRoutines() {
        setupGroupFilters()
        val safeContext = context ?: return
        safeContext.routineGroupHelper.getRoutineGroups { groups ->
            val groupTitles = groups.associate { it.id to it.title }
            val showGroupPrefix = selectedGroupFilterId == null
            safeContext.routineHelper.getRoutines { newRoutines ->
                val activity = activity ?: return@getRoutines
                activity.runOnUiThread {
                    val sortedRoutines = ArrayList(newRoutines.sortedBy { it.startTimeMinutes })
                    routines = if (selectedGroupFilterId == null) {
                        sortedRoutines
                    } else {
                        ArrayList(sortedRoutines.filter { it.groupId == selectedGroupFilterId })
                    }
                    val safeActivity = activity as? SimpleActivity ?: return@runOnUiThread
                    var currAdapter = binding.routinesList.adapter as? RoutinesAdapter
                    if (currAdapter == null) {
                        currAdapter = RoutinesAdapter(
                            activity = safeActivity,
                            routines = routines,
                            recyclerView = binding.routinesList,
                            groupTitles = groupTitles,
                            showGroupPrefix = showGroupPrefix,
                            onToggle = { routine, isEnabled ->
                                toggleRoutine(routine, isEnabled)
                            },
                        ) {
                            openEditRoutine(it as Routine)
                        }.apply {
                            binding.routinesList.adapter = this
                        }
                    } else {
                        currAdapter.apply {
                            updatePrimaryColor()
                            updateBackgroundColor(safeActivity.getProperBackgroundColor())
                            updateTextColor(safeActivity.getProperTextColor())
                            updateItems(routines, groupTitles, showGroupPrefix)
                        }
                    }
                    binding.routinesPlaceholder.beVisibleIf(routines.isEmpty())
                }
            }
        }
    }

    /**
     * Builds the horizontal "All / <group> / ..." filter row shown above the routines list,
     * mirroring [AlarmFragment.setupGroupFilters] but backed by the independent Routine group
     * list ([RoutineGroup] / [routineGroupHelper]). The group-management action is a separate
     * gear icon (not a chip) so it can never be mistaken for an actual group filter.
     */
    private fun setupGroupFilters() {
        val safeContext = context ?: return
        val safeActivity = activity as? SimpleActivity ?: return

        binding.routineGroupsManageIcon.applyColorFilter(safeContext.getProperTextColor())
        binding.routineGroupsManageIcon.setOnClickListener {
            ManageRoutineGroupsDialog(safeActivity) {
                setupRoutines()
            }
        }

        safeContext.routineGroupHelper.getRoutineGroups { fetchedGroups ->
            val activity = activity ?: return@getRoutineGroups
            activity.runOnUiThread {
                val groups = fetchedGroups.sortedBy { it.title.lowercase() }

                // The filter row (chips + gear icon) is always shown so group management stays
                // reachable even with zero groups defined yet.
                binding.routineGroupsFilterRow.beVisibleIf(true)

                // if the previously selected group filter got deleted, fall back to "All"
                if (selectedGroupFilterId != null && groups.none { it.id == selectedGroupFilterId }) {
                    selectedGroupFilterId = null
                }

                binding.routineGroupsFilterHolder.removeAllViews()

                val primaryColor = safeContext.getProperPrimaryColor()
                val textColor = safeContext.getProperTextColor()

                fun addChip(id: Int?, title: String) {
                    val chipBinding = ItemGroupChipBinding.inflate(layoutInflater, binding.routineGroupsFilterHolder, false)
                    chipBinding.groupChipText.apply {
                        text = title
                        background = background.mutate()
                        val isSelected = selectedGroupFilterId == id
                        setTextColor(if (isSelected) safeContext.getProperBackgroundColor() else textColor)
                        (background as? android.graphics.drawable.GradientDrawable)?.apply {
                            setColor(if (isSelected) primaryColor else android.graphics.Color.TRANSPARENT)
                            setStroke(2, textColor)
                        }
                        setOnClickListener {
                            selectedGroupFilterId = id
                            setupRoutines()
                        }
                    }
                    binding.routineGroupsFilterHolder.addView(chipBinding.root)
                }

                addChip(null, getString(R.string.all_groups))
                groups.forEach { group: RoutineGroup ->
                    addChip(group.id, group.title)
                }
            }
        }
    }

    private fun toggleRoutine(routine: Routine, isEnabled: Boolean) {
        val safeContext = context ?: return
        routine.isEnabled = isEnabled
        safeContext.routineController.onRoutineToggled(routine.id ?: return, isEnabled)
    }

    private fun openEditRoutine(routine: Routine) {
        currentEditRoutineDialog = EditRoutineDialog(activity as SimpleActivity, routine) {
            currentEditRoutineDialog = null
            setupRoutines()
        }
    }

    fun updateRoutineSound(alarmSound: AlarmSound) {
        currentEditRoutineDialog?.updateSelectedAlarmSound(alarmSound)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(@Suppress("unused") event: RoutineEvent.Refresh) {
        setupRoutines()
    }
}
