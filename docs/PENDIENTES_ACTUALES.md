# Pendientes actuales · Windows rc4 USB / Android rc4-usb

2026-10-09 · Controlador R1: driver2.0.0.3 oem237 instalado, PnP0 y PCSC publicado. Retiro EvtIoStop sin replay, cancelación invalida backend, purge síncrono y host UMDF aislado. Servicio/gestor distinguen ReaderPublished del CardPresent privado. 6797 comprobaciones C++ pasan, 25 PCSC nativas y 13 TLSrelay pasan; Control45 Debug/45 Release. Firma física/suspensión reales pendientes; APK76 intacta. [Detalle](architecture/CONTROLADOR_ENERGIA_Y_PUBLICACION_R1.md).

2026-10-09 · Tras reinicio manual vuelve lector propio a WinSCard y PnP código0, sin tarjeta presente. Revisión de driver detecta falta de EvtIoStop para solicitudes pendientes, coordinación de purge/arranque y host UMDF compartido; estado backend no acredita publicación. Sin cambio de controlador; corrección y reproducción de suspensión pendientes. [Detalle](operations/LECTOR_AUSENTE_PCSC_2026_10_07.md).

2026-10-09 · Reincidencia lector ausente tras suspensión: móvil PACE y backend presentes, WinSCard sin lector propio. Recuperación PnP/SCardSvr no logró publicarlo; PnP exige reinicio y UMDF termina código43. Puente detenido, reinicio manual pendiente; causa inicial sin demostrar. [Evidencia](operations/LECTOR_AUSENTE_PCSC_2026_10_07.md).

2026-10-09 · Conexiones R4: APK76 / 3.0.0-alpha04-links instalada. Puente autentica PC antes de esperar DNI; Suite tiene pantalla completa con presencia separada y diagnóstico de conexión. Red agrupa Wi-Fi/Tailscale, USB separado. Windows host actualizado y GUI Suite selecciona IP vigente; emparejamiento anterior puede requerir TXT/QR actualizado de la misma PC. 154 JVM pasan, 3 omitidas; 93 comprobaciones Windows y 34 Android pasan. Prueba física bilateral y detección de cierre remoto antes de NFC pendientes. [Detalle](architecture/CONEXIONES_INDEPENDIENTES_R4.md).

2026-10-09 · Rediseño Android R3: APK75 / 3.0.0-alpha03-sessions instalada. Disposición compacta basada en Aether, estados con comprobaciones y detalles técnicos desplegables. Puente Tailscale/Wi-Fi/USB con pantalla completa PC–teléfono–DNI y único botón Detener sesión. Vibración común; pulso de 90 ms ejecutado por el sistema verificado. 79 JVM pasan, uno omitido; 33 Android pasan. Sesión física y percepción al leer DNI pendientes. [Detalle](architecture/REDISENO_ANDROID_3_0_R3.md).

2026-10-08 · Rediseño Android R2: APK74 / 3.0.0-alpha02-interface instalada. Apariencia Claro/Oscuro/Del sistema en Configuración; bóveda integrada en navegación principal y paleta común; interiores, vistas previas, Bridge/Suite y diagnóstico usan presentación compartida. Retorno PC y restauración de Inicio corregidos. 77 JVM pasan, uno omitido, 32 Android pasan. Flujos NFC físicos tras rediseño pendientes. [Detalle](architecture/REDISENO_ANDROID_3_0_R2.md).

2026-10-08 · Rediseño Android R1: APK73 / 3.0.0-alpha01-interface instalada. Inicio, navegación de cinco secciones, acceso diferenciado Bridge/Suite y tema Aether adaptados a funciones reales. Cierre inicial de navegación corregido (lista lazy); 77 pruebas JVM pasan, una omitida y 28 Android pasan. Interiores y medición de rendimiento pendientes. [Detalle](architecture/REDISENO_ANDROID_3_0_R1.md).

2026-10-08 · Rendimiento R2: capturas ADB fuera de instrumental localizan picos de medición de Compose al entrar en pestañas y recomposición al editar. Navegación caliente P50 6 ms; edición 11 ms. APK72 intacta; optimización y NFC físico pendientes. [Diagnóstico y límites](architecture/DIAGNOSTICO_RENDIMIENTO_UI_R2.md).

