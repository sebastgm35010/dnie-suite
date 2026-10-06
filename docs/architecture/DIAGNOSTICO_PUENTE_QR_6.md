# Revisión del diagnóstico 6: puente QR

Revisión del 2026-10-05. Fuentes: JSONL suministrado por el usuario y `host.log` / `host-error.log` de la última sesión privada en D:. No se copia el JSONL personal al repositorio.

## Separación del historial

El archivo contiene 454 eventos. Se toma como prueba actual el inicio de proceso `1.8.1-bridge-qr`, evento 2164, y los 65 eventos hasta 2228. Los 389 anteriores corresponden al historial conservado; no se atribuyen al QR nuevo. No se hizo comparación exacta con el diagnóstico 5, que no estaba en la ruta de Descargas comprobada.

## Resultado observado

- El ciclo de vida registra apertura y retorno del escáner Google, seguido de operación NFC.
- Aparecen MSE `0022C1A4`, cuatro intercambios GENERAL AUTHENTICATE y SELECT protegido `0CA4040C`: seis respuestas externas `9000`, con duración individual de 14 a 41 ms.
- El código abre PACE únicamente después de completar TLS con huella fijada y recibir aceptación del token. Por ello, la aparición de estas APDU en este flujo es evidencia fuerte de que la PC aceptó la autenticación; el JSONL no registra explícitamente esa fase.
- Los `9000` externos por sí solos no prueban validación del MAC/token ni lectura final de certificados. Faltan eventos de fase completada para distinguir esas etapas sin recurrir a la pantalla.
- No hay eventos de error/crash en el tramo nuevo. Esto no sustituye una captura completa del buffer de crashes de Android.
- Windows registra inicio del listener TLS y finalización con `Bridge stopped or session timed out.`; stderr está vacío. Esa frase agrupa cancelación y vencimiento, sin identificar el punto exacto.
- El endpoint privado anunciado al arrancar no acredita que haya un consumidor conectado. En `serve-local`, tras autenticar el móvil, Windows espera un cliente del pipe durante 15 segundos. El launcher actual no inicia el relay PC/SC, por lo que la finalización es compatible con esa espera agotada; es una inferencia, no una causa expresamente registrada.
- Al revisar, el teléfono ya estaba disponible por ADB y el reverse `tcp:39443` hacia `tcp:39443` seguía configurado. El proceso del host de esta prueba había terminado.

## Rendimiento y observabilidad

El tramo nuevo contiene diez eventos `slow_frame` (umbral de captura 48 ms): máximo de 683 ms en primer dibujo al iniciar y 405 ms inmediatamente después. Hay también primeros dibujos al abrir otras actividades. No se puede inferir FPS medio ni la causa de los bloqueos solo de este muestreo; los 389 eventos históricos no forman parte de esta medición.

Los frames de actividades auxiliares aparecen con la última ruta de la pantalla principal, por ejemplo `certificates` al abrir `BridgeActivity`. Hace falta etiquetar la actividad/ventana para atribuirlos correctamente. El puente carece además de eventos propios persistentes para importación QR, TLS/token, PACE completado, conexión del consumidor PC/SC y motivo preciso de cierre.

## Siguiente trabajo

1. Añadir eventos estructurados por fase y motivo de cierre, sin payload QR, token, CAN, PIN ni contenido del DNI.
2. Completar el acceso autorizado del backend nativo y conectar el relay al endpoint privado; comprobar presencia, ATR y APDU procedentes de PC/SC.
3. Resolver reset/power-off y reconstrucción NFC/PACE para el middleware. Esta prueba no acredita aún uso completo desde cddnie ni firma desde Windows.

La prueba respalda conexión inicial y ejecución NFC; no permite declarar completo el puente PC/SC. No se modificó el motor ni se instaló otra versión durante esta revisión.
