package org.fossify.clock.helpers

import android.content.Context
import org.fossify.clock.extensions.routineDb
import org.fossify.clock.models.Routine
import org.fossify.commons.helpers.ensureBackgroundThread

class RoutineHelper(val context: Context) {
    private val routineDao = context.routineDb

    fun getRoutines(callback: (routines: List<Routine>) -> Unit) {
        ensureBackgroundThread {
            callback.invoke(routineDao.getRoutines())
        }
    }

    fun getRoutine(routineId: Int, callback: (routine: Routine?) -> Unit) {
        ensureBackgroundThread {
            callback.invoke(routineDao.getRoutine(routineId))
        }
    }

    fun getEnabledRoutines(callback: (routines: List<Routine>) -> Unit) {
        ensureBackgroundThread {
            callback.invoke(routineDao.getEnabledRoutines())
        }
    }

    fun insertOrUpdateRoutine(routine: Routine, callback: (id: Long) -> Unit = {}) {
        ensureBackgroundThread {
            val id = routineDao.insertOrUpdateRoutine(routine)
            callback.invoke(id)
        }
    }

    fun deleteRoutine(id: Int, callback: () -> Unit = {}) {
        ensureBackgroundThread {
            routineDao.deleteRoutine(id)
            callback.invoke()
        }
    }

    fun deleteRoutines(routines: List<Routine>, callback: () -> Unit = {}) {
        ensureBackgroundThread {
            routineDao.deleteRoutines(routines)
            callback.invoke()
        }
    }
}
