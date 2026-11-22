# Arquitectura

PARKEASY es una app de escritorio local. Un solo proceso, una base SQLite, sin red.

```
┌───────────────────────────────────────────────┐
│ presentation   pantallas Compose, ViewModels, │
│                navegación, tema               │
└───────────────┬───────────────────────────────┘
                │ usa
┌───────────────▼───────────────────────────────┐
│ domain         modelos, reglas de integridad, │
│                cobro, validador de placas,    │
│                interfaces de repositorio      │
└───────────────▲───────────────────────────────┘
                │ implementa
┌───────────────┴───────────────────────────────┐
│ data           repositorios, SQLDelight,      │
│                migraciones, copias            │
└───────────────────────────────────────────────┘

┌───────────────────────────────────────────────┐
│ plan           motor del plano: geometría,    │
│                selección, transformaciones,   │
│                deshacer y rehacer. No depende │
│                de Compose ni de la base       │
└───────────────────────────────────────────────┘

di/  Koin: arma las dependencias al arrancar
```

## Reglas

- `domain` no conoce a `data` ni a `presentation`. Define las interfaces de repositorio y `data` las implementa.
- `presentation` habla con `domain`. Los ViewModels exponen `StateFlow` y reciben eventos.
- `plan` es Kotlin puro. El lienzo de Compose solo dibuja lo que `plan` calcula.
- La lógica crítica (cobro, placas, integridad, plano) vive en clases puras con pruebas unitarias.

## Flujo de arranque

1. `main()` abre la ventana (mínimo 960×640).
2. `KoinApplication` carga los módulos de `di/`.
3. Al pedir la base por primera vez, `DatabaseFactory` abre `parkeasy.db`, crea el esquema o aplica migraciones.
4. `AppShell` dibuja la barra lateral y la pantalla actual según `Navigator`.

## Paquetes de `domain` y `data` (Etapa 2)

```
domain/model        Floor, Spot, ParkingRecord, Tariff, ChangeLogEntry y enums
domain/repository   interfaces: FloorRepository, SpotRepository, ParkingRecordRepository,
                    ChangeLogRepository, TariffRepository, Transactor
domain/service      SpotService, FloorService, ParkingService, ChangeLogger
domain/DomainError  errores de las reglas de integridad
data/db             esquema SQLDelight (.sq), migraciones (.sqm), DatabaseFactory
data/repository     implementaciones Sql* de cada interfaz
```

Los servicios corren cada operación dentro de `Transactor.run`, así que un fallo a mitad de camino no deja datos a medias.

## Esquema de la base (versión 2)

| Tabla | Contenido |
|---|---|
| `Setting` | Ajustes clave-valor |
| `Floor` | Pisos y tamaño del lienzo |
| `Spot` | Puestos: geometría en el plano, bloqueo, borrado lógico |
| `ParkingRecord` | Entradas y salidas, con copia del puesto, piso y tarifa |
| `ChangeLog` | Bitácora |
| `Tariff` | Una tarifa por tipo de vehículo |

## Estado actual (Etapa 2)

Existen la ventana, el tema, la navegación con siete pantallas vacías, Koin y la capa de datos con sus reglas. Las pantallas todavía no usan los servicios. El paquete `plan` se crea en la Etapa 5.
