# Contexto vigente del proyecto

Hito Android21: FIRMA PERÚ GENERÓ PDF CON FIRMA VÁLIDA. APK1.8.14 actual, cliente único,56/56 respuestas,VERIFY real9000,MSE firma9000,PSO002A9E9A9000; sin PACE PC, PACE físico Android2veces. PDF[FP]158851bytes: pyHanko offline intact/valid=true,ENTIRE_FILE,NONE,SHA256_RSA;trusted=true contexto local, no comprobación online de revocación. Cierre manual119,795s y colectores detenidos. Preservar versión/configuración; pendientes reset-SM interclientes,selector Ciudadano Digital,minidriver y ReFirma independiente. Ver [resultado verificado](architecture/RESULTADO_ANDROID21_FIRMA_PERU_VALIDADA.md).

Prueba Firma Perú cliente único preparada: APK1.8.14 actual, nueva sesión con traducción Android, PDF sintético privadoD y monitor siguiente conexión300s/espera2h en D:\DNIeToolchain\LiveCaptures\tailnet-firma-peru-aa0d8a82c89d45668703e7083fe6ebeb. IDplug cierre normal solicitado; Firma Perú lanzamiento ClickOnce solicitado; sin Java paralelo ni nueva modificación del motor. Error SM tras reset permanece pendiente. Ver [procedimiento](architecture/PRUEBA_FIRMA_PERU_CLIENTE_UNICO.md).

Android20: 248,324 s, 202 comandos/201 respuestas; IDplug muestra token operativo y Login success. Ciudadano Digital solo detección v3 en ventana inicial; selector Java ClickOnce sigue pendiente. VERIFY plano186 rechazado6985 localmente (consulta sin PIN, no prueba bloqueo). Cierre202 confirmado por SM PC sin autenticación después de reset201; PACE físico reconstruido, no TagLost. Pendientes rechazo protocolario controlado tras reset, compatibilidad de consulta PIN y selector real, sin reutilizar SSC/claves viejos. Ver [correlación y plan](architecture/RESULTADO_ANDROID20_IDPLUG_Y_RESET_SM.md).

Android19 / diagnóstico19 confirma causa inmediata del cierre web: TagLostException físico durante el primer GENERAL AUTHENTICATE al abrir PACE Android, después de MSE9000 y antes de pace_ready/ATR/presencia PCSC. Cierre automático transport_or_card_failed; Detener ocurrió después con estado idle. IOException Windows y Win32 code5 limpieza posteriores. Sin APDU del PC ni lectura web. Repetir con nueva detección NFC y tarjeta estable; no atribuir aún movimiento ni CAN incorrecto. Ver [cierre antes de presencia](architecture/RESULTADO_NAVEGADOR_CIERRE_ANTES_DE_PRESENCIA.md).

Android18 / diagnóstico18: lectura real completada con 1.8.14 (47). Java original RENIEC devuelve success:true, identidad no vacía y certificados Fir/Aut. 60 comandos/60 respuestas; CE82 (2.403 bytes) y CE81 (2.346 bytes) reconstruidos y parseados X.509, vigencia actual comprobada (sin validar cadena/revocación). 76,221 s de sesión, cierre manual confirmado; monitor cerrado. Selector por proceso funciona; pendiente integrar ejecución ClickOnce/web y resolver coexistencia del minidriver antes de restaurarlo. No nuevo APK ni ensayo armado. Ver [resultado completo](architecture/RESULTADO_ANDROID18_LECTURA_RENIEC_COMPLETA.md).

Android16 / diagnóstico17: PACE PC real completado con Java original, tres operaciones protegidas de identidad respondidas y cierre manual estable. El SELECT IAS plano35 fue bloqueado localmente con 6985 al pasar a PKI. Corregido en 1.8.14 (47) mediante ruta modular IAS → PKI → certificados CE81/CE82/CE83 → READ, con cifrado físico independiente y comandos planos sensibles bloqueados. 185 pruebas aprobadas y 2 omitidas; APK compilado. Lectura completa con hardware pendiente. Captura siguiente armada en tailnet-reader-selector-7f2f892e2ade425aab4a96ce66d27c9c; entrega del APK confirmada por Telegram. Ver [resultado y adaptación](architecture/RESULTADO_ANDROID16_TRANSICION_PKI.md).

