package org.fossify.clock.models

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A group for organizing Routines, fully independent from [AlarmGroup] (alarms have their own
 * group list; routines have theirs). Room-backed so it lives in the same `app.db` as [Routine].
 */
@Entity(tableName = "routine_groups")
@Keep
@kotlinx.serialization.Serializable
data class RoutineGroup(
    @PrimaryKey(autoGenerate = true) var id: Int,
    var title: String,
    var isEnabled: Boolean,
)
