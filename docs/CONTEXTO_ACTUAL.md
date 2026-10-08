# Estado vigente · corrección USB tras reinicio

2026-10-08 · Passkeys R1: acceso perdido al simplificar Boveda R6; proveedor y catalogo siguen implementados. Usuario elige almacen independiente autorizado inicialmente con DNI. Diseno y migracion pendientes; sin cambio automatico de politica ni APK nueva. [Detalle](architecture/PASSKEYS_ALMACEN_INDEPENDIENTE_R1.md).

2026-10-08 · Bóveda R7: corregido inicio tras SAF: selector primero, credenciales después y autorización explícita. onPause limpiaba PIN y R6 rechazaba el inicio automático, dejando destino vacío. APK69 instalada por ADB y versión comprobada; 79 pruebas app pasan y 3 pruebas instrumentadas de interfaz pasan (8,537 s). El selector se simula con registro aislado; exportación/importación con SAF real y DNI físico pendientes. [Detalle](architecture/BOVEDA_SELECTOR_Y_AUTORIZACION_R7.md).

2026-10-08 · Bóveda R6: APK68 instalada; biblioteca única con Cifrar/Descifrar/Importar/Exportar. Respaldo autosuficiente DNIEBKP2 comprimido y cifrado por contraseña. Importar verifica DNI destino y recifra todo, actualiza UUID coincidentes sin duplicarlos, conserva archivos ajenos y usa journal durable. 137 pruebas JVM y 4 instrumentadas Android pasan; NFC/SAF reales y cruce Windows pendientes. [Detalle](architecture/BOVEDA_RESPALDO_UNIFICADO_R6.md).

2026-10-08 · Bóveda R5: APK67 instalada. Mis archivos/Protección/Copias; protección y recuperación de toda la bóveda (incluida papelera), PIN introducido una vez, ZIP recuperado verificado antes de publicar. 79 pruebas app + 23 motor sin fallos; dos pruebas instrumentadas de recuperación pasan. Prueba visual superada en teléfono desbloqueado (1 test, 2.096 s). Recifrado con DNI nuevo manual y lote NFC/SAF real pendientes. [Detalle](architecture/RECUPERACION_COMPLETA_Y_FLUJO_R5.md).

2026-10-07 · Bóveda UI R4: APK66 instalado; Archivos/DNI/Recuperar/Más separados, tarjetas compactas, selección múltiple para organizar/papelera/restaurar. 76 pruebas JVM sin fallos y 1 prueba visual en teléfono superada; motor/PACE intactos. [Detalle](architecture/BOVEDA_UI_SIMPLE_R4.md).

2026-10-07 · Recuperación Android R3: APK65 instalado por ADB; motor aislado + adapter IO/SAF + UI. Paquete por archivo preparado con DNI original, recuperación con paquete y contraseña. 125 resultados JVM sin fallos; APK principal/auxiliar compilados. Auxiliar instalado al reintentar; 1 prueba instrumentada sintética superada en teléfono (4.708 s). Flujo real CIF/SAF y cruces pendientes. [Detalle](architecture/RECUPERACION_ANDROID_R3.md).

2026-10-07 · Recuperación R2: 23 pruebas ejecutadas sin fallos; incluye límites, errores de almacenamiento/proveedor, cancelación con progreso y concurrencia. Corregido cierre de entrada ante staging inválido y cancelación previa a operación de tarjeta. Android y pruebas físicas pendientes. [Detalle](architecture/PRUEBAS_RECUPERACION_WINDOWS_R2.md).

2026-10-07 · Recuperación R1: módulo JVM aislado y 12 pruebas nuevas Windows superadas; formato actual preservado. No habilitado en APK/Suite. Pendientes Android, cruces, cobertura y CIF hard físico; passkeys no modificadas. [Resultados](architecture/PRUEBAS_RECUPERACION_WINDOWS_R1.md).

2026-10-07 · Investigación de riesgos de recuperación: Cryptomator/Bitwarden distinguen reenvolver y rotar claves; contraseñas anteriores siguen útiles en copias viejas. age prohíbe mezclar su destinatario password con otros. Se recomienda evaluar paquete separado para preservar CIF hard portable; diseño/parametrización e implementación siguen pendientes. [Análisis y controles](architecture/RIESGOS_RECUPERACION_PASSWORD_Y_DNI.md).