Android15 confirmó el selector: Java original identifica IAS v3 y lee EF.CardAccess, que anuncia el parámetro 13. La solicitud MSE omite DO84 y 1.8.12 responde 6A80 antes de verificar el CAN. Corregido en 1.8.13 (46): se acepta la forma exacta OID + referencia CAN sin DO84, únicamente para el perfil 13, conservando token/MAC/SSC. 182 pruebas aprobadas y 2 omitidas; APK compilado y arquitectura aprobada. Siguiente lectura sin ventana armada en tailnet-reader-selector-d94aec2e81f64914813aa21be2ca6780. Entrega del APK confirmada por Telegram. Guías revisadas; la guía Ciudadano Digital devolvió 401. Ver [resultado y corrección](architecture/RESULTADO_ANDROID15_MSE_IMPLICITO.md).

Selector Java implementado y armado: proveedor TerminalFactory por proceso filtra lector puente exacto, rechaza fallback y mantiene Hello. Seis pruebas+metadata runtimeRENIEC11 aprobadas; JAR original de lectura sin modificar, llamada por lanzador propio. Control espera siguiente tarjeta y lee automáticamente; aún no integra ClickOnce/web. Logs oficiales desactivados antes de main; CANstdin/memoria y resultado privadoD. Captura tailnet-reader-selector-f8ea777fc3fe419eba379e04044ce3d5. Ver [alcance/prueba](architecture/SELECTOR_JAVA_POR_PROCESO.md).

Android14 nuevo control:182s,22/22 respuestas,cierre manual,PACE físico/reset estables sin SSCfallido. .NET detectaIASv3(9000),luego Java7088 ejecuta y obtiene6A82 a v2/v3 sin nuevos comandos al móvil. Java conserva último CARD_PRESENT; captura tiene WindowsHello presente después del puente. Inferencia fuerte de lector equivocado,pendiente confirmar terminal/handle Java; no desactivarHello ni reordenar globalmente(.NET usa primero). Ver [resultado](architecture/RESULTADO_ANDROID14_SELECCION_JAVA.md).

Control sin minidriver activo:22 solicitudes/22 respuestas, reset/power-off completados sin cierre. Usuario informa canal estable pero Ciudadano Digital rechaza; no aparece anterior secuencia PACE/SSC119→1. CertPropSvc/SCardSvr activos y asociación contactless ausente. Resultado provisional; pedir exportación móvil al detener y cruzar SELECT9000/6A82 con payload antes de modificar gateway. Ver [observación](architecture/CONTROL_SIN_MINIDRIVER_PROVISIONAL.md).

Control sin minidriver armado: tailnet-without-minidriver-70314f9ef4274ba2830b35fa445ada8d; espera2h, captura máxima300s, ETW automático y cierre por fin de sesión. Baseline cuatro asociaciones ausentes y oem237 retirado; CertPropSvc/SCardSvr activos. Usuario conecta Android1.8.12, observa90s y detiene manualmente si no hay error. No interpretar falta de APDU automáticas como compatibilidad final. Pendiente resultado y comprobar reinstalación.

Minidriver DNI inhabilitado por autorización explícita: paquete oem237 exportado/retirado, cuatro asociaciones Calais respaldadas y retiradas. CertPropSvc/SCardSvr siguen Running; lector puente oem236 iniciado y host escuchando. Restore administrativo privado preparado; vigilar reinstalación automática en próxima inserción. Ver [resultado y restauración](architecture/MINIDRIVER_DNI_INHABILITADO.md).

Restricción nueva: preservar CertPropSvc; retirada la propuesta de detenerlo. Revisión de alcance completada: requerido para smart-card Plug and Play; minidriver Perú separado oem237.inf(IN Groupe1.8.0.459), lector puente dnibridgereader independiente. Aislamiento mediante retirada temporal del paquete DNI queda en planificación con respaldo/restauración y cliente APDU controlado; no se ejecutó ninguna modificación. Ver [alcance](architecture/ALCANCE_AISLAMIENTO_MINIDRIVER.md).

