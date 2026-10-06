# Comparación de propietario PACE: 1.8.7

Android versionCode 40, versión 1.8.7-pace-owner. El modo por defecto conserva PACE en Android. El selector de diagnóstico «PACE en Windows · comparación» solo puede cambiarse fuera de una sesión y antes de armar NFC; el modo se toma al autorizar la sesión, sin persistirlo para siguientes arranques de la pantalla.

En modo Windows, BridgeConnection abre TransparentNfcCard y retransmite todas las APDU mediante IsoDep, sin ejecutar PACE propio ni cifrar/descifrar Secure Messaging. Windows puede abrir PACE directamente y usar sus claves/SSC con el DNI. El túnel TLS, pin del servidor, token temporal, presencia y GET UID de ContactlessReaderCard permanecen. Es un relay físico con adaptación de lector, no una emulación de tarjeta ni traducción criptográfica. Reabrir IsoDep en reset no garantiza un reset eléctrico/RF del chip.

La app no requiere CAN local en ese modo: el middleware puede solicitarlo. El modo Android sí lo requiere y mantiene RestartablePaceCard. No se intercepta la entrada del middleware ni se adivina CAN almacenado. La nueva prueba debe retirar/reaproximar el DNI tras la sesión anterior para no asumir estado físico limpio por un reset lógico.

TransparentNfcCard reabre transporte si el PC envía un comando después de power-off sin reset. TransparentBridge conserva la causa original al cerrar por error; no convierte errores de protocolo en respuestas 9000. El registro identifica pace_windows_raw o pace_android_logical, incluidos fallos de apertura, manteniendo la ocultación de cuerpos PIN. Ningún registro incluye claves PACE.

Esto permite comparar el control actual de Windows (CertPropSvc y minidriver oficial iniciando PACE) con las lecturas lógicas exitosas bajo PACE de Android. No implementa terminación del PACE del PC sobre otro canal PACE físico. Esa alternativa requeriría un extremo criptográfico independiente y traducción explícita con claves/SSC separados.

Validación y resultado de hardware se documentarán después de compilar, instalar y ejecutar. Cambios limitados al puente Android/UI y propagación de errores, sin reinstalar controladores, cambiar Calais, servicios ni firewall. Las bases clausuradas permanecen intactas.

Validación completada: buildDebug y pruebas core (10), Android bridge (18 aprobadas, 2 interop omitidas) y app (67) sin fallos; arquitectura correcta. Se probaron reapertura tras power-off sin añadir PACE, transmisión exacta de CLA protegido, perfil raw separado y conservación de cause. Compilación offline con dependencias existentes para evitar nuevas descargas, artefactos de captura/herramientas en D:. Instalación ADB Success y dumpsys confirma versionCode 40/versionName 1.8.7-pace-owner.

Ensayo real preparado: emparejamiento fec13c2db35c4f1889e5547698bb2776; captura privada D:\DNIeToolchain\LiveCaptures\e98ac3a2481343e498382ea223568fe8. Pendiente el usuario activando «PACE en Windows · comparación», nueva aproximación del DNI y lectura automática del minidriver. No se afirma compatibilidad física ni éxito final antes de ese resultado.

## Resultado del primer ensayo directo

El registro confirma pace_windows_raw, sesión 63d6a6b5-fa35-4967-8d4d-ffa6061ec2d0. GET UID fue 9000, SELECT IAS 9000 y SELECT EF.CardAccess 011C con FCI 9000. La siguiente READ BINARY 00B0000000 (Le 256) no recibió respuesta: tiempo Android 1791293610484 → 1791293614310, 3.826 ms. Se registró CardDisconnectedException y se cerró la sesión.

La causa persistente ahora conservada es IllegalStateException > CardDisconnectedException > TagLostException, con frames IsoDepCardChannel.transceive. Esto es una pérdida NFC comunicada por Android durante READ, distinta de la CardProtocolException inmediata posterior a PACE de los ensayos anteriores. TagLostException no determina si el DNI se movió, cayó el acoplamiento RF o el chip dejó de responder ante esa secuencia.

En este intento Windows no alcanzó MSE/GA: falló antes de iniciar PACE. El EOF posterior del host es consecuencia del cierre móvil. No afirmar que PACE del PC falló ni que este resultado refuta el conflicto SM anterior. No repetir sin distinguir el tamaño/Le de READ y el estado inicial de la tarjeta.

Próximo control: lectura física de CardAccess en bloques cortos (p. ej. Le 16), con nueva aproximación de tarjeta, antes de probar PACE Windows. Permite comparar la READ física Le 256 con las lecturas lógicas protegidas exitosas, sin modificar APDU silenciosamente ni reinstalar controladores como supuesto arreglo.

