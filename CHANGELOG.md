# Changelog

Este proyecto es un fork personal de [Fossify Clock](https://github.com/FossifyOrg/Clock) con funcionalidades añadidas a medida.

> **Nota sobre versionado (17-sep-2026):** a partir de aquí el segundo dígito de versión indica el hito de funcionalidad: `1.0.x` = fork original corregido, `1.1.x` = grupos de alarmas, `1.2.x` = rutinas (en validación). Las entradas anteriores a esta nota (1.0.0–1.0.3) se dejan tal cual en este fichero como registro histórico; sus tags de git equivalentes son ahora `v1.1.0`/`v1.1.1`/`v1.1.2`/`v1.1.3` — ver tags en el repositorio.

> **Política de versionado (1-oct-2026):** el número de versión sube SOLO por cambios de funcionalidad visibles para el usuario. Correctivos, refactors y renombrados internos son *builds* dentro de la versión vigente: se identifican por el sufijo de build (`1.1.4 b20261001_0905`, fecha-hora de compilación) y por `versionCode` (contador que solo crece), sin tocar `VERSION_NAME` ni crear tag.

## [1.1.5] - 2026-10-01

Funcionalidad:
- Alarmas de un solo uso: check "Un solo uso" al crear/editar una alarma sin días repetidos; se elimina sola tras sonar (también si se pierde o se omite) o si se apaga antes de sonar (con aviso). Etiqueta "un solo uso" en la lista. Reutiliza `Alarm.oneShot` y la columna `one_shot` ya existentes: sin migración de BD.

## [1.1.4] - 2026-09-26

Funcionalidad:
- Las notificaciones de alarma perdida/sustituida/caducada muestran grupo y título de la alarma.
- Ajuste para elegir el punto de inicio de la lista de alarmas; por defecto se muestra desde la hora actual.

Builds posteriores dentro de esta versión (sin cambio de versión):
- `b20261001_0858` — corregido: desactivar un grupo de alarmas no silenciaba sus alarmas. Ahora el estado efectivo es *alarma encendida Y grupo encendido*; apagar/encender el grupo cancela/arma sus alarmas; el aviso de próxima alarma lo respeta; alarmas de grupos apagados atenuadas y grupo tachado en los filtros.
- `b20261001_0905` — interno: la tabla de alarmas de la BD pasa de `contacts` (nombre heredado erróneo) a `alarms` (BD v5, migración idempotente, sin pérdida de datos).

## [1.1.3] - 2026-09-15

Mismo contenido que la antigua v1.0.3 (ver más abajo), renombrada para reflejar el nuevo esquema de versionado.

## [1.0.3] - 2026-09-15

- Nuevo: al crear una alarma o un temporizador nuevo, el campo de nombre queda vacío por defecto (antes heredaba el texto del último usado, dando la sensación de estar editando uno existente).
- Nuevo: crear O editar un temporizador (al confirmar) lo pone en marcha automáticamente y lo sube al principio de la lista.
- Nuevo: los temporizadores se ordenan por último uso (el más reciente arriba) en vez de por fecha de creación — el que más usas queda más a mano.
- Corregido: bug real en el ordenamiento — el código comparaba contra una constante equivocada (`SORT_BY_DATE_CREATED` de la librería compartida en vez de `SORT_BY_CREATION_ORDER`, la que realmente se guarda), por lo que la lista nunca se reordenaba visualmente aunque el dato sí se actualizara en la base de datos.
- Nuevo: cada build incluye fecha y hora en la versión (ej. `1.0.3 b20260915_1357`) para identificar exactamente qué compilación está instalada en cada dispositivo.
- Nuevo: al tocar la línea de la versión en "Acerca de" se abre un popup con el icono de la app, presentación, versión actual y el historial completo de versiones con scroll (más reciente arriba) — antes solo había un bloque de texto plano sin interacción.

## [1.0.2] - 2026-09-11

- Nuevo: pantalla "Acerca de" indica que es un fork personal derivado de Fossify Clock, con enlace al repositorio e historial de versiones visible ahí mismo (para comparar qué versión tiene cada dispositivo).

## [1.0.1] - 2026-09-11

Correctivos y ajustes menores sobre la v1.0.0, pulidos durante el uso diario real:

- Corregido: el teclado tapaba los botones "Confirmar"/"Cancelar" al editar una alarma (la ventana ahora se redimensiona en vez de taparlos).
- Corregido: el orden por defecto de la lista de alarmas era "por orden de creación"; ahora es "por hora" (ascendente), como se esperaba.
- Corregido: 41 alarmas creadas con un sonido inválido quedaban mudas; ahora usan el sonido de sistema por defecto (o el elegido explícitamente).
- Corregido: las cuentas atrás (temporizadores) no reproducían sonido por un canal de notificación con caché desactualizada; se fuerza su recreación al cambiar el sonido.
- Nuevo: al crear una alarma con el botón "+" estando dentro de un grupo filtrado, la alarma nueva se asigna automáticamente a ese grupo.
- Nuevo: en el listado "Todas", cada alarma muestra el nombre de su grupo como prefijo entre paréntesis, ej. `(ADL) Fortnite con Duo`.
- Cambiado: el botón "De acuerdo" se renombra a "Confirmar" en toda la app (español).

## [1.0.0] - 2026-09-11

Primera versión funcional del fork, partiendo de Fossify Clock (upstream) más la funcionalidad de **grupos de alarmas**:

- **Grupos de alarmas**: cada alarma puede pertenecer a un grupo con nombre propio (ej. "Trabajo", "Casa", "Deporte"). Un grupo se puede activar/desactivar entero de golpe además de cada alarma individualmente.
- **Gestión de grupos**: pantalla dedicada para crear, renombrar y borrar grupos. Al borrar un grupo, se pregunta si se quiere borrar también sus alarmas o dejarlas sin grupo.
- **Filtro por grupo**: fila de chips en la pantalla de alarmas para filtrar la lista por grupo (o ver "Todas"), ordenados alfabéticamente.
- **Selector de grupo al editar una alarma**: nuevo campo en el diálogo de edición para asignar/cambiar el grupo de una alarma.
- Base: todas las funcionalidades originales de Fossify Clock (reloj, alarmas, cronómetro, temporizador, sin anuncios, open source).
