# Preparaci?n de correcciones HU-7 ? 03/11/2026

Estado: preparaci?n local; sin push, sin actualizar PR y sin merge a main. HU-7 contin?a abierta.

## Referencias y sincronizaci?n

- main comprobado: `15d8dc9` (planificaci?n S3, PR #26).
- Cabeceras remotas conservadas: #14 `8c887ae`, #16 `61427bb`.
- #14 preparado en `prepare/hu7-t71-pr14`, merge local `36de951` con main.
- #16 incorpora esa preparaci?n por merge normal en `prepare/hu7-t72-pr16`.
- En ambos merges solo hubo conflictos en AVANCES.md y SPRINTS.md. Se preservaron completos los documentos de main y los sufijos hist?ricos de T7.1/T7.2, sin duplicarlos.
- docs/sprint2-review.md coincide con main. Se conserva S2 en 15/23 h y S3 en 24 h de trabajo m?s 6 h de reserva; no se descuentan horas nuevas.
- El backend de #16 es id?ntico al de #14 preparado. Se mantienen todos los commits anteriores; sin rebase, squash ni cherry-pick.

## Evidencias y privacidad

- El JSON crudo de Playwright y dos informes de integridad expon?an rutas personales. Sus copias se conservan solo en `.hu7-local/evidencias-historicas/`, ignorado; los informes de integridad se retiran de la punta preparada.
- `resultados.json` publica un resumen de 14 casos: t?tulos, archivos relativos, estados, duraci?n y reintentos. El reporter nuevo no exporta argv, configuraci?n del equipo, adjuntos, cookies, logs ni rutas absolutas.
- Las ejecuciones siguientes usan autom?ticamente el reporter p?blico. Las trazas y test-results quedan ignorados.
- Se escanearon las evidencias textuales: sin rutas personales restantes ni campos de credenciales identificados. Las nueve capturas actuales (aprox. 1,65 MB) se conservan como evidencia visual de casos distintos con prestadores ficticios; sin EXIF/GPS/comentarios incrustados.
- La limpieza afecta la punta preparada, no elimina los artefactos de commits remotos antiguos. No se reescribe el historial publicado.

## Validaci?n de la combinaci?n preparada

| Comprobaci?n | Resultado |
|---|---|
| Django #14 + main | 168 ejecutadas: 165 aprobadas y 3 expectedFailure conocidas; 35,655 s |
| Django #16 + #14 preparado | 168 ejecutadas: 165 aprobadas y 3 expectedFailure conocidas; 53,969 s |
| Migraciones, ambas preparaciones | makemigrations --check --dry-run sin cambios |
| Playwright HU-7 | 16/16 aprobadas, 29,7 s; API y teselas simuladas |
| Integraci?n propia de #16 | 14/14 aprobadas, 37,3 s; Django y teselas OSM reales, SQLite temporal con siete prestadores ficticios; 0 reintentos |
| Frontend | lint correcto; builds de ambas suites completados |
| Git | diff --check correcto; sin archivos en conflicto |

La BD de trabajo y Firebase no se usan: Django unitario usa SQLite en memoria/media temporal; integraci?n usa el generador aislado del propio worktree. HU7_TEST_BACKEND_ROOT ausente. Los tres expectedFailure pertenecen a certificados en main; la correcci?n de #29 no se incorpora a esta preparaci?n.

## Decisiones pendientes y siguiente paso

- PO: confirmar precisi?n de coordenadas p?blicas. El serializer y sus pruebas siguen id?nticos a #14 remoto, sin redondear ni cambiar filtros.
- PO: confirmar ?Sin calificaciones?. El c?digo del mapa y ese texto siguen id?nticos a #16 remoto.
- David: publicar sus observaciones/revisi?n en #14/#16. Las notas de su revisi?n est?n en #29; no equivalen a aprobaci?n formal de esos PR.
- Siguen pendientes validaciones f?sicas/humanas documentadas: tel?fono, GPS/permisos, otros navegadores, accesibilidad y red m?vil real.
- Antes de una publicaci?n futura autorizada, repetir fetch y verificar las cabeceras. Revisar/integrar #14 antes de #16 y comprobar de nuevo su diff. No marcar HU-7 terminada por esta preparaci?n.
