# PARKEASY

Aplicación de escritorio para administrar un parqueadero: configurar pisos y puestos, diseñar el plano, registrar la entrada y salida de vehículos, calcular el cobro por tiempo y ver qué puesto está libre, ocupado o bloqueado.

Estado: en desarrollo, Etapa 1 terminada. El avance está en [PLAN.md](PLAN.md).

## Resumen

- Kotlin + Compose Multiplatform Desktop (JVM)
- Datos locales en SQLite, sin servidor ni internet
- Un solo administrador y un solo parqueadero por instalación
- Moneda COP, zona horaria America/Bogota, interfaz en español

## Requisitos

- JDK 17 o superior
- Windows 10 u 11 (objetivo principal)

## Comandos

Abrir la app:

```powershell
.\gradlew.bat run
```

Correr las pruebas:

```powershell
.\gradlew.bat test
```

## Documentación

- [PLAN.md](PLAN.md): etapas y estado
- [docs/DECISIONES.md](docs/DECISIONES.md): decisiones técnicas y su motivo
- [docs/ARQUITECTURA.md](docs/ARQUITECTURA.md): capas y flujo de arranque

## Datos

La base `parkeasy.db` se guarda en la carpeta de datos del usuario (en Windows, `%APPDATA%\ParkEasy`). La app no hace llamadas de red.