2026-10-08 · Diagnóstico R1: APK72 / 2.0.0-rc24-diagnostics instalada. Extracción directa ADB debug sin exportación manual; metadatos de hilo/proceso/capa y spans. 26 pruebas Android pasan; endpoint revalidado y tres lecturas con proceso cerrado pasan. CAN asíncrono, transacciones por lotes y protección de captura incompleta. Cuadros lentos instrumentados siguen pendientes de atribución. [Detalle](architecture/DIAGNOSTICOS_DIRECTOS_Y_CAPAS_R1.md).

2026-10-08 · Retiro R3: passkeys eliminadas. APK71 / 2.0.0-rc23-core-only instalada por ADB. Limpieza automática al primer arranque verificada: catálogo y aliases ausentes, proveedor no registrado. 165 pruebas JVM y 5 Android pasan; un caso Windows omitido por permisos se verifica en Android. Bóveda y emparejamientos fuera del alcance de borrado. [Detalle](architecture/RETIRO_PASSKEYS_R3.md).

2026-10-08 · Bóveda R7: corregido inicio tras SAF: selector primero, credenciales después y autorización explícita. onPause limpiaba PIN y R6 rechazaba el inicio automático, dejando destino vacío. APK69 instalada por ADB y versión comprobada; 79 pruebas app pasan y 3 pruebas instrumentadas de interfaz pasan (8,537 s). El selector se simula con registro aislado; exportación/importación con SAF real y DNI físico pendientes. [Detalle](architecture/BOVEDA_SELECTOR_Y_AUTORIZACION_R7.md).

2026-10-08 · Bóveda R6: APK68 instalada; biblioteca única con Cifrar/Descifrar/Importar/Exportar. Respaldo autosuficiente DNIEBKP2 comprimido y cifrado por contraseña. Importar verifica DNI destino y recifra todo, actualiza UUID coincidentes sin duplicarlos, conserva archivos ajenos y usa journal durable. 137 pruebas JVM y 4 instrumentadas Android pasan; NFC/SAF reales y cruce Windows pendientes. [Detalle](architecture/BOVEDA_RESPALDO_UNIFICADO_R6.md).

2026-10-08 · Bóveda R5: APK67 instalada. Mis archivos/Protección/Copias; protección y recuperación de toda la bóveda (incluida papelera), PIN introducido una vez, ZIP recuperado verificado antes de publicar. 79 pruebas app + 23 motor sin fallos; dos pruebas instrumentadas de recuperación pasan. Prueba visual superada en teléfono desbloqueado (1 test, 2.096 s). Recifrado con DNI nuevo manual y lote NFC/SAF real pendientes. [Detalle](architecture/RECUPERACION_COMPLETA_Y_FLUJO_R5.md).

2026-10-07 · Bóveda UI R4: APK66 instalado; Archivos/DNI/Recuperar/Más separados, tarjetas compactas, selección múltiple para organizar/papelera/restaurar. 76 pruebas JVM sin fallos y 1 prueba visual en teléfono superada; motor/PACE intactos. [Detalle](architecture/BOVEDA_UI_SIMPLE_R4.md).

2026-10-07 · Recuperación Android R3: APK65 instalado por ADB; motor aislado + adapter IO/SAF + UI. Paquete por archivo preparado con DNI original, recuperación con paquete y contraseña. 125 resultados JVM sin fallos; APK principal/auxiliar compilados. Auxiliar instalado al reintentar; 1 prueba instrumentada sintética superada en teléfono (4.708 s). Flujo real CIF/SAF y cruces pendientes. [Detalle](architecture/RECUPERACION_ANDROID_R3.md).

2026-10-07 · Recuperación R2: 23 pruebas ejecutadas sin fallos; incluye límites, errores de almacenamiento/proveedor, cancelación con progreso y concurrencia. Corregido cierre de entrada ante staging inválido y cancelación previa a operación de tarjeta. Android y pruebas físicas pendientes. [Detalle](architecture/PRUEBAS_RECUPERACION_WINDOWS_R2.md).

2026-10-07 · Recuperación R1: módulo JVM aislado y 12 pruebas nuevas Windows superadas; formato actual preservado. No habilitado en APK/Suite. Pendientes Android, cruces, cobertura y CIF hard físico; passkeys no modificadas. [Resultados](architecture/PRUEBAS_RECUPERACION_WINDOWS_R1.md).

