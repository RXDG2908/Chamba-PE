# Demo del Sprint 1 · 01/10 – 14/10/2026

Guion para presentar el Sprint 1 en vivo sobre el `main` actual. Lo preparó Renzo León (Scrum Master) el 29/10. El flujo completo se probó ese día en el navegador (registro → verificación → búsqueda → solicitud → Aceptada).

## 1. Mis historias de usuario (Renzo León)

| Sprint | HU | Historia | Mis tareas | Horas | Estado |
|:---:|:---:|---|---|:---:|:---:|
| 1 | HU-1 | Como **prestador**, quiero registrarme con mis datos, rubros y área de cobertura, a fin de que los clientes cercanos puedan encontrarme. | T1.1 modelo del prestador · T1.2 API de registro · T1.3 formulario con mapa | 7 | ✅ completa (05/10) |
| 1 | HU-4 | Como **cliente**, quiero enviar una solicitud al prestador y que él pueda aceptarla o rechazarla, a fin de contratar en pocos minutos. | T4.1 flujo de solicitud (backend) | 3 | ✅ completa (12/10, PR 4). Pantalla: David (T4.2). Pruebas: Luis (T4.3) |
| 2 | HU-5 | Como **cliente o prestador**, quiero registrarme e iniciar sesión de forma segura, a fin de acceder a las funciones de mi perfil. | T5.1 API con roles (#13) · T5.2 pantallas de registro e inicio de sesión (#17) | 6 | ✅ completa (28/10) |
| 2 | HU-6 | Como **prestador**, quiero activar o desactivar mi disponibilidad, a fin de recibir solicitudes solo cuando esté listo. | T6.1 endpoint (#15) · T6.2 interruptor (#18) | 4 | ✅ completa (28/10) |

En el Sprint 1 hice **10 de 10 h** y las dos historias que construí (HU-1 y HU-4) quedaron completas.

**Criterios de aceptación que se muestran en la demo:**

- **HU-1:** no se completa sin DNI, al menos un rubro y un área de cobertura. Al guardar, el perfil queda "Pendiente de verificación".
- **HU-4:** el prestador recibe la solicitud y la acepta o la rechaza. El cliente ve el cambio de estado.

## 2. Resumen del sprint (para la primera diapositiva)

| Indicador | Valor |
|---|---|
| Meta | El prestador se registra y el cliente lo encuentra y le envía una solicitud |
| Horas | 26 de 30 h (87 %) |
| Tareas | 11 de 13. T2.2 y T3.4 (David) pasaron al Sprint 2 y se unieron el 18/10 |
| Historias | HU-1 y HU-4 completas al cierre. HU-2 y HU-3 se completaron el 18/10 |
| `main` al cierre | 49 de 49 pruebas OK, lint y build sin errores |

## 3. Preparación (15 minutos antes)

```powershell
git checkout main
git pull origin main
venv\Scripts\activate
pip install -r requirements.txt
python manage.py migrate
python manage.py preparar_demo
python manage.py runserver
```

En otra terminal:

```powershell
cd frontend
npm install
npm run dev
```

`preparar_demo` crea estas cuentas. Son ficticias, solo sirven para la base local y el comando se niega a correr si `DEBUG` está apagado. Si se corre de nuevo, no duplica nada.

| Rol | Usuario | Contraseña | Dónde se usa |
|---|---|---|---|
| Administrador | `admin.demo` | `AdminChamba2026` | `http://127.0.0.1:8000/admin/` |
| Cliente (opcional) | `ana.cliente` o `ana@demo.pe` | `ClienteChamba2026` | `/ingresar` |
| Prestador de respaldo | `jorge.respaldo` | `PrestadorChamba2026` | `/ingresar`. Ya está verificado y activo |
| Prestador en vivo | `rosa.q` | `ClaveSegura2026` | Se registra durante la demo |

Datos para registrar a Rosa: Rosa Quispe, DNI `41234567`, correo `rosa@demo.pe`, rubro Gasfitería y un clic en el centro de Lima en el mapa.

Abre **dos ventanas** del navegador:

- **Ventana A (normal):** el cliente, más una pestaña con `http://127.0.0.1:8000/admin/` ya iniciada como `admin.demo`.
- **Ventana B (incógnito):** el prestador.

## 4. Cómo funcionan las sesiones

1. **Una ventana tiene una sola sesión** en `localhost:5173`. Si inicias sesión con otra cuenta en la misma ventana, la anterior se reemplaza.
2. **El cliente no necesita sesión.** Buscar, enviar la solicitud y ver el cambio de estado funcionan sin iniciar sesión.
3. **`/admin/` está en otra dirección** (`127.0.0.1:8000`), así que su sesión no choca con la de React.
4. **Las direcciones `/api/...` se abren por el puerto 5173**, para que usen la sesión iniciada en React.

## 5. Guion de la presentación (unos 10 minutos)

| # | Min | Ventana · sesión | Qué hago | Qué digo |
|:---:|:---:|---|---|---|
| 1 | 1 | — | Muestro el resumen (sección 2) | "La meta era el MVP: que el prestador se registre y el cliente lo encuentre y le envíe una solicitud. Cerramos con 26 de 30 h y el flujo completo funcionando." |
| 2 | 1 | A · ninguna | En `/` pulso **Registrarme** sin elegir rubro | "HU-1. El primer criterio: sin DNI, rubro y cobertura no se completa. El servidor responde *Elige al menos un rubro*." |
| 3 | 1 | A · ninguna | Lleno los datos de Rosa, marco el mapa, ajusto el radio y registro | "Los rubros vienen de la API y el círculo es el área de cobertura. Al guardar queda *Pendiente de verificación*: un prestador no aparece hasta que lo revisan." |
| 4 | 1 | A · pestaña admin | En Prestadores → Rosa Quispe cambio Estado a **Verificado** | "Esto se agregó en el Sprint 2 por seguridad. En el Sprint 1 bastaba con registrarse." |
| 5 | 1 | B · **rosa.q** | En `/ingresar` entro como Rosa y en **Mi disponibilidad** la pongo en **Activo** | "El prestador decide cuándo recibe trabajo (Sprint 2)." |
| 6 | 1,5 | A · ninguna | En `/resultados` elijo Gasfitería, latitud `-12.0464` y longitud `-77.0428`, y pulso **Usar coordenadas** y **Buscar** | "HU-3, de Luis y David: solo salen prestadores disponibles, del rubro elegido y con cobertura sobre el cliente, ordenados por distancia." |
| 7 | 1 | A · ninguna | En la tarjeta de Rosa lleno nombre, teléfono y descripción, y pulso **Enviar solicitud** | "HU-4. Antes de crearla, la API valida que el prestador esté disponible, ofrezca el rubro y cubra la ubicación. Queda *Pendiente de respuesta*." |
| 8 | 1 | B · **rosa.q** | Abro `localhost:5173/api/servicios/solicitudes/recibidas/` | "Esta es la bandeja del prestador. Solo la ve el dueño, con su sesión iniciada." |
| 9 | 0,5 | B · **rosa.q** | Abro `.../api/servicios/solicitudes/<id>/aceptar/` y pulso **POST** | "El prestador acepta." |
| 10 | 0,5 | A | Vuelvo a la tarjeta | "El cliente ve **Aceptada** sin recargar. Ese es el segundo criterio de HU-4." |
| 11 | 0,5 | B · **rosa.q** | Abro `.../<id>/rechazar/` y pulso **POST** | "Una solicitud se responde una sola vez: *La solicitud ya fue aceptada*." |
| 12 | 1 | — | Cierre con la retrospectiva | "Aprendimos tres cosas: las tareas que bloquean a otros van primero, nada entra a `main` sin un PR revisado y los PRs tienen que ser pequeños. Las aplicamos en el Sprint 2." |

**Extras, si hay tiempo:**

- `python manage.py test` para mostrar todas las pruebas en verde.
- `python manage.py medir_busqueda` para mostrar que la búsqueda responde en menos de 3 s (criterio de HU-3).

## 6. Plan B

| Si pasa esto | Hago esto |
|---|---|
| El mapa no carga (sin internet) | La búsqueda sigue funcionando con las coordenadas escritas a mano. Para el registro, uso a Jorge (`jorge.respaldo`), que ya existe |
| Falla el registro de Rosa porque ya existe | La base viene de una demo anterior: borro `db.sqlite3` y corro `migrate` y `preparar_demo`, o sigo con Jorge |
| La bandeja da 403 | Se cerró la sesión de Rosa en la ventana B. Vuelvo a entrar en `/ingresar` |
| Rosa no sale en la búsqueda | Le falta estar **Verificado** (paso 4) o **Activo** (paso 5) |

## 7. Preguntas probables

- **¿Por qué la bandeja del prestador no tiene pantalla?** En el Sprint 1 se construyó la API. La pantalla para aceptar o rechazar llega con las notificaciones (HU-8, T8.2), que pasaron al Sprint 3.
- **¿Cómo se calcula la distancia?** Con la fórmula de Haversine entre la ubicación del cliente y el centro de cobertura del prestador. T7.3 la prueba con un error menor a 1e-9 km.
- **¿Qué pasa si dos clientes solicitan al mismo prestador?** Cada solicitud es independiente y el prestador responde cada una por separado.
