# Qué espera el frontend de cada microservicio

Resumen basado en el *Documento de arquitectura v1.0* (secciones 5, 6, 8, 9 y 10), **con las correcciones acordadas por el equipo** que lo modifican (ver [Cambios respecto del documento de arquitectura](#cambios-respecto-del-documento-de-arquitectura)). Estos cambios se reflejan en la versión 1.1 del documento de arquitectura. Todo lo que no está definido explícitamente está marcado como **[A confirmar]** y debe acordarse con el responsable del servicio antes de integrar.

Además del contrato con el front, cada servicio incluye una sección de **patrones de diseño recomendados**, para cumplir el mínimo de 3 patrones orientados a objetos que pide la consigna.

El frontend **solo habla con el API Gateway** (`VITE_API_URL`, por defecto `http://localhost:8080/api`). Nunca llama directo a los servicios ni a Kafka.

## Responsabilidad de cada servicio

| Servicio | Responsable | Qué guarda | Lo usa el front para |
|---|---|---|---|
| vehiculos | Naim | Unidades del stock y sus precios | Selector de vehículos del preformulario y sus filtros |
| clientes | Juan | **Solo** los datos del cliente que carga en el formulario, más su historial de calificaciones | Historial en la ficha del lead; el alta la hace `leads` por detrás |
| leads | Iván | El **lead**: la estructura completa del preformulario (cliente, vehículo, ubicación, permuta, financiación) y su estado | Enviar el preformulario y trabajar la cola del telemarketer |
| calificacion | Manual | La calificación vigente y las correcciones manuales | Motivos de la calificación y corrección manual del nivel |
| monitor (opcional) | A definir | Estado de salud de los demás servicios | Nada por ahora (Segunda Parte) |

## Cambios respecto del documento de arquitectura

El equipo acordó estas correcciones al documento v1.0; conviene reflejarlas en una v1.1:

1. **`clientes` guarda únicamente los datos personales del formulario.** La estructura completa (cliente, vehículo, ubicación y todo lo que viaja en el JSON del preformulario) pertenece al **Lead**, en `leads`.
2. **El historial de calificaciones pasa de `calificacion` a `clientes`.** Afecta a RF10 (§5.2) y a las tablas de endpoints de las secciones 8.2 y 8.4: `GET /calificaciones/{leadId}/historial` desaparece y aparece `GET /clientes/{id}/historial`.
3. **Se agrega un servicio opcional de monitoreo (encuestador)** para la Segunda Parte.
4. **Se recomiendan patrones de diseño por servicio** (nueva sección en cada uno).

## Convenciones generales

- **Errores:** formato `ProblemDetail` (RFC 7807): `type`, `title`, `status`, `detail`. El front debe mostrar `detail` y, en errores de validación, mapearlos al campo. **[A confirmar]** cómo vienen los errores por campo (ej. extensión `errors: { campo: mensaje }`).
- **IDs:** UUID en todos los servicios.
- **Trazabilidad:** el gateway genera el `correlationId`; el front no lo envía.
- **Sin autenticación** en la Primera Parte.
- **CORS:** lo resuelve el gateway.
- **Prefijo de rutas:** el documento dice que cada servicio se expone bajo `/api/{servicio}`, y las tablas listan rutas internas (`/leads`, `/vehiculos`...). **[A confirmar]** si la URL final es `/api/leads/leads` o `/api/leads`. Hoy `src/utils/constants.js` asume `/leads`, `/clientes`, `/vehiculos`, `/calificaciones` sobre `baseURL=/api`, lo que se corresponde con la segunda opción.

---

## vehiculos (Naim)

Alimenta el selector del paso 1 (RF05, RF11).

| Método | Ruta | Uso en el front |
|---|---|---|
| GET | `/vehiculos?disponible=true` | Lista del preformulario |
| GET | `/vehiculos/{id}` | Detalle de una unidad |
| GET | `/vehiculos/{id}/precio` | Lo usa `calificacion`; el front no lo necesita |
| POST / PUT | `/vehiculos` | Alta/edición (carga inicial por script, sin pantalla en el MVP) |

Campos del vehículo que el front consume (los del modelo de dominio del documento más dos que hay que acordar):

| Campo | Tipo | Estado |
|---|---|---|
| `id` | UUID | Del documento |
| `marca`, `modelo` | texto | Del documento |
| `anio` | entero | Del documento |
| `kilometraje` | entero (km) | Del documento; el front lo muestra y lo filtra |
| `precio` | numérico | Del documento |
| `disponible` | booleano | Del documento |
| `caja` | `Automática` o `Manual` | **[A confirmar]** no está en el documento; el front filtra por este campo |
| `fotoUrl` | URL, **opcional** | **[A confirmar]** no está en el documento; si falta o no carga, el front muestra un bloque de color con iniciales |

Las fotos no deberían guardarse en la base: `vehiculos` guarda solo la URL y sirve los archivos como estáticos. El front las muestra en una miniatura de proporción fija (recorte sin deformar), así que no necesitan venir con medidas exactas.

**Diferencias con el mock actual (`src/data/vehicles.js`)** a resolver al integrar:

- El mock usa nombres en inglés (`brand`, `model`, `year`, `km`, `transmission`, `price`, `fotoUrl`), más `detail` (versión), `tone` y `mark` (solo visuales). El front deberá adaptar los nombres del backend y derivar nombre, iniciales y formato de precio. `detail` (versión, ej. "Exclusive CVT") tampoco está en el modelo del documento **[A confirmar]**.
- **Filtros:** no se define filtrado en el servidor, solo `disponible`. El front trae la lista completa una vez y filtra en memoria, sin pedidos por tecla ni por cambio de filtro: marca y modelo (autocompletado), caja, año, y rangos de precio y kilometraje (sliders cuyos límites salen de los propios datos). Es suficiente para un stock chico; si el stock crece mucho habría que paginar o filtrar en el servidor.
- La lista del formulario muestra 6 vehículos a la vez con scroll.

### Patrones de diseño recomendados en `vehiculos`

| Patrón | Dónde | Por qué |
|---|---|---|
| **Repository** | `VehiculoRepository` (Spring Data JPA) | Aísla el dominio de la persistencia, como en el resto de los servicios. Es el patrón más natural acá: el servicio es básicamente un inventario con altas, consultas y actualizaciones. |
| **Builder** | `Vehiculo.builder()` para el alta | La unidad tiene muchos campos y varios opcionales (`fotoUrl`, `caja`, versión). Un Builder evita constructores con diez parámetros y valida las invariantes (precio positivo, año razonable) en `build()`. |
| **Strategy** | `FotoStorage` con `LocalFotoStorage` (hoy) y otra implementación futura | Dónde se guardan las fotos es una decisión que probablemente cambie (carpeta local, bucket, CDN). Con una interfaz y una implementación por estrategia, cambiar el almacenamiento no toca el resto del servicio. Encaja con la idea de guardar solo la `fotoUrl` en la base. |

## clientes (Juan)

**Guarda únicamente los datos del cliente que se cargan en el formulario**, y nada más del lead. El front **no lo llama para dar de alta**: `leads` crea el cliente por REST al recibir el preformulario. Es el único servicio que conoce el CUIL.

Datos propios del cliente:

| Campo | Tipo | Notas |
|---|---|---|
| `id` | UUID | Es el `clienteId` que referencia el lead |
| `nombre`, `apellido` | texto | Obligatorios |
| `email` | texto | Obligatorio (decisión del formulario) |
| `telefono` | texto, opcional | |
| `cuil` | texto, opcional | Solo si el cliente pidió financiación y, en ese caso, **obligatorio** y validado (11 dígitos, prefijo 20, 23, 24 o 27 y dígito verificador). Siempre **enmascarado** hacia afuera |
| `consentimiento` | `otorgado`, `finalidad`, `otorgadoEn` | Solo junto con el CUIL |

Ubicación, vehículo de interés, anticipo, permuta y si solicita financiación **no** son datos del cliente: pertenecen al Lead.

| Método | Ruta | Uso |
|---|---|---|
| POST | `/clientes` | Alta del cliente con consentimiento. La llama `leads`. Responde `201` con el `clienteId` |
| GET | `/clientes/{id}` | Datos del cliente con el CUIL enmascarado |
| GET | `/clientes/{id}/historial` | **Historial de calificaciones** del cliente, automáticas y manuales (RF10). La usa el front en la ficha del lead |

- El front obtiene el `clienteId` desde la ficha del lead (`GET /leads/{id}` debe devolverlo). **[A confirmar]**.
- El CUIL nunca se muestra completo ni se guarda en el estado del front más tiempo del necesario.
- **No se indexa ni se direcciona por CUIL.** Todo se identifica por UUID (`clienteId`, `leadId`, `vehiculoId`). El CUIL solo lo aporta quien pide financiación, así que no sirve como clave, y en una ruta o parámetro quedaría registrado en logs. Nunca viaja en rutas, parámetros de consulta, eventos ni logs sin enmascarar.
- Como no hay una clave natural, cada preformulario crea un cliente nuevo. Deduplicar por email normalizado queda como punto abierto.
- **Situación crediticia:** si el cliente no tiene CUIL, `GET /clientes/{id}/situacion-crediticia` responde `200` con valor nulo ("sin datos"), no `404`. No es un dato del formulario. El documento de arquitectura la resuelve a través de `clientes` (`GET /clientes/{id}/situacion-crediticia`, número 1 a 6 o "sin datos") para que la consuma `calificacion`. El front no la usa. **[A confirmar]** si sigue en este servicio como valor derivado y cacheado o se mueve.

**Historial de calificaciones.** Cada elemento: `leadId`, `nivel`, `puntaje`, `origen` (`AUTOMATICA` | `MANUAL`), `motivos[]`, `calculadaEn` y, si fue manual, usuario y motivo de la corrección. El campo `leadId` es necesario porque un mismo cliente puede tener más de un lead. Nombres exactos **[A confirmar]**.

**Cómo se alimenta (a definir con Juan y Manual).** `calificacion` ya publica `LeadCalificado` en cada cálculo y cada corrección manual. Lo más simple es que `clientes` consuma ese evento y lo registre. Eso exige que el evento lleve el `clienteId` (hoy el contrato de eventos solo menciona `leadId`) y que incluya los motivos. **[A confirmar]**.

### Patrones de diseño recomendados en `clientes`

| Patrón | Dónde | Por qué |
|---|---|---|
| **Decorator** | Sobre el puerto `PrecalificacionPort`: `PrecalificacionConCache` y `PrecalificacionConLog` envolviendo al adaptador simulado (y después al cliente SOAP/BCRA) | El documento pide caché con fecha de consulta (RNF y riesgos del BCRA) y logs con el CUIL enmascarado (RNF-03). Ambas son responsabilidades **añadidas** a una misma interfaz, sin tocar el adaptador. Cuando en la Segunda Parte se reemplace el simulado por el real, los decoradores no cambian. |
| **Factory** | `ClienteFactory` | Arma el `Cliente` desde la solicitud aplicando las invariantes: normaliza el CUIL y el teléfono, exige consentimiento si hay CUIL y rechaza un CUIL sin finalidad declarada. Concentra en un lugar las reglas de la Ley 25.326. |
| **Observer** | Consumidor de `lead-calificado` que registra el historial | Si el historial vive en `clientes`, este servicio tiene que reaccionar a un hecho de otro (una calificación) sin que `calificacion` lo conozca. Es el uso de Observer ya previsto en el documento (eventos de integración por Kafka), aplicado a la nueva ubicación del historial. |
| *Repository* | Spring Data JPA | Ya lo cubre el documento; no hace falta contarlo entre los tres elegidos, pero está presente. |

## leads (Iván)

Es el servicio principal del front (RF01–RF03, RF07, RF08). **El Lead es la estructura completa del preformulario**: referencia al cliente (`clienteId`), al vehículo (`vehiculoId`), la ubicación, la permuta, el anticipo, si solicita financiación, y el estado y la calificación resultante.

| Método | Ruta | Respuesta | Uso |
|---|---|---|---|
| POST | `/leads` | `202 Accepted` con `{ leadId }` | Enviar el preformulario |
| GET | `/leads?estado=&nivel=` | Lista ordenada por nivel, puntaje y antigüedad | Cola del telemarketer |
| GET | `/leads/{id}` | Ficha del lead (incluye `clienteId`) | Panel de detalle |
| PATCH | `/leads/{id}/estado` | Lead actualizado | Tomar, derivar o descartar |

**Preformulario (`POST /leads`).** Campos que el documento exige (RF01/RF02). Nombres de propiedades **[A confirmar]**; se sugiere agruparlos para dejar claro qué se queda en el Lead y qué reenvía `leads` a `clientes`:

```json
{
  "cliente": {
    "nombre": "Martina",
    "apellido": "López",
    "email": "martina@gmail.com",
    "telefono": "11 5555 5555",
    "cuil": "20-12345678-6",
    "consentimiento": true
  },
  "vehiculoId": "uuid",
  "anticipo": 3000000,
  "permuta": {
    "tienePermuta": true,
    "descripcion": "Fiat Cronos 2019",
    "valorDeclarado": 3000000
  },
  "ubicacion": {
    "provincia": "BUENOS_AIRES",
    "localidad": "San Isidro"
  },
  "solicitaFinanciacion": true
}
```

- El bloque `cliente` es lo que `leads` reenvía a `clientes` (`POST /clientes`); lo demás queda en el Lead junto con el `clienteId` devuelto.
- `anticipo` y `valorDeclarado` son **números**, no strings con `$`. El front debe parsear lo que escribe el usuario.
- `provincia` es un enum en el contrato de eventos (`"BUENOS_AIRES"`). El select actual guarda texto libre (`"Buenos Aires"`, `"CABA"`); hace falta un mapeo o que Iván acepte ambos. **[A confirmar]** lista de valores válidos.
- `cuil` y `consentimiento` solo si `solicitaFinanciacion = true`, y en ese caso son **obligatorios**: el front valida el formato del CUIL (11 dígitos, prefijo 20, 23, 24 o 27 y dígito verificador) y exige el consentimiento, y el backend debe repetir esas validaciones. Si el prospecto no quiere compartirlos, vuelve atrás con "Seguir sin financiación": se envía sin ellos y el lead se califica igual, sin situación crediticia.
- `telefono` es opcional; el resto del bloque `cliente`, salvo `cuil` y `consentimiento`, es obligatorio.
- La respuesta es **inmediata (202)**: el front debe mostrar la confirmación con el `leadId` sin esperar la calificación.
- **Nombre y apellido:** el documento solo habla de "nombre", pero el formulario los pide por separado (ambos obligatorios). Se sugiere enviarlos separados. **[A confirmar]**.
- **Email y teléfono:** el formulario pide el **email como obligatorio** y el **teléfono/WhatsApp como opcional** (el documento pide teléfono en RF01 y no menciona el email, así que hay que acordarlo). Un lead puede llegar sin teléfono, y la cola del telemarketer y la ficha deben tolerar `telefono` vacío o ausente. Se valida que el email tenga "@" y dominio.
- **Copia para la cola:** el documento indica que `leads` guarda nombre y teléfono como copia para mostrarlos en la cola sin llamar a `clientes` en cada fila. **[A confirmar]** si se mantiene, o si la cola resuelve esos datos por `clienteId`.
- **Validaciones del front** (el backend debe repetirlas, porque el cliente no es confiable): nombre y apellido solo letras (mín. 2); email obligatorio con formato válido; teléfono opcional y, si se completa, de 10 a 13 dígitos (admite `+`, espacios, guiones y paréntesis). Los errores 4xx con `ProblemDetail` deberían poder asociarse a estos campos.
- **Captcha:** hoy es una operación aritmética local, sin ninguna verificación en servidor ni campo en el `POST`. Si se quiere uno real (reCAPTCHA, Turnstile), el front enviaría un token y el gateway o `leads` tendrían que validarlo contra el proveedor con una clave secreta guardada en variables de entorno. **[A confirmar]** si se hace.

**Cola del telemarketer.**

- Estados del lead: `PENDIENTE_CALIFICACION`, `CALIFICADO`, `EN_GESTION`, `DERIVADO`, `DESCARTADO`. El mock actual usa `'EN GESTION'` con espacio; con el backend serán con guión bajo.
- Niveles: `No potable`, `Potable`, `Muy potable` (**[A confirmar]** si vienen como enum `NO_POTABLE`, `POTABLE`, `MUY_POTABLE`; `constants.js` ya define la variante legible).
- La cola viene **ya ordenada** por el servidor. El front solo envía el filtro `nivel` y, si corresponde, `estado`.
- Cada item debería incluir: `id`, `clienteId`, nombre y teléfono (si se mantiene la copia), `vehiculoId` (o datos del vehículo), `nivel`, `puntaje`, `estado`, `creadoEn`, y si la ficha está completa. **[A confirmar]** si el vehículo viene embebido o hay que resolverlo contra `vehiculos`.
- Un lead recién enviado puede aparecer sin nivel/puntaje (`PENDIENTE_CALIFICACION`) hasta que `calificacion` responda. No hay push: se necesita refrescar (botón o polling) para verlo calificado.

**Cambios de estado (`PATCH /leads/{id}/estado`).**

- Body **[A confirmar]**: `{ "estado": "EN_GESTION" | "DERIVADO" | "DESCARTADO" }`.
- Derivar con la ficha incompleta debe devolver un error 4xx con `ProblemDetail` (regla RF08). Hoy el mock valida esto en el cliente; con el backend hay que mostrar el `detail` del error.

### Patrones de diseño recomendados en `leads`

| Patrón | Dónde | Por qué |
|---|---|---|
| **Factory** | `LeadFactory` | Ya está en el documento (§11). Construye el Lead desde el preformulario, deja el estado inicial en `PENDIENTE_CALIFICACION` y aplica sus invariantes (ficha completa, permuta coherente, `clienteId` presente). |
| **State** | `EstadoLead` con una clase por estado (`Pendiente`, `Calificado`, `EnGestion`, `Derivado`, `Descartado`) | El ciclo de vida del lead (Figura 6) tiene transiciones válidas e inválidas, y reglas por estado: por ejemplo, **solo se deriva con la ficha completa** (RF08) y un lead descartado no vuelve a gestión. En vez de un `switch` gigante sobre un enum, cada estado decide qué transiciones permite. Es el patrón que mejor calza con este dominio. |
| **Facade** | `RegistrarLeadFacade` (o `LeadFacade`) | Registrar un lead implica varios pasos: llamar a `clientes` por REST, crear el lead con la factory, persistirlo y publicar `PreformularioCompletado`. La facade expone una sola operación (`registrar`) al controlador y oculta esa coordinación. |
| *Observer* | Publicación de `PreformularioCompletado` y consumo de `LeadCalificado` | Ya cubierto por Kafka y los eventos de dominio del documento. |

## calificacion (Manual)

Aporta el detalle de la calificación en el panel del lead (RF09). **Ya no expone el historial**: pasó a `clientes` (ver arriba).

| Método | Ruta | Uso en el front |
|---|---|---|
| GET | `/calificaciones/{leadId}` | Calificación vigente con la lista de motivos |
| POST | `/calificaciones/{leadId}/correcciones` | Corrección manual del nivel |

- Calificación: `nivel`, `puntaje`, `origen` (`AUTOMATICA` | `MANUAL`), `motivos[]` (cada uno con `regla`, `puntos`, `descripcion`) y `calculadaEn`.
- Corrección: body con el nivel elegido y un **motivo obligatorio** (**[A confirmar]** nombres de campos). Reemplaza el `window.prompt` y el cambio de nivel "alternante" del mock por un selector de nivel + campo de motivo.
- Una corrección **no cambia el estado** del lead; solo nivel y puntaje. Tras corregir, hay que refrescar la ficha, la cola y el historial.
- Cada cálculo y cada corrección publican `LeadCalificado`; con eso `clientes` registra el historial.
- Los puntajes pueden ser negativos (mín. −25 en los casos de referencia); el mock asume `0–100` (`score / 100`), eso hay que corregirlo.

### Patrones de diseño recomendados en `calificacion`

Es el servicio donde el documento ya concentra los patrones (§11); se recomienda mantenerlos tal como están.

| Patrón | Dónde | Por qué |
|---|---|---|
| **Strategy** | `ReglaCalificacion` con `ReglaCobertura`, `ReglaFinanciacion`, `ReglaUbicacion` | Cada criterio del negocio es intercambiable. Agregar una regla (por ejemplo, la de antigüedad del lead) no modifica el motor ni a las demás. Las reglas son además fáciles de testear una por una con los casos de §6.3. |
| **Factory** | `MotorCalificacionFactory` | Arma el motor con las reglas y los umbrales vigentes según la configuración, lo que permite cambiar puntos sin recompilar (RNF-07). |
| **Facade** | `CalificacionFacade` | Una sola operación `calificar` que oculta la coordinación entre `vehiculos` (precio), `clientes` (situación crediticia), el motor, la persistencia y la publicación del resultado. |
| *Observer* | Evento de dominio `CalificacionCalculada` | Un listener interno guarda el resultado; ya está en el documento. |

## gateway

- Punto de entrada único (`:8080`), ruteo y CORS.
- El front en producción corre en nginx (`:3000`) y apunta a `VITE_API_URL`.

### Patrones de diseño recomendados en `gateway`

| Patrón | Dónde | Por qué |
|---|---|---|
| **Chain of Responsibility** | Cadena de filtros: `CorrelationIdFilter`, logging, CORS y ruteo | Spring Cloud Gateway ya funciona como una cadena ordenada de filtros que procesan la solicitud uno tras otro. Escribir los filtros propios (en especial el que genera el `correlationId`, AD-10) es una aplicación directa del patrón y el uso más honesto que tiene este servicio. |
| *Facade* (a nivel arquitectura) | El gateway en sí | Ofrece un único punto de entrada que oculta la cantidad de servicios detrás. Se puede mencionar en el informe, pero no requiere código propio. |

## monitor / encuestador (opcional, Segunda Parte)

**No se hace en la Primera Parte.** Es un servicio opcional que **consulta periódicamente a todos los microservicios**, intercambia datos de prueba con ellos y registra el estado de cada uno, de modo que si algo se cae se sepa **cuál fue** y en qué punto de la cadena falló.

**Qué haría**

1. **Chequeo de salud:** consulta el endpoint de salud de cada servicio (Spring Boot Actuator, `/actuator/health`) y registra estado y latencia.
2. **Chequeo funcional:** envía datos de prueba atravesando la cadena real (por ejemplo, consulta `vehiculos`, da de alta un cliente de prueba y un lead de prueba, y verifica que `calificacion` produzca el `LeadCalificado`). Así detecta no solo que el servicio responde, sino que **integra bien con sus vecinos** y con Kafka.
3. **Diagnóstico:** si la cadena falla, informa en qué eslabón se cortó (por ejemplo "leads respondió, pero `calificacion` no consumió el evento").

**Precauciones**

- Los datos de prueba deben estar **marcados como sintéticos** y limpiarse, o filtrarse, para que no aparezcan en la cola del telemarketer ni contaminen el historial. **[A confirmar]** cómo se marcan (campo, prefijo, cliente reservado).
- **Ningún servicio debe depender del monitor.** Si el monitor se cae, el sistema sigue funcionando.
- Necesita alcanzar a cada servicio por la red interna de Docker, no solo por el gateway.

**Contrato para el front (cuando se haga).** No impacta en la Primera Parte. Si en algún momento se muestra un panel de estado:

| Método | Ruta | Respuesta |
|---|---|---|
| GET | `/monitor/estado` | Lista por servicio: `servicio`, `estado` (`UP` \| `DEGRADADO` \| `DOWN`), `latenciaMs`, `ultimaVerificacion` y `detalle` |
| GET | `/monitor/historial?servicio=` | Últimas verificaciones, para ver cuándo empezó una caída |

Nombres y forma **[A confirmar]**.

**Alternativa a evaluar:** existen herramientas hechas (por ejemplo Spring Boot Admin) que cubren el chequeo de salud. Lo que no cubren de fábrica es el chequeo funcional con datos de prueba entre servicios, que es lo que hace valioso al encuestador.

### Patrones de diseño recomendados en `monitor`

| Patrón | Dónde | Por qué |
|---|---|---|
| **Strategy** | `Verificacion` con `VerificacionSalud`, `VerificacionKafka`, `VerificacionFlujoSintetico` | Cada tipo de chequeo es una forma distinta de comprobar un servicio, y se puede agregar uno nuevo sin tocar el planificador. |
| **Observer** | Notificación de cambios de estado | Cuando un servicio pasa de `UP` a `DOWN`, quienes estén interesados (log, alerta, panel) reaccionan sin que el verificador los conozca. |
| **Composite** | `VerificacionCompuesta` | Permite tratar un conjunto de chequeos (por ejemplo, "todo el flujo de leads") como si fuera un solo chequeo, y reportar el primero que falla. |

---

## Resumen de patrones por servicio

La consigna de la Primera Parte pide como mínimo 3 patrones (el documento de arquitectura menciona Factory, Repository, Strategy, Observer y Facade). Con las recomendaciones de arriba, cada servicio tiene al menos 3 y el conjunto cubre más de los necesarios:

| Servicio | Patrones recomendados |
|---|---|
| vehiculos | Repository · Builder · Strategy (almacenamiento de fotos) |
| clientes | Decorator · Factory · Observer (+ Repository) |
| leads | Factory · State · Facade (+ Observer) |
| calificacion | Strategy · Factory · Facade (+ Observer) |
| gateway | Chain of Responsibility (+ Facade a nivel arquitectura) |
| monitor (opcional) | Strategy · Observer · Composite |

**Sobre Singleton.** En Spring todos los beans (`@Service`, `@Component`, etc.) son singleton por defecto, así que técnicamente el patrón "está presente" sin que el equipo lo escriba. No se recomienda contarlo como uno de los 3 patrones, porque no se implementa. Si la cátedra pide uno explícito, el candidato razonable es una pieza de configuración inmutable que se instancia una sola vez, como el motor de reglas ya armado por su Factory, y conviene justificarlo en el informe.

---

## Qué cambia en el frontend al integrar (resumen)

1. Reemplazar `src/data/vehicles.js` por `GET /vehiculos?disponible=true` y adaptar el formato (los filtros ya funcionan en memoria sobre esa lista).
2. Convertir montos a número antes de enviar, mapear `provincia` al enum y armar el body agrupado (`cliente`, `permuta`, `ubicacion`).
3. Enviar el preformulario con `POST /leads` y mostrar el `leadId` real en lugar del generado localmente (`DDA-<timestamp>`).
4. Reemplazar `initialLeads` de la cola por `GET /leads?nivel=` y las acciones por `PATCH /leads/{id}/estado`.
5. Cargar los motivos desde `calificacion` y **el historial desde `clientes`** (`GET /clientes/{clienteId}/historial`, con el `clienteId` de la ficha del lead); usar `POST .../correcciones` para la corrección manual.
6. Manejar errores `ProblemDetail` en todas las llamadas (red caída, 4xx, 5xx).

## Puntos abiertos para el equipo

- Prefijo final de las URLs a través del gateway.
- Nombres exactos de campos del `POST /leads` (y si se agrupa en `cliente`, `permuta` y `ubicacion`) y de la corrección manual.
- Valores del enum `provincia` y formato de `nivel`/`estado` en las respuestas.
- Campos `caja`, `fotoUrl` (opcional) y `detalle`/versión en vehículos.
- Cómo guarda `leads` el nombre, y si mantiene una copia de nombre y teléfono para la cola.
- Si `GET /leads/{id}` devuelve el `clienteId`.
- **Historial en `clientes`:** forma de cada elemento, si el evento `LeadCalificado` lleva `clienteId` y motivos, y cómo se alimenta el historial.
- Si la situación crediticia sigue en `clientes` como dato derivado.
- Si habrá un captcha verificado en servidor.
- **Deduplicación de clientes:** sin CUIL como clave, una misma persona que envía dos veces genera dos clientes. La opción es que `POST /clientes` busque o cree por email normalizado (`200` si ya existía, `201` si es nuevo). Para el MVP se recomienda no deduplicar.
- Si el item de la cola trae el vehículo embebido.
- Formato de errores de validación por campo.
- Si se hace el monitor, cómo se marcan los datos sintéticos.