2026-10-07 · P0 conceptual de bóveda/passkeys: renovación puede sustituir la privada aun en el mismo chip. Respaldos actuales siguen ligados a la clave anterior y no recuperan tras su eliminación; migración actual exige abrir con clave vieja y nueva después. Recuperación independiente/asistente previo todavía sin implementar ni activar. [Auditoría y alternativas](architecture/RECUPERACION_Y_RENOVACION_DNI.md).

2026-10-07 · Registro estable SQL: SQLite portable de archivos, módulos, símbolos, dependencias, mapa de funciones y pendientes revisados, con evidencia documental. Consulta/actualización offline por CLI Python; inventario verificable por SHA256 y exportación SQL versionable. [Uso y límites](project-registry/README.md).

2026-10-07 · H15 físico confirmado: usuario comunica aceptación de la firma nueva. PDF aportado contiene cuatro firmas criptográficamente válidas; la última incluye issuerSerial y TSA válido. Se cierra el pendiente de aceptación externa de la corrección; la primera firma conserva la omisión antigua. [Evidencia y límites](suite/REFIRMA_ISSUER_SERIAL_H15.md).

2026-10-07 · H15: ReFirma PCX/DSS 5.4.3 exige issuerSerial en signingCertificateV2 mediante política PE. Corrección del motor CMS compartido Android/Windows, APK64 y GUI H15 compiladas; 247 pruebas pasan. Prueba sintética con el motor real cambia emisor/serie de false a true. Pendiente validar firma NUEVA con DNI y TSA en ReFirma PCX; las firmas anteriores conservan su integridad pero no se pueden parchear. [Causa, evidencia y entregables](suite/REFIRMA_ISSUER_SERIAL_H15.md).

2026-10-07 · H14: batería sintética forzada (--rerun), 330 pruebas pasan, 2 opt-in externas omitidas, 0 fallos/errores. Nuevos casos TSA timeout/respuestas inválidas y lote PDF fallido con TXT posterior válido sin repetir firma. Sin DNI ni cambios de producción; recuperación persistente de solo TSA y preflight online automático siguen sin implementar. [Resultados y límites](suite/VALIDACIONES_SINTETICAS_H14.md).

2026-10-07 · Prioridad ajustada por el usuario: lote PC01 cerrado como satisfactorio; confirma también el segundo PDF validado. Integración visual completa Suite/Bridge pasa a 3.0 con rediseño. Distribución se pospone hasta pulir Android y Windows. Recuperación de solo TSA y validación online previa automática siguen sin implementar; no se confunde confianza del usuario con evidencia de funcionalidades ausentes. LT/LTA se conserva como evolución técnica, sin prioridad inmediata.

2026-10-07 · H12 físico superado desde Windows: usuario aporta Firma Perú VÁLIDO para acta PDF PAdES_BASELINE_T (raíces firmante/TSA en TSL Sí, TSA RENIEC TSU-2026-01 21:08:57 UTC-05) y TXT/.p7s CAdES_BASELINE_B válido sin TSA. Falta resultado del segundo PDF, confirmación 3/3/PIN único y cambio Bridge↔Suite. [Evidencia y alcance](suite/TSA_RENIEC_WINDOWS_H12.md).

2026-10-07 · H13: integración inicial Suite + Bridge con módulo JVM de coordinación y adaptador .NET opcional al servicio autenticado. GUI integra estado, abrir gestor y detener puente; permiso local evita cambiar de modo durante suite, sin exclusión global entre procesos. 43 pruebas y smoke real de consulta pasan, APK63 recompilado sin cambio. Paquete en D:/DNIeToolchain/Builds/suite-bridge-h13; configuración Bridge conserva ventana nativa y ambas distribuciones autónomas. [Capas, distribución y pendientes](suite/INTEGRACION_BRIDGE_MODULAR_H13.md).

2026-10-07 · H12 Windows: TSA RENIEC opcional integrada en PDF visible/invisible de GUI y consola (--pdf-tsa reniec), reutilizando el motor Android verificado. 76 pruebas pasan y ejecutable distribuido produce PDF sintético con TSA real verificado. Distribuciones nuevas en D:/DNIeToolchain/Builds/tsa-reniec-h12; APK63 no cambia. Pendiente firma Windows con DNI real y validación externa. [Arquitectura, uso y prueba](suite/TSA_RENIEC_WINDOWS_H12.md).