2026-10-07 · Investigación de recuperación: casos KeePassXC/YubiKey y age, protección intermedia de claves Bitwarden y límites Keystore. Propuesta: passkeys con almacenamiento independiente opcional; bóveda con clave propia estable, DNI y recuperación alternativa. Ningún cambio de autorización/cifrado activado. [Fuentes y comparación](architecture/INVESTIGACION_RECUPERACION_COMUNIDAD.md).

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

2026-10-07, suite rc8 física: PC recordada conserva huella, reconexión reportada sin QR y lote nuevo de tres archivos 3/3 con PIN único. Tres CMS verificados .NET (integridad, certificado DNI, rechazo de alteración). Usuario finalizó después con Ctrl+C. No confundir transcript de intentos anteriores con nuevos trabajos. [Detalle](suite/SESION_CONTINUA_H2.md).

2026-10-07, suite rc8: APK 56 instalado, estados de DNI separados de TLS, monitor NFC nativo sin APDU, espera de tag nuevo si el previo desaparece, timeout sin DNI conserva TLS, diagnóstico por fase sin secretos y QR automático al primer enrolamiento. 116 pruebas pasan. H2 físico previo: segundo trabajo 1/1 verificado .NET tras primero interrumpido en la misma conexión; causa del primero no identificada. Estados/retirada rc8 pendientes de ensayo físico. [Detalle](suite/SESION_CONTINUA_H2.md).

2026-10-07, suite H2 implementado e instalado: APK 55 / rc7-continuous-suite. PC recordada (DPAPI/Keystore), conexión privada continua, trabajos individuales/lotes y PIN desde Windows por trabajo. 113 pruebas pasan, incluyendo TLS continuo de dos trabajos, rechazo sin reintento y persistencia DPAPI. Evidencia física H2 pendiente; H1b sigue validado. [Uso y límites](suite/SESION_CONTINUA_H2.md).

2026-10-07, suite H1b: lote físico remoto de tres archivos completado (3/3 TXT/CSV/binario, PRIVATE_ANDROID). Tres CMS verificados independientemente .NET con certificado DNI coincidente y rechazo de contenido alterado. Sin cambio de APK/motor; pendientes cancelación/pérdida física, identidad permanente y PDF integrado. [Evidencia](suite/FIRMA_REMOTA_H1B.md).

2026-10-07, suite H1b: implementados comando `remote`, TLS inverso (PC inicia TCP y es servidor TLS), huella/token de un trabajo y pantalla Android Certificados → Suite con PIN local y NFC. APK 54 / rc6-remote-signing. Tests TLS con tarjeta simulada, huella/token erróneos y CMS pasan; primera firma física LAN completada (1/1 CMS, backend PRIVATE_ANDROID) y verificada con .NET: integridad, certificado del DNI y rechazo al alterar contenido. Revocación no comprobada. Sin cambios al servicio/lector compatible. [Procedimiento](suite/FIRMA_REMOTA_H1B.md).

2026-10-07, decisión vigente de suite: conexión privada directa PC↔Android, **sin depender del lector PC/SC ni publicar DNI a Windows**. Implementado H1a `dnie-signing-link`: protocolo semántico, endpoint autorizado con PIN móvil, adaptador del motor CMS y flujos independientes. Dieciséis pruebas nuevas (15 canal, 1 CMS) y APK Debug compilados; tarjeta y transporte de prueba simulados. Falta emparejamiento/transporte físico, consentimiento UI y primera firma real. [Contexto vigente](suite/CONTEXTO_SUITE_FIRMA.md). Las propuestas PC/SC siguientes son historia superada para la suite; el puente compatible permanece preservado.

2026-10-07: primer hito de suite local implementado en `dnie-signing-desktop`: CLI JVM y backend de simulación explícito, reutilizando CMS/lotes. Ocho pruebas nuevas pasan y tres archivos sintéticos produjeron CMS verificados independientemente .NET. Contexto propio permanente: [CONTEXTO_SUITE_FIRMA.md](suite/CONTEXTO_SUITE_FIRMA.md). Próximo hito: cliente PC/SC y primera firma con DNI. Esta entrega no firma con DNI, no tiene PDF integrado/UI ni modifica el puente instalado.

2026-10-07, prioridad acordada para la próxima sesión: suite local por adaptación del comportamiento Android probado. Firma Perú/ReFirma PCX funcionales acreditan la capacidad del lector Windows. Orden actualizado: checkpoint y CLI JVM, primera firma CMS, lotes CMS, PDF sin visor, contratos locales y finalmente UI/editor PDF. Documento operativo: [plan de desarrollo](architecture/PLAN_SUITE_FIRMA_PROPIA.md). Hoy se preparó el plan; no se inició implementación ni nuevas pruebas físicas.

