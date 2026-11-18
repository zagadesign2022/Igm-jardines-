# IGM JARDINES — proyecto Android en desarrollo (v0.9)

Proyecto Android nativo en Kotlin y Jetpack Compose. **Este ZIP contiene código fuente; no es una APK. No se ha podido compilar ni probar en Android Studio ni en un teléfono.**

## Módulos incluidos en el código
- Clientes y jardines con almacenamiento local Room.
- Catálogo básico de servicios y tarifas sin IVA.
- Creación/listado básico de presupuestos, varias líneas, selección opcional de jardín y descuento global sencillo.
- Cambio manual de estado de presupuesto a «Aceptado».
- Conversión de presupuesto aceptado a factura, evitando la conversión duplicada en el flujo normal de la pantalla.
- Numeración independiente de facturas y numeración separada para rectificativas.
- Registro de pagos parciales y actualización de estado a pendiente, parcialmente pagada o pagada.
- Exportación de factura a PDF con logo y datos de empresa configurables, paginación básica y desglose por tipo de IVA.
- Recibos PDF simples por cada pago registrado.
- Ficha «Mi empresa» persistida localmente.
- Creación de facturas rectificativas íntegras: conserva la factura original, genera líneas e importes negativos, registra factura original y motivo, y permite exportar PDF rectificativo.
- Agenda básica: programar trabajo por cliente, jardín opcional, fecha, duración, descripción y estado; cambiar a «En curso» o «Terminado».
- Registro local de gastos: concepto, categoría, fecha, base imponible, IVA soportado, total y observaciones; permite borrar un registro.
- Estadísticas acumuladas: facturación emitida, cobrado, pendiente, gastos registrados y estimación sencilla de cobros menos gastos. Desglose de base e IVA soportado.

## Protección de datos
- No se usa `fallbackToDestructiveMigration()`.
- Hay migraciones explícitas 1→2 (servicios), 2→3 (presupuestos), 3→4 (facturas y pagos), 4→5 (perfil de empresa) y 5→6 (agenda) y 6→7 (gastos).
- Si no existe una ruta de migración, Room detendrá la apertura en vez de borrar automáticamente.
- Esto **no garantiza** que todas las bases históricas migren correctamente. Antes de usar información real hay que probar cada migración con copias reales de cada versión y verificar exportación/restauración.

## Limitaciones importantes
- No se ha compilado ni ejecutado. Puede contener errores que solo aparezcan al sincronizar/compilar en Android Studio.
- La rectificativa implementada es una rectificación íntegra de una factura original y se limita a una rectificativa por factura en esta interfaz. No permite rectificaciones parciales, abonos por líneas, ni contabilización de pagos/reembolsos asociados a la rectificativa.
- El módulo de gastos todavía no adjunta fotos de tickets ni permite editar registros; las estadísticas son acumuladas, no incluyen filtros temporales ni conciliación bancaria.
- No se implementa edición de facturas emitidas; debe mantenerse la trazabilidad documental. Los errores se corrigen mediante rectificativa, no sobrescribiendo el original.
- La agenda es básica: no incluye recurrencias, calendario visual día/semana/mes, avisos/notificaciones, fotos, firma, materiales ni vinculación automática a factura.
- No están implementados todavía inventario de maquinaria, desplazamientos, copias de seguridad ni recordatorios.
- Los PDFs necesitan revisión visual, impresión y validación fiscal/contable. No sustituyen el asesoramiento profesional ni garantizan por sí solos el cumplimiento normativo.
- No debe usarse aún para emitir facturas reales ni como único registro contable.

## Abrir el proyecto
1. Descomprime el ZIP.
2. Abre la carpeta `IGM_JARDINES_Android` en Android Studio.
3. Sincroniza Gradle con Android SDK 35.
4. Compila y prueba primero en un dispositivo/emulador vacío y con datos ficticios.

## Antes de producción
Pruebas instrumentadas de todas las migraciones, restauración de copias, numeración concurrente, cálculos de IVA/descuentos, rectificativas, pagos, facturas largas y PDFs multipágina; completar módulos pendientes y revisar obligaciones fiscales y de facturación aplicables en España.


## Versión 0.10 — maquinaria y desplazamientos