Resultado android13: SSC119→1 reproducido,77 solicitudes/76 respuestas. ETW arrancó/paró correctamente: CertPropSvc PID2716 carga ambos minidrivers Perú durante la sesión y los descarga tras cierre; principal hipótesis consumidor automático, sin atribución definitiva del SELECT77. IDplug PID59216 abierto, sin WPP observado de su PID. Próximo control propuesto: aislar temporalmente CertPropSvc y restaurar estado; aún no ejecutado ni nuevo monitor armado. Ver [captura y límites](architecture/RESULTADO_ATRIBUCION_ANDROID13.md).

Próxima prueba armada: monitor administrativo PC/SC/ETW automático con límite300s y espera2h, APK1.8.12 existente. Última captura SSC preservada. Doce resúmenes históricos verificados; limpieza general bloqueada, tres archivos pesados eliminados individualmente. Ver [preparación y retención](architecture/PRUEBA_ATRIBUCION_TRAS_SSC119.md).

Resultado real 1.8.12: sesión tail3 nueva, 77 comandos/76 respuestas. SELECT 77 autentica con las claves MAC actuales y SSC=1, frente a SSC esperado119; desfase demostrado. Una única negociación PACE, sin reset/power-off Windows observado; no atribuir aún al proceso IDplug. Rechazo Android precede EOF y Win32 código5 de limpieza. No reajustar SSC ni aceptar replay; siguiente paso atribuir ciclo de vida/contextos PC/SC y diseñar reautenticación fresca. Monitor concluido. Ver [resultado SSC](architecture/RESULTADO_SSC_TAIL3_1_8_12.md).

Preparación 1.8.12-sm-diagnostics (45): diagnóstico limitado de SSC al fallar MAC, sin aceptar/reenviar ni reajustar contador. APDU exporta esperado/coincidencias/contexto; diagnóstico general registra generación PACE. Windows registra solicitud antes de enviar y código Win32 del cierre. La prueba distinguirá un desfase demostrado de otros casos todavía inconclusos. Ver [ejecución e interpretación](architecture/PRUEBA_SSC_Y_CONTEXTO_1_8_12.md).

Diagnóstico general acumulado analizado: 1.525 eventos. IDs 5914 (17:46 UTC) y 6106 (18:08 UTC) confirman fallo en `TerminalSecureMessaging.decode:40`, comparación MAC PC→Android. SELECT 77 se rechaza antes de descifrar y de enviar al DNI. TLS/TagLost/DO85/manuales anteriores separados; EOF/Win32 Windows posteriores son consecuencia observable del cierre. Causa inmediata confirmada, pendiente determinar claves/SSC/contexto del cliente tras GET UID; no desactivar MAC ni resintonizar contadores sin prueba. Ver [análisis del diagnóstico completo](architecture/DIAGNOSTICO_COMPLETO_MAC_SELECT77.md).

APDU móvil tail2 recibido: ambos ensayos de 76 respuestas Windows tuvieron realmente 77 comandos; SELECT protegido 0CA4040C tras GET UID falla en Android con IllegalArgumentException en 1 ms. Reproducido sin/con IDplug, formato DO87/DO8E válido; no es el antiguo DO85. No atribuir EOF/Win32 posterior como causa inicial ni declarar lectura completa. Pendiente cadena de causas del diagnóstico general Android y revisión de contextos PC/SC/SSC manteniendo MAC. Ver [correlación del SELECT 77](architecture/SELECT_77_TRAS_UID_TAILNET.md).

Ensayo con IDplug Manager activo completado: 18:08:09–18:08:33 UTC, 76 comandos respondidos; PID 59216 confirmado en cuatro muestras. Misma secuencia de headers/estados que el ensayo anterior: no demuestra que IDplug haya emitido esos comandos ni reconocido el token. EOF y Win32Exception de cierre otra vez; monitor detenido y host reutilizable vuelve a espera. Falta APDU móvil de esta ventana y operación explícita del middleware; DO85/RENIEC aún pendientes. Ver [resultado con IDplug](architecture/PRUEBA_IDPLUG_ACTIVO_2026_10_06.md).

