# Class diagram

Mermaid diagrams (render natively on GitHub, or with any Mermaid viewer). Scope: alarms, alarm groups and their scheduling. Routines mirror the same pattern and are omitted for clarity.

```mermaid
classDiagram
    direction LR

    class Alarm {
        +Int id
        +Int timeInMinutes
        +Int days
        +Boolean isEnabled
        +Boolean vibrate
        +String label
        +Boolean oneShot
        +Int? groupId
        +Boolean isNextExecutionCancelled
        +isRecurring() Boolean
    }

    class AlarmGroup {
        +Int id
        +String title
        +Boolean isEnabled
    }

    class DBHelper {
        +getAlarms() List~Alarm~
        +getEnabledAlarms() List~Alarm~
        +getDisabledGroupIds() Set~Int~
        +isGroupEnabled(groupId) Boolean
        +isAlarmActive(alarm) Boolean
        +updateAlarmEnabledState(id, enabled)
        +getGroups() List~AlarmGroup~
        +updateGroupEnabledState(groupId, enabled)
        +deleteGroup(groupId, deleteAlarms)
    }

    class AlarmController {
        +rescheduleEnabledAlarms()
        +scheduleNextOccurrence(alarm)
        +onGroupEnabledChanged(groupId)
        +onAlarmTriggered(alarmId)
        +skipNextOccurrence(alarmId)
        +stopAlarm(alarmId)
        +snoozeAlarm(alarmId, minutes)
        -scheduleNextAlarm(alarm)
    }

    class ManageGroupsDialog
    class AlarmFragment
    class AlarmsAdapter
    class AlarmReceiver
    class UpcomingAlarmReceiver
    class RescheduleAlarmsReceiver
    class AlarmManagerAPI["Android AlarmManager"]

    Alarm "0..*" --> "0..1" AlarmGroup : groupId (nullable FK)
    DBHelper ..> Alarm : persists (table alarms)
    DBHelper ..> AlarmGroup : persists (table alarm_groups)
    AlarmController --> DBHelper : reads state
    AlarmController ..> AlarmManagerAPI : set / cancel
    ManageGroupsDialog --> DBHelper : toggles group flag
    ManageGroupsDialog --> AlarmController : onGroupEnabledChanged
    AlarmFragment --> AlarmController : toggle one alarm
    AlarmFragment --> AlarmsAdapter
    AlarmsAdapter ..> AlarmGroup : dims disabled groups
    AlarmReceiver --> AlarmController : onAlarmTriggered
    RescheduleAlarmsReceiver --> AlarmController : after boot
    UpcomingAlarmReceiver --> DBHelper : isAlarmActive
```

## Data model (SQLite)

| Table | Column | Notes |
|---|---|---|
| `alarms` (hasta v1.1.5: `contacts`, nombre heredado erróneo; renombrada en BD v5) | `id`, `time_in_minutes`, `days`, `is_enabled`, `vibrate`, `sound_*`, `label`, `one_shot`, `next_execution_cancelled` | `is_enabled` is the alarm's **own** switch |
| `alarms` | `group_id` | nullable; `NULL` = ungrouped. No SQL foreign key: integrity is kept in code (`deleteGroup`) |
| `alarm_groups` | `id`, `title`, `is_enabled` | `is_enabled` is the group's switch |

## Effective state rule

An alarm rings only if **`alarm.isEnabled` AND its group is enabled** (ungrouped alarms count as enabled).
Toggling a group never modifies the `is_enabled` of its alarms, so re-enabling the group restores each alarm exactly as the user left it. The single source of truth for this rule is `DBHelper.getEnabledAlarms()` / `isAlarmActive()`, plus the guard inside `AlarmController.scheduleNextAlarm()`.