2026-10-07: subagente completó [plan de suite propia](architecture/PLAN_SUITE_FIRMA_PROPIA.md), revisado por el agente principal. Recomendación: GUI .NET y auxiliar JVM de usuario que reutilice CMS/lotes, con adaptador PDF de escritorio y cliente PC/SC independiente del backend IOCTL. Primer ensayo: sesión limpia y ReFirma → suite → ReFirma, porque el estado PC-PACE limita comandos claros después de negociar. Inventario de candidatos MIT fijado por commit; PDFBox Apache-2.0 y avisos transitivos separados. Plan documental, sin nuevas instalaciones ni cambios en motores.

2026-10-07, última evidencia: el teléfono ejecuta `2.0.0-rc5-signing`; lote físico de cuatro archivos completado en 3.721 s con una entrada PIN y una apertura PACE. ZIP verificado con .NET SignedCms: dos PDF y dos CMS separados válidos, más una firma PDF anterior válida; certificado coincide con el instalado Windows. Diagnóstico restringido a los últimos diez minutos del archivo, sin fallas; frames lentos siguen pendientes. Detalle y ubicación de evidencia privada en [motor de lotes rc5](architecture/MOTOR_FIRMA_LOTES_RC5.md).

2026-10-07: primer incremento de la suite propia, enfocado en motor Android. Respaldo comprimido/bundle/APK 52 guardado en D: antes de editar; tag `checkpoint/pre-signing-suite-2026-10-06`. Se extrajeron contratos y casos de uso a `dnie-signing-core` JVM y se añadió autorización por operación dentro de una sola sesión de lote. El usuario confirmó que antes solo se firmaba el primer archivo de varios añadidos juntos. Esa causa física aún requiere diagnóstico/prueba; detalles en [motor de lotes rc5](architecture/MOTOR_FIRMA_LOTES_RC5.md). 232 pruebas sin fallos; APK Debug 53 / `2.0.0-rc5-signing` compilado, no instalado. Windows no se modificó.

2026-10-06, dirección más reciente: el usuario prioriza desarrollar una suite propia de firma y conservar el lector actual, facilitando su instalación. Revisión del motor Android completada en [núcleo de firma y suite propia](architecture/NUCLEO_FIRMA_SUITE_PROPIA.md). CMS y firma separada ya son JVM; hay que separar contratos del módulo PDF Android y adaptar PDFBox para escritorio. La ruta de producción pasa por BatchSigner, no por el overload legado del ViewModel. Checks Gradle satisfactorios; revisión documental sin cambios operativos ni APK nuevo.

2026-10-06: el usuario confirma que funciona y firma, en el contexto de la prueba USB. Evidencia reportada por el usuario; no se recibió artefacto nuevo para verificación independiente. La estabilidad de reconexión sigue pendiente. Evaluación de escalabilidad y alternativas sin lector propio en [viabilidad de distribución](architecture/VIABILIDAD_DISTRIBUCION_SIN_CONTROLADOR.md): conservar PC/SC compatible y estudiar edición portable por API/PKCS#11. No se cambiaron componentes operativos en esa evaluación.

Actualización posterior: Windows aísla el motor USB en un auxiliar con Job Object, cancelación y límite exclusivo para llamadas nativas de preparación. Se identificó bloqueo físico en `UsbDk_StartRedirect` antes de AOA/PACE; reconectar liberó el auxiliar. Registro y límites en [aislamiento USB](architecture/AISLAMIENTO_USB_USBDK.md). Android sigue en 52. USB todavía necesita validación TLS y firma; no declarar transporte estable por tener aislamiento.

Actualización 2026-10-06: Android `2.0.0-rc4-usb` (52) instalado y gestor/servicio Windows Release actualizado. Tailscale y LAN parecen funcionar según el usuario; no se documenta una nueva firma de regresión. Se corrigió el registro Android de accesorio AOA, el permiso USB y la espera/estado Windows. Prueba física sin DNI: host llegó a `usb_waiting_android` y Android ofreció abrir DNIe Evolución, confirmado por el usuario. Falta la conexión TLS y firma por USB. Detalles y pruebas en [corrección USB AOA](architecture/CORRECCION_USB_AOA_RC4.md). El host se detuvo tras la prueba, sin tarjeta publicada; servicio permanece activo. Distribuciones/evidencia en `D:/DNIeToolchain/Checkpoints/usb-aoa-rc4`.