- Inventario local de máquinas con tipo, marca/modelo, coste, horas de uso y próxima revisión por fecha u horas.
- Registro de mantenimientos con descripción, fecha, coste y horas de máquina.
- Registro de desplazamientos con cliente, origen/destino, kilómetros, litros, coste de combustible, otros costes y duración.
- Resumen acumulado de kilómetros y costes.
- Base de datos Room versión 9 con migraciones explícitas 7→8 y 8→9. No se utiliza `fallbackToDestructiveMigration`.

**Limitaciones:** esta entrega añade pantallas funcionales básicas para altas/listados y borrado de máquinas/desplazamientos. No incluye todavía edición de registros, avisos automáticos por horas/fecha, adjuntar fotos o tickets, asignación automática del viaje a un trabajo, ni calendario recurrente. No se ha compilado ni probado en dispositivo en este entorno; debe abrirse en Android Studio y validar las migraciones con copias de bases de datos de versiones anteriores antes de introducir datos reales.


## v0.12 — Avisos locales
- Programación diaria aproximada con WorkManager, sin conexión a internet.
- Avisos de mantenimiento si se alcanza la hora indicada o faltan siete días o menos para la fecha prevista.
- Recordatorio de copia de seguridad semanal o mensual; la fecha se actualiza al exportar correctamente una copia.
- Pantalla Avisos para activar/desactivar notificaciones y elegir intervalo. En Android 13 o superior se solicita permiso de notificaciones.
- Los avisos dependen de las restricciones de batería y notificaciones del sistema; no son alarmas exactas.


## v0.13 — Agenda y trabajos recurrentes
- Filtro de agenda para próximos trabajos o todos los trabajos registrados.
- Al programar un trabajo se puede elegir sin repetición, cada 15 días, mensual o cada temporada (cada 3 meses).
- Las recurrencias se materializan como trabajos individuales para los siguientes seis meses (máximo 13 ocurrencias), por lo que cada cita puede cambiar de estado de forma independiente.
- No se ha añadido todavía edición/eliminación de citas ni una cuadrícula mensual visual.


## Versión 0.14.0 — Agenda y gestión de trabajos
- Navegación por meses y filtro de trabajos del mes seleccionado o todos los trabajos.
- Edición de título, descripción, fecha y duración de un trabajo ya creado.
- Eliminación individual con confirmación previa.
- Se mantienen las recurrencias materializadas de la versión anterior.
- La agenda todavía se presenta como listado mensual; la cuadrícula visual de calendario y las fotos antes/después quedan pendientes.
- ZIP revisado estructuralmente; no se ha podido verificar una compilación Android en este entorno.


## Versión 0.15.0 — calendario mensual visual
- Cuadrícula mensual de lunes a domingo con navegación entre meses.
- Los días con trabajos aparecen señalados; al tocar un día se filtra la agenda a sus citas.
- Se puede quitar el filtro y volver a ver todos los trabajos del mes.
- La cuadrícula usa las fechas de los trabajos existentes y no requiere cambios de base de datos ni migraciones.
- Se conservan la edición/eliminación individual y las recurrencias ya generadas.

**Validación:** se ha revisado el contenido del proyecto y se comprobará la integridad del ZIP. No se ha compilado ni ejecutado en Android; la compilación y las pruebas de interfaz siguen pendientes.


## Versión 0.16.0 — Fotografías de trabajos

- Cada trabajo permite seleccionar una fotografía de antes y otra de después desde el selector de imágenes de Android.
- Las imágenes se copian al almacenamiento privado de la aplicación (`files/work_photos`) para no depender de permisos temporales sobre la galería.
- Se guardan las rutas en los campos `beforePhotoPath` y `afterPhotoPath` del trabajo.
- La base de datos sube de versión 9 a 10 mediante la migración explícita `MIGRATION_9_10`, que añade ambos campos sin borrar registros anteriores.

**Limitación importante:** el sistema de copia de seguridad actual exporta la base de datos, pero todavía no incluye la carpeta de fotografías. Antes de usar fotos como archivo permanente, debe completarse la copia/restauración conjunta de base de datos e imágenes. La compilación y las pruebas en dispositivo Android siguen pendientes.


## Versión 0.17.0 — Historial por jardín
- Añade acceso al historial desde Trabajos y agenda.
- Permite seleccionar cliente y jardín y consultar los trabajos vinculados a ese jardín, ordenados del más reciente al más antiguo.
- Muestra fecha, estado, descripción y número de fotografías locales disponibles.
- No añade tablas nuevas; reutiliza la relación gardenId ya existente en los trabajos.
- Pendiente de compilación y prueba en dispositivo Android.


