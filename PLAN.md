# Plan de PARKEASY

Presupuesto de commits: **mínimo 150**, sin máximo fijo, un cambio lógico por commit y sin relleno. Los commits van solo con la identidad de Git del dueño del repositorio.

## Estado por etapa

| Etapa | Alcance | Commits previstos | Estado |
|---|---|---|---|
| 1 | Arquitectura y estructura del proyecto | 22 | Terminada |
| 2 | Modelo de datos y reglas | 30 | Terminada, pendiente de confirmación |
| 3 | Pisos y puestos | 16 | Pendiente |
| 4 | Registro, cobro y bloqueos | 22 | Pendiente |
| 5 | Motor y editor del plano | 34 | Pendiente |
| 6 | Plano operativo | 12 | Pendiente |
| 7 | Historial, exportación y copias | 12 | Pendiente |
| 8 | Ajustes, tema, atajos y pulido | 11 | Pendiente |
| 9 | Pruebas, rendimiento y privacidad | 8 | Pendiente |
| 10 | Empaquetado y entrega | 5 | Pendiente |

Total previsto: 172. La Etapa 2 usó más commits de los previstos (cada repositorio, servicio y archivo de pruebas va aparte), así que las siguientes bajan un poco; el mínimo de 150 sigue en pie.

## Etapa 1: criterio de aceptación

- [x] La app compila y abre una ventana de 960×640 como mínimo.
- [x] Koin inicializado.
- [x] Navegación base con las siete pantallas.
- [x] Base SQLite inicializada con migraciones (probado con 6 pruebas unitarias).
- [x] README, .gitignore, PLAN.md y docs/DECISIONES.md.
- [x] Arranque real verificado en Windows 11: abre la ventana PARKEASY y crea parkeasy.db en %APPDATA%\ParkEasy. La interfaz no se revisó visualmente.

## Etapa 2: criterio de aceptación

- [x] Entidades: Floor, Spot, ParkingRecord, ChangeLog, Tariff y Setting. PlanElement pasa a la Etapa 5 (decisión D15).
- [x] Repositorios con interfaz en `domain` e implementación SQLite en `data`.
- [x] Migración 1 → 2 con prueba: conserva datos, deja el mismo esquema que una base nueva y se verificó con la base real de la Etapa 1.
- [x] Reglas de integridad con pruebas: ocupado solo con registro abierto, bloqueado no admite entradas, placa no duplicada, salida no anterior a la llegada, llegada no futura, puesto ocupado no se elimina, borrado lógico con historial, etiquetas únicas.
- [x] 71 pruebas en verde.
- [ ] La numeración automática de etiquetas y el resumen de ocupación se hacen en la Etapa 3, donde se usan.

## Registro

- Historial anterior del repositorio (versión de reservas) reemplazado por un historial nuevo el 22 de noviembre de 2026, por pedido del dueño.
