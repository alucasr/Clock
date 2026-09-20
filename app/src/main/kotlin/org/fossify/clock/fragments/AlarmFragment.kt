package org.fossify.clock.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.fossify.clock.R
import org.fossify.clock.activities.MainActivity
import org.fossify.clock.activities.SimpleActivity
import org.fossify.clock.adapters.AlarmsAdapter
import org.fossify.clock.databinding.FragmentAlarmBinding
import org.fossify.clock.databinding.ItemGroupChipBinding
import org.fossify.clock.dialogs.ChangeAlarmSortDialog
import org.fossify.clock.dialogs.EditAlarmDialog
import org.fossify.clock.dialogs.ManageGroupsDialog
import org.fossify.clock.extensions.alarmController
import org.fossify.clock.extensions.cancelAlarmClock
import org.fossify.clock.extensions.config
import org.fossify.clock.extensions.createNewAlarm
import org.fossify.clock.extensions.dbHelper
import org.fossify.clock.extensions.firstDayOrder
import org.fossify.clock.extensions.handleFullScreenNotificationsPermission
import org.fossify.clock.extensions.updateWidgets
import org.fossify.clock.helpers.DEFAULT_ALARM_MINUTES
import org.fossify.clock.helpers.SORT_BY_ALARM_TIME
import org.fossify.clock.helpers.SORT_BY_DATE_AND_TIME
import org.fossify.clock.helpers.getTomorrowBit
import org.fossify.clock.interfaces.ToggleAlarmInterface
import org.fossify.clock.models.Alarm
import org.fossify.clock.models.AlarmEvent
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.helpers.SORT_BY_CUSTOM
import org.fossify.commons.helpers.SORT_BY_DATE_CREATED
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.AlarmSound
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class AlarmFragment : Fragment(), ToggleAlarmInterface {
    private var alarms = ArrayList<Alarm>()
    private var currentEditAlarmDialog: EditAlarmDialog? = null

    // null = "All groups" filter selected (default)
    private var selectedGroupFilterId: Int? = null

    private lateinit var binding: FragmentAlarmBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentAlarmBinding.inflate(inflater, container, false)
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

    fun showSortingDialog() {
        ChangeAlarmSortDialog(activity as SimpleActivity) {
            setupAlarms()
        }
    }

    private fun setupViews() {
        binding.apply {
            requireContext().updateTextColors(alarmFragment)
            alarmFab.setOnClickListener {
                val newAlarm = root.context.createNewAlarm(DEFAULT_ALARM_MINUTES, 0)
                newAlarm.isEnabled = true
                newAlarm.days = getTomorrowBit()
                newAlarm.groupId = selectedGroupFilterId
                openEditAlarm(newAlarm)
            }
        }

        setupAlarms()
    }

    private fun getSortedAlarms(callback: (alarms: ArrayList<Alarm>) -> Unit) {
        val safeContext = context ?: return
        ensureBackgroundThread {
            var newAlarms = context?.dbHelper?.getAlarms()
            if (newAlarms == null) {
                activity?.runOnUiThread {
                    callback(arrayListOf())
                }
                return@ensureBackgroundThread
            }

            when (safeContext.config.alarmSort) {
                SORT_BY_ALARM_TIME -> newAlarms.sortBy { it.timeInMinutes }
                SORT_BY_DATE_CREATED -> newAlarms.sortBy { it.id }
                SORT_BY_DATE_AND_TIME -> newAlarms.sortWith(compareBy<Alarm> {
                    safeContext.firstDayOrder(it.days)
                }.thenBy {
                    it.timeInMinutes
                })

                SORT_BY_CUSTOM -> {
                    val customAlarmsSortOrderString = activity?.config?.alarmsCustomSorting
                    if (customAlarmsSortOrderString == "") {
                        newAlarms.sortBy { it.id }
                    } else {
                        val customAlarmsSortOrder: List<Int> =
                            customAlarmsSortOrderString?.split(", ")?.map { it.toInt() }!!
                        val alarmsIdValueMap = newAlarms.associateBy { it.id }

                        val sortedAlarms: ArrayList<Alarm> = ArrayList()
                        customAlarmsSortOrder.map { id ->
                            if (alarmsIdValueMap[id] != null) {
                                sortedAlarms.add(alarmsIdValueMap[id] as Alarm)
                            }
                        }

                        newAlarms =
                            (sortedAlarms + newAlarms.filter { it !in sortedAlarms }) as ArrayList<Alarm>
                    }
                }
            }

            activity?.runOnUiThread {
                callback(newAlarms)
            }
        }
    }

    private fun setupAlarms() {
        setupGroupFilters()
        val safeContext = context ?: return
        val groupTitles = safeContext.dbHelper.getGroups().associate { it.id to it.title }
        val showGroupPrefix = selectedGroupFilterId == null
        getSortedAlarms { sortedAlarms ->
            val filteredAlarms = if (selectedGroupFilterId == null) {
                sortedAlarms
            } else {
                ArrayList(sortedAlarms.filter { it.groupId == selectedGroupFilterId })
            }
            alarms = filteredAlarms
            val safeActivity = activity as? SimpleActivity ?: return@getSortedAlarms
            var currAdapter = binding.alarmsList.adapter as? AlarmsAdapter
            if (currAdapter == null) {
                currAdapter = AlarmsAdapter(
                    activity = safeActivity,
                    alarms = alarms,
                    toggleAlarmInterface = this,
                    recyclerView = binding.alarmsList,
                    groupTitles = groupTitles,
                    showGroupPrefix = showGroupPrefix,
                ) {
                    openEditAlarm(it as Alarm)
                }.apply {
                    binding.alarmsList.adapter = this
                }
            } else {
                currAdapter.apply {
                    updatePrimaryColor()
                    updateBackgroundColor(safeActivity.getProperBackgroundColor())
                    updateTextColor(safeActivity.getProperTextColor())
                    updateItems(alarms, groupTitles, showGroupPrefix)
                }
            }
            binding.alarmsPlaceholder.beVisibleIf(alarms.isEmpty())
        }
    }

    /**
     * Builds the horizontal "All / <group> / ..." filter row shown above the alarms list.
     * Tapping a group filters the list to that group's alarms. Group management is a separate
     * gear icon (not a chip) so it can never be mistaken for an actual group filter. Groups are
     * shown alphabetically, as requested.
     */
    private fun setupGroupFilters() {
        val safeContext = context ?: return
        val safeActivity = activity as? SimpleActivity ?: return
        val groups = safeContext.dbHelper.getGroups().sortedBy { it.title.lowercase() }

        // 8% of screen width on each side, so the row isn't flush against the edges -- taps
        // near the edge were being intercepted by the system's back-gesture instead of the chip.
        val screenWidth = resources.displayMetrics.widthPixels
        val sideMargin = (screenWidth * 0.08f).toInt()
        binding.alarmGroupsFilterRow.setPadding(
            sideMargin,
            binding.alarmGroupsFilterRow.paddingTop,
            sideMargin,
            binding.alarmGroupsFilterRow.paddingBottom
        )

        binding.alarmGroupsManageIcon.applyColorFilter(safeContext.getProperTextColor())
        binding.alarmGroupsManageIcon.setOnClickListener {
            ManageGroupsDialog(safeActivity) {
                setupAlarms()
            }
        }

        // The filter row (chips + gear icon) is always shown so group management stays
        // reachable even with zero groups defined yet.
        binding.alarmGroupsFilterRow.beVisibleIf(true)

        // if the previously selected group filter got deleted, fall back to "All"
        if (selectedGroupFilterId != null && groups.none { it.id == selectedGroupFilterId }) {
            selectedGroupFilterId = null
        }

        binding.alarmGroupsFilterHolder.removeAllViews()

        val primaryColor = safeContext.getProperPrimaryColor()
        val textColor = safeContext.getProperTextColor()

        fun addChip(id: Int?, title: String) {
            val chipBinding = ItemGroupChipBinding.inflate(layoutInflater, binding.alarmGroupsFilterHolder, false)
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
                    setupAlarms()
                }
            }
            binding.alarmGroupsFilterHolder.addView(chipBinding.root)
        }

        addChip(null, getString(R.string.all_groups))
        groups.forEach { group ->
            addChip(group.id, group.title)
        }
    }

    private fun openEditAlarm(alarm: Alarm) {
        currentEditAlarmDialog = EditAlarmDialog(activity as SimpleActivity, alarm) {
            alarm.id = it
            currentEditAlarmDialog = null
            setupAlarms()
            checkAlarmState(alarm)
        }
    }

    override fun alarmToggled(id: Int, isEnabled: Boolean) {
        (activity as SimpleActivity).handleFullScreenNotificationsPermission { granted ->
            if (granted) {
                if (requireContext().dbHelper.updateAlarmEnabledState(id, isEnabled)) {
                    val alarm = alarms.firstOrNull { it.id == id }
                        ?: return@handleFullScreenNotificationsPermission
                    alarm.isEnabled = isEnabled
                    checkAlarmState(alarm)
                    if (!alarm.isEnabled && alarm.oneShot) {
                        requireContext().dbHelper.deleteAlarms(arrayListOf(alarm))
                        setupAlarms()
                    }
                } else {
                    requireActivity().toast(org.fossify.commons.R.string.unknown_error_occurred)
                }
                requireContext().updateWidgets()
            } else {
                setupAlarms()
            }
        }
    }

    private fun checkAlarmState(alarm: Alarm) {
        val activity = activity as? MainActivity ?: return
        if (alarm.isEnabled) {
            activity.alarmController.scheduleNextOccurrence(alarm = alarm, showToasts = true)
        } else {
            activity.cancelAlarmClock(alarm)
        }
        activity.updateClockTabAlarm()
    }

    fun updateAlarmSound(alarmSound: AlarmSound) {
        currentEditAlarmDialog?.updateSelectedAlarmSound(alarmSound)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(@Suppress("unused") event: AlarmEvent.Refresh) {
        setupAlarms()
    }
}