Preparación de siguiente prueba con programas activos: IDplugManager.exe abierto, PID 59216, ventana y proceso respondiendo. cddnie-app.exe ejecutado por acceso ClickOnce y directamente; ambos terminaron y su log oficial indica «Argumentos vacíos». RENIEC requiere los argumentos generados por Ciudadano Digital; no afirmar que siga activo. El entorno rechazó por política tanto abrir Edge con diagnóstico como abrir normalmente el sitio oficial, sin motivo más específico. No se intentó evadir ese bloqueo. Nuevo monitor automático armado para ensayo con IDplug; captura actual indicada en `D:\DNIeToolchain\LiveCaptures\current-tailnet-auto-monitor.txt`. El host Tailscale reutilizable sigue activo. Pendiente iniciar correctamente RENIEC desde el flujo autorizado de su web.

Captura automática de siguiente sesión completada: 17:45:55–17:46:16 UTC, 76 comandos PC/SC con respuesta; PACE y lecturas protegidas continuaron, GET RESPONSE observado. Monitor cerrado y recolectores detenidos. Host vuelve a esperar tras EOF; excepción Win32 posterior pendiente de caracterizar. Edge no se capturó. Falta el APDU móvil de esa prueba y no hubo INS CB/DO85. Ver [resultado y cobertura](architecture/RESULTADO_MONITOR_TAILNET_2026_10_06.md).

Primer ensayo real del flujo Tailscale sin JSON: nueva sesión `1a450938-ffa6-4a3b-bb1c-8792e9003c1d`, 41 comandos con respuesta y PACE completado; 26 respuestas protegidas DO99=9000, sin transport_failed. Host confirma sincronización, lector presente y retorno a espera tras EOF. Cierre sin motivo explícito: pendiente distinguir salida/Detener de cierre espontáneo. DO85 no apareció en este nuevo ensayo. El archivo incluía además el fallo viejo del comando 77; se separó antes de analizar. Ver [resultado y límites](architecture/PRIMER_REGISTRO_TAILNET_1_8_11.md).

1.8.11-tailnet-sync (44): Android solicita sincronización por Tailscale en el mismo socket, sin JSON/QR ni sesión Windows nueva por intento. Host `serve-tailnet` reutilizable, peer Poco verificado, token fresco por conexión y lector/PACE retirados entre intentos. Botón «Conectar por Tailscale · sin JSON» selecciona traducción Android y arma NFC si CAN disponible. Host se inicia/detiene con los nuevos BAT; no arranque automático ni cambio de firewall. 93 comprobaciones Windows aprobadas. Ver [protocolo, uso y pendientes](architecture/TAILNET_SIN_JSON_1_8_11.md).

1.8.10-tailscale (43): nuevo perfil de pruebas `tailscale_tcp`, sin TLS adicional, entre laptop/Poco por Tailscale. TLS legado conservado. Token/autorizar DNI/PCSC/reset/PACE/registro APDU conservados. Compilación, arquitectura y 77 comprobaciones Windows aprobadas; envío APK por mensajero solicitado. Host temporal enlazado solo a 100.104.106.125:39443, sin cambios de firewall. Ver [modo remoto y pendientes](architecture/TAILSCALE_DIRECTO_1_8_10.md).

Corrección 1.8.9-pace-do85 (42): la prueba real de 1.8.8 alcanzó el comando 77 y cerró por DO85 no admitido, no por pérdida NFC demostrada. Añadido soporte de criptogramas DO85 en ambas sesiones y corregidos textos UTF-8 del puente. Validación e instalación se registran en [corrección 1.8.9](architecture/PACE_DO85_1_8_9.md); nueva prueba real pendiente.

Instalada 1.8.8-pace-translation (41): opción experimental de terminación PACE IM AES-256 del PC y traducción SM a sesión física Android independiente. Tokens/MAC/SSC verificados, GET RESPONSE del PC y físico separados, contextos retirados en reset/power-off. 168 pruebas aprobadas, 2 interop omitidas; hardware pendiente. IDplug inspeccionado mediante imports/exports/desensamblado del wrapper, sin fuente original ni copia de código propietario. Ver [implementación y límites](architecture/PACE_TRANSLATION_1_8_8.md).

Control comparativo de bloques con 1.8.7: PACE Android completó tres ciclos y 27 SELECT/READ con 9000 en 13,804 segundos, cierre esperado y cero fallos. Raw había fallado en el primer SELECT con TagLostException. Mantener ruta Android funcional; pendiente ciclo de vida NFC del raw y adaptación explícita del PACE/SM iniciado desde Windows. Ver [comparación 1.8.7](architecture/PACE_OWNER_1_8_7.md).

