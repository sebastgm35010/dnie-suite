# Prueba de contador y contexto PACE — 1.8.12

Objetivo: explicar por qué el SELECT protegido posterior al segundo GET UID falla en la verificación MAC. Esta versión diagnostica el rechazo; no introduce un reajuste de SSC ni relaja la integridad.

## Instrumentación

- Cada negociación PACE del PC recibe un identificador aleatorio de contexto/generación. El diagnóstico general registra `pc_pace_requested` y `pc_pace_ready` con ese ID; no registra claves ni tokens de PACE.
- Ante MAC inválido, el motor calcula el MAC de la misma trama con hasta nueve hipótesis de SSC: 0, 1, 2 y el esperado ±1, ±2, ±4, excluyendo valores negativos y el propio esperado. No usa esos candidatos para descifrar, aceptar o enviar el comando. La sesión se invalida igualmente y borra sus claves.
- El APDU exportado incluye `smDiagnostic`: etapa `pc_command_mac`, contexto, SSC esperado y lista de candidatos que coincidieron. Sin claves, CAN, PIN, MAC calculado ni plaintext añadido.
- Windows registra el header y requestId antes de intercambiar el comando con Android, además del estado de respuesta. Registra el código nativo si falla el backend durante el cierre.
- La captura automática sigue empezando en la solicitud Tailscale y termina cuando el servidor vuelve a espera. No exige JSON/QR y el servidor continúa disponible para otros intentos.

## Interpretación

| Resultado | Conclusión permitida |
|---|---|
| `matchingSsc` contiene 1 | La trama autentica con las claves actuales y SSC=1; indica reinicio/desfase del contador del emisor respecto al esperado. |
| Coincide otro candidato | La trama autentica con las claves actuales y ese contador; cuantifica el desfase comprobado. |
| No coincide ningún candidato | No distingue claves diferentes de un contador fuera de la muestra u otra divergencia de entrada/algoritmo; no afirmar claves cambiadas por descarte. |
| Nuevo `pc_pace_ready` antes del fallo | Hubo otra negociación terminada por el gateway; comparar IDs para detectar cambio de contexto. |
| Mismo contexto, nuevo consumidor Windows | Requiere atribución externa del consumidor: los IDs de request del backend no son PID ni handles del cliente PC/SC. |

La prueba no registra claves para reconstruir MAC fuera del móvil. Un MAC válido con un candidato solo se utiliza como evidencia, nunca como recuperación de la conexión. Una coincidencia no determina quién produjo el comando; el muestreo de procesos tampoco lo demuestra.

## Ejecución

1. Instalar 1.8.12 enviada por el mensajero. Mantener IDplug Manager activo en la PC.
2. Tailscale activo en ambos dispositivos. En Puente NFC · PC, **Conectar por Tailscale · sin JSON**, CAN correcto y DNI apoyado. El botón selecciona traducción PACE Android.
3. Mantener la pantalla activa hasta el resultado. El monitor armado captura ese intento y se cierra automáticamente.
4. Exportar APDU y diagnóstico general para cruzar SSC/contexto con los eventos Windows. Los acumulados se separarán por sesión y hora, sin exigir borrar historia.

Pruebas sintéticas añadidas: contador reiniciado, comando omitido, claves diferentes, cambios de generación PACE y exportación de metadata sin mensajes sensibles. En todos los rechazos se comprueba que el motor no adopta un contador candidato; la sesión sigue cerrada.

Entrega: APK 1.8.12-sm-diagnostics (45) compilado; 179 pruebas aprobadas y 2 de interoperabilidad omitidas, cero fallos. Arquitectura aprobada; host Windows 93 comprobaciones aprobadas. Servidor SYSTEM actualizado escuchando en 100.104.106.125:39443, PID observado 53000. Registros privados en `D:\DNIeBridgePrivileged\tailnet-b840ed3a1a234354bba19c8b5abaa5f4`. Monitor de siguiente conexión armado, PID observado 53712, carpeta `D:\DNIeToolchain\LiveCaptures\tailnet-ssc-f09576843e51457dbdeb2281bb5149df`; comprobar/rearmar si pasan más de dos horas antes de probar. IDplug Manager sigue activo, PID 59216. El mensajero recibió la tarea de enviar solo APK e instrucciones; confirmar recepción/instalación antes del ensayo.

Entrega confirmada: Telegram recibió el APK 1.8.12-sm-diagnostics (30.171.446 bytes). Se enviaron únicamente el APK y las instrucciones de prueba; sin registros ni configuraciones.