## Versión 0.18.0 — Copias completas con fotografías
- La exportación genera un archivo ZIP con la base de datos y todas las fotografías privadas de `work_photos`.
- La restauración extrae primero a una zona temporal, comprueba la integridad SQLite, la versión de base de datos y las tablas necesarias antes de sustituir los datos actuales.
- Se valida que las rutas de las fotografías del ZIP no puedan salir de la carpeta temporal de restauración.
- Se conserva una copia temporal de la base de datos y de las fotografías actuales para intentar revertir la sustitución si ocurre un error.
- Se actualizan las rutas absolutas de las fotos para que apunten al almacenamiento privado del dispositivo donde se restaura.
- Compatibilidad de restauración con las copias antiguas que contenían únicamente el archivo SQLite (versiones 9 y 10), además de copias ZIP completas de versiones 10 y 11. Esas copias antiguas no contienen fotografías.
- La exportación se identifica como archivo ZIP (`IGM_JARDINES_copia_*.zip`).

**Validación pendiente:** el código y el contenido del ZIP del proyecto se revisan estructuralmente, pero todavía no se ha compilado ni ejecutado en Android. Antes de confiar en copias reales hay que probar exportación/restauración con fotos, una copia antigua, y un fallo durante la restauración en un dispositivo de prueba.

## Versión 0.19.0 — ficha completa del cliente

- La ficha del cliente muestra sus datos de contacto y observaciones.
- Reúne los jardines/ubicaciones asociados y muestra cuántos trabajos, presupuestos y facturas hay vinculados a cada jardín.
- Añade un historial del cliente con trabajos, presupuestos y facturas, ordenando los trabajos por fecha más reciente.
- Incluye un resumen de facturación del cliente: total facturado, cobrado y pendiente, calculado a partir de las facturas registradas.
- No cambia el esquema de Room ni modifica datos existentes; se mantienen las migraciones previas y no se activa ninguna migración destructiva.
- Pendiente: compilación e instalación en dispositivo Android y pruebas funcionales con datos reales/de prueba.

## Versión 0.20.0 — edición de clientes y jardines
- Permite editar una ficha de cliente desde el listado y desde su propia ficha.
- Permite modificar el nombre, DNI/CIF, teléfono, email, dirección y observaciones sin crear un cliente duplicado.
- Permite editar el nombre, dirección/ubicación y observaciones de cada jardín.
- Las actualizaciones se realizan sobre el registro existente mediante `@Update` de Room; no se elimina ni recrea el cliente, por lo que se conservan sus jardines, trabajos, presupuestos y facturas relacionados.
- No cambia el esquema de la base de datos ni requiere una nueva migración.

**Validación pendiente:** revisión estática y comprobación del ZIP del proyecto. No se ha compilado ni probado en un dispositivo Android; hay que probar edición, persistencia tras cerrar/reabrir y conservación de las relaciones antes de usarlo con datos importantes.


## Versión 0.21.0 — Validación y formularios

- Los formularios de cliente y jardín permiten desplazamiento vertical cuando el contenido no cabe en pantalla.
- El email del cliente se valida si se introduce; el campo puede dejarse vacío. No se permite guardar un email con formato incorrecto.
- Se mantiene la obligatoriedad del nombre del cliente y del jardín.
- No se modifica el esquema Room (base de datos versión 10), por lo que esta mejora no requiere migración.
- Verificación realizada en esta entrega: inspección del código y comprobación de integridad del ZIP. No se ha ejecutado una compilación Gradle ni pruebas en dispositivo Android.

## Versión 0.22.0 — archivo seguro de clientes y jardines
- Se añade el campo `isArchived` a clientes y jardines mediante la migración explícita `MIGRATION_10_11`; todos los registros anteriores quedan activos (`0`) y se conservan.
- El listado de clientes separa activos y archivados. Permite archivar con confirmación y restaurar desde la pestaña de archivados.
- La ficha del cliente separa jardines activos y archivados. Permite archivar jardines con confirmación y restaurarlos.
- Archivar no elimina filas ni modifica trabajos, presupuestos, facturas, pagos o fotografías relacionados. Se evita el borrado físico en esta función para proteger el historial y la trazabilidad.
- No se usa `fallbackToDestructiveMigration`; la migración 10→11 añade únicamente dos columnas `INTEGER NOT NULL DEFAULT 0`.

