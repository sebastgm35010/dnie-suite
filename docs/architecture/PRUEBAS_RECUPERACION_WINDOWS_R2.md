# Recuperación: ampliación de escenarios Windows R2

2026-10-07. Checkpoint previo `checkpoint/pre-vault-recovery-expanded-r2`, sobre `7a3ff0b`. Prototipo JVM aislado; no integrado en Android/Suite.

Resultado: **23 pruebas ejecutadas, cero fallos, errores u omisiones**: 12 anteriores y 11 nuevas. JAR compilado. Comando final: `gradlew.bat :dnie-vault-recovery:test :dnie-vault-recovery:jar --console=plain`. En la primera ejecución también se consultó `dnie-vault-core:test`: 26 pruebas sin fallos, resultado reutilizado por Gradle, fuentes intactas.

## Escenarios añadidos

| Escenario | Resultado verificado |
| --- | --- |
| Staging ausente o archivo en lugar de directorio | Rechazo, cierre de entrada y ninguna publicación |
| Cancelación antes de acceder a la privada | No se invoca proveedor/operación de tarjeta; entrada cerrada |
| Destino falla tras empezar a leer | Error original propagado, callback una sola vez, streams cerrados, staging retirado; nuevo intento explícito funciona |
| Cancelación con plaintext ya escrito | Prueba observa progreso real antes de cancelar; no publicación y staging vacío |
| Proveedor de entrada desconectado | IOException inyectada durante lectura, entrada cerrada, staging retirado |
| Proveedor de secreto ausente o secreto incorrecto | Rechazo, entrada cerrada y buffer entregado por proveedor borrado |
| Archivo vacío y límites de segmento | Contenido exacto para 0, 1, 1048575, 1048576, 1048577 y 8388621 bytes |
| Dos restauraciones concurrentes | Archivos diferentes recuperados exactamente en mismo directorio, sin mezcla ni temporales sobrantes |
| Paquete máximo | 1000 entradas y 112108 bytes serializados/reabiertos; exceso rechazado |
| Cuerpos malformados con GCM válido | Rechazo de contador cero, negativo, excesivo, longitud incoherente y UUID duplicados |
| Cancelación al abrir paquete | Rechazo después de KDF, durante lectura de entradas y antes de devolver paquete |
| Sal/nonce y contraseña | Dos paquetes usan sal/nonce diferentes; no alteran buffers del llamador; rechazan contraseña corta, excesiva y NUL |

La falla del destino se simula en su callback (por ejemplo disco lleno); no se llenó el disco real. La desconexión del proveedor también es inyectada. No se simularon muerte forzada de proceso, cortes de energía ni fallo real de `fsync`. El máximo de entradas usa fixtures sintéticos del formato: no representa 1000 operaciones de DNI.

## Correcciones del motor

`VaultRecoveryService.restore` ahora adquiere propiedad de la entrada desde el inicio: también la cierra si falla la validación o creación del staging. Antes quedaba abierta si el directorio no existía. `prepare` comprueba cancelación antes de leer cabecera y de solicitar el secreto, evitando una operación de tarjeta innecesaria si ya fue cancelado.

La cancelación de Argon2 sigue cooperativa entre etapas: no interrumpe una derivación en curso. Los adaptadores deben ejecutarla fuera del hilo UI. La publicación final sigue siendo responsabilidad del adaptador; que el callback falle puede dejar un destino parcial si ese adaptador no usa publicación atómica. El motor retira su staging y no repite automáticamente la operación.

Pendientes: Android, cruces entre plataformas, prueba física CIF hard, RAM/latencia en móvil, limpieza tras muerte de proceso y publicación transaccional. Las pruebas concurrentes no autorizan usar `RecoveryEntry.close()` simultáneamente con una restauración: el llamador debe mantener la entrada viva hasta que terminen sus operaciones.
