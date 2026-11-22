# Privacidad y datos personales

Este documento describe qué guarda PARKEASY y qué puede hacer quien lo administra. No es asesoría legal.

## Qué se guarda

| Dato | Dónde | Para qué |
|---|---|---|
| Placa, color y tipo de vehículo | Tabla `ParkingRecord` | Registrar la estadía y calcular el cobro |
| Hora de entrada y de salida, puesto, piso, valor cobrado | Tabla `ParkingRecord` | Historial y totales |
| Observaciones del registro | Tabla `ParkingRecord` | Texto libre que escribe el administrador |
| Cambios hechos en la app (quién no se guarda: hay un solo administrador) | Tabla `ChangeLog` | Bitácora para corregir errores |
| Nombre del parqueadero, tema, hash del PIN | Tabla `Setting` | Preferencias |

No se guardan nombres de personas, documentos de identidad, teléfonos ni correos. La placa, aun así, puede identificar a una persona, por eso se trata como dato personal.

## Dónde

Todo queda en un archivo SQLite local: `%APPDATA%\ParkEasy\parkeasy.db` en Windows. Las copias de seguridad que crea la app están en `%APPDATA%\ParkEasy\copias` y las que guarda el usuario van donde él elija.

## Qué no hace la app

- No tiene telemetría ni analítica.
- No hace llamadas de red (no hay código de red en el proyecto; se puede comprobar con una búsqueda de `java.net` o `http` en `src/main`).
- No envía los datos a ningún servidor.

## Cuánto tiempo se conservan

La app no borra nada sola. El administrador decide: en **Ajustes → Privacidad y registros antiguos** puede borrar los registros cerrados anteriores a una fecha. Antes de borrar se muestra cuántos son y se pide confirmación. El borrado es real (no queda marcado como borrado) y no toca los vehículos que siguen dentro. Las copias de seguridad ya creadas conservan lo que tenían: hay que borrarlas aparte.

## Límites del PIN

El PIN (4 a 8 dígitos, guardado como hash con sal) solo impide que alguien use la app sin permiso. **No cifra la base de datos**: quien tenga acceso al archivo `.db` puede leerlo con otra herramienta. Para protegerlo de verdad hace falta cifrar el disco (BitLocker) o la carpeta del usuario.

## Ley 1581 de 2012 (Colombia)

La ley de protección de datos personales puede aplicar al parqueadero como responsable del tratamiento. Puntos que conviene revisar con un abogado, porque dependen del caso:

- Si hace falta una autorización del titular para recoger la placa y cómo se comunica (aviso en la entrada, por ejemplo).
- La política de tratamiento y el tiempo de conservación que se va a declarar.
- Si el parqueadero debe inscribir su base de datos en el Registro Nacional de Bases de Datos.
- Cómo atender solicitudes de consulta o supresión: la app permite buscar por placa en el historial, pero el borrado de una sola persona hoy se hace por fecha, no por placa.

## Datos de prueba

Los perfiles `demo` y `perf` usan sus propias bases (`parkeasy-demo.db`, `parkeasy-perf.db`) y solo placas ficticias con prefijo `TST` (o `INS` en rendimiento). Cada registro lleva la marca `TEST_DATA` en observaciones. Nunca se mezclan con la base de producción.
