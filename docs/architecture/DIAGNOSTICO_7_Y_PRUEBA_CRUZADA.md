# Diagnóstico 7 y prueba cruzada

Revisión del 2026-10-05. Se examinaron el JSONL del usuario, los registros del host integrado, los logs oficiales `.dcdelivery-2.0`, el código ya inspeccionado del middleware y la presencia PC/SC. Los registros personales completos permanecen fuera de Git.

## Resultado de la prueba real

El diagnóstico 7 contiene 219 eventos y no comparte registros idénticos con el diagnóstico 6. Incluye sesiones anteriores a la última prueba; se usa la última autorización como ancla. Las marcas de teléfono y host presentan una diferencia estimada de **740 ms**, que también incluye latencia de red; se conserva la hora original en el correlador.

| Etapa, horas de Bogotá | Evidencia |
| --- | --- |
| 22:00:12 | TLS/token aceptados y lector marcado con tarjeta presente. |
| 22:00:24 | Windows solicita power-off; Android lo confirma. |
| 22:00:57.901 | PC/SC solicita reset. |
| 22:00:58.562 aproximado | Android completa una nueva sesión PACE. |
| 22:00:58.844 | SELECT IAS v3 `9000`; el log oficial .NET confirma DNIe v3. |
| 22:00:59.242 | El lanzador .NET inicia Java. |
| 22:01:00.033 | Java devuelve el mensaje sobre conectar correctamente el DNI. |
| 22:01:19 | El usuario detiene la sesión; Windows recibe EOF y retira la tarjeta. |

La reapertura PACE fue real y el error web ocurrió antes del cierre manual. El tiempo adicional permite ver que esta ejecución superó la detección .NET y alcanzó Java; no demuestra por sí solo que toda la demora fuese procesamiento. No había captura DevTools activa en ese ensayo: no se puede reconstruir retrospectivamente el tráfico del navegador.

## Selección diferente entre .NET y Java

El componente .NET elige el primer lector de la lista. El `DnieComponent.connect` Java recorre `CARD_PRESENT` y conserva el último. Windows enumera:

1. DNIe Bridge Android NFC Private Reader 0.
2. Virtual Smart Card Architecture Virtual PCD Contactless 0.
3. Windows Hello for Business 1.

Windows Hello marca tarjeta presente aunque el DNI físico no esté conectado al puente. Los últimos sondeos v2/v3 del log Java devolvieron `0000`, mientras el SELECT v3 en nuestro lector devolvió `9000`. Después de ese SELECT no hay una segunda identificación ni SELECT P2=04 en el host del puente.

En la prueba cruzada, `ReaderMetadata` se ejecutó con el runtime oficial **`java.exe -client`** y consultó únicamente listas/presencia. Con nuestra tarjeta sintética presente, reprodujo el criterio: primer lector .NET = DNIe Bridge; último presente Java = Windows Hello. No conecta ni transmite a Windows Hello. Esto, unido al código y los logs, apunta fuertemente a selección del lector equivocado en el ensayo real; el log oficial no imprime el nombre elegido y por tanto la atribución histórica no es una captura directa de esa llamada.

## Prueba sintética conjunta ejecutada

Cadena real: **Edge headless aislado → endpoint local de prueba → winscard/controlador instalado → backend TLS → PinnedBridgeClient y ReadOnlyBridge del motor Kotlin**. `RestartablePaceCard` usa adaptadores NFC y establecimiento PACE explícitamente simulados; no se usó el DNI, la UI Android ni la web RENIEC para fabricar un resultado favorable.

La prueba pasó y registró:

- Nueve eventos DevTools de red/consola del navegador local.
- Presencia, selección de lectores, estados PC/SC y fases de backend.
- SELECT v2 rechazado con `6985` y SELECT v3 aceptado con `9000` usando bytes sintéticos en el lector propio.
- Warm reset que reconstruye el canal del motor Kotlin; cierre de los adaptadores anteriores.
- Reconocimiento v3 de la segunda fase si se dirige al lector correcto.
- SELECT Java posterior con P2=04 rechazado con `6985`: reproduce un límite del perfil, distinto de la elección de Windows Hello.
- Power-off, cierre y retirada del lector. La tarea SYSTEM temporal se elimina al finalizar.

Artefactos privados de la ejecución aprobada: `D:\DNIeToolchain\CrossLayerTests\52a701515a1443e8892df7c1ac71b76d`. Incluyen `edge.jsonl`, `controller.jsonl`, `mobile-engine.jsonl`, `java-readers.jsonl`, `timeline.jsonl` y resultados. La configuración de emparejamiento sintético permanece privada y vence; no distribuir esa carpeta completa.

Los ensayos iniciales detectaron errores del propio banco: vencimiento UTC con un formato distinto al aceptado y espera insuficiente de DevTools durante navegación. Se corrigieron antes de la ejecución aprobada. No se atribuyen esos fallos al DNI ni se cuentan como pruebas del middleware.

## Herramientas y validación

- `tools/host-diagnostics/Run-CrossLayerSynthetic.ps1`: repite la cadena; requiere la terminal administrativa tmux existente para el banco SYSTEM y los SDK ya instalados. Usa D: para el perfil Edge, clases Java y temporales privados.
- `cross-layer-browser.mjs`: DevTools solo para una pestaña aislada y un endpoint loopback sintético; cierra ese navegador al terminar.
- `ReaderMetadata.java`: metadatos PC/SC bajo Java, sin conexión a tarjetas.
- `pcsc-presence.ps1`: estado de lectores sin APDU ni bytes ATR.
- `correlate-bridge.py`: correlación del JSONL/host y fases oficiales, con desfase estimado explícito; resultados locales en `build/diagnostic7-correlation`.
- `watch-host.ps1` incorpora Java/javaw, dotnet, WUDFHost, j2pcsc y presencia de lectores. El muestreo cada dos segundos sigue pudiendo perder procesos breves; los logs oficiales complementan esa limitación.
- `Start-HostDiagnostics.ps1` guarda nuevos perfiles Edge en D:. El capturador mantiene exclusión de cookies, query, payload y argumentos personales.

Pasaron las dos pruebas de sanitización del colector, la prueba conjunta Kotlin y la comprobación de arquitectura. Se compila y valida el APK de la misma versión 1.8.2; no cambia su comportamiento ni se reinstala durante un ensayo activo.

## Cómo seguir

1. Obtener una selección de lector acotada al proceso Java y comprobar el nombre elegido. Evaluar un proveedor/selector PC/SC propio para el proceso de prueba antes de alterar dispositivos del sistema. No se deshabilitó ni reconfiguró Windows Hello durante esta revisión.
2. Probar nuevamente con captura DevTools, logs oficiales, estado de lectores y host integrado desde antes de abrir el flujo web.
3. Diseñar el perfil Java completo: después de P2=04, espera EF.CardAccess y PACE en la PC, lectura de identidad protegida y certificados directos. Necesita una decisión explícita sobre el dueño de PACE y sus resets, no solo permitir otro SELECT. Mantener MAC/token/SSC y las autorizaciones de lectura/firma separadas.

El perfil actual soporta lectura PKI adaptada con PACE en Android; esta prueba no declara compatibilidad completa con Ciudadano Digital.
