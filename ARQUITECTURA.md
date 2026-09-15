# Arquitectura y guía de desarrollo — Fossify Clock fork (personal)

Documento de referencia técnica para futuras sesiones de desarrollo sobre este fork. Complementa `CHANGELOG.md` (qué cambió) con el **cómo está construido** y **cómo trabajar en él**.

## Repos involucrados

| Repo | Ruta local | Remote | Propósito |
|---|---|---|---|
| **Clock** (la app) | `~/repos/Clock` | `myfork` → `github.com/alucasr/Clock` (push), `origin` → `github.com/FossifyOrg/Clock` (upstream, solo fetch — **nunca push aquí**) | App de reloj/alarmas/timer, fork personal de Fossify Clock |
| **commons** (librería compartida) | `~/repos/commons-fork/commons` | tu fork de `FossifyOrg/commons` | Librería base que usan TODAS las apps Fossify (UI compartida, About/FAQ/Settings screens, helpers, temas) |

`Clock` depende de `commons` vía JitPack: `gradle/libs.versions.toml` tiene:
```toml
commons = "5be48d0421f23e54500039dccf55bec6c992fd46"  # commit hash exacto usado
fossify-commons = { module = "com.github.alucasr:commons", version.ref = "commons" }
```
Esto apunta a **tu fork** de commons (`com.github.alucasr:commons`), no al original de Fossify — importante: si necesitas cambiar algo en pantallas compartidas (About, FAQ, Settings genéricos), se edita en `~/repos/commons-fork/commons`, no en `Clock`.

### Cómo actualizar `commons` cuando se modifica
1. Editar en `~/repos/commons-fork/commons` (el módulo Gradle vive DENTRO de `~/repos/commons-fork`, que es el root real: contiene `gradlew`, `settings.gradle.kts` con `include(":commons", ":samples")`)
2. Publicar a Maven local para probar rápido sin esperar JitPack — el comando se lanza desde el ROOT (`~/repos/commons-fork`), no desde `~/repos/commons-fork/commons`:
   ```bash
   cd ~/repos/commons-fork
   ./gradlew :commons:publishToMavenLocal -PVERSION=<mismo-hash-que-libs.versions.toml>
   ```
   El `-PVERSION` debe coincidir EXACTAMENTE con el valor de `commons` en `Clock/gradle/libs.versions.toml` (ej. `5be48d0421f23e54500039dccf55bec6c992fd46`), porque Gradle resuelve la dependencia por group:artifact:version — si no coincide, `Clock` seguirá usando la versión de JitPack cacheada, no tu build local.
3. En `Clock/settings.gradle.kts`, `mavenLocal()` debe ir ANTES que JitPack en la lista de `repositories` (dependencyResolutionManagement) — si JitPack ya tiene esa versión cacheada (fue publicada antes), Gradle la resuelve ahí y NUNCA llega a mirar mavenLocal, aunque esté en la lista. Orden correcto: `mavenLocal()` primero, luego `mavenCentral()`, `google()`, JitPack al final.
4. Para probar el cambio en `Clock`: `./gradlew --stop` (mata el daemon, evita cache obsoleta) antes de recompilar, si el cambio no se refleja.
5. Para publicar de verdad en JitPack (build reproducible desde GitHub), hacer commit+push en `commons-fork` y actualizar el hash en `libs.versions.toml` de `Clock`.

## Stack técnico

- **Kotlin** + **Jetpack Compose** (pantallas nuevas de `commons`, ej. AboutActivity/AboutScreen) + **Android Views clásicas / ViewBinding** (la mayoría de la UI propia de Clock, ej. TimerFragment, EditTimerDialog)
- **Room** (SQLite) para persistencia — base de datos `app.db` (timers) y `alarms.db` (alarmas, gestionada por `commons`)
- **EventBus** (greenrobot) para comunicación entre componentes (`TimerEvent.Start/Pause/Reset/Delete/Refresh/Finish`, gestionados centralmente en `App.kt`)
- **Gradle Kotlin DSL** (`build.gradle.kts`), flavors: `core`, `foss`, `gplay` (variantDimension `variants`)
- Compilación de debug: `./gradlew assembleFossDebug` → APK en `app/build/outputs/apk/foss/debug/clock-<versionCode>-foss-debug.apk`

## Estructura relevante de `Clock`

