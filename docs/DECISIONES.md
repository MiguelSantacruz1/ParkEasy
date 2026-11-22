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
