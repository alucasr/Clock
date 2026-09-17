package org.fossify.clock.helpers

import android.content.Context
import org.fossify.clock.extensions.routineGroupDb
import org.fossify.clock.models.RoutineGroup
import org.fossify.commons.helpers.ensureBackgroundThread

class RoutineGroupHelper(val context: Context) {
    private val dao = context.routineGroupDb

    fun getRoutineGroups(callback: (groups: List<RoutineGroup>) -> Unit) {
        ensureBackgroundThread {
            callback.invoke(dao.getRoutineGroups())
        }
    }

    fun getRoutineGroup(id: Int, callback: (group: RoutineGroup?) -> Unit) {
        ensureBackgroundThread {
            callback.invoke(dao.getRoutineGroup(id))
        }
    }

    fun insertOrUpdateRoutineGroup(group: RoutineGroup, callback: (id: Long) -> Unit = {}) {
        ensureBackgroundThread {
            val id = dao.insertOrUpdateRoutineGroup(group)
            callback.invoke(id)
        }
    }

    fun updateRoutineGroupEnabledState(id: Int, isEnabled: Boolean, callback: () -> Unit = {}) {
        ensureBackgroundThread {
            dao.updateRoutineGroupEnabledState(id, isEnabled)
            callback.invoke()
        }
    }

    fun getRoutineCountForGroup(groupId: Int, callback: (count: Int) -> Unit) {
        ensureBackgroundThread {
            callback.invoke(dao.getRoutineCountForGroup(groupId))
        }
    }

    /**
     * Deletes a routine group. If [deleteRoutinesInGroup] is true, all routines belonging to it
     * are deleted too (and their pending alarms cancelled by the caller beforehand). Otherwise
     * they are unassigned (groupId = null), mirroring [DBHelper.deleteGroup]'s behavior for
     * alarm groups.
     */
    fun deleteRoutineGroup(groupId: Int, deleteRoutinesInGroup: Boolean, callback: () -> Unit = {}) {
        ensureBackgroundThread {
            if (deleteRoutinesInGroup) {
                dao.deleteRoutinesInGroup(groupId)
            } else {
                dao.unassignRoutinesFromGroup(groupId)
            }
            dao.deleteRoutineGroup(groupId)
            callback.invoke()
        }
    }
}