Primer control 1.8.7 raw: SELECT IAS y EF.CardAccess 9000; READ física 00B0000000 falló tras 3.826 ms con CardDisconnectedException > TagLostException, antes de MSE/GA del PC. Distinto del fallo SM anterior. Pendiente controlar READ corta y nueva aproximación, sin concluir desplazamiento físico ni fallo PACE Windows. Ver [resultado 1.8.7](architecture/PACE_OWNER_1_8_7.md).

Instalada por ADB 1.8.7-pace-owner (40): selector de comparación «PACE en Windows» con relay físico sin PACE Android ni doble envoltura, TLS y GET UID conservados; por defecto sigue PACE Android. Causas de excepción preservadas. Build y 95 pruebas aprobadas (2 interop omitidas), arquitectura correcta. Ensayo real pendiente. Ver [modo y validación](architecture/PACE_OWNER_1_8_7.md).

Control de APDU públicas sin PC/SC aprobado: 19 ciclos, 57 SELECT/READ con 9000, EF.CardAccess idéntico en memoria y cierre esperado sin transport_failed. Android gestiona PACE y SM correctamente para esta secuencia de lecturas NFC repetidas. El fallo con PC/SC debe investigarse en la superposición del PACE/SM del cliente automático y Android; no asumir fallo físico ni necesidad de reinstalar. Detalle en [capturas y controles](architecture/CAPTURA_ATRIBUCION_PCSC.md).

Control sin presencia PC/SC aprobado: 19 controles ATR/presencia durante 91,287 segundos, cero fallos y cierre deliberado confirmado por usuario y registros. TLS/Android/PACE inicial estables en este intervalo; no se probaron APDU NFC continuadas. Ver [atribución y control](architecture/CAPTURA_ATRIBUCION_PCSC.md). La desconexión reproducida con PC/SC sigue asociada al SELECT protegido posterior al PACE iniciado desde PC.

Captura ETW 2026-10-06: CertPropSvc (svchost PID 2716) cargó PeruMinidriver64.dll e idplug-peru-minidriver64.dll durante la inserción; actividad WPP correlacionada con el nuevo fallo SM a 35 ms. La UI IDplug puede estar cerrada mientras su minidriver trabaja dentro de otro proceso. No se confirmó CAN almacenado ni atribución de cada APDU. Ver [atribución PC/SC](architecture/CAPTURA_ATRIBUCION_PCSC.md).

Última prueba real, 2026-10-06: primer SELECT protegido 0CA40204 falló en Android con CardProtocolException a los 27 ms; Android cerró después y Windows recibió EOF. No crash observado. Falta categorizar el error y conservar su causa en TransparentBridge. Ver [captura y pendientes](architecture/DESCONEXION_REAL_2026_10_06.md).

**Pendiente prioritario al retomar:** el registro mostró PACE final 9000 y fallo del primer comando SM con CardProtocolException; el usuario aclara que la ventana CAN solo apareció en el primer intento, sin replicarse después. No asumir entrada nueva ni CAN correcto confirmado en los siguientes ensayos. Ver [PENDIENTE_PACE_MIDDLEWARE_CAN_CORRECTO.md](architecture/PENDIENTE_PACE_MIDDLEWARE_CAN_CORRECTO.md) y [experimentos sintéticos de IDplug](architecture/IDPLUG_EXPERIMENTOS_SINTETICOS.md). Resolver propiedad/traducción PACE y procedencia de los intentos antes de más ensayos equivalentes.

Actualizado: 2026-10-05. Entrega Android: **1.8.6-bridge-diagnostics** (versionCode 39). El puente usa PACE en Android, APDU lógicas abiertas y GET UID adaptado; ahora los fallos anteriores a abrir la tarjeta también quedan en el registro exportable con cadena de excepciones. La compatibilidad real con RENIEC e IDplug sigue pendiente. Detalle en [DIAGNOSTICO_APERTURA_1_8_6.md](architecture/DIAGNOSTICO_APERTURA_1_8_6.md), [LECTOR_UID_1_8_5.md](architecture/LECTOR_UID_1_8_5.md) y [PUENTE_PACE_ABIERTO_1_8_4.md](architecture/PUENTE_PACE_ABIERTO_1_8_4.md). Este documento es el punto de entrada para continuar el desarrollo; los registros anteriores conservan el detalle histórico y sus limitaciones en cada fecha.

