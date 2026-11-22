# Plan de PARKEASY

Presupuesto de commits: **mínimo 150**, sin máximo fijo, un cambio lógico por commit y sin relleno. Los commits van solo con la identidad de Git del dueño del repositorio.

## Estado por etapa

| Etapa | Alcance | Commits previstos | Estado |
|---|---|---|---|
| 1 | Arquitectura y estructura del proyecto | 22 | Terminada, pendiente de confirmación |
| 2 | Modelo de datos y reglas | 14 | Pendiente |
| 3 | Pisos y puestos | 16 | Pendiente |
| 4 | Registro, cobro y bloqueos | 22 | Pendiente |
| 5 | Motor y editor del plano | 34 | Pendiente |
| 6 | Plano operativo | 12 | Pendiente |
| 7 | Historial, exportación y copias | 12 | Pendiente |
| 8 | Ajustes, tema, atajos y pulido | 11 | Pendiente |
| 9 | Pruebas, rendimiento y privacidad | 8 | Pendiente |
| 10 | Empaquetado y entrega | 5 | Pendiente |

Total previsto: 156.

## Etapa 1: criterio de aceptación

- [x] La app compila y abre una ventana de 960×640 como mínimo.
- [x] Koin inicializado.
- [x] Navegación base con las siete pantallas.
- [x] Base SQLite inicializada con migraciones (probado con 6 pruebas unitarias).
- [x] README, .gitignore, PLAN.md y docs/DECISIONES.md.
- [x] Arranque real verificado en Windows 11: abre la ventana PARKEASY y crea parkeasy.db en %APPDATA%\ParkEasy. La interfaz no se revisó visualmente.

## Registro

- Historial anterior del repositorio (versión de reservas) reemplazado por un historial nuevo el 22 de noviembre de 2026, por pedido del dueño.
