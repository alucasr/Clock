# Sequence diagrams

Mermaid diagrams (render natively on GitHub, or with any Mermaid viewer).

## 1. Disabling / enabling an alarm group (fixed in v1.1.5)

```mermaid
sequenceDiagram
    actor U as User
    participant D as ManageGroupsDialog
    participant DB as DBHelper
    participant C as AlarmController
    participant AM as Android AlarmManager
    participant UI as AlarmFragment (EventBus)

    U->>D: toggles group switch
    D->>DB: updateGroupEnabledState(groupId, newState)
    D->>C: onGroupEnabledChanged(groupId)
    activate C
    C->>DB: isGroupEnabled(groupId)
    C->>DB: getAlarms() where groupId matches AND isEnabled
    alt group disabled
        loop each alarm of the group
            C->>AM: cancelAlarmClock(alarm)
        end
    else group enabled
        loop each alarm of the group
            C->>AM: setupAlarmClock(alarm, nextTrigger)
        end
    end
    C-->>UI: AlarmEvent.Refresh (+ widgets)
    deactivate C
    UI->>DB: getDisabledGroupIds()
    UI-->>U: alarms dimmed / group chip struck through
```

Before v1.1.5 the dialog only called `updateGroupEnabledState` and posted a refresh: the flag was stored but never read by the scheduling code, so alarms kept ringing.

## 2. Scheduling guard (every path that arms an alarm)

```mermaid
sequenceDiagram
    participant S as Caller (toggle, edit dialog, boot, intent)
    participant C as AlarmController
    participant DB as DBHelper
    participant AM as AlarmManager

    S->>C: scheduleNextOccurrence(alarm)
    C->>DB: isGroupEnabled(alarm.groupId)
    alt group disabled
        C->>AM: cancelAlarmClock(alarm)
        Note over C: returns without arming
    else group enabled / ungrouped
        C->>AM: setupAlarmClock(alarm, getTimeOfNextAlarm)
    end
```

## 3. An alarm fires

```mermaid
sequenceDiagram
    participant AM as AlarmManager
    participant R as AlarmReceiver
    participant C as AlarmController
    participant DB as DBHelper
    participant SVC as AlarmService

    AM->>R: trigger(alarmId)
    R->>C: onAlarmTriggered(alarmId)
    C->>DB: getAlarmWithId(alarmId)
    alt group disabled (e.g. disabled after arming / snooze)
        C-->>R: stay silent, alarm state untouched
    else active
        opt recurring
            C->>C: scheduleNextOccurrence(alarm)
        end
        alt isNextExecutionCancelled
            C->>DB: clear flag (skip this one)
        else
            C->>SVC: ACTION_START_ALARM
        end
    end
```

## 4. Upcoming-alarm notification

```mermaid
sequenceDiagram
    participant AM as AlarmManager
    participant U as UpcomingAlarmReceiver
    participant DB as DBHelper
    participant N as NotificationManager

    AM->>U: fires shortly before the alarm
    U->>DB: getAlarmWithId(alarmId)
    U->>DB: isAlarmActive(alarm)
    alt not active (alarm off OR group off)
        U-->>AM: return, no notification
    else active
        U->>N: notify "time - (Group) label"
    end
```