## Base y alcance

IDplug oficial 4.6.1.475 quedó instalado correctamente el 2026-10-05 (exit 0, sin reinicio), con aplicaciones principales en D:, minidrivers firmados y perfiles de contacto/contactless registrados. PKCS#11 inicializa y enumera slots; falta prueba con DNI activo y firmadores. Ver [IDPLUG_INSTALADO_Y_PRUEBAS_FIRMADORES.md](architecture/IDPLUG_INSTALADO_Y_PRUEBAS_FIRMADORES.md). La instalación previa fallida ya no describe el estado actual.

La versión 1.0.8 permitió firmar con DNIe v3 por NFC y el usuario confirmó validación externa en Certezia y el verificador del Estado. Se clausuró la base y el desarrollo continuó en `modest-bose-evolution`, con identidad Android independiente. No modificar `modest-bose` ni la carpeta original de Antigravity.

El hito 2.0 previsto es un puente Android NFC/PACE hacia aplicaciones Windows que esperan un lector PC/SC. **Todavía no está validado de extremo a extremo con un DNI real.** La configuración por QR, el transporte TLS y el lector Windows son piezas disponibles, con pendientes de integración indicados abajo.

## Capacidades y evidencia

| Función | Estado y límites |
| --- | --- |
| Certificados y firma | Lectura de certificados personales y firma NFC funcionales confirmadas por el usuario. Mantener IAS → PKI → PACE → PKI protegido, MAC/token/SSC y GET RESPONSE físico. |
| Identidad ICAO | Sesión independiente y lectura por bloques; el fallo de un bloque no invalida toda la ficha. Foto y firma manuscrita pueden alimentar la presentación PDF. EF.SOD identifica al emisor, no sustituye al certificado personal. |
| PDF y lotes | Flujos separados, editor ampliado de posición y tamaño, campos configurables y presentación de firma manuscrita sola. Criptografía en motores; geometría y estampado en `dnie-pdf`. |
| Bóveda | Cifrado, recuperación con DNI, nombres/extensiones conservados, catálogo y herramientas. Vista previa condicionada a formatos compatibles. CAN compartido con la pantalla principal. No habilitar biometría para bóveda. |
| Passkeys | El usuario confirmó registro/uso en Google y Facebook. Autorización DNI de una utilización; delegación biométrica opcional para login mediante clave del teléfono. Alta, gestión y migración mantienen autorización DNI. |
| Respaldo y migración | Hay implementación y pruebas simuladas. No afirmar migración física validada sin dos DNI. Certificados y DNI tienen vencimientos distintos; reemplazar DNI/certificado exige planificación antes de perder acceso a la clave anterior. |
| QR FIDO | Flujo experimental del proveedor/Google; distinto del QR del puente. No constituye implementación propia del transporte híbrido caBLE. |
| Diagnóstico | Registro persistente exportable y borrado explícito. No incorporar CAN, PIN, claves privadas ni documentos personales en Git o mensajes. |
| Lector Windows | UMDF propio instalado con certificado local de desarrollo; 25 comprobaciones sintéticas PC/SC. No se alteraron Secure Boot, modo test-signing ni firewall. |
| Puente seguro y QR | 63 comprobaciones Windows y pruebas Android; diagnóstico 6 aporta evidencia de autenticación y APDU NFC en 1.8.1. En 1.8.2 el backend integrado pasó 13 comprobaciones con controlador instalado, PC/SC y teléfono TLS simulado, incluyendo reset y 35 s de espera. Falta validar reapertura real NFC y middleware. |

## Arquitectura que debe conservarse

`app/ui` presenta estado y acciones; ViewModels coordinan, `app/data` adapta Android/IO y los módulos de motor implementan formatos y criptografía. Las bibliotecas JVM no importan Android ni Compose. El mapa detallado está en [ARQUITECTURA_FORK.md](../ARQUITECTURA_FORK.md); `python tools/check_architecture.py` comprueba límites.

