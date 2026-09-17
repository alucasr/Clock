package org.fossify.clock.models

sealed interface RoutineEvent {
    data object Refresh : RoutineEvent
}
