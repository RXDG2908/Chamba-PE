# Preparación local de T7.1 · PR #14

> **Actualización autorizada del 02/11/2026:** main f515837 ya se incorporó por merge normal en esta preparación. Pasaron 168 pruebas Django (165 aprobadas y los 3 fallos esperados de certificados), sin migraciones pendientes ni cambios de frontend frente a main. Las secciones siguientes conservan la preparación y secuencia originales como historial; ya no describen una sincronización pendiente. El PR #14 continúa abierto para revisión; no se integra a main ni se modifica #16 en esta operación.

Fecha: 02/11/2026. Rama local: `prepare/hu7-t71-pr14`.
Base: `origin/feature/t7.1-mapa-prestadores 5ddaa14d6eaa19eb697184b8e72e27e41fe425d4`.
Destino de documentación reconciliada: `origin/main f515837`.

## Separación y estado

Los cambios funcionales de T7.1 ya existen en el PR: FloatField read_only de
latitud/longitud en servicios/serializers.py y dos pruebas/contrato público en
servicios/tests.py. No necesitan una segunda implementación. No hay cambios
frontend, marcadores, estilos, dependencias ni pruebas Playwright en este worktree.

Solo se preparan documentos: SPRINTS.md y AVANCES.md conservan íntegro main
y añaden seguimiento HU-7; docs/API_BUSQUEDA.md conserva el contrato actual de
main y añade las coordenadas numéricas; esta guía separa historial y publicación.
El historial particular del PR se conserva en AVANCES, rotulado como histórico.
El cierre S2 sigue en 15 de 23 horas y no se altera el compromiso S3.

Los conflictos SPRINTS.md y docs/API_BUSQUEDA.md tienen contenido de resolución
preparado; no se ha ejecutado merge y no hay archivos unmerged en el índice.
El código de la cabecera antigua todavía no incorpora main (autenticación,
Verificado, etc.). No confundir preparar documentos con integrar ese código.

## Evidencia y pendientes

La revisión conjunta sobre main en review/hu7-t71-t72 ejecutó 168 pruebas Django
(165 pasan y 3 fallos esperados de certificados), sin migraciones pendientes.
La ejecución aislada sobre esta cabecera antigua debe registrarse aparte:
no sustituye repetir las regresiones después de sincronizar main.

Validación manual pendiente con Django y datos ficticios propios de una BD de
prueba: consultar la API con coordenadas dentro/borde/fuera de cobertura;
comprobar números JSON, coordenadas del prestador y ausencia de DNI/teléfono;
ausentes, texto, NaN/infinito y fuera de rango deben dar 400; comprobar filtros
Verificado/Activo/rubro y orden. No usar la BD ni la sesión local de HU-8.

T7.1 no está declarada terminada; requiere revisión de David e integración.

## Secuencia futura que requiere autorización

1. Revisar el diff de esta preparación y autorizar el commit local de los
   documentos, manteniendo los commits existentes de #14.
2. Con autorización explícita de merge local, actualizar referencias e incorporar
   origin/main a esta rama mediante merge normal. No rebase ni cherry-pick.
   Resolver documentos con la versión más reciente de main más los registros
   particulares de T7.1; las copias actuales son candidatas, no una licencia
   para sobrescribir cambios que main reciba después.
3. Ejecutar la suite/regresiones sobre esa combinación real y revisar que el diff
   final de #14 contra main no incluya archivos del mapa ni Playwright.
4. Con autorización de publicación, push normal (sin force) desde la preparación:
   `git push origin HEAD:feature/t7.1-mapa-prestadores`.
   Esto actualiza el PR #14 existente; no crear otro.
5. David revisa. El merge de #14 a main corresponde al equipo y requiere su
   autorización; no se realiza en esta preparación.
6. Solo después de que #14 esté integrado, sincronizar/preparar #16 contra ese
   main para heredar una sola vez T7.1 y sus correcciones.

Antes del push verificar que la cabecera remota sigue siendo ancestro de HEAD.
Si alguien actualiza el PR, detener la publicación y reconciliar por merge normal,
sin descartar trabajo ni reescribir historial.

**En esta sesión:** no commit, merge, push, PR ni cambios en la carpeta original.

## Validación de esta preparación

02/11/2026: 60 pruebas Django de la cabecera T7.1 aprobadas. No cubren las apps nuevas de main que aún no forman parte de esta cabecera; mantener separada la evidencia de 168 pruebas sobre la combinación de main del worktree integrado.
