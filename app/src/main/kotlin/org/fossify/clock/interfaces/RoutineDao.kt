package org.fossify.clock.interfaces

import androidx.room.*
import org.fossify.clock.models.Routine

@Dao
interface RoutineDao {

    @Query("SELECT * FROM routines ORDER BY startTimeMinutes ASC")
    fun getRoutines(): List<Routine>

    @Query("SELECT * FROM routines WHERE id=:id")
    fun getRoutine(id: Int): Routine?

    @Query("SELECT * FROM routines WHERE isEnabled=1")
    fun getEnabledRoutines(): List<Routine>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdateRoutine(routine: Routine): Long

    @Query("DELETE FROM routines WHERE id=:id")
    fun deleteRoutine(id: Int)

    @Delete
    fun deleteRoutines(list: List<Routine>)
}
