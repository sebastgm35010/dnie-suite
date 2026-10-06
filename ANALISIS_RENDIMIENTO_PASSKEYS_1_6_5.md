# Prueba incremental y ajustes · 1.6.5

Se compararon eventos completos (id, tiempo, categoría, evento y campos) del diagnóstico 3 frente al diagnóstico 2. Coinciden los 649 eventos anteriores; quedan 183 eventos nuevos. Se excluyeron esos antecedentes al calcular los tiempos. Dos eventos nuevos corresponden al cierre previo de la pantalla de diagnóstico, antes del proceso 1.6.4: tampoco se consideran parte del inicio de sesión.

## Evidencia de la prueba nueva

La solicitud de Google finaliza mediante `continuation_consumed`. Solo hay un `awaiting_card` y un `card_detected` en el recorrido nuevo, sin otro VERIFY en la continuación. No hay fallos ni crashes registrados.

| Intervalo | Tiempo |
| --- | --- |
| Validación inicial, desde validation_started hasta validation_accepted | 1234 ms |
| origin_check inicial hasta aceptación (incluye verificación DAL y trabajo posterior) | 877 ms |
| Tarjeta detectada hasta publicación de cuentas | 1580 ms |
| Lectura certificado hasta PIN verificado | 137 ms |
| PIN verificado hasta catálogo abierto | 237 ms |
| Solicitud final, desde validation_started hasta continuation_consumed | 429 ms |
| Verificación final desde origin_check hasta aceptación | 165 ms |
| Aceptación final hasta continuation_consumed | 260 ms |

El intervalo entre publicar las cuentas y abrir la solicitud final incluye selección del usuario y tránsito por el selector Android; no se atribuye íntegramente al motor. Los eventos existentes tampoco permiten separar coste criptográfico, preparación de respuesta y planificación de hilos dentro de los últimos 260 ms.

Se registran 37 reportes de fotogramas de al menos 48 ms, con máximo 376 ms. Aparecen después del inicio de sesión, al abrir MainActivity y entrar a bóveda. La operación de preparación de bóveda dura 195 ms, pero eso no demuestra que haya bloqueado el hilo principal: su IO se ejecuta fuera de él. Algunos reportes comparten tiempo y duración; sin identificador de fotograma no se puede determinar cuántos eran duplicados. La versión anterior solo medía MainActivity, por lo que no permite cuantificar los tirones de la ventana de passkeys.

## Cambios

- La continuación muestra un estado mínimo mientras valida y firma; no compone formularios CAN/PIN ni las tarjetas de cuentas antes de saber que necesita una nueva autorización.
- No activa Reader Mode al abrir una continuación con token. Si hace falta volver a presentar el DNI, el botón de autorización habilita NFC. Se conserva el cierre del canal y la cancelación al pausar.
- Si la ventana se pausa durante la validación o firma automática, al regresar reinicia la validación; una autorización revocada vuelve al flujo DNI en lugar de dejar la pantalla esperando indefinidamente.
- Caché positiva DAL de hasta 60 segundos, en memoria y con máximo 32 decisiones. Clave exacta RP/paquete/certificado firmante actual; las lecturas no prolongan la caducidad. No se guardan fallos y la limpieza de Credential Manager la vacía. La identidad del navegador y sus hashes siguen comprobándose en cada solicitud. Las decisiones DAL pueden reutilizarse dentro de ese breve plazo; después se consulta de nuevo la delegación HTTPS.
- La paleta Compose deja de reconstruirse por cambios de configuración ajenos a la selección de colores, como dimensiones del teclado. Conserva claves explícitas de contexto, color dinámico y modo oscuro.
- El saneamiento de eventos reutiliza su expresión regular, evitando compilarla por cada evento.
- Medición de fotogramas también en PasskeyCeremonyActivity. Cada reporte incluye vsync, primer dibujo y tiempos de layout/dibujo/sincronización/entrada/espera desconocida; se suprime la repetición del mismo vsync dentro de una ventana. Esto permitirá distinguir el siguiente cuello de botella.

Estos cambios reducen trabajo redundante; la mejora percibida debe medirse con el APK nuevo. No se afirma que todos los tirones estén resueltos. La sesión segura del DNI, la verificación de PIN, el formato de passkeys y la política de bóveda no se modifican.

## Validación

110 pruebas aprobadas de los módulos afectados (diagnósticos 3, passkeys JVM 42, adaptador Android 11 y app 54). Tres pruebas nuevas comprueban caducidad sin extensión por lectura, aislamiento por RP/paquete/firmante y límite de memoria de la caché DAL. El diagnóstico personal completo no se copia al repositorio.

También pasaron las 12 comprobaciones WebAuthn independientes. La instalación está pendiente: ADB no lista dispositivos ni servicios mDNS y la reconexión al último puerto del teléfono fue rechazada. No se ejecutó una prueba física del rendimiento de esta versión.

APK: `app/build/outputs/apk/evolution/dnie-evolution-debug.apk`, versión 29 / `1.6.5-passkeys-performance`. Repetir un inicio de sesión y abrir/desplazar la bóveda permite comparar el siguiente registro. No es necesario borrar el historial: puede aislarse por versión, id y tiempos.
