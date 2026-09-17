package org.fossify.clock.models

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey

// notificationStyle values
const val ROUTINE_STYLE_CONTINUOUS = 0
const val ROUTINE_STYLE_DISCREET = 1

@Entity(tableName = "routines")
@Keep
@kotlinx.serialization.Serializable
data class Routine(
    @PrimaryKey(autoGenerate = true) var id: Int?,
    var label: String,
    var groupId: Int? = null,
    var intervalSeconds: Int,
    var isEnabled: Boolean,
    var startTimeMinutes: Int,
    var endTimeMinutes: Int,
    var days: Int,
    var vibrate: Boolean,
    var soundUri: String,
    var soundTitle: String,
    var notificationStyle: Int = ROUTINE_STYLE_CONTINUOUS,
) {
    fun isRecurring() = days > 0
}
