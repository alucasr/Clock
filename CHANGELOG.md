# Changelog

Este proyecto es un fork personal de [Fossify Clock](https://github.com/FossifyOrg/Clock) con funcionalidades añadidas a medida.

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
