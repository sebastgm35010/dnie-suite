# Motor inicial del puente Android

Registro histórico de la entrega de backend. La continuación con pantalla, propietario NFC y prueba interoperable se documenta en [preview 1.8.0](PUENTE_PREVIEW_1_8.md).

Primera entrega de backend, desarrollada junto con el host Windows delegado. No hay pantalla de emparejamiento, servicio Android en ejecución ni puente con DNI desplegado. No se instaló ni envió un APK nuevo: se compiló para verificar integración de módulos. La identidad/versión de la app sigue en 1.7.2; este trabajo no declara la versión 2.0 funcional.

## Componentes incorporados

- `dnie-bridge-core`: framing VPCD propio, contrato BridgeCard y perfil ReadOnlyBridge. Es JVM puro y no depende de Android ni de criptografía. Permite SELECT IAS/PKI y selección de EF CE81/CE82/CE83, seguido de READ BINARY solo después de que el chip confirme selección de certificado. Rechaza demás instrucciones con 6985 sin transmitirlas: PIN, firma, administración, PACE o SM originado en PC quedan fuera de este perfil. Conserva respuestas reales, incluida advertencia de fin de archivo.
- `dnie-protocol/PaceBridgeChannel`: abre una conexión PKI nueva con la secuencia existente IAS → PKI → PACE → PKI protegido. Usa un logger sin payload, mantiene la implementación existente de MAC/SSC/GET RESPONSE y cierra el canal físico si falla. No altera la sesión de firma, bóveda ni ICAO.
- `dnie-bridge-android`: cliente TLS con SHA-256 fijado del certificado DER, verificación de vigencia, token de 32 bytes y preámbulo DNIBRDG1. Nunca envía el token antes de verificar el certificado. No admite conexión sin TLS. El transporte espera confirmación Windows y entonces llama a la fábrica local de tarjeta autorizada. PaceBridgeCard adapta el canal físico nuevo al motor PACE. El ATR debe venir de esa conexión NFC, sin fingir una tarjeta por contacto.
- `tools/windows-bridge`: host .NET implementado por el subagente; contrato y comandos en [su documento](PUENTE_WINDOWS_PROTOCOLO.md).

El adaptador Android bloquea: ejecutar fuera de Main y llamar close desde el propietario de cancelación, además de conservar propiedad del recurso NFC durante la apertura. No se ha añadido un ViewModel de puente ni se han usado ViewModels existentes como servicios. El token y el pin se copian al aprovisionamiento de memoria; close borra esas copias. No hay almacenamiento permanente de emparejamientos. El CAN se usa localmente para abrir PACE y no forma parte del protocolo con PC.

El cliente permite TLS1.2/1.3, conexión de cinco segundos, espera de IO de treinta segundos, expiración del aprovisionamiento y límite de sesión de treinta minutos. Una instancia sirve para una sola sesión. Tramas y respuestas se borran después del procesamiento; no se registran sus contenidos. Los diagnósticos heredados de PACE con logger desactivado conservan únicamente cabeceras/status/duración, no secretos ni respuestas en claro.

## Controles de ciclo de vida pendientes

En esta entrega OFF o RESET cierran la tarjeta y retiran la sesión TLS. No se simula un reset con SELECT MF ni se reutiliza SSC. Es una conducta de cierre conservadora, pero Windows puede enviar resets al insertar y enumerar: por ello todavía no puede afirmarse que una herramienta PCSC complete una lectura física a través del nuevo motor.

Antes de desplegar falta un propietario NFC que pueda realizar y comprobar reconexión real, abrir otra sesión PACE y coordinar ese cambio con PCSC. También falta adaptar la selección IAS/PKI y lectura de bloques a comandos de las herramientas destino sobre tarjeta real, con un solo dueño del canal seguro. La ruta RENIEC que ejecuta PACE en PC necesita un perfil distinto; este perfil Android rechaza ese PACE entrante deliberadamente y no es aún compatible con Ciudadano Digital.

## Control del tramo Windows

El host conecta al lector local solo después de autenticar Android y rechaza el Bix instalado mientras escuche en 0.0.0.0:35963. Conectar desde loopback no elimina la vía de acceso directo por LAN. No se instaló un driver ni se cambió el firewall; el servicio no se arrancó con el DNI real. La solución para distribución requiere aislar/reemplazar ese driver, conservar su licencia si se distribuye, o implementar una alternativa propia/permisiva. Este prototipo no declara MIT para toda la pila.

TLS protege el enlace, no decide qué proceso Windows usa PC/SC. El perfil de Android permite una lista limitada de operaciones durante la sesión autorizada, pero no atribuye las APDU a un programa Windows individual. Autorización por aplicación/operación será una capacidad adicional del agente de host, no una propiedad ya conseguida por usar TLS.

## Validación y siguiente etapa

Pruebas JVM cubren framing fragmentado y consecutivo, truncamiento, cero longitud, rechazo de instrucciones sensibles, cierre con reset, lectura sin certificado seleccionado y status reales; vigencia/propiedad/limpieza de credenciales; certificados fijados válidos, incorrectos, ausentes y vencidos; y fallo de selección IAS con cierre físico. Las pruebas de protocolo existentes siguen siendo la regresión de PACE y firma.

Android/JVM pasó 73 pruebas en los tres módulos verificados: núcleo del puente 4, adaptador Android 5 y protocolo 64, sin fallos. Se compiló el APK debug y pasó la comprobación de límites de arquitectura. Windows pasó 16 comprobaciones con TLS real y contenido sintético, incluidas credenciales incorrectas, fragmentación, límites, cierre y rechazo de driver expuesto. No equivalen a una prueba de intercambio TLS entre Android real y Windows ni a una operación NFC física del puente.

Siguiente orden: aislamiento del driver, propietario de reset/reconexión, prueba Windows↔Android con tarjeta sintética, pantalla/servicio de emparejamiento y sesión, lectura de certificados con DNI real y, después, perfil específico RENIEC. No habilitar firma ni operaciones de administración antes de validar permisos por operación y ciclo de vida.
