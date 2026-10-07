# H5 · interfaz aislada y una aproximación del DNI

2026-10-07. Base: H4 físico validado, PDF visible/invisible y CMS por TLS privado. Checkpoint previo `checkpoint/pre-suite-ui-unified-scan`.

## Interfaz Windows

Módulo JVM `dnie-signing-ui` con Swing de Java 17, sin descargar otro runtime, GUI en EDT y tareas de conexión/firma/preview fuera de EDT. Frontend aislado, sin elevación, instalación de servicio, minidriver, registro PC/SC ni modificación del gestor DNI Bridge actual. Backend nuevo `DesktopSuiteSession` en `dnie-signing-desktop` permite reutilizar identidad DPAPI y conexión continua sin depender de Console; un PIN en JPasswordField por lote, sin argv/eco/persistencia, arreglo limpiado al terminar incluso tras error. Android mantiene CAN/NFC/PACE. CLI conserva compatibilidad y su ruta existente.

Funciones: conexión IP PC/Android, TXT/QR opcional para primer emparejamiento, misma identidad recordada, cola de hasta 50 archivos, PDF visible/invisible/CMS separado, página, campos, motivo/lugar, card/minimal, selección y redimensionamiento del área mediante ratón. Resultado se abre desde la carpeta del trabajo. Documentos no PDF no tienen preview. El dibujo azul es una guía del área, no una simulación del certificado o de la firma final. Parámetros de página/sello son comunes a los PDF del lote; preview por documento permite comprobar dónde ubicarlo. Configuración individual por archivo sigue pendiente.

`DesktopPdfPreview` en `dnie-pdf-desktop` lee páginas con CropBox/rotación y limita imagen a 1400 de lado, cierra cada documento. El frontend no utiliza objetos PDFBox ni código criptográfico; los resultados se publican por los motores existentes. Preview obsoleto se descarta por generación. Cerrar/interrumpir conexión no reproduce firmas; después de un fallo deben revisarse resultados parciales. No declarar presencia física del DNI por tener TLS conectado.

Preferencias de UI guardan solo IP/rutas, no PIN/CAN, claves ni contenidos. Emparejamiento sigue DPAPI/Keystore. Identidad existente ligada a IP PC; cambio de IP requiere tratar el perfil según el contrato vigente (no renovar silenciosamente la confianza). QR requiere el helper/QRCoder instalado; TXT sigue disponible. Java 17 instalado es requisito.

Distribución: `D:/DNIeToolchain/Builds/signing-ui/install/dnie-signing-ui`; acceso `DNIe Suite.lnk` lanza javaw sin consola. También existe `bin/dnie-signing-ui.bat`. El acceso se genera con `tools/create-shortcut.ps1 -JavaHome <JDK17>`; no contiene secretos ni requiere admin. Ventana inicial inspeccionada con PDF sintético y sin conexión real. Prueba física de firma desde GUI pendiente; H4 previo prueba el motor, no el frontend nuevo.

## Android: una lectura de usuario, dos sesiones independientes

APK 57 / `2.0.0-rc9-unified-scan`, paquete debug existente y misma firma. Inicio «Mi DNIe»: botón «Leer identidad y certificados», un CAN y una aproximación mantenida. Primero certificados públicos y después ICAO. La pestaña Identidad y lectura ICAO sola siguen disponibles; no vincular el motor ICAO a PKI RENIEC.

`app/domain/scan/UnifiedDniScan` es coordinación sin APIs Android ni ViewModels. `DniePublicCertificateReader` es un adaptador al transceptor existente. Certificados se tratan individualmente; falta de PKI o un certificado no impide ICAO. Conexión PKI se cierra antes de crear otro `IsoDepCardChannel` para ICAO a partir del mismo Tag; nunca se comparte secureChannel, SSC o sesión PACE. `MainActivity` fabrica canales y proyecta snapshots públicos a IdentityViewModel; los ViewModels no se invocan como servicios de otros motores. Identidad/firma manuscrita ya consumen esos modelos mediante el puente de datos existente.

Lectura/procesado/cierre en dispatcher IO. Propietario único registra el lector incluso antes de que comience el trabajo encolado; cancelación cierra una vez el recurso activo y conserva certificados/bloques completos. Nuevos tags no inician otra lectura mientras esté activa. Pérdida de transporte o integridad detiene todo el intento, sin abrir la siguiente sesión ni hacer operaciones privadas. Excepción `SecureMessagingIntegrityException` añade un tipo semántico al rechazo MAC existente; comparación/MAC/SSC/APDU no se modifican.