Control corto preparado: Test-PrivateBridgeIdle.ps1 -SmallBlocks selecciona IAS/011C y lee offsets 0,16,32,48,64,80,96, con Le 16 salvo último bloque Le 11. Longitud pública conocida de este DNI: 107 bytes, observada en FCI y lecturas anteriores; no usar como lector genérico de archivos arbitrarios. Tres ciclos, pausa cinco segundos entre ellos, 27 APDU. No manda PACE desde PC ni expone presencia PC/SC. Requiere seleccionar modo Windows/raw en Android; el ensayo no abre PACE en ninguno de los dos extremos.

Se registra etapa antes de transmitir y SW/longitud/tiempo al responder; rechazo, longitud inesperada o EOF detienen el control. Parser sin errores y host compilado; resultado físico pendiente. Captura privada 105c45760cb7463798966260c28feccd, emparejamiento d0a5a1735e004b0681dbbde2d0ed3cac.

Resultado: perfil pace_windows_raw confirmado, pero el primer SELECT IAS (APDU 1) falló antes de seleccionar CardAccess o enviar READ. Tiempo Android 1791293840334 → 1791293845366: 5.032 ms, CardDisconnectedException; ninguna respuesta APDU. El cliente privado recibió EOF y registró test_failed. Por tanto este ensayo no compara Le 16 con Le 256: ningún bloque llegó a enviarse. No había presencia PC/SC ni PACE iniciado en este control, de modo que su fallo no puede atribuirse al PACE del minidriver. Captura detenida después del aviso del usuario.

Pendiente separar disponibilidad inicial NFC/estado de tarjeta del conflicto SM ya reproducido. Control útil siguiente: comparar SELECT IAS bajo las mismas condiciones de nueva detección NFC, una vez físico/raw y otra con apertura local PACE, conservando estado de conexión, duración de connect y causas. No alterar el SELECT ni responder éxito sintético, y no concluir que un READ grande provocó este nuevo fallo.

## Revisión del núcleo de certificados y firma

DnieTransceiver.openV3Pki y PaceBridgeChannel.open reutilizan el mismo V3PkiSession.open: SELECT IAS físico → SELECT PKI físico → PACE perfil IM AES-256, parámetro 13 → SELECT PKI protegido. Ambos usan PaceHandler/PaceSecureChannel para claves, SSC, MAC y GET RESPONSE físico. El puente Android no tiene otra implementación de PACE diferente del flujo de firma. La firma añade VERIFY PIN, MSE de firma y PSO tras abrir ese canal; esos comandos no se ejecutan en los controles de lectura públicos.

El modo directo usa el mismo IsoDepCardChannel, pero no esa inicialización IAS/PKI/PACE. Su constructor crea el adaptador sin conectar: IsoDepCardChannel conecta al primer transceive. Por ello el mensaje de puente raw activo/ATR no demuestra que el enlace físico ya se estableció. También checkPresence en raw puede consultar isConnected antes del primer intercambio y tratar una conexión aún no iniciada como pérdida; revisar ese ciclo de vida antes de atribuir todos los fallos directos a RF. En el ensayo privado fallido sí se llegó a transceive y TagLostException, así que esa posibilidad no basta para explicar el fallo observado.

Nuevo control preparado, sin modificar APK ni núcleo: mismos tres ciclos de SELECT/READ 16 bytes, ahora con PACE Android y sin PC/SC. Requiere desactivar «PACE en Windows · comparación» y usar el CAN local. Emparejamiento a41ee9e0fd0c4ee8a432c55265c0ae21, captura privada 413a8fb0b8d642b3b0c1992e7f68f309. Resultado físico pendiente.

Resultado confirmado: perfil pace_android_logical, 27 APDU con 9000, tres ciclos completos, cero fallos y test_completed esperado a los 13.804 ms desde conexión privada. Incluye 21 READ de bloques, último Le 11, longitud prevista en todas las respuestas. Android cerró después de responder la APDU 27, sin transport_failed. Captura detenida tras el aviso del usuario. No hubo caída prematura.

La comparación apoya que la apertura completa local IAS/PKI/PACE del núcleo funcional permite esta secuencia, mientras el control raw previo perdió comunicación desde SELECT IAS. No se aisló causalmente PACE frente al estado inicial o momento de conectar IsoDep: no afirmar que todo acceso físico raw es imposible. Mantener Android como ruta funcional; revisar conexión/presencia/reset del raw y gestionar explícitamente el segundo PACE/SM del PC antes de volver a exponer esa ruta a clientes automáticos.