**Validación pendiente:** revisar la migración sobre una copia de base de datos real y compilar/ejecutar en Android. En este entorno no está disponible Gradle ni el wrapper, así que esta entrega no incluye APK ni certifica compilación.


## Versión 0.23.0 — Edición segura de presupuestos

- Se pueden modificar el descuento y las notas de un presupuesto ya creado, conservando sus líneas, precios unitarios, cantidades e IVA originales.
- El total se recalcula a partir de la base e IVA guardados en el documento.
- Se valida que el descuento sea numérico, finito, no negativo y no supere el importe antes del descuento.
- Si existe una factura vinculada al presupuesto, se bloquea la modificación comercial para no dejar el documento facturado incoherente.
- Se añade acceso rápido a marcar presupuestos como aceptados o rechazados.
- No cambia el esquema de Room; se mantiene la versión de base de datos 11 y la cadena de migraciones existente.

**Pendiente de verificación:** compilación Gradle, pruebas instrumentadas Room y pruebas funcionales en dispositivo Android.


## Versión 0.24.0 — Edición segura de vencimientos y observaciones de facturas
- Permite corregir la fecha de vencimiento y las observaciones de una factura antes de registrar cobros.
- La edición se valida dentro de una transacción y se bloquea si existen cobros o rectificativas vinculadas.
- No modifica número, importes, IVA ni líneas de una factura emitida.
- Valida fechas estrictamente en formato dd/MM/aaaa y refuerza la validación de importes y método al registrar pagos.
- No cambia el esquema Room (se mantiene la versión 11); no requiere migración.
- Pendiente: compilar con Gradle/Android SDK y probar en dispositivo, incluyendo pagos parciales y restauración de copias.


## Versión 0.25.0 — estadísticas por periodos y borrado protegido de gastos
- Las estadísticas permiten filtrar por mes actual, trimestre actual, año actual o todos los registros.
- Se filtran facturas por fecha de emisión y gastos por fecha del gasto, mostrando el intervalo aplicado y el número de registros incluidos.
- Se aclara que el resultado mostrado es una estimación de caja (cobros registrados menos gastos registrados), no un beneficio contable/fiscal.
- El borrado de un gasto solicita confirmación explícita antes de eliminarlo.
- No cambia el esquema Room (se mantiene la versión 11) y no requiere migración.
- Pendiente: compilación Gradle y pruebas en Android; revisar visualmente el comportamiento en pantallas estrechas.


## Versión 0.26 — Justificantes de gastos
- Los gastos permiten adjuntar una fotografía desde la galería mediante el selector de Android.
- Las imágenes se guardan en el almacenamiento privado de la aplicación y su ruta queda asociada al gasto.
- Las copias ZIP incluyen la carpeta `expense_receipts/` y la restauración valida las rutas antes de extraerlas.
- Migración explícita Room 11→12: añade `receiptPath` sin borrar gastos existentes.
- Pendiente: compilación Android y pruebas funcionales en dispositivo.

## Versión 0.27 — Edición de gastos
- Permite editar concepto, categoría, fecha, base imponible, IVA soportado y observaciones de un gasto existente.
- Al editar, conserva el justificante anterior si no se selecciona una imagen nueva.
- Si se sustituye el justificante, actualiza primero el registro y elimina después el archivo anterior.
- No cambia el esquema de Room (versión 12).
- Pendiente: compilación y pruebas instrumentadas en Android.

## Versión 0.28.0 — Comparativa mensual
- Añade una comparativa de los últimos seis meses con facturación emitida, gastos registrados y diferencia entre ambos.
- La pantalla de estadísticas permite desplazamiento vertical para consultar todos los bloques en móviles pequeños.
- La comparativa utiliza la fecha de emisión de las facturas y la fecha de registro de los gastos; no representa cobros reales por fecha de pago ni beneficio contable/fiscal.
- No cambia el esquema Room ni requiere migración.
- Pendiente: compilación Gradle, pruebas instrumentadas y validación en dispositivo Android.


## Versión 0.29 — IVA y cobros por fecha real

