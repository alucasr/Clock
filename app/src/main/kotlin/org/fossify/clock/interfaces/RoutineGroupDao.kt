package org.fossify.clock.interfaces

import androidx.room.*
import org.fossify.clock.models.RoutineGroup

@Dao
interface RoutineGroupDao {

    @Query("SELECT * FROM routine_groups ORDER BY title COLLATE NOCASE ASC")
    fun getRoutineGroups(): List<RoutineGroup>

    @Query("SELECT * FROM routine_groups WHERE id=:id")
    fun getRoutineGroup(id: Int): RoutineGroup?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdateRoutineGroup(group: RoutineGroup): Long

    @Query("DELETE FROM routine_groups WHERE id=:id")
    fun deleteRoutineGroup(id: Int)

    @Query("UPDATE routine_groups SET isEnabled=:isEnabled WHERE id=:id")
    fun updateRoutineGroupEnabledState(id: Int, isEnabled: Boolean)

    @Query("SELECT COUNT(*) FROM routines WHERE groupId=:groupId")
    fun getRoutineCountForGroup(groupId: Int): Int

    @Query("UPDATE routines SET groupId=NULL WHERE groupId=:groupId")
    fun unassignRoutinesFromGroup(groupId: Int)

    @Query("DELETE FROM routines WHERE groupId=:groupId")
    fun deleteRoutinesInGroup(groupId: Int)
}
