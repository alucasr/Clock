# Índice del proyecto — Fossify Clock (fork alucasr)

Mapa fichero → propósito, generado y mantenido por el agente. **Actualizar tras cualquier cambio estructural** (fichero nuevo, movido, o cuyo rol cambie). Objetivo: evitar tener que releer/explorar todo el árbol en cada tarea nueva.

Repo: `~/repos/Clock`. Ver `ARQUITECTURA.md` para decisiones de diseño, convenciones y pitfalls ya descubiertos (ese doc NO se duplica aquí).

## Versionado (tags git)

Esquema: `1.<hito>.<fix>` — segundo dígito = hito de funcionalidad, tercero = correctivo/build dentro del hito.
- **v1.0.x** = fork original de FossifyOrg/Clock con sus errores corregidos, sin features propias aún.
- **v1.1.x** = grupos de alarmas (v1.1.0 la introduce; v1.1.1/1.1.2/1.1.3 son los correctivos/UI que antes se numeraban 1.0.1/1.0.2/1.0.3 — renumerados el 17-sep-2026, ver `CHANGELOG.md` para el detalle histórico completo).
- **v1.2.0** = Rutinas (pendiente de tag hasta que el usuario valide la funcionalidad en uso real).

Tags añadidas sobre commits ya existentes (nunca se reescribe historial): `v1.0.0`→352869b, `v1.1.0`→352869b (mismo commit que trajo grupos+fixes juntos), `v1.1.1`→352869b, `v1.1.2`→1bd20fa, `v1.1.3`→1bd20fa. `gradle.properties` (`VERSION_NAME`) y `AppVersionHistory.kt`/`strings.xml` (pantalla "Acerca de") reflejan siempre la numeración vigente (1.1.x), no la antigua.

## activities/
- `MainActivity.kt` — actividad host del ViewPager de 5 pestañas (Reloj/Alarmas/Cronómetro/Timer/Rutinas), drawables/labels de tabs, `getInactiveTabIndexes`.
- `AlarmActivity.kt` — pantalla de alarma sonando (fullscreen).
- `SnoozeReminderActivity.kt` — recordatorio de snooze.
- `SettingsActivity.kt` — ajustes de la app.
- `SimpleActivity.kt` — base compartida (helpers de tema/color, usada por todos los diálogos vía `activity: SimpleActivity`).
- `IntentHandlerActivity.kt`, `SplashActivity.kt` — arranque/deep links.
- `WidgetAnalogueConfigureActivity.kt`, `WidgetDigitalConfigureActivity.kt` — configuración de widgets de reloj.

## adapters/
- `AlarmsAdapter.kt` — lista de alarmas (RecyclerView), patrón de referencia para adapters de items.
- `RoutinesAdapter.kt` — lista de rutinas; recibe `groupTitles`/`showGroupPrefix` desde `RoutineFragment` (patrón idéntico a `AlarmsAdapter`, prefijo "(Grupo) " en label cuando el filtro es "Todas").
- `TimerAdapter.kt`, `StopwatchAdapter.kt`, `TimeZonesAdapter.kt`, `SelectTimeZonesAdapter.kt`, `ViewPagerAdapter.kt` (registro de fragments de las 5 pestañas).

## databases/
- `AppDatabase.kt` — Room DB (`app.db`), entidades `Timer`, `Routine`, `RoutineGroup`. Versión actual: **7**. Migraciones manuales `MIGRATION_N_N+1` — añadir una nueva al tocar el esquema, nunca modificar una ya publicada. **Pitfall confirmado**: en el SQL crudo de una migración, `INTEGER PRIMARY KEY AUTOINCREMENT` necesita `NOT NULL` explícito o Room rechaza el esquema al arrancar (ver `registro_aprendizajes_confirmados.md`, 2026-09-16). `MIGRATION_5_6` renombra `Routine.intervalMinutes`→`intervalSeconds` (×60); `MIGRATION_6_7` añade la 3ª rutina por defecto a BDs ya migradas; `onCreate` siembra 1 grupo + 3 rutinas por defecto vía `insertDefaultRoutines()` (instalaciones nuevas).

## dialogs/
- `EditAlarmDialog.kt` — edición de alarma; referencia de patrón para diálogos de edición (TimePicker, selector de días, grupo, sonido).
- `EditRoutineDialog.kt` — edición de rutina (nombre, grupo propio, intervalo, horario, días, sonido, vibración, switch habilitado, toggle continua/discreta).
- `EditRoutineIntervalDialog.kt` — selector de minutos del intervalo.
- `ManageGroupsDialog.kt` + `GroupTitleDialog.kt` — CRUD de **grupos de alarmas** (tabla legacy SQLite vía `DBHelper`, `AlarmGroup`).
- `ManageRoutineGroupsDialog.kt` — CRUD de **grupos de rutinas**, INDEPENDIENTE de los de alarmas (tabla Room `routine_groups`, vía `RoutineGroupHelper`/`RoutineGroup`). Reutiliza `GroupTitleDialog` (genérico) y los layouts `dialog_manage_groups.xml`/`item_alarm_group.xml` (visualmente genéricos pese al nombre).
- `EditTimerDialog.kt`, `EditTimeZoneDialog.kt`, `AddTimeZonesDialog.kt`, `SelectAlarmDialog.kt`, `ChangeAlarmSortDialog.kt`, `ChangeTimerSortDialog.kt`, `ExportDataDialog.kt`, `VersionHistoryDialog.kt`, `MyTimePickerDialogDialog.kt`.

