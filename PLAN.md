# Plan de PARKEASY

Presupuesto de commits: **mínimo 150**, sin máximo fijo, un cambio lógico por commit y sin relleno. Los commits van solo con la identidad de Git del dueño del repositorio.

## Estado por etapa

| Etapa | Alcance | Commits previstos | Estado |
|---|---|---|---|
| 1 | Arquitectura y estructura del proyecto | 22 | Terminada |
| 2 | Modelo de datos y reglas | 30 | Terminada |
| 3 + 4 | Pisos, puestos, registro, cobro, bloqueos y bitácora | 31 | Terminada, pendiente de confirmación |
| 5 + 6 | Motor y editor del plano, plano operativo | 30 | Terminada, pendiente de confirmación |
| 7 + 8 | Historial, exportación, copias, ajustes, tema, atajos y pulido | 22 | Pendiente |
| 9 + 10 | Pruebas, rendimiento, privacidad, empaquetado y entrega | 15 | Pendiente |

Por pedido del dueño las etapas se juntaron en bloques para ir más rápido; se reporta al final de cada bloque. Llevamos más de 110 commits; el total previsto es 183, por encima del mínimo de 150.

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

## Etapas 3 y 4: criterio de aceptación

- [x] Pisos: agregar, renombrar, reordenar y eliminar, con confirmación que dice cuántos puestos se van y bloqueo si hay ocupados.
- [x] Puestos: agregar varios con numeración consecutiva, editar, eliminar con deshacer, filtrar por piso, tipo, estado y techado.
- [x] Resumen de ocupación en total y por piso.
- [x] Entrada con placa (validador con aviso), color, tipo, hora editable y observaciones.
- [x] Salida con duración y desglose del cobro antes de confirmar. Valores de referencia en pruebas: carro 61 min = 5.500, 3 h = 7.000; moto 61 min = 3.500, 3 h = 4.500.
- [x] Tarifas configurables en Ajustes; cada registro cerrado guarda su copia.
- [x] Corregir registros, cambiar de puesto sin perder la llegada, buscar por placa, marcar libre u ocupado, bloquear y desbloquear. Todo queda en la bitácora.
- [x] 122 pruebas en verde, incluida una prueba de interfaz que recorre el flujo completo con la ventana real.
- [ ] No verificado a mano: el aspecto visual de las pantallas, el recorrido con teclado y el cambio de tamaño de la ventana. La prueba de interfaz comprueba funciones, no estética.
- [ ] Pendiente: la bitácora no tiene pantalla todavía; se puede ver en la base. Textos aún en el código (D17).

## Etapas 5 y 6: criterio de aceptación

- [x] Motor del plano sin Compose, con pruebas: mover (con imán), girar, redimensionar con manijas (el lado opuesto queda fijo, también con giro), editar puntos de trazos, orden de apilado, candado, borrar respetando bloqueados y ocupados, copiar, hilera numerada, selección por clic y por recuadro, zonas techadas rectangulares, elípticas y poligonales.
- [x] Deshacer y rehacer de al menos 50 pasos (límite 100), probado con 60.
- [x] Guardado atómico del plano con migración a la versión 3: puestos nuevos, renombrados, movidos, borrados, restaurados con deshacer, y puestos ocupados protegidos.
- [x] Editor: herramientas (seleccionar, rectángulo, elipse, línea, polilínea, polígono, curva), paleta de 10 tipos de elemento, zoom con la rueda, desplazamiento con botón medio o derecho, cuadrícula, cotas, panel de propiedades, hilera, atajos (Ctrl+Z/Y/C/V/D/A, Supr, Esc, Enter, R, flechas) y autoguardado con estado visible.
- [x] Plano operativo en Inicio: pisos, resumen, búsqueda de placa con resaltado, clic en puesto libre → entrada, ocupado → salida, bloqueado → desbloquear.
- [x] 174 pruebas en verde, con 16 del editor desde el puntero hasta la base y una de interfaz que dibuja el plano real.
- [x] La app real abre (ventana PARKEASY, sin errores en la salida).
- [ ] No verificado a ojo: cómo se ve el dibujo del plano, el arrastre real con el ratón, el zoom con la rueda, los atajos con foco en el lienzo y la rampa o el texto dentro de campos. Las pruebas cubren la lógica, no el aspecto ni el manejo real del puntero.
- [ ] Pendiente: cotas entre elementos (solo hay cotas de tamaño), y las esquinas redondeadas de polígonos solo afectan al contorno.

## Registro

- Historial anterior del repositorio (versión de reservas) reemplazado por un historial nuevo el 22 de noviembre de 2026, por pedido del dueño.