El puente usa `dnie-bridge-core` para contratos/protocolo, `dnie-bridge-android` para conexión TLS, autorización y adaptación NFC; `app/data/bridge` y `app/ui/bridge` separan plataforma y pantalla. Windows mantiene `tools/windows-bridge` sin dependencia de renderizado; `tools/windows-pairing` genera la presentación QR con QRCoder MIT. El controlador propio permanece en `tools/windows-pcsc-driver`; no se integró el motor GPLv3 de la app Virtual Smart Card.

## Configuración móvil por QR

1. Ejecutar [emparejar-pc.bat](../emparejar-pc.bat) en Windows. El modo integrado pide elevación para iniciar una sesión temporal SYSTEM del backend, sin ampliar la ACL del lector.
2. Abrir en Android **Puente NFC · PC → Escanear QR de la PC** y comparar la huella mostrada en ambos extremos.
3. El QR importa dirección, puerto, huella TLS, token temporal y vencimiento; no importa CAN/PIN ni autoriza automáticamente operaciones NFC.
4. Para conectar, el launcher prepara `adb reverse` si hay un teléfono autorizado. También admite ADB inalámbrico ya conectado; no modifica firewall ni habilita depuración por su cuenta.

El QR vence en diez minutos. Los archivos privados se crean en `D:\DNIeToolchain\Pairings`; ocultar el QR no elimina el archivo. El servidor valida el token y la caducidad antes de habilitar el endpoint privado; una sesión ya aprobada conserva su propio ciclo de vida. Detalles y comandos alternativos en [windows-pairing/README.md](../tools/windows-pairing/README.md).

## Pendientes concretos del puente

- Validar y madurar el host temporal SYSTEM de 1.8.2. Ya conecta el backend integrado; todavía no es un servicio de distribución de producción.
- Probar la reconstrucción NFC/PACE real de 1.8.2: reset/power-off ya están implementados y probados sintéticamente. No confundir adaptación IsoDep con reset eléctrico de contacto.
- Actualización diagnóstico 7: el reset real completó PACE y .NET reconoció v3. El error aparece en Java; su selección del último lector presente apunta a Windows Hello. La prueba conjunta Edge/PCSC/motor Kotlin pasó y reproduce tanto la discrepancia de selección como el bloqueo posterior de P2=04. Detalles en [diagnóstico 7](architecture/DIAGNOSTICO_7_Y_PRUEBA_CRUZADA.md).
- Verificar cddnie y su continuación Java con el DNI real. Las selecciones IAS en claro del middleware y su posible PACE posterior requieren perfiles compatibles, no reutilización indiscriminada de la sesión segura del móvil.
- Probar escaneo QR real, comparación de huella, caducidad, desconexión y reconexión en teléfono. El escáner depende de Google Play Services; la importación JSON permanece como alternativa.
- Resolver limpieza/administración de sesiones privadas y distribución firmada de producción. El certificado local del controlador sirve para desarrollo, no para distribución pública.

## Ubicaciones y registros útiles

Herramientas, paquetes y nuevos temporales Windows en `D:\DNIeToolchain`; evitar consumir más C:. SDK Android existente en `%LOCALAPPDATA%\Android\Sdk`. Compilación Android requiere JDK 17. APK exportado por `:app:exportBranchApk` en `app/build/outputs/apk/evolution/dnie-evolution-debug.apk`.

- [Reglas de trabajo](../AGENTS.md) y [mapa de documentos](README.md).
- [Pruebas sintéticas del lector](architecture/LECTOR_PCSC_PRUEBAS_SINTETICAS.md).
- [Inspección estática del middleware](architecture/INSPECCION_ESTATICA_CDDNIE.md).
- [Plan del puente](architecture/PLAN_PUENTE_ANDROID_PC_2_0.md), histórico; este contexto prevalece para el estado de implementación.
- [Checkpoint passkeys](../CHECKPOINT_Y_PASSKEYS_1_7.md) y [pendientes de pruebas anteriores](../PENDIENTES_Y_DIAGNOSTICO_1_7_1.md).

Validación de esta entrega: `build/bridge-qr-android-validation.log` y `build/bridge-qr-generator-validation.log` (artefactos locales, no datos personales). Las pruebas sintéticas y los compiladores no sustituyen la confirmación del usuario en hardware.