2026-10-07 · H11 físico superado: usuario aporta resultado de Firma Perú del PDF firmado con APK63 y DNI real: VÁLIDO, integridad conservada, PAdES_BASELINE_T y raíces del firmante y TSA en TSL Sí. TSA RENIEC TSU-2026-02, hora 18:13:47 UTC-05. Quedan LT/LTA, recuperación de solo sellado e integración Windows. [Evidencia y alcance](suite/ANDROID_TSA_RENIEC_H11.md).

2026-10-07 · H10 externo superado: Firma Perú reconoce ambos PDF sintéticos como PAdES_BASELINE_T e íntegros; raíz TSA RENIEC en TSL Sí, FreeTSA No. Resultado global indeterminado con error de cadena del firmante sintético, como corresponde a esta prueba. Pendientes módulo TSA Android, confianza/revocación local ROOT5, disponibilidad/condiciones y PDF con DNI real. APK62 sin cambios. [Evidencia y límites](suite/TSA_EXPERIMENTO_REAL_H10.md).

2026-10-07 · H9b físico superado: usuario confirma APK62 con vigencia/uso aptos, titular GOOD por OCSP firmado/vigente, ambas CA intermedias GOOD por CRL vigente/firmada y ruta PKIX verificada con TSL Perú autenticada secuencia25. Vigencia agregada hasta 2026-10-07 17:33:45 UTC-05, limitada por OCSP; requiere nueva consulta al caducar. La causa del UNKNOWN anterior no quedó demostrada. Preflight automático, histórico y TSA pendientes. [Evidencia y límites](suite/REVOCACION_CA_DIAGNOSTICO_H9B.md).

2026-10-07 · H9b: usuario confirma APK61 con TSL autenticada y ruta PKIX verificada; titular GOOD, revocación global UNKNOWN. CRL de ambas CA verificadas en PC; causa del fallo en móvil aún sin determinar. Motor extraído por CA con resultado/método/código seguro, panel y registro por CA, distinción de evidencia caducada durante consulta. 236 pruebas pasan; APK62 compilado, prueba física pendiente. [Detalle](suite/REVOCACION_CA_DIAGNOSTICO_H9B.md).

2026-10-07 · H9a: APK60 físico reporta TSL indeterminada. Reproducida dependencia DatatypeFactory/Xerces ausente con runtime XML Android14; reemplazo por lector estricto de fechas y diagnóstico por etapa. 233 pruebas/Debug pasan, APK61 compilado. DOM Android + BC verifican TSL y ruta; teléfono pendiente. No se relaja confianza ni se declara TSA. [Detalle](suite/TSL_ANDROID_COMPATIBILIDAD_H9A.md).

2026-10-07 · H9: TSL Perú autenticada con ancla AAC independiente, firmante autorizado y CRL; ruta PKIX completa y revocación de CA intermedias. Prueba real JVM TRUSTED/GOOD, 229 pruebas. Android APK60 con panel y rollback persistido; prueba física pendiente. Consulta manual, preflight automático/histórico/TSA pendientes. Usuario confirma H8 en teléfono. [Detalle y límites](suite/CONFIANZA_TSL_CADENA_H9.md).

2026-10-07 · H8: módulo JVM dnie-validation-core, control local previo a CMS, consulta Android manual OCSP/CRL y rechazo de revocación conocida. 215 pruebas pasan y OCSP real firmado devuelve GOOD con confianza UNKNOWN. APK 59 compilado; UI física pendiente. Confianza TSL, cadena completa y preflight online automático siguen pendientes. [Arquitectura, límites y rutas](suite/VALIDACION_CERTIFICADOS_H8.md).

2026-10-07 · H7: usuario confirma PDF Android 58 VÁLIDO y PAdES_BASELINE_B. Perfil DER sin signingTime y /M UTC trasladado a PDF desktop; 104 pruebas y demo sintético .NET pasan. GUI/CLI nuevas en D:/DNIeToolchain/Builds/pades-baseline-b, distribuciones anteriores preservadas. Pendiente clasificación externa de PDF Windows con DNI, revocación/TSL integradas y TSA. [Estado y rutas](suite/PADES_BASELINE_DESKTOP_H7.md).

