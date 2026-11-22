# Decisiones técnicas

Cada entrada dice qué se decidió y por qué. Las nuevas van al final.

## D1. Compose Multiplatform Desktop y no JavaFX ni Swing

El plano necesita un lienzo con zoom, rotación y curvas, y el resto de la app es formularios y listas. Compose resuelve las dos cosas con el mismo modelo de estado, y Material 3 trae componentes y temas claro y oscuro. JavaFX tiene un buen lienzo, pero obliga a mezclar otro modelo de UI y pierde la comodidad de Kotlin. Swing se ve anticuado y tiene un lienzo más limitado.

## D2. SQLDelight para la base SQLite

Genera código Kotlin tipado a partir de SQL, tiene soporte probado en JVM y maneja migraciones con archivos `.sqm` y `PRAGMA user_version`. Room multiplataforma es más nuevo y tiene menos recorrido en escritorio. Versiones: SQLDelight 2.4.1, dialecto SQLite 3.38.

## D3. Versiones fijadas el 22 de noviembre de 2026

Gradle 9.9.2, Kotlin 2.4.21, Compose Multiplatform 1.12.2, Koin 4.2.2, kotlinx.coroutines 1.10.2, kotlinx.serialization 1.12.0. Se eligió la última versión estable de cada una en Maven Central. JDK de compilación: 17, que es el instalado y el que usa el toolchain de Gradle. Si Compose Desktop pide un JDK mayor para el instalador, se revisa en la Etapa 10.

## D4. Un solo módulo Gradle, paquetes separados

El proyecto es una app de escritorio con un equipo de una persona. Varios módulos agregarían configuración sin beneficio hoy. Los paquetes `presentation`, `domain`, `data` y `plan` se mantienen sin dependencias cruzadas indebidas. El motor del plano (`plan`) no importa nada de Compose, así que se puede mover a su propio módulo si hace falta.

## D5. Navegación propia

La app tiene siete pantallas de primer nivel, sin pila de retroceso ni argumentos. Un `Navigator` con un `StateFlow<Screen>` cubre eso con 15 líneas. Las librerías de navegación de Compose para escritorio aún cambian de API entre versiones, y no aportan nada a este caso. Si más adelante aparecen pantallas anidadas, se reevalúa.

## D6. Textos en recursos de Compose

Los textos viven en `src/main/composeResources/values/strings.xml` y se leen con `stringResource`. La clase generada está en `com.ejemplo.parkeasy.resources`. Los atajos `compose.material3` y `compose.components.resources` del plugin aparecen como obsoletos y compilan con aviso. Se dejan así hasta que el plugin publique el reemplazo estable.

## D7. Carpeta de datos del usuario

La base `parkeasy.db` se guarda en `%APPDATA%\ParkEasy` en Windows, `~/Library/Application Support/ParkEasy` en macOS y `$XDG_DATA_HOME/ParkEasy` (o `~/.local/share/ParkEasy`) en Linux. Está en `AppDirectories` y tiene pruebas.

## D8. El documento de instrucciones no se versiona

`Prompt maestro - PARKEASY (administrador, escritorio).md` está en `.gitignore`. Es el encargo, no parte del código.

## D9. El estado del puesto no se guarda

La tabla `Spot` solo guarda si el puesto está bloqueado. OCUPADO se calcula con un `LEFT JOIN` contra el registro abierto (`exitTime IS NULL`). Así el estado y el registro no pueden discrepar. `Spot.status` en el dominio resuelve las tres posibilidades: bloqueado, ocupado o libre. Bloquear exige que el puesto esté libre, así que los dos nunca coinciden.

## D10. Reglas en dos capas

Las reglas viven en los servicios (`SpotService`, `FloorService`, `ParkingService`), que dan errores claros (`DomainError`) para mostrar en la interfaz. Además, la base tiene índices únicos parciales como red de seguridad:

- `Spot(label)` único entre puestos no eliminados.
- `ParkingRecord(spotId)` único mientras no tenga salida.
- `ParkingRecord(plate)` único mientras no tenga salida.

Si una regla del servicio tuviera un hueco, la base rechaza el dato igual. Hay pruebas para ambas capas.

## D11. Borrado lógico y copias en el historial

Pisos y puestos tienen una columna `deleted`. El registro guarda `spotLabel` y `floorName` como copia, así el historial sigue legible aunque el puesto o el piso ya no existan. Una etiqueta de un puesto eliminado se puede volver a usar.

## D12. Horas como milisegundos UTC

Las horas se guardan como `INTEGER` (milisegundos desde 1970 en UTC) y se manejan como `java.time.Instant`. La zona America/Bogota se aplica solo al mostrar. Los servicios reciben un `java.time.Clock`, lo que permite probar con un reloj controlado.

## D13. Compatibilidad entre vehículo y puesto

Un carro cabe en puestos de carro, de discapacitados y otros. Una moto, en puestos de moto y otros. El documento pide compatibilidad sin definir la tabla; esta es la interpretación elegida y vive en `SpotType.accepts`.

## D14. Tarifas iniciales al abrir la base

Las tarifas de carro (4.000 + 1.500) y moto (2.500 + 1.000) se insertan con `INSERT OR IGNORE` cada vez que se abre la base. Funciona igual para una base nueva y para una migrada, y no pisa tarifas que el administrador ya cambió.

## D15. PlanElement llega en la Etapa 5

El plano necesita un modelo de geometría que todavía no existe. Se agrega con su propia migración (`2.sqm`) en la Etapa 5, lo que además ejercita el mecanismo de migraciones con datos reales.

## D17. Textos de pantalla en el código, por ahora

El documento pide los textos en recursos. Las pantallas nuevas (pisos, puestos, registros, ajustes) tienen sus textos en español directamente en el código para avanzar más rápido. Solo la navegación usa recursos. En la etapa de pulido se pasan todos a `strings.xml`. Es una deuda conocida.

## D18. Estados de pantalla propios

Los estados (`FloorsViewModel`, `SpotsViewModel`, `RecordsViewModel`, `SettingsViewModel`) son clases con `StateFlow` y un alcance de corrutinas propio, registradas como singletons en Koin. No usan la librería de ViewModel multiplataforma: la app tiene una sola ventana y estas clases viven mientras la app esté abierta. Los diálogos de entrada, salida, corrección y traslado viven en `RecordsViewModel` para poder abrirlos desde cualquier pantalla.

## D19. Etapas 3 y 4 juntas

Por pedido del dueño se juntaron las etapas 3 y 4 y se pasó a trabajar por bloques: 3+4, 5+6, 7+8, 9+10. El reporte y la confirmación se hacen al final de cada bloque.

## D20. Compilación sin incremental

En esta máquina Windows el compilador incremental de Kotlin falla de vez en cuando por archivos que desaparecen o quedan bloqueados en `build/`. `gradle.properties` compila dentro de Gradle y sin modo incremental. Es un poco más lento, pero estable.

## D16. Booleanos como INTEGER

`INTEGER AS Boolean` no compila con SQLDelight 2.4.1 en este proyecto (genera un import inexistente). Las columnas son `INTEGER` 0 o 1 y los repositorios convierten.
