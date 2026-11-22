# Cobro

PARKEASY calcula y muestra el valor a cobrar. No procesa pagos: el cobro se hace en caja.

## Tarifas iniciales

| Vehículo | Primera hora | Cada hora adicional |
|---|---|---|
| Carro | 4.000 COP | 1.500 COP |
| Moto | 2.500 COP | 1.000 COP |

Se cambian en Ajustes. Cada tipo de vehículo tiene la suya, con sus minutos de gracia (0 por defecto).

## Regla

1. Se mide el tiempo entre la llegada y la salida, en segundos.
2. Se restan los minutos de gracia.
3. Se redondea hacia arriba a horas completas, con mínimo una hora. Todo vehículo paga al menos la primera hora.
4. Total = primera hora + (horas − 1) × hora adicional.

Ejemplos con la tarifa de carro:

| Tiempo | Horas cobradas | Total |
|---|---|---|
| 1 min | 1 | 4.000 |
| 60 min | 1 | 4.000 |
| 61 min | 2 | 5.500 |
| 3 h exactas | 3 | 7.000 |
| 2 días | 48 | 4.000 + 47 × 1.500 = 74.500 |

Con la tarifa de moto: 61 min = 3.500 y 3 h = 4.500.

Con 10 minutos de gracia, 70 minutos se cobran como 1 hora y 71 minutos como 2.

## Redondeo e impuestos

- El total es un número entero de pesos. Los valores no tienen decimales.
- Una fracción de hora, aunque sea de un segundo, cuenta como hora completa.
- Los valores no incluyen impuestos.

## Registros cerrados

Al registrar la salida, el registro guarda el valor cobrado y una copia de la tarifa usada (primera hora, hora adicional y gracia). Cambiar una tarifa después no altera esos registros.

Si se corrige la hora de llegada o de salida de un registro cerrado, el valor se recalcula con la tarifa guardada en ese mismo registro, y el cambio queda en la bitácora.

## Dónde está en el código

`ParkingPriceCalculator` es una función pura (`domain/rules`). `ParkingService.quote`, `checkOut` y `correct` la usan.
