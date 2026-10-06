# Sincronización directa de sesiones — 1.8.11

Sustituye la provisión manual del modo Tailscale por una solicitud del teléfono al servidor, sobre el mismo socket TCP protegido por la red Tailscale. No exige generar JSON, QR, certificado ni una sesión Windows por intento. Los perfiles anteriores de JSON/TLS y JSON/Tailscale siguen disponibles.

## Flujo de uso

1. Iniciar una vez `iniciar-tailscale.bat` en la laptop. El backend requiere elevación para abrir el lector PC/SC existente; no instala servicios de inicio ni modifica firewall/controladores.
2. En Android, Puente NFC · PC: nombre de la laptop Tailscale y botón **Conectar por Tailscale · sin JSON**. El nombre inicial es `sebas-laptop.tail2752e9.ts.net`.
3. Con CAN disponible se arma automáticamente la autorización NFC y se selecciona traducción PACE Android. Si falta CAN, introducirlo y autorizar. Acercar el DNI.
4. Android solicita sincronización, el servidor verifica la IP Tailscale del teléfono configurado y responde. Se abre el backend, Android prepara PACE y entrega ATR cuando Windows lo pide.
5. Ante cierre o pérdida de conexión se retira el contexto PC/SC/PACE. El servidor continúa esperando; otro intento no requiere recrear emparejamiento. Cada nueva sesión ejecuta un PACE nuevo. No se reanuda un SSC perdido.

Detener el host con `detener-tailscale.bat`. El servidor permanece activo hasta detenerlo o reiniciar Windows; no se configuró arranque automático. Cada sesión de tarjeta tiene límite de treinta minutos. El socket actual admite una tarjeta/sesión a la vez.

## Protocolo y límites

- Android envía ocho bytes ASCII `DNISYNC1`.
- En la conexión de un peer verificado, Windows devuelve `DNISYNC1` y un token aleatorio de 32 bytes, con plazo de sincronización de quince segundos.
- Android consume esa respuesta y envía el preámbulo existente `DNIBRDG1` con ese token en el mismo socket; continúa el protocolo bidireccional original.
- El token se genera por conexión, no se persiste, se borra al cerrar y no funciona en otro intento. Es un vínculo de sincronización de sesión, **no una prueba independiente de identidad del teléfono**. La autorización del peer depende de Tailscale y de la dirección del Poco configurada en el servidor (`sebas-poco.tail2752e9.ts.net`).
- Verificación del peer ocurre antes de enviar la sincronización y antes de abrir el dispositivo nativo. No hay listener público/LAN ni downgrade automático desde TLS.
- El servidor mantiene un listener reutilizable. La sesión y el dispositivo se cierran antes de aceptar el siguiente contexto; errores de sincronización y de protocolo no terminan el listener.
- No se introdujo lectura/firma automática en segundo plano: el usuario autoriza NFC y mantiene la pantalla del puente activa. La solicitud de sincronización se ejecuta después de acercar el DNI autorizado.

## Organización y pruebas

Sincronización de transporte Android en `TailnetSynchronization`; creación de configuración en `BridgeAndroidAdapter`; coordinación en ViewModel; Compose emite acciones. Windows tiene `TailnetSyncServer`, reutilizando `PcscHostSession` y el núcleo PC/SC existente. No cambia IAS/PKI/PACE ni el motor de firma.

Pruebas: respuesta fragmentada y solicitud exacta, rechazo de respuesta truncada/protocolo incorrecto, solicitud inválida que no abre el lector, dos conexiones consecutivas al mismo listener con tokens diferentes, ATR/warm reset y cierre del dispositivo antiguo antes de la siguiente sesión. La suite Windows completó 93 comprobaciones; se conserva la integración TLS anterior.

Pendiente: comprobar desde el teléfono fuera de casa, validar los siguientes comandos de IDplug tras DO85 y confirmar el comportamiento del lector oficial con ciclos sucesivos reales. La prueba sintética no demuestra compatibilidad final RENIEC.

Entrega y despliegue: compilación offline correcta en 2 min 32 s; 174 pruebas JVM/Android aprobadas, 2 de interoperabilidad omitidas, cero fallos; arquitectura aprobada. Telegram confirmó el APK 1.8.11 (30.171.454 bytes), sin JSON/QR/registros. Host SYSTEM reutilizable escuchando en `100.104.106.125:39443`, PID observado 56024. Registros privados: `D:\DNIeBridgePrivileged\tailnet-9f1b2c14019b42bfa59fb0c1a1bc1a3c`. No incluir registros en Git. El host anterior de emparejamiento no es necesario para este flujo.
