# Entrega candidata 2.0.0-rc1

VersionCode49, misma identidad evolution.debug y clave de actualizacion. APK compilado y verificado, no instalado: ADB no tiene dispositivos conectados. La referencia fisica permanece en Android1.8.14 y checkpoint/ciudadano-digital-android22. No se declara ausencia absoluta de deuda ni validacion fisica de los cambios nuevos.

## Motor y responsabilidades

PcPaceNegotiator posee negociacion, claves, contadores y retiro de contexto PC. PaceTranslationEndpoint encamina APDU y coordina rutas; PcResponseFragments posee fragmentacion PC; PlainCertificateReadRoute posee seleccion/lectura publica; PlainPinStatusQuery reconoce solo el VERIFY vacio observado00200003. El enlace fisico conserva PACE y SSC propios.

Tras reset o power-off, SM obsoleto recibe6985 local sin retransmitirse, sin usar claves anteriores y sin cerrar un transporte sano. Una nueva autenticacion PACE funciona; MAC alterado/replay siguen rechazandose y retirando el contexto. La consulta vacia PIN conserva la respuesta real del DNI, sin ampliar la ruta a VERIFY con datos o cambio de PIN.

BridgeMode representa modos excluyentes y se consume desde coordinacion/adaptador/conexion. Android muestra traduccion como modo recomendado por defecto. Opciones directas y comparacion quedan en panel avanzado. Compose presenta estado y acciones, no APDU/criptografia.

## Windows operativo

ABRIR-PUENTE-PC.bat abre panel con inicio/parada del host Tailscale y activacion/retirada del selector RENIEC. BridgeDesktopController.ps1 contiene acceso a procesos/estado; BridgeDesktop.ps1 contiene controles y presentacion. Host requiere elevacion; selector usa cuenta de RENIEC. No inicia ETW ni captura navegador.

selector_engine.py posee enganches y child gating limitado; selector_service.py contiene singleton por usuario, duracion, estado y registro acotado. Filtro nativo propio seleccionado para Java exacto de RENIEC; otros Java no elegibles. Windows Hello y servicios no se modifican. Dependencias privadas descritas en tools/windows-reader-selector/README.md.

Supervisor consulta nombres primero y rutas solo para candidatos, con intervalo200ms. Muestra corta en reposo tras2s de calentamiento:0.172s CPU en8s (2.15% de un nucleo), no benchmark general ni medicion de latencia NFC.

PcscBackend conserva el fallo original cuando Complete/Detach fallan durante limpieza, registra esos fallos aparte y no intenta retirar una tarjeta sin adquirirla. PcscHostSession conserva tambien el fallo principal frente al fallo de backend/Dispose al cerrar. Registro solo de tipo/codigo de error, sin mensajes potencialmente personales.

## Validacion

- Gradle offline: protocolo85, bridge-core10, bridge-android27 (2omitidas), app68;188aprobadas,2omitidas,0fallos/errores. APK assembleDebug correcto.
- Chequeo de arquitectura: imports principales y ciclos de modulos correctos.
- Host Windows:98comprobaciones aprobadas, loopback TLS/aislamiento/ABI/cleanup, bytes sinteticos.
- Lector PCSC instalado:26comprobaciones aprobadas, sin NFC real. Incluye reset, respuesta exacta, aislamiento de dueño, retiro y cancelacion.
- Selector:4tests de alcance/cleanup; prueba real de runtime reducido padre/hijo con lista exacta pasa sin conectar tarjeta. Diagnostico antiguo sigue funcionando tras mover el filtro a su componente operativo.
- Panel Windows Forms: self-test inicio/parada del supervisor pasa; status final stopped y registro de error vacio.

El primer ensayo nativo fallo al asumir que cualquier cambio tras power-off era retirada. Se preservo su resultado y se corrigio el harness para consumir estados intermedios durante un plazo total de3s. El driver instalado paso sin reinstalarlo ni modificarlo. No se presenta el primer fallo como defecto demostrado del controlador.

## Artefactos y uso

APK privado: D:/DNIeToolchain/Checkpoints/2.0.0-rc1/dnie-evolution-2.0.0-rc1.apk. SHA256:5125C8B3C2ED1E6FDA5D3454BD92DED1AAFADDE1BCCCD54B79135BFBF29EB0E7. Manifest y host candidato en esa carpeta; host anterior preservado en windows-host-before.

El candidato host esta preparado en D:/DNIeToolchain/PairingUi para el siguiente arranque. El proceso host que ya estaba activo conserva su copia anterior; detener/iniciar desde el panel para probar los cambios de cleanup. No se reinicia automaticamente la sesion existente.

Instalar el APK conservando datos, abrir panel, reiniciar host y activar selector antes de lanzar Ciudadano Digital. Conectar Android con modo recomendado y repetir solo reconocimiento/certificados; autenticacion facial ID Peru/cambio de PIN externo quedan fuera del ensayo. Firma Peru se prueba en una sesion separada con PDF de prueba. Estos dos ensayos fisicos pendientes cierran la candidata local2.0.

## Deuda y alcance restante

No incorporar redisenio total en esta candidata. Para3.0 usar PLAN_VERSIONES_2_Y_3.md y contratos existentes. Firma XML/ReFirma masiva pendiente de compatibilidad, sin afirmar validacion. Migraciones de boveda/passkeys y QR pendientes donde los informes originales lo indican. Instalador portable y firma de driver para distribucion a otras PC siguen pendientes; no cambiar servicios ni instalar middleware adicional para este cierre.