**Falta prueba física del paso PKI → canal nuevo ICAO manteniendo el DNI.** Una Tag Android válida no acredita por sí sola que el chip permita abrir la segunda sesión sin retirar el documento. Si falla, usar los resultados y diagnóstico; no reutilizar SSC ni forzar un fallback. Lectura extranjera depende de ICAO/PACE/CAN soportados por el lector actual; no se declara que cualquier DNIe tenga ICAO, ni soporte BAC/MRZ o lectura de pasaportes general. Ausencia de PKI e independencia de ramas están probadas con lectores sintéticos, no otros documentos físicos.

APK: `D:/DNIeToolchain/AndroidPackages/dnie-evolution-2.0.0-rc9-unified-scan-debug.apk`, SHA256 `C9A30D5F8AE3DFC65E6C931A6ED3D945A23E1EA62F93E4A7F3C0C39FCAD2330D`. Instalado por ADB el 2026-10-07 por solicitud del usuario: instalación Success, versionCode 57 y versionName 2.0.0-rc9-unified-scan verificados; MainActivity abierta. Pendiente probar físicamente Mi DNIe y comprobar certificados e Identidad, incluida manuscrita si está disponible. La instalación no acredita esa prueba ni la firma desde GUI.

## Validación y siguiente integración

265 pruebas sin fallos/errores/omisiones en app, protocolo, ICAO, bridge core, signing core/link/desktop y PDF desktop; NFC no tiene tests unitarios propios y compiló. APK Debug y ambas bibliotecas/GUI compilados. Diez pruebas nuevas cubren coordinación/cancelación, orden/cierre independiente, ramas ausentes e integridad, preview y caso de uso de GUI sobre TLS real con Android/tarjeta simulados y dos trabajos consecutivos, firmas verificadas y PIN borrado. Se adaptó el dispatcher del test legado de certificados a IO inyectable; no se eliminó la cobertura anterior.

Logs: `D:/DNIeToolchain/Temp/suite-ui-unified-release-checks.log`, `suite-ui-bridge-regression.log`. Captura UI privada/sintética: `D:/DNIeToolchain/Temp/suite-ui-window-full.png`. Checkpoint final `checkpoint/suite-ui-unified-scan`. Sin archivos personales, APK, credenciales ni capturas en Git.

Secuencia propuesta después de pruebas físicas:

1. GUI → lote real y selector sobre PDF real; Android 57 → lectura unificada y cancelación/retirada. Certificados legibles no acreditan autenticación de chip/CA ni validez de ICAO por sí solos.
2. Integrar **gestión** del puente compatible en un adaptador `BridgeControlGateway`, a través del contrato de `tools/windows-control/ControlPipe.cs` y su `AgentClient`, con verificación del servidor/ACL existentes. No portar operaciones de instalador/admin a Swing ni sustituir el servicio. UI solicita estado/inicio/parada al adaptador; servicio conserva permisos. Aislar perfiles/red/USB y gestor de instalación. Modo firma privada y modo lector compatible deben excluirse al usar el mismo DNI; no compartir canales.
3. Puerto de operaciones públicas `DocumentInspectionGateway` para certificados e ICAO remotos, con modelos de capacidades/resultados por bloque y límites de imágenes/datos. Ampliar protocolo semántico versionado para inspección pública; no convertir el canal de firmas en APDU arbitraria. Autorización Android específica, sin confundir resultados locales Android con lectura disponible ya en Windows. No copiar PIN/CAN ni instalar certificados automáticamente en el almacén Windows.
4. Gestión/exportación de certificados y ficha de identidad en paneles independientes, presentación del nombre sin sufijos técnicos, comprobación de vigencia distinta de fecha del documento. Manuscrita requiere transferencia autorizada y cotejo con identidad/certificado; no reutilizar imagen previa de otro DNI. Después perfil por documento, preview fiel del sello, integración de opciones persistentes y rediseño definitivo.

## Confirmación posterior del usuario

2026-10-07: tras instalar APK 57, el usuario confirma que funciona. Se registra éxito reportado del escaneo combinado; no hay desglose de bloques ni evidencia de cancelación/retirada o firma desde GUI en esta confirmación. También aporta resultados del validador peruano: PDF de la suite válido en integridad/confianza, clasificado PDF_NOT_ETSI. Se abre investigación de conformidad PAdES B/T y requisitos peruanos/europeos, separada de la validación criptográfica.
