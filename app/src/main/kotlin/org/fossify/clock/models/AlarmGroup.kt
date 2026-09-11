package org.fossify.clock.models

import androidx.annotation.Keep

@Keep
@kotlinx.serialization.Serializable
data class AlarmGroup(
    var id: Int,
    var title: String,
    var isEnabled: Boolean,
)
