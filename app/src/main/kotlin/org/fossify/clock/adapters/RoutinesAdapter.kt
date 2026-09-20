package org.fossify.clock.adapters

import android.annotation.SuppressLint
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import org.fossify.clock.R
import org.fossify.clock.activities.SimpleActivity
import org.fossify.clock.databinding.ItemRoutineBinding
import org.fossify.clock.extensions.getFormattedTime
import org.fossify.clock.extensions.routineHelper
import org.fossify.clock.extensions.toCompactDurationString
import org.fossify.clock.models.Routine
import org.fossify.clock.models.RoutineEvent
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.commons.extensions.getSelectedDaysString
import org.fossify.commons.helpers.EVERY_DAY_BIT
import org.fossify.commons.views.MyRecyclerView
import org.greenrobot.eventbus.EventBus

class RoutinesAdapter(
    activity: SimpleActivity,
    private var routines: ArrayList<Routine>,
    recyclerView: MyRecyclerView,
    private var groupTitles: Map<Int, String> = emptyMap(),
    private var showGroupPrefix: Boolean = false,
    private val onToggle: (routine: Routine, isEnabled: Boolean) -> Unit,
    itemClick: (Any) -> Unit,
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick) {

    override fun getActionMenuId() = R.menu.cab_alarms

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_delete -> deleteItems()
        }
    }

    override fun getSelectableItemCount() = routines.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = routines.getOrNull(position)?.id

    override fun getItemKeyPosition(key: Int) = routines.indexOfFirst { it.id == key }

    @SuppressLint("NotifyDataSetChanged")
    override fun onActionModeCreated() {
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onActionModeDestroyed() {
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return createViewHolder(ItemRoutineBinding.inflate(layoutInflater, parent, false).root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val routine = routines[position]
        holder.bindView(
            any = routine,
            allowSingleClick = true,
            allowLongClick = true
        ) { itemView, _ ->
            setupView(itemView, routine)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = routines.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateItems(
        newItems: ArrayList<Routine>,
        newGroupTitles: Map<Int, String>? = null,
        newShowGroupPrefix: Boolean? = null,
    ) {
        routines = newItems
        if (newGroupTitles != null) {
            groupTitles = newGroupTitles
        }
        if (newShowGroupPrefix != null) {
            showGroupPrefix = newShowGroupPrefix
        }
        notifyDataSetChanged()
        finishActMode()
    }

    private fun deleteItems() {
        val routinesToRemove = ArrayList<Routine>()
        val positions = getSelectedItemPositions()
        getSelectedItems().forEach {
            routinesToRemove.add(it)
        }

        routines.removeAll(routinesToRemove)
        removeSelectedItems(positions)
        activity.routineHelper.deleteRoutines(routinesToRemove) {
            EventBus.getDefault().post(RoutineEvent.Refresh)
        }
    }

    private fun getSelectedItems(): ArrayList<Routine> {
        return routines.filter { selectedKeys.contains(it.id) } as ArrayList<Routine>
    }

    private fun setupView(view: View, routine: Routine) {
        val isSelected = selectedKeys.contains(routine.id)
        ItemRoutineBinding.bind(view).apply {
            routineHolder.isSelected = isSelected

            routineLabel.text = buildDisplayLabel(routine)
            routineLabel.setTextColor(textColor)

            routineInterval.text = activity.getString(
                R.string.routine_every_x, routine.intervalSeconds.toCompactDurationString()
            )
            routineInterval.setTextColor(textColor)

            routineTimeRange.text = "${formatMinutes(routine.startTimeMinutes)} - ${formatMinutes(routine.endTimeMinutes)}"
            routineTimeRange.setTextColor(textColor)

            routineDays.text = if (routine.days == EVERY_DAY_BIT) {
                activity.getString(org.fossify.commons.R.string.every_day)
            } else if (routine.isRecurring()) {
                activity.getSelectedDaysString(routine.days)
            } else {
                activity.getString(R.string.not_scheduled)
            }
            routineDays.setTextColor(textColor)

            routineSwitch.isChecked = routine.isEnabled
            routineSwitch.setColors(textColor, properPrimaryColor, backgroundColor)
            routineSwitch.setOnClickListener {
                routine.isEnabled = routineSwitch.isChecked
                onToggle(routine, routineSwitch.isChecked)
            }
        }
    }

    private fun buildDisplayLabel(routine: Routine): String {
        val label = routine.label.ifEmpty { activity.getString(R.string.routines) }
        val prefix = if (showGroupPrefix && routine.groupId != null) {
            val groupTitle = groupTitles[routine.groupId] ?: return label
            "($groupTitle) "
        } else {
            ""
        }
        return prefix + label
    }

    private fun formatMinutes(minutes: Int): String {
        return activity.getFormattedTime(
            passedSeconds = minutes * 60,
            showSeconds = false,
            makeAmPmSmaller = true
        ).toString()
    }
}