```
app/src/main/kotlin/org/fossify/clock/
├── App.kt                          # Application class, listeners EventBus centrales (arranque/pausa/fin de timers)
├── activities/
│   ├── MainActivity.kt             # pantalla principal, tabs (Alarm/Timer/Stopwatch/World Clock), launchAbout()
│   ├── AlarmActivity.kt            # pantalla de alarma/timer SONANDO (swipe to dismiss/snooze)
│   └── SimpleActivity.kt           # base activity con iconos de la app, nombre del repo
├── adapters/
│   └── TimerAdapter.kt             # RecyclerView adapter de la lista de timers
├── dialogs/
│   ├── EditTimerDialog.kt          # crear/editar un timer
│   └── EditAlarmDialog.kt          # crear/editar una alarma
├── fragments/
│   └── TimerFragment.kt            # lista de timers, lógica de ORDENACIÓN (getSortedTimers)
├── helpers/
│   ├── Constants.kt                # constantes propias del proyecto (¡cuidado, ver nota below!)
│   ├── Config.kt                   # SharedPreferences wrapper (timerSort, timerLastConfig, etc.)
│   └── TimerHelper.kt              # capa de acceso a TimerDao (background thread)
├── models/
│   ├── Timer.kt                    # entity Room (campos: id, seconds, state, label, createdAt, lastUsedAt, oneShot...)
│   └── TimerEvent.kt               # sealed class de eventos EventBus
├── interfaces/
│   └── TimerDao.kt                 # Room DAO (@Query SQL de timers)
└── databases/
    └── AppDatabase.kt              # Room database, migraciones (@Database version=N)
```

## ⚠️ Trampa descubierta: constantes de ordenación duplicadas con el mismo nombre conceptual

`org.fossify.commons.helpers` tiene `SORT_BY_DATE_CREATED = 262144` (genérica, para otras apps Fossify).
`org.fossify.clock.helpers.Constants.kt` tiene **sus propias constantes locales** para timers:
```kotlin
const val SORT_BY_CREATION_ORDER = 0   // valor real guardado por defecto en Config.timerSort
const val SORT_BY_ALARM_TIME = 1
const val SORT_BY_DATE_AND_TIME = 2
const val SORT_BY_TIMER_DURATION = 3
```
**Bug real que costó una sesión entera depurar (15-sep-2026)**: en `TimerFragment.getSortedTimers()`, el `when` comparaba `config.timerSort` contra `SORT_BY_DATE_CREATED` (import de `commons`, valor 262144) en vez de `SORT_BY_CREATION_ORDER` (import local, valor 0, que es el que realmente se guarda). Como nunca coincidían, el código caía siempre en `else -> timers` (sin ordenar), aunque toda la lógica de actualizar `lastUsedAt` en la base de datos funcionaba perfectamente — el bug era puramente de comparación de constante equivocada, no de datos.

**Lección**: al tocar lógica de ordenación de timers, verificar SIEMPRE qué constante usa realmente `Config.kt` como valor por defecto/guardado, no asumir por el nombre que suena parecido a una constante de `commons`.

## Verificar cambios de datos/BD sin adb backup

`adb backup` es un mecanismo **obsoleto y poco fiable** en Android reciente (Pixel con Android 15+): no siempre muestra el diálogo de confirmación, y puede fallar silenciosamente. Alternativa fiable usada en este proyecto:
```bash
adb -s <serial> shell run-as org.fossify.clock.debug cat /data/data/org.fossify.clock.debug/databases/app.db > backup_local.bak
# repetir para: alarms.db, app.db-shm, app.db-wal
```
Esto extrae directamente los ficheros SQLite reales — más rápido y verificable con `sqlite3 backup_local.bak "SELECT COUNT(*) FROM timers;"`.

## App deshabilitada tras instalar (no aparece en el launcher)

Si tras un `adb install -r` el icono no aparece pese a instalación exitosa, comprobar:
```bash
adb -s <serial> shell dumpsys package org.fossify.clock.debug | grep enabled=
```
Si `enabled=0`, habilitar con:
```bash
adb -s <serial> shell pm enable org.fossify.clock.debug
```
(pasó una vez sin causa clara identificada — puede ser un residuo de una desinstalación/reinstalación previa).

## Versionado

- `gradle.properties`: `VERSION_NAME` (ej. `1.0.3`) y `VERSION_CODE` (entero incremental, ej. `4`)
- Convención: último dígito = fix, segundo dígito = feature nueva
- Desde v1.0.3: `app/build.gradle.kts` añade un **timestamp de build** al `versionName` en tiempo de compilación (`versionName = "${VERSION_NAME} b${yyyyMMdd_HHmm}"`), para poder identificar exactamente qué build concreto está instalado en el móvil sin depender solo del número de versión. Se ve en Ajustes → Acerca de.

## Cambiar pantallas de `commons` sin duplicar toda la librería

Cuando se necesita interceptar/personalizar un comportamiento de una pantalla compartida (ej. AboutActivity, que usa Jetpack Compose y no expone callbacks para todo), el patrón usado es: añadir un **hook opcional estático** (`companion object { var onXxx: (() -> Unit)? = null }`) en la clase de `commons`, que la app anfitriona (`Clock`) puede registrar justo antes de lanzar la Activity/Fragment. Se limpia solo tras usarse una vez, para no dejar referencias colgadas entre instancias.

## Instalación en el Pixel 7 de pruebas

Ver procedimiento operativo completo (backup → instalar → informe) en `~/Documents/Hermes/procedimientos/instalacion_apps_moviles.md`. Este documento (`ARQUITECTURA.md`) es sobre el código; ese otro es sobre el proceso de despliegue al dispositivo físico.