2026-10-07 · H6 Android: perfil PAdES explícito sin signingTime CMS, DER y /M UTC; CMS .p7s y Windows conservan perfil previo. 189 pruebas finales pasan, APK 58 compilado y pendiente de instalar (ADB sin dispositivo). Checkpoint previo de código/APK 57 en D:. Siguiente: PDF físico firmado en Android y clasificación Baseline B en validador antes de portar a Windows. [Detalle y evidencia](suite/ANDROID_PADES_BASELINE_H6.md).

2026-10-07 · Prioridad de conformidad: validador reporta PDF_NOT_ETSI para la suite pese a integridad válida. Auditoría confirma signingTime CMS incompatible y BER en PDF real; investigar/corregir perfil PAdES B antes de TSA T, luego LT/LTA. Sin cambios de motor en este diagnóstico. [Plan y evidencia](suite/PADES_CONFORMIDAD_PLAN.md).

2026-10-07 · H5: GUI aislada `dnie-signing-ui`, preview/selector y firma privada sin consola; APK 57 implementa escaneo combinado certificados→ICAO con sesiones independientes y cancelación que conserva completos. 265 pruebas pasan. GUI inspeccionada sin DNI; firma GUI pendiente. APK 57 instalado por ADB y versión verificada el 2026-10-07; usuario confirma que funciona el escaneo unificado, sin desglose de bloques ni prueba de cancelación. DNI Bridge/inspección pública Windows son próximos adaptadores, no funciones ya integradas. [Contexto, rutas y límites](suite/INTERFAZ_AISLADA_Y_ESCANEO_UNIFICADO_H5.md).

2026-10-07 · H4 físico validado: trabajo `b9b8c2f6-18c2-4a86-bab4-3e9dc173eb65`, PDF visible 1/1, verificado .NET/certificado DNI y render legible. Posición fija tapa contenido original; siguiente prioridad selector/preview y presentación del nombre del certificado. [Detalle](suite/PDF_VISIBLE_H4.md).

2026-10-07 · H4: sello visible de escritorio modular implementado, page/box/fields/style por argumentos y apariencia del campo firmada incrementalmente. Pruebas sintéticas de giros/recortes, firmas sucesivas y render pasan; falta sello físico con DNI. H3 cierre confirmado manual Ctrl+C. [Uso y límites](suite/PDF_VISIBLE_H4.md).

2026-10-07 · H3 físico validado: trabajo `ed878567-300a-4a38-aeba-e56cd47eb640` 3/3 (dos PDF integrados y un CMS DOCX), verificados .NET con certificado DNI coincidente y rechazo de alteración. Cierre posterior confirmado manual por Ctrl+C; no es incidente de conexión. Sigue pendiente prueba específica de dos lotes consecutivos en mismo TLS. [Evidencia y límites](suite/PDF_INVISIBLE_H3.md).

2026-10-07 · H3: PDF integrado invisible de escritorio implementado en módulo JVM `dnie-pdf-desktop` (PDFBox 3.0.8). Por defecto PDF → `_firmado.pdf`, otros → `.p7s`; `--pdf-mode detached` conserva el modo anterior. Simulación del ejecutable verificada independientemente .NET; prueba física con DNI pendiente. APK instalado 56 y transporte/motor NFC sin cambios funcionales. [Arquitectura, límites y procedimiento](suite/PDF_INVISIBLE_H3.md).

Actualizado 2026-10-07 tras primera firma remota física de suite H1b (APK 54), además de la validación del motor compartido de firma. El diagnóstico del teléfono confirma `2.0.0-rc5-signing`, correspondiente al candidato compilado 53. Este listado sustituye los encabezados de pendientes de versiones anteriores. Diferenciar implementación, simulación y validación con dispositivo real.

## Siguiente secuencia