## extensions/
- `Context.kt` — punto central de extension properties: `dbHelper` (SQLite legacy, alarmas+grupos alarma), `routineDb`/`routineHelper`/`routineController` (Room, rutinas), `routineGroupDb`/`routineGroupHelper` (Room, grupos de rutinas), `timerDb`/`timerHelper`, `config`. **Revisar aquí primero** al añadir cualquier acceso a datos nuevo.
- `Activity.kt`, `Fragment.kt`, `BroadcastReceiver.kt` — helpers de esas clases base.
- `Int.kt`, `Long.kt`, `Lap.kt`, `TextView.kt`, `Logs.kt`, `gson/TypeAdapter.kt`.

## fragments/ (una por pestaña)
- `ClockFragment.kt`, `AlarmFragment.kt` (referencia de patrón: filtro por grupo con chips, FAB, RecyclerView), `StopwatchFragment.kt`, `TimerFragment.kt`, `RoutineFragment.kt` (FAB, EventBus `RoutineEvent.Refresh`, delega grupos a `EditRoutineDialog`→`ManageRoutineGroupsDialog`).

## helpers/
- `Constants.kt` — constantes globales: `TAB_*_INDEX`, bits de días, IDs de intents (`OPEN_ROUTINE_TAB_INTENT_ID` etc).
- `DBHelper.kt` — acceso legacy SQLite (alarmas + **grupos de alarmas**, tabla `alarm_groups`). NO usar para rutinas.
- `RoutineController.kt` — lógica central de disparo/reprogramación (AlarmManager.setExactAndAllowWhileIdle, ventana horaria, bitmask de días). Singleton `getInstance(context)`.
- `RoutineHelper.kt` — CRUD async (Room) sobre `Routine`.
- `RoutineGroupHelper.kt` — CRUD async (Room) sobre `RoutineGroup`. Todas las funciones son async con callback (Room prohíbe queries síncronas en el hilo principal) — seguir ese patrón al extenderlo.
- `AlarmController.kt`, `AlarmNotificationHelper.kt`, `TimerHelper.kt`, `Config.kt`, `Converters.kt` (TypeConverters Room), `ExportHelper.kt`, `ImportHelper.kt`, `Stopwatch.kt`, `MyAnalogueTimeWidgetProvider.kt`, `MyDigitalTimeWidgetProvider.kt`, `DisabledItemChangeAnimator.kt`.

## interfaces/
- `RoutineDao.kt` — Room DAO tabla `routines`.
- `RoutineGroupDao.kt` — Room DAO tabla `routine_groups` (incluye `getRoutineCountForGroup`, `unassignRoutinesFromGroup`, `deleteRoutinesInGroup` para el flujo de borrado de grupo).
- `TimerDao.kt`, `ToggleAlarmInterface.kt`.

## models/
- `Routine.kt` — entidad Room rutina (label, groupId, intervalSeconds [hh:mm:ss, igual que timer], isEnabled, startTimeMinutes, endTimeMinutes, days bitmask, vibrate, soundUri/soundTitle, notificationStyle 0=continua/1=discreta).
- `RoutineGroup.kt` — entidad Room grupo de rutina (id, title, isEnabled) — independiente de `AlarmGroup`.
- `RoutineEvent.kt` — eventos EventBus de refresco UI rutinas.
- `AlarmGroup.kt` — grupo de alarma (legacy SQLite, NO Room, NO usar para rutinas).
- `RoutineFragment.kt` — pestaña Rutinas; filtro de grupo = chips ("Todas"/grupos) + icono de engranaje SEPARADO (no un chip) para gestión — lección aprendida: un chip de texto "Gestionar..." se confunde visualmente con un grupo real (ver registro_aprendizajes_confirmados.md, 2026-09-17). Mismo patrón aplicado también a `AlarmFragment.kt` por consistencia.
- `Alarm.kt`, `AlarmEvent.kt`, `Timer.kt`, `TimerEvent.kt`, `TimerState.kt`, `Lap.kt`, `MyTimeZone.kt`, `StateWrapper.kt`, `AlarmTimerBackup.kt`, `VersionHistory.kt`.

## receivers/
- `RoutineReceiver.kt` — recibe el disparo AlarmManager de una rutina, delega a `RoutineController.onRoutineTriggered()`.
- `RoutineStopReceiver.kt` — botón "Parar" de notificación en modo discreta.
- `AlarmReceiver.kt`, `RescheduleAlarmsReceiver.kt` (BOOT_COMPLETED, también llama a `rescheduleEnabledRoutines()`), `StopAlarmReceiver.kt`, `SkipUpcomingAlarmReceiver.kt`, `UpcomingAlarmReceiver.kt`, `HideTimerReceiver.kt`, `UpdateWidgetReceiver.kt`.

## services/
- `AlarmService.kt`, `SnoozeService.kt`, `TimerService.kt`, `StopwatchService.kt`.

## views/ (custom Views)
- `RoutineNotificationStyleView.kt` — toggle continua/discreta, Canvas onDraw (onda cuadrada), callback `onStyleChanged`.
- `AutoFitTextView.kt`, `MyTextClock.kt`.

## res/layout/ (los no obvios)
- `dialog_manage_groups.xml` + `item_alarm_group.xml` — genéricos pese al nombre, reutilizados TANTO por `ManageGroupsDialog` (alarmas) como `ManageRoutineGroupsDialog` (rutinas).
- `dialog_group_title.xml` — genérico, usado por `GroupTitleDialog` para crear/renombrar cualquier tipo de grupo.
- `fragment_routine.xml`, `item_routine.xml`, `dialog_edit_routine.xml`, `dialog_edit_routine_interval.xml` — UI específica de rutinas.

---
*Última actualización: 2026-09-16, tras hacer los grupos de Rutinas independientes de los de Alarmas (RoutineGroup/RoutineGroupDao/RoutineGroupHelper/ManageRoutineGroupsDialog, migración Room 4→5).*
