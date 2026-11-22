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

## Estado actual (Etapa 1)

Existen la ventana, el tema, la navegación con siete pantallas vacías, Koin y la base con la tabla `Setting`. Los paquetes `domain` y `plan` se crean en las etapas 2 y 5.