- El resumen de estadísticas incorpora el IVA repercutido de las facturas emitidas en el periodo.
- Muestra el IVA soportado de los gastos y una diferencia orientativa entre ambos.
- El total de cobros del periodo se calcula a partir de los movimientos de `invoice_payments` usando la fecha real de cada pago, no la fecha de emisión de la factura.
- El pendiente se calcula para las facturas emitidas en el periodo seleccionado, descontando sus cobros acumulados.
- La comparativa mensual separa facturación emitida, cobros recibidos y gastos registrados según sus respectivas fechas.
- No cambia el esquema Room ni requiere migración. La versión de la base de datos permanece en 12.

**Limitación:** las cifras de IVA son orientativas y no constituyen una liquidación fiscal; no se sustituyen las reglas contables o fiscales aplicables. Esta versión se ha empaquetado y verificado como ZIP, pero no se ha compilado ni probado en un dispositivo Android.


## Versión 0.30 — costes por trabajo y rentabilidad

- Los gastos y desplazamientos pueden vincularse opcionalmente a un trabajo de la agenda.
- Se mantiene la opción de registrar costes generales sin asignarlos a un trabajo.
- Estadísticas con desglose orientativo por cliente y jardín: facturación emitida, gastos vinculados, desplazamientos vinculados y margen estimado.
- Nuevo informe PDF de rentabilidad exportable desde Estadísticas.
- Migración Room 12→13 que añade los vínculos sin borrar registros anteriores.
- Restauración de copias compatible con bases de datos de las versiones 9 a 13, con límites de tamaño, rechazo de entradas duplicadas y comprobación de integridad SQLite.
- Se corrigió un bloque de código ajeno que había quedado dentro de la pantalla de desplazamientos.

**Limitaciones:** el margen solo descuenta costes vinculados expresamente a trabajos; no reparte gastos generales. Los datos de facturación se agrupan por cliente y jardín. El informe es orientativo, no contabilidad fiscal. Esta versión aún requiere compilación y pruebas en Android antes de considerarse lista para instalar.


## Versión 0.31 — integridad verificable de copias y preparación de compilación

- Las nuevas copias ZIP incluyen un manifiesto con tamaño y huella SHA-256 de la base de datos, fotografías y justificantes.
- Antes de exportar, se vuelve a leer el ZIP generado y se verifican sus archivos y huellas; solo después se copia al destino elegido.
- Al restaurar una copia nueva, se verifican todas las huellas y se rechazan archivos ausentes, alterados, duplicados o no declarados antes de tocar los datos actuales.
- Se mantienen compatibles las copias antiguas sin manifiesto, incluidas las copias que contienen únicamente la base de datos.
- Se limitan el tamaño individual de archivos, el tamaño total y el número de entradas para reducir riesgos de archivos ZIP anómalos.
- Se corrige la gestión de la instancia de Room cuando una restauración falla durante la fase de validación, evitando invalidar una base de datos que sigue abierta.
- Se añade un flujo de GitHub Actions y una guía para compilar y obtener una APK de depuración con JDK 17, Android SDK 35 y Gradle 8.9.
- No cambia el esquema Room (versión 13); no requiere migración.

**Validación realizada:** el gestor de copias supera una comprobación de sintaxis Kotlin aislada con stubs de Android; se ha simulado la migración SQL 12→13 conservando registros y se han probado las reglas del manifiesto SHA-256. El ZIP del proyecto también se ha comprobado. No se ha compilado la aplicación Android ni generado una APK en este entorno porque no hay Gradle ni Android SDK y no hay acceso de red para instalarlos. El flujo de GitHub Actions está preparado, pero todavía no se ha ejecutado.


## Versión 0.32 — Justificantes de gastos más seguros

- Al adjuntar una imagen a un gasto, la aplicación exige que se pueda leer y copiar correctamente antes de guardar el registro.
- Si falla el guardado del gasto después de copiar la imagen, se elimina el archivo temporal para evitar residuos.
- Al borrar un gasto, también se elimina su justificante local asociado.
- El diálogo de confirmación aclara que se borrará el gasto y su justificante.
- Se mantiene la compatibilidad de la base de datos; no requiere migración Room.


## Comprobación previa de la versión 0.32

Antes de compilar, el flujo de GitHub Actions ejecuta `python3 tools/preflight.py`. Esta comprobación valida invariantes del proyecto y ejecuta la SQL de la migración Room 12 → 13 sobre una base SQLite temporal. No sustituye la compilación Gradle, el procesamiento KSP de Room ni las pruebas en un dispositivo Android.
