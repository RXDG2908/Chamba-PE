# Índice del Sprint 3 · Parte de Renzo León (HU-19)

**Sprint 3:** 29/10 – 11/11/2026 · **Mi trabajo:** HU-19, escaneo y validación del DNI del prestador · **Horas:** T19.1 (3) + T19.2 (4) + T19.3 (2) = **9 h**, todas integradas en `main`.

> **En una frase:** el prestador toma dos fotos de su DNI desde `/verificar-dni`. El servidor lee la zona MRZ del reverso, comprueba los dígitos de control y compara el DNI y los nombres con lo que el prestador registró y con una fuente externa. **Nunca aprueba por sí solo**: si algo falla o no coincide, lo manda a revisión manual del administrador.

## 1. Qué entregué

| Tarea | Qué es | Horas | PR | Estado |
|---|---|---:|---|---|
| Contrato de HU-19 | Documento que fija la API, los datos guardados y el enganche con HU-21, antes de programar | — | [#27](https://github.com/RXDG2908/Chamba-PE/pull/27) | ✅ Unido |
| **T19.1** | Pantalla de escaneo con cámara (React, responsive) | 3 | [#28](https://github.com/RXDG2908/Chamba-PE/pull/28) | ✅ Unido |
| **T19.2** | Lectura del DNI (OCR de la MRZ) y recorte de la foto del titular | 4 | [#28](https://github.com/RXDG2908/Chamba-PE/pull/28) | ✅ Unido |
| **T19.3** | Validador intercambiable y paso a revisión manual si algo falla | 2 | [#28](https://github.com/RXDG2908/Chamba-PE/pull/28) | ✅ Unido |
| Cierre documental | Marcar las tareas en `AVANCES.md` y `SPRINTS.md` | — | [#35](https://github.com/RXDG2908/Chamba-PE/pull/35) | ✅ Unido |

## 2. Documentos (léelos en este orden)

| Documento | Para qué sirve |
|---|---|
| [API_VERIFICACION_DNI.md](API_VERIFICACION_DNI.md) | **El contrato**: flujo, rutas, códigos de error, validador, datos guardados y dependencias con el equipo |
| [EXPOSICION_HU19.md](EXPOSICION_HU19.md) | Guía para exponer: la idea, el flujo paso a paso, el código en orden y el guion de la demo |
| [../SPRINTS.md](../SPRINTS.md) | Plan aprobado del Sprint 3 y seguimiento por fecha |
| [../AVANCES.md](../AVANCES.md) | Cuadro por tarea y bitácora (filas del 03/11, 04/11 y 12/11) |

## 3. Código (de la petición a la pantalla)

### Backend · app `verificacion/`

| Archivo | Qué hace | Tarea |
|---|---|---|
| [urls.py](../verificacion/urls.py) | Dos rutas: `dni/` y `dni/<id>/foto/<tipo>/` | T19.2 |
| [views.py](../verificacion/views.py) | La API. Orden de las defensas: consentimiento → imágenes válidas → máximo 5 intentos al día → análisis → guardado | T19.2 |
| [servicio.py](../verificacion/servicio.py) | Las reglas: junta los **motivos** (`mrz_ilegible`, `dni_distinto`, `nombres_distintos`, `fuente_no_disponible`…) y decide el resultado | T19.2 / T19.3 |
| [mrz.py](../verificacion/mrz.py) | Interpreta las 3 líneas MRZ del reverso y verifica los dígitos de control | T19.2 |
| [lector.py](../verificacion/lector.py) | OCR cambiable: interfaz `LectorDni` y la implementación `LectorEasyOcr` | T19.2 |
| [validadores.py](../verificacion/validadores.py) | Validador intercambiable: `simulado` (por defecto, sin internet) y `eldni` (reservado). Compara nombres sin tildes ni mayúsculas | T19.3 |
| [models.py](../verificacion/models.py) | `IntentoDni` y el almacenamiento privado de fotos, **fuera de `/media/`** | T19.2 |
| [consentimiento.py](../verificacion/consentimiento.py) | Punto de enganche con HU-21 de Luis; hoy responde que sí | Contrato |
| [tests.py](../verificacion/tests.py) | Pruebas de HU-19 (468 líneas) | T19.2 / T19.3 |

### Frontend

| Archivo | Qué hace | Tarea |
|---|---|---|
| [VerificarDni.jsx](../frontend/src/VerificarDni.jsx) | Pantalla `/verificar-dni`: cámara trasera con marco guía, foto del frente y del reverso, «Repetir», «Validando…» y el resultado con motivos en palabras simples | T19.1 |
| [VerificarDni.css](../frontend/src/VerificarDni.css) | Estilos de la pantalla | T19.1 |
| [App.jsx](../frontend/src/App.jsx) | Ruta `/verificar-dni` solo para prestadores y enlace «Verificar mi DNI» en su menú | T19.1 |

## 4. Cómo funciona, paso a paso

1. El prestador inicia sesión y entra a `/verificar-dni`.
2. Toma la foto del frente y la del reverso. Si no hay cámara, usa el selector de archivo.
3. La pantalla envía `POST /api/verificacion/dni/` ([views.py](../verificacion/views.py)).
4. El servidor comprueba el consentimiento, que las imágenes sean JPG o PNG sanas de hasta 5 MB, y el límite de 5 intentos al día.
5. [lector.py](../verificacion/lector.py) lee la MRZ y [mrz.py](../verificacion/mrz.py) valida los dígitos de control.
6. [servicio.py](../verificacion/servicio.py) compara el DNI y los nombres con el registro del prestador y con la fuente de [validadores.py](../verificacion/validadores.py).
7. Guarda un `IntentoDni` y devuelve el resultado. Si algo falla o no coincide, queda **en revisión manual**.

## 5. Cómo verificarlo

```bash
python manage.py test verificacion
```

- La suite completa tenía 219 pruebas OK al unir HU-19; el lint y el build del frontend también pasaban.
- Con la app corriendo: iniciar sesión como prestador y abrir `http://localhost:5173/verificar-dni`.

## 6. Lo que falta o no está probado

- **Cámara real en celular:** no se ha probado. El panel del navegador la bloquea.
- **OCR real:** `easyocr` está en `requirements.txt`, pero no está instalado en el `venv` local. Sin él, el envío responde 500. Hay que ejecutar `pip install -r requirements.txt` para probar la lectura.
- **Fuente externa:** `eldni.com` no tiene API. El validador `eldni` queda reservado y la decisión es del Product Owner.
- **T19.4:** las pruebas cruzadas con muestras de DNI son de **Luis** y no tienen rama ni PR.
- **HU-21:** el consentimiento y la eliminación de datos son de **Luis**. La función de borrado parcial ya existe en `models.py` para que la use.
- **Menú:** con unos 830 px de ancho, «Verificar mi DNI» queda oculto y hay que deslizar el menú.
