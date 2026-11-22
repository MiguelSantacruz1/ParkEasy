# PARKEASY

Aplicación de escritorio para administrar un parqueadero: pisos y puestos, plano editable, entrada y salida de vehículos con cobro por tiempo, historial con filtros, exportación y copias de seguridad.

- Kotlin + Compose Multiplatform Desktop (JVM), Material 3
- Datos locales en SQLite (SQLDelight), sin servidor ni internet
- Un administrador y un parqueadero por instalación
- Moneda COP, zona horaria America/Bogota, interfaz en español

## Qué hace

- **Inicio**: plano del piso con el estado de cada puesto (○ libre, ● ocupado, ⊘ bloqueado). Clic en un puesto para registrar entrada o salida.
- **Editor del plano**: puestos, vías, rampas curvas, columnas, zonas techadas, entradas y salidas, textos. Deshacer y rehacer, copiar y pegar, alinear, guardado automático.
- **Registros**: entrada, salida con cobro, corrección, cambio de puesto, bloqueo de puestos.
- **Historial**: filtros por placa, fechas, piso, tipo y estado; totales; exportar a CSV.
- **Exportar el plano** a PNG y PDF.
- **Ajustes**: tarifas, tema claro/oscuro, PIN, copias de seguridad, borrado de registros antiguos, atajos.

## Requisitos

- Windows 10 u 11
- JDK 17 o superior para compilar y correr desde el código (el instalador ya trae su propio runtime)

## Comandos

Abrir la app:

```powershell
.\gradlew.bat run
```

Correr las pruebas:

```powershell
.\gradlew.bat test
```

Abrir con datos de demostración (2 pisos, vehículos y historial ficticios, base aparte):

```powershell
.\gradlew.bat run -Pdemo
```

Abrir con el perfil de rendimiento (500 puestos, 3000 registros cerrados, 150 vehículos dentro):

```powershell
.\gradlew.bat run -Pperf
```

Los perfiles de prueba usan `parkeasy-demo.db` y `parkeasy-perf.db`; nunca tocan la base real. Muestran un aviso «DATOS DE DEMOSTRACIÓN» y un botón para restablecerlos. `-Dparkeasy.dataDir=<carpeta>` cambia dónde se guardan los datos.

## Instalador

App lista para ejecutar, sin instalar (carpeta `build\compose\binaries\main\app\ParkEasy`):

```powershell
.\gradlew.bat createDistributable
```

Instalador MSI (`build\compose\binaries\main\msi\ParkEasy-1.0.0.msi`):

```powershell
.\gradlew.bat packageMsi
```

`packageExe` genera el `.exe` instalador equivalente. Ambos necesitan que el Gradle de Compose pueda usar WiX Toolset 3.x (en este equipo lo resolvió solo).

## Copias de seguridad

En **Ajustes → Copias de seguridad**: «Crear copia…» guarda un archivo `.db` con todos los datos; «Restaurar copia…» reemplaza todo por el contenido de un archivo. Antes de restaurar, la app guarda una copia de lo actual en `%APPDATA%\ParkEasy\copias`. Una copia de una versión anterior de la app se migra al restaurarla.

## Datos y privacidad

La base `parkeasy.db` está en `%APPDATA%\ParkEasy`. La app no hace llamadas de red ni envía telemetría. Detalle, conservación de datos y notas sobre la Ley 1581 de 2012 en [docs/PRIVACIDAD.md](docs/PRIVACIDAD.md).

## Documentación

- [PLAN.md](PLAN.md): etapas y estado
- [docs/ARQUITECTURA.md](docs/ARQUITECTURA.md): capas y flujo de arranque
- [docs/DECISIONES.md](docs/DECISIONES.md): decisiones técnicas y su motivo
- [docs/COBRO.md](docs/COBRO.md): cómo se calcula el cobro
- [docs/ATAJOS.md](docs/ATAJOS.md): atajos y accesibilidad
- [docs/PRIVACIDAD.md](docs/PRIVACIDAD.md): datos personales

## Límites conocidos

- Los textos de la interfaz están en el código, no en un archivo de recursos de idioma (solo español).
- El PIN no cifra la base de datos.
- Compatibilidad con lectores de pantalla no verificada: el plano es un canvas.
- El instalador MSI se generó pero no se probó instalándolo en un equipo limpio.
- Solo probado en Windows 11; la ventana se comprobó abriendo la app, no recorriendo cada pantalla a mano.