## Registro previo conservado: antes del reinicio
Actualizado: 2026-10-06; comprobación operativa 2026-10-07 02:42 UTC. Este archivo describe el estado actual. Los informes con números de versión anteriores son historia, no instrucciones para iniciar hosts adicionales.

## Por dónde retomar

1. [Estado y procedimiento tras reiniciar](operations/RETOMAR_TRAS_REINICIO_RC4.md).
2. [Arquitectura actual y límites de las capas](architecture/ARQUITECTURA_ACTUAL_2_0.md).
3. [Pendientes priorizados y criterios de cierre](PENDIENTES_ACTUALES.md).
4. [Implementación del gestor nativo](architecture/GESTOR_NATIVO_WINDOWS_RC4.md).

## Versión y checkpoint

- Fork autorizado: `modest-bose-evolution`, rama `refactor/modularizacion`. Base `modest-bose` y Antigravity clausuradas.
- Código funcional Windows: `77afc1f622454221b2ed84b2e95c9cd543708aad`, tag `checkpoint/windows-native-2.0.0-rc4`.
- Android instalado: `2.0.0-rc3`, versionCode 51, paquete debug `pe.gob.reniec.dnie.evolution.debug`. No se cambió ni reinstaló Android al sustituir el gestor Windows.
- Referencia previa: `checkpoint/puente-2.0.0-rc3`; firma Windows real preservada en `checkpoint/firma-windows-1.8.14`, además de evidencia Android21/22.
- Distribuciones, hashes y pruebas: `D:/DNIeToolchain/Checkpoints/windows-native-rc4`. APK rc3: `D:/DNIeToolchain/Checkpoints/2.0.0-rc3`.

## Estado comprobado

`DNIeBridgeAgent`: Running, Automatic; binarios Release instalados en D:. GUI nativa abierta, una instancia. Consulta autenticada de solo lectura: host Stopped, fase idle, sin tarjeta publicada y sin error; último perfil solicitado LAN, sin conexión activa. No había conexiones al puerto 39443. No se inició ni detuvo un host para guardar esta documentación.

El lector propio aparece con estado PnP OK. Existe también un listener anterior en `0.0.0.0:35963`, asociado a WUDFHost. Su dispositivo exacto no se identificó en esta comprobación; queda pendiente aislarlo del flujo nuevo. No detener WUDFHost genéricamente.

El minidriver DNI quedó aislado en los experimentos anteriores; no se restauró en rc3/rc4. No se tocaron Hello, SCardSvr, CertPropSvc, firewall o perfiles de red. El reinicio pendiente es manual, por solicitud del usuario.

## Evidencia y límites

Windows: 123 comprobaciones del motor, 31 del control en Debug y 31 en Release; 10 comprobaciones sobre el servicio Release instalado desde cuenta no administrativa. Inicio/parada/reinicio conservaron PID del servicio. Release rechazó un comando debug directo; paquete sin BAT/PowerShell/PDB y sin DebugForm. Certificado y metadatos de identidad preservados exactamente, estado con ACL protegida.

Android: APK Debug/Release recompilados satisfactoriamente; 251 pruebas y dos omisiones opcionales corresponden a la validación rc3 anterior, no a una nueva ejecución en esta documentación.

Tailscale y las firmas/lecturas del puente tienen evidencia física histórica. El gestor nativo aún necesita una firma real de regresión. LAN TLS y USB AOA tienen cobertura sintética y siguen pendientes de prueba física. UsbDk 1.0.22 está instalado; PnP devolvió 3010 al reiniciar el dispositivo del teléfono. Reiniciar no demuestra por sí solo que USB funcione.

## Historia

El contexto anterior, que mezclaba rc1/rc2/rc3/rc4 y PIDs antiguos, se preserva en [archivo histórico](archive/CONTEXTO_HASTA_WINDOWS_NATIVE_RC4.md). Para operar, usar esta entrada y el procedimiento actual; no los launchers ni sesiones temporales de esas notas.

2026-10-07: fallo real de enumeración: backend Tailscale con CardPresent=true pero lector propio ausente de SCardListReaders. Reinicio específico del dispositivo devuelve 3010 (requiere reinicio manual de Windows); no se reinició PC ni se modificó SCardSvr/minidriver. No considerar estado privado del host como presencia usable por ReFirma. Ver [diagnóstico](operations/LECTOR_AUSENTE_PCSC_2026_10_07.md).
