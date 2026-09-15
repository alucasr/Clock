package org.fossify.clock.models

data class VersionHistoryEntry(
    val version: String,
    val date: String,
    val changes: List<String>,
)

object AppVersionHistory {
    // most recent first
    val entries = listOf(
        VersionHistoryEntry(
            version = "1.0.3",
            date = "2026-09-15",
            changes = listOf(
                "Al crear una alarma o temporizador nuevo, el nombre queda vacío por defecto",
                "Crear o editar un temporizador lo pone en marcha y lo sube al principio de la lista (orden por último uso)",
                "Botón de deslizar más claro en la pantalla de alarma sonando: fondo circular con borde y flechas dobles a cada lado",
                "Cada build incluye fecha y hora para identificar exactamente qué versión está instalada",
            )
        ),
        VersionHistoryEntry(
            version = "1.0.2",
            date = "2026-09-11",
            changes = listOf(
                "La pantalla \"Acerca de\" indica que es un fork personal, con enlace al repositorio",
            )
        ),
        VersionHistoryEntry(
            version = "1.0.1",
            date = "2026-09-11",
            changes = listOf(
                "Botón \"Confirmar\" en vez de \"De acuerdo\"",
                "El teclado ya no tapa los botones al editar una alarma",
                "Las alarmas se ordenan por hora por defecto",
                "Sonido corregido en alarmas y temporizadores",
                "Prefijo de grupo en la vista \"Todas\"",
                "Nueva alarma creada desde un grupo hereda ese grupo",
            )
        ),
        VersionHistoryEntry(
            version = "1.0.0",
            date = "2026-09-08",
            changes = listOf(
                "Primera versión con grupos de alarmas",
                "Crear, renombrar y borrar grupos",
                "Activar o desactivar un grupo entero",
                "Filtro por grupo en la lista de alarmas",
                "Selector de grupo al editar una alarma",
            )
        ),
    )
}