| Prioridad | Trabajo | Estado / criterio de cierre |
| --- | --- | --- |
| P1 | Causa de lector ausente de PC/SC pese a backend conectado | Usuario confirma recuperación tras reiniciar. Falta determinar causa y reproducibilidad: capturar enumeración/arranque PnP, UMDF y SCardSvr si se repite, distinguiendo estado privado del backend y visibilidad real. No reinstalar minidriver sin evidencia. [Diagnóstico](operations/LECTOR_AUSENTE_PCSC_2026_10_07.md). |
| P1 | Firma remota privada de suite, sin PC/SC | H1b implementa comando real/TLS inverso y aprobación Android/PIN local. Tests de red TLS con tarjeta simulada pasan, APK 54 compilado. Primera firma física LAN completada (1/1 CMS) y verificada .NET, con certificado DNI coincidente y rechazo de contenido alterado. Lote físico adicional 3/3 verificado .NET (TXT/CSV/binario). H2 añade PC recordada, canal continuo y PIN PC por trabajo; APK 55 instalado, 113 pruebas pasan. H2 físico: un trabajo interrumpido y otro firmado/verificado dentro del mismo TLS. APK 56 añade presencia NFC/recuperación. rc8 físico: reconexión sin QR con misma huella reportada y lote 3/3 CMS verificado .NET. Faltan dos trabajos exitosos seguidos en mismo TLS y secuencia detallada de retirada/reaproximación con estados visibles; USB/PDF pendientes. [H2](suite/SESION_CONTINUA_H2.md). No compartir SSC ni ampliar SYSTEM. [Procedimiento](suite/FIRMA_REMOTA_H1B.md). |
| P1 | Ampliar regresión física de lotes Android rc5 | Lote físico de cuatro archivos y ZIP verificados: dos PDF, dos CMS y una firma PDF anterior conservada; mismo certificado instalado Windows. Una entrada de PIN, una apertura PACE, 3.721 s, sin fallas. Pendientes: lotes mayores, interrupción/cancelación física y perfilado de frames lentos. Ver MOTOR_FIRMA_LOTES_RC5.md. |
| P0 | Arranque real después del reinicio | Comprobar servicio automático y lector PnP, sin host/red hasta pulsar Iniciar. Abrir dos veces el gestor y comprobar una ventana; iniciar/parar sin UAC. No asumir que la prueba previa de SCM acredita un reinicio real. |
| P0 | Identificar y retirar el listener legado | Antes del reinicio hay `0.0.0.0:35963` bajo WUDFHost. Identificar su dispositivo/controlador; retirar únicamente el componente legado si se confirma. No matar WUDFHost genéricamente, ni tocar SCardSvr/Hello. El lector propio no usa red/VPCD. Cierre: sin listener legado y lector propio operativo. |
| P0 | Regresión Tailscale con gestor nativo | Sesión física, lectura y una firma ReFirma/Firma Perú con verificación independiente; manualmente retirar/volver a presentar el DNI y reconectar. Evidencia histórica funciona, pero no valida la GUI/servicio recién instalados. |
| P1 | USB AOA real tras reinicio | Usuario confirma funcionamiento y firma después del aislamiento. Falta adjuntar/validar artefacto independiente y cerrar estabilidad, reconexión y parada repetidas. APK 52 y gestor Release actualizado. Ver AISLAMIENTO_USB_USBDK.md. |
| P1 | LAN TLS real | Usar IP Wi-Fi real del teléfono, ruta PC→Android y huella estable. Localizar fallo por etapa: TCP, DNIREADY1, TLS, token, PC/SC, NFC. Validar firma y reconexión sin abrir reglas globales ni desactivar cifrado. |
| P1 | Ciudadano Digital sobre candidato actual | Activar selector opcional, comprobar lector exacto y repetir reconocimiento. El hito previo es «DNIe ya tiene certificados instalados» y paso a autenticación facial. No continuar a descargar certificados/cambio de PIN como prueba automática. |

## Seguridad y distribución

- USB P0: identificado `UsbDk_StartRedirect` en `usb_redirect_initial`. Implementado aislamiento en auxiliar, Job Object, cancelación acotada y cuarentena si no termina. Reconectar el cable liberó el auxiliar físico. Usuario confirma funcionamiento/firma; continúa pendiente eliminar el bloqueo nativo y validar ciclos/artefactos. Ver AISLAMIENTO_USB_USBDK.md; una firma no acredita estabilidad en reconexión.

