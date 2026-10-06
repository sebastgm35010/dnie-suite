# Validación física inicial y mejora de fluidez

Fecha de revisión: 2026-10-04. Nueva versión: **1.3.1-vault-lab**, código 15.

## Evidencia recibida

El usuario reportó resultado positivo de la prueba y lentitud percibida. El log contiene un sondeo de certificados anterior y una sesión nueva de bóveda; el intervalo entre ambas acciones no representa tiempo de ejecución de la bóveda.

En la sesión de bóveda: PACE IAS/PKI autenticado, lectura CE83, PIN verificado y MSE/PSO DECIPHER completados con SW 9000. La respuesta privada permanece oculta en la consola. El resultado positivo de la UI corresponde además a comparación del secreto y apertura/verificación de contenido AES-GCM en el motor.

| Tramo observado en el log | Tiempo aproximado |
|---|---:|
| Primer SELECT IAS hasta PKI protegido | 398 ms |
| Selección, lectura y parseo del certificado de cifrado | 706 ms |
| VERIFY PIN | 66 ms |
| MSE de descifrado | 29 ms |
| PSO DECIPHER extendido, envoltura/transporte incluidos | 279 ms |
| Primer SELECT IAS hasta respuesta PSO | 1.568 s |

Son tiempos de esta ejecución; no constituyen benchmark ni medición de frames de UI. La lectura del certificado emplea 11 bloques y explica parte importante de la espera. El análisis conserva el perfil de comandos validado; no aumenta bloques, omite MAC o cachea credenciales para acelerar.

## Cambios aplicados

- La construcción de `IsoDepCardChannel` ya no llama a `connect()` en el hilo de la Activity. Conexión y configuración del timeout se aplazan al primer `transceive`, ejecutado por los consumidores existentes en IO. Cerrar la sesión impide reconectar.
- La consola procesa su bus en un dispatcher de fondo. Se eliminan sonidos/vibraciones por cada RX, que antes también reaccionaban a logs de otras funciones. El sondeo conserva feedback por operación y cierra su transceptor al terminar.
- El historial de UI tiene límite de 1000 entradas, evitando crecimiento indefinido y copias cada vez más grandes.
- APDU comando/respuesta usan codificación hexadecimal mediante tabla, sin crear un Formatter por byte. Conserva mayúsculas y separadores del formato anterior.

No se modifican la secuencia IAS/PKI/PACE, el formato de descifrado, SSC, MAC, PIN ni el motor sintético. La fluidez en el teléfono necesita comprobación con esta nueva versión; no se afirma una reducción medida de latencia después del cambio.

## Alcance de la validación física

La evidencia acredita la operación de laboratorio en esta tarjeta y ejecución. Todavía falta terminar/revisar cifrado de archivos, contenedor persistente, política de recuperación y passkeys. El perfil RSA PKCS#1 v1.5 continúa siendo una decisión que debe evaluarse para producción.

No se añaden logs personales al repositorio y no se instala ni controla el dispositivo durante esta revisión. APK exportado en `app/build/outputs/apk/evolution/dnie-evolution-debug.apk`; pruebas y hash se registran al finalizar la compilación.

## Resultado de compilación

APK 1.3.1 exportado y firma v2 verificada. SHA-256: `1E55C258D1B4DB79F62C5ED2D49707D3187012C2585932AA5AFB18E54A9F31AF`.

205 pruebas aprobadas: núcleo 49, protocolo 61, bóveda 5, ICAO 27, PDF 20 y app 43. Las nuevas comprobaciones verifican formato hexadecimal equivalente para todos los valores de byte y separadores, historial acotado y ausencia de feedback por respuestas recibidas en segundo plano.

El APK 1.3.0 validado por el usuario quedó respaldado en `app/build/outputs/apk/checkpoints/dnie-evolution-1.3.0-vault-lab.apk`.
