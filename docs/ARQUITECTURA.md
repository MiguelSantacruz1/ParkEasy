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

## Paquete `plan` (Etapas 5 y 6)

```
plan/model          Geometry (Box, PathGeometry), PlanItem, PlanDocument, PlanJson
plan/ops            PlanOps (mover, girar, redimensionar, puntos, apilado, borrar, copiar, hilera,
                    zonas techadas, clics), ItemGeometry, Handles, History
plan/PlanPersistence  une el plano con las tablas Spot y PlanElement
presentation/plan   PlanViewModel, PlanRenderer, PlanCanvas, EditorScreen, HomeScreen
```

- El plano es inmutable: cada operación devuelve un `PlanDocument` nuevo. Deshacer y rehacer solo guardan y recuperan planos (`History`, 100 pasos).
- Las medidas están en metros y el giro en grados alrededor del centro. El lienzo convierte píxeles a metros con el `Viewport`.
- `plan/model` usa el enum `SpotType` del dominio; es la única dependencia de `plan` hacia `domain`.
- Los puestos viven en `Spot` para que Puestos, Registros y el plano operativo vean lo mismo. El resto de elementos va en `PlanElement` como JSON con versión.
- Guardar un plano es una sola transacción: borra los puestos quitados (no los ocupados), reactiva los que vuelven por «deshacer», crea los nuevos y reemplaza los elementos del piso.
- El estado de cada puesto en el plano operativo sale de `Spot` + registros abiertos, igual que en la lista.

## Esquema de la base (versión 3)

| Tabla | Contenido |
|---|---|
| `Setting` | Ajustes clave-valor |
| `Floor` | Pisos y tamaño del lienzo |
| `Spot` | Puestos: geometría en el plano, bloqueo, borrado lógico |
| `ParkingRecord` | Entradas y salidas, con copia del puesto, piso y tarifa |
| `ChangeLog` | Bitácora |
| `Tariff` | Una tarifa por tipo de vehículo |
| `PlanElement` | Vías, paredes, rampas, zonas techadas, textos…: JSON por elemento y piso |

## Estado actual (Etapa 6)

Funcionan los pisos, los puestos, los registros con cobro, los ajustes, el editor del plano y el plano operativo en Inicio. Falta el historial/bitácora como pantalla, la exportación, las copias de seguridad, el tema oscuro seleccionable y el empaquetado.