- Escalabilidad: conservar el lector funcional y simplificar su instalación. El usuario prioriza una suite propia de firma sobre la propuesta portable LAN. Revisión y secuencia de extracción en [núcleo de firma](architecture/NUCLEO_FIRMA_SUITE_PROPIA.md): contratos JVM neutrales, casos de uso compartidos, consolidación Android y adaptador PDF/PCSC de escritorio. No se cambió el driver ni el motor durante la revisión. Las alternativas anteriores siguen documentadas en VIABILIDAD_DISTRIBUCION_SIN_CONTROLADOR.md.

| Trabajo | Pendiente concreto |
| --- | --- |
| Consentimiento por operación del puente | La PC autorizada puede pedir operaciones mediante clientes PC/SC. Diseñar confirmación Android para operaciones privadas sin confundirla con selector o ACL local; mantener compatibilidad y pruebas de cancelación. No declararlo ya implementado. |
| Controlador de producción | El controlador instalado es de pruebas. Validar carga después de reiniciar y planificar firma/distribución admitida por Windows. Certificado personal DNI o confianza raíz no reemplazan los requisitos de firma del controlador. |
| Firma de editor / instalador | Paquete nativo sin Authenticode público. SHA256 verifica consistencia, no identidad del editor. Planificar instalación/desinstalación y actualización de producción. |
| Servicio y actualizaciones | Probar fallo de arranque, cierre de SCM con sesión real, recuperación ante crash, actualización interrumpida/rollback, disco D no disponible y limpieza de versiones antiguas. No afirmar cobertura por los 10 controles básicos existentes. |
| Convivencia de middleware | Minidriver DNI aislado en ensayos anteriores; preservar respaldo y estado. Investigar compatibilidad separada antes de restaurarlo, porque antes generaba PACE PC y cierres. |
| Sesiones prolongadas | Probar inactividad, pérdida NFC, desconexión de cable/red, fondo/bloqueo Android y varias presentaciones. El host persistente no significa mantener una sesión criptográfica válida sin el DNI. |

## Otras funciones y deuda restante

- QR FIDO: probar ceremonia completa navegador→teléfono→proveedor→sitio. Abrir la cámara no acredita WebAuthn ni transporte híbrido propio; caBLE/CTAP híbrido propio no está implementado. Reproducir el error GMS/ML Kit si vuelve a aparecer con la versión actual.
- Bóveda/passkeys: migración con dos DNI reales y certificados distintos, recuperación/cancelación SAF, muerte de proceso y restauraciones parciales. Las simulaciones y aperturas de catálogo no sustituyen esa prueba. Mantener separado vencimiento del DNI y vencimiento del certificado.
- Passkeys: comprobar delegación biométrica en teléfono, cancelación, cambio de inscripción y vuelta al DNI. La bóveda conserva requisito DNI y no usa biometría.
- Rendimiento Android: recorrido repetible de escritura, scroll y pestañas, con medición comparable; hay mejoras confirmadas por el usuario pero no evidencia de eliminación de todos los tirones.
- Compatibilidad Windows adicional: XML y lotes ReFirma son candidatos de ensayo; CMS/P7S y PDF tienen evidencia previa. No reenviar una firma por timeout ni hacer intentos PIN con datos simulados contra el DNI real.
- Mantenimiento: revisión de contratos/errores, estilo y legibilidad C#, matriz de pruebas, empaquetado portable de dependencias opcionales y versión de artefactos. Separación en archivos/módulos no equivale a deuda técnica cero.
- Versión 3.0: rediseño completo de navegación/presentación después de consolidar 2.0. Usar los mismos casos de uso; no mover PACE, SSC, sockets o APDU a UI.

## Ya resuelto: no reabrir sin una regresión nueva

Gestor nativo y servicio permanente; control desde cuenta normal; inicio idempotente; instancia única de ventana; limpieza al detener; identidad TLS estable; QR local en memoria; separación Debug/Release Windows. El pipe ya corrige consulta de identidad bajo SYSTEM y pérdida de respuesta antes del acuse. En rc3 se retiraron límites de inactividad/30 minutos del host autenticado y se introdujeron perfiles cifrados por transporte.

Resultados exactos y procedimiento de prueba: [estado/retomar](operations/RETOMAR_TRAS_REINICIO_RC4.md). No usar PIDs, monitores o launchers de documentos históricos como estado actual.
