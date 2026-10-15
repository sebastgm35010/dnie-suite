# Contexto vigente de la suite propia de firma

2026-10-15 · R9 / Android3.1.2 (APK86) instalada por ADB sin borrar datos y con firma compatible; auxiliar actualizado con la misma firma. DESIGN.md, paleta compartida claro/oscuro, categorías sans y números tabulares; radar NFC/resortes con movimiento reducido y guía optativa CAN/NFC/móvil-PC. Suite/gestor Windows compilados y accesos actualizados. 252 pruebas JVM pasan/1 omitida preexistente,48 comprobaciones Manager y8 instrumentadas Android pasan. Motores, servicio y driver sin cambios. NFC/TSA/háptica físicos y regresión USB/suspensión pendientes. Ver `docs/design/SISTEMA_DISENO_GUIA_R9.md` y `docs/operations/DISENO_R9_RESULTADOS.json`. Los párrafos siguientes conservan historial.

2026-10-14 · Android 3.1.1 / APK85 instalada por ADB, sin desinstalar ni borrar datos. Usuario confirmó APK84 y reactivó ADB. Versionado central en gradle.properties y comando para incrementar patch/código por entrega; Configuración muestra versión. Firma app/auxiliar idéntica y compatible con84; ambos instalados. 89 unitarias pasan/1 omitida y4 instrumentadas R8 pasan (9,22s), sin DNI. Auxiliar queda instalado con identidad estable. NFC/TSA/háptica físicos siguen pendientes; Windows instalado conserva R8. [Detalle](../operations/VERSIONADO_Y_FIRMA_3_1_1.md). Los avisos de ADB suspendido siguientes son históricos y están revocados por el usuario.

2026-10-14 · Pantallas y NFC R8 / APK84 (3.1.0) compilada debug/release, sin instalar ni usar ADB; APK82 sigue siendo la última instalada. Hoja continua credenciales→NFC→progreso→resultado; CAN compacto y PIN transitorio. Firma con preview superior, tres pestañas y barra fija, resultados parciales explícitos. Cinco destinos comunes; Mi DNIe agrupa visualmente Certificados/Identidad, credencial ICAO y detalles plegables. Suite Windows con plantillas 2×2, indicador de actividad y apertura del PDF real. 251 JVM pasan/1 omitida y Manager44 pasan; auxiliar Android compilado, sin ejecutar. Suite preparada y acceso actualizado; Bridge/servicio/driver y motores criptográficos sin cambios. Pruebas Android/NFC pendientes. [R8](../design/PANTALLAS_FLUJOS_NFC_R8.md).

2026-10-14 · Personalización R7 / APK83 (3.1.0) compilada debug/release; no instalada: ADB suspendido hasta nuevo aviso del usuario. APK82 sigue siendo la última instalada. Sello con fondo transparente, insignias check/candado, fecha/densidad/colores, plantillas y esquinas al6%; diseño Android persistente, navegación y acciones Mi DNIe. Respuesta táctil por eventos NFC con ajuste de usuario. Windows añade PNG de rúbrica, DnD visual y tema claro/oscuro. 241 JVM pasan/1 omitida por permisos Windows y 44 comprobaciones Manager pasan. Interfaces Windows y accesos actualizados; servicio, driver, identidad y motores criptográficos conservados. Pruebas Android y firma física R7 pendientes. [R7](../design/PERSONALIZACION_SELLO_HAPTICA_R7.md).

2026-10-14 · Red R6 / APK82 (3.1.0) instalada: Bridge Red (Wi-Fi/Tailscale) + USB; descubrimiento autenticado y TLS fijado por huella/token, IP opcional en Suite/gestor y CLI. Vista previa Android muestra anotaciones existentes. QR de sello/cotejador retirados de UI; QR de emparejamiento conservado. 283 pruebas JVM pasan, 3 optativas/privilegios omitidas; 261 Bridge + 48 Control + 41 Manager + 3 servicio instalado pasan; 8 instrumentadas Android pasan. Descubrimiento Windows→Android verificado por LAN y Tailscale por separado, credenciales sintéticas, sin DNI. Gestor/servicio/Suite actualizados, identidad conservada y driver sin reinstalar. Firma NFC y cambio físico de red pendientes. [Rutas, pruebas y límites](../architecture/RED_COORDINADA_R6.md).

2026-10-14 · QR R5: GUI/CLI generan sello con metadatos reales por documento, sin cambiar protocolo Suite ni autorización. Distribuciones actuales: D:/DNIeToolchain/Builds/stamp-qr-r5/{signing-ui/install/dnie-signing-ui,signing-desktop/install/dnie-signing-desktop}. Acceso directo DNIe Suite actualizado; Bridge nativo R4 permanece. 207 pruebas pasan/1 omitida y 6 instrumentadas Android pasan. [Formato, capas y límites](../design/QR_COTEJO_R5.md).

2026-10-14 · Aether R4 / 3.1.0: Bridge corrige pintura y selección, inputs, bandeja y consulta WinSCard. Suite añade hoja de muestra, controles, preferencias visuales, zoom y apertura/autocompletado de resultados; emblema compartido actualizado. 196 pruebas pasan/1 omitida preexistente, 60 comprobaciones nativas y 2 instrumentadas Android pasan. Windows actualizado con identidad y 13 archivos runtime conservados; APK80 instalada. QR enriquecido diferido; firma física nueva y suspensión siguen pendientes. [Detalle](../design/RESULTADO_AETHER_R4.md).

2026-10-14 · Aether Android79 / 3.1.0 instalada por ADB con reemplazo sin borrar datos. Dos pruebas instrumentadas pasan (4,01 s): cinco estilos/rúbrica transparente y selector de páginas/preview Compose. Arranque normal comprobado, sin fatal en el PID observado; auxiliar retirado. Firma con DNI/TSA y regresión física Suite/puente pendientes. [Detalle](../design/VALIDACION_ANDROID_AETHER_R3.md).

2026-10-14 · Aether R2 / 3.1.0: Suite y Manager Windows rediseñados e instalados; sellos vectoriales/preview/colores/páginas compartidos con Android. Una firma, un widget y réplicas gráficas cubiertas por CMS; DocMDP P=2 se rechaza antes de firmar réplicas. 191 pruebas pasan, una omitida preexistente y 24 comprobaciones Manager pasan. APK79 debug/release compilada; Android78 sigue instalada, dos pruebas instrumentadas nuevas sin ejecutar por falta de ADB. Identidad y runtime del servicio conservados, driver intacto. [Detalle](../design/RESULTADO_AETHER_R2.md).

2026-10-13 · Suite entradas R2: alias virtual de Recientes era una ruta inexistente y causaba tanto la falta de vista previa como rechazo local al firmar. Selector resuelve shell shortcuts a archivos reales, preflight anterior a PIN, conexión conservada ante rechazo previo a BEGIN y diálogo PIN explícito. 50 tests pasan; PDF real seleccionado y GUI muestran página 1/11 a 918×1188. Acceso directo actualizado; firma DNI física pendiente. [Detalle](SELECCION_RECIENTES_Y_PIN_R2.md).

2026-10-13 · Suite QR R1: Abrir QR inicia preparación y muestra QR nativo en memoria, permite reabrirlo y lo retira al conectar/cancelar. Corregida espera de EOF heredado por gestor Bridge que bloqueaba conexiones. 45 tests UI/escritorio/orquestación pasan, incluida pulsación real del botón sin teléfono/DNI y proceso Windows con stdout heredado. Acceso directo actualizado; lectura QR desde Android físico pendiente. [Detalle](QR_NATIVO_SUITE_R1.md).

2026-10-13 · Versión 3.1.0 / APK78 instalada: integración v4 de autenticación resistente, plazos por candidato, integridad SHA-256 de originales/resultados, migración de identidad Windows protegida y DPAPI acotado. Servicio/gestor Windows y Suite actualizados; identidad conservada según metadatos. 177 JVM, 134 comprobaciones Windows y 3 pruebas Android pasan; debug/release compilados y producción comprobada. Firma y USB/NFC físicos nuevos pendientes. [Detalle](../architecture/SEGURIDAD_BACKEND_3_1_V4.md).

2026-10-09 · Conexiones R5: APK77 / 3.0.0-alpha05-mobility instalada sin borrar datos. Suite y Bridge Wi-Fi ya no fijan identidad a la IP; TLS y token siguen obligatorios. Windows conserva la identidad DPAPI al cambiar IP. TLS directo permite editar destino; Suite tiene pantalla completa independiente y limpia conexión al finalizar lote único. 159 JVM pasan / 3 omitidas, release compilado y 3 pruebas instrumentadas de Suite/Bridge pasan en el móvil. Cambio físico de IP con DNI pendiente. [Detalle](../architecture/CONEXIONES_DIRECCION_E_IDENTIDAD_R5.md).

2026-10-09 · Conexiones R4: APK76 / 3.0.0-alpha04-links instalada. Puente autentica PC antes de esperar DNI; Suite tiene pantalla completa con presencia separada y diagnóstico de conexión. Red agrupa Wi-Fi/Tailscale, USB separado. Windows host actualizado y GUI Suite selecciona IP vigente; emparejamiento anterior puede requerir TXT/QR actualizado de la misma PC. 154 JVM pasan, 3 omitidas; 93 comprobaciones Windows y 34 Android pasan. Prueba física bilateral y detección de cierre remoto antes de NFC pendientes. [Detalle](../architecture/CONEXIONES_INDEPENDIENTES_R4.md).

2026-10-07 · H15: CMS compartido incluye issuerSerial exacto en ESSCertIDv2 para compatibilidad con política PE de ReFirma PCX. Sin cambios de UI, PACE o transporte; 247 pruebas y comparación sintética con DSS real satisfactorias. Validación física externa pendiente. Ver docs/suite/REFIRMA_ISSUER_SERIAL_H15.md.

2026-10-07 · H14: batería sintética forzada (--rerun), 330 pruebas pasan, 2 opt-in externas omitidas, 0 fallos/errores. Nuevos casos TSA timeout/respuestas inválidas y lote PDF fallido con TXT posterior válido sin repetir firma. Sin DNI ni cambios de producción; recuperación persistente de solo TSA y preflight online automático siguen sin implementar. [Resultados y límites](VALIDACIONES_SINTETICAS_H14.md).

2026-10-07 · Prioridad ajustada por el usuario: lote PC01 cerrado como satisfactorio; confirma también el segundo PDF validado. Integración visual completa Suite/Bridge pasa a 3.0 con rediseño. Distribución se pospone hasta pulir Android y Windows. Recuperación de solo TSA y validación online previa automática siguen sin implementar; no se confunde confianza del usuario con evidencia de funcionalidades ausentes. LT/LTA se conserva como evolución técnica, sin prioridad inmediata.

2026-10-07 · H12 físico superado desde Windows: usuario aporta Firma Perú VÁLIDO para acta PDF PAdES_BASELINE_T (raíces firmante/TSA en TSL Sí, TSA RENIEC TSU-2026-01 21:08:57 UTC-05) y TXT/.p7s CAdES_BASELINE_B válido sin TSA. Falta resultado del segundo PDF, confirmación 3/3/PIN único y cambio Bridge↔Suite. [Evidencia y alcance](TSA_RENIEC_WINDOWS_H12.md).

2026-10-07 · H13: integración inicial Suite + Bridge con módulo JVM de coordinación y adaptador .NET opcional al servicio autenticado. GUI integra estado, abrir gestor y detener puente; permiso local evita cambiar de modo durante suite, sin exclusión global entre procesos. 43 pruebas y smoke real de consulta pasan, APK63 recompilado sin cambio. Paquete en D:/DNIeToolchain/Builds/suite-bridge-h13; configuración Bridge conserva ventana nativa y ambas distribuciones autónomas. [Capas, distribución y pendientes](INTEGRACION_BRIDGE_MODULAR_H13.md).

2026-10-07 · H12 Windows: TSA RENIEC opcional integrada en PDF visible/invisible de GUI y consola (--pdf-tsa reniec), reutilizando el motor Android verificado. 76 pruebas pasan y ejecutable distribuido produce PDF sintético con TSA real verificado. Distribuciones nuevas en D:/DNIeToolchain/Builds/tsa-reniec-h12; APK63 no cambia. Pendiente firma Windows con DNI real y validación externa. [Arquitectura, uso y prueba](TSA_RENIEC_WINDOWS_H12.md).

2026-10-07 · H11 físico superado: usuario aporta resultado de Firma Perú del PDF firmado con APK63 y DNI real: VÁLIDO, integridad conservada, PAdES_BASELINE_T y raíces del firmante y TSA en TSL Sí. TSA RENIEC TSU-2026-02, hora 18:13:47 UTC-05. Quedan LT/LTA, recuperación de solo sellado e integración Windows. [Evidencia y alcance](ANDROID_TSA_RENIEC_H11.md).

2026-10-07 · Investigación previa TSA: LLAMA.PE figura en TSL y anuncia sellado gratuito web en Watana (PDF hasta50MB). API documentada exige cuenta/token y sellar_pdf solo producción; gratuidad de endpoint RFC3161 integrable no demostrada. Se informa hallazgo antes de modificar Android, conforme a la condición solicitada. APK62 preservado. [Fuentes y alcance](TSA_PROVEEDORES_GRATUITOS_PERU.md).

2026-10-07 · H10 externo superado: Firma Perú reconoce ambos PDF sintéticos como PAdES_BASELINE_T e íntegros; raíz TSA RENIEC en TSL Sí, FreeTSA No. Resultado global indeterminado con error de cadena del firmante sintético, como corresponde a esta prueba. Pendientes módulo TSA Android, confianza/revocación local ROOT5, disponibilidad/condiciones y PDF con DNI real. APK62 sin cambios. [Evidencia y límites](TSA_EXPERIMENTO_REAL_H10.md).

2026-10-07 · H10 experimental: PDF sintético con TSA real FreeTSA y RENIEC generados/verificados localmente, ruta HTTPS RENIEC /service responde sin credenciales desde esta PC. Integridad/token comprobados; clasificación externa, confianza/revocación TSA y condiciones de distribución pendientes. APK62 sin cambios. [Archivos, fuentes y límites](TSA_EXPERIMENTO_REAL_H10.md).

2026-10-07 · H9b físico superado: usuario confirma APK62 con vigencia/uso aptos, titular GOOD por OCSP firmado/vigente, ambas CA intermedias GOOD por CRL vigente/firmada y ruta PKIX verificada con TSL Perú autenticada secuencia25. Vigencia agregada hasta 2026-10-07 17:33:45 UTC-05, limitada por OCSP; requiere nueva consulta al caducar. La causa del UNKNOWN anterior no quedó demostrada. Preflight automático, histórico y TSA pendientes. [Evidencia y límites](REVOCACION_CA_DIAGNOSTICO_H9B.md).

2026-10-07 · H9b: usuario confirma APK61 con TSL autenticada y ruta PKIX verificada; titular GOOD, revocación global UNKNOWN. CRL de ambas CA verificadas en PC; causa del fallo en móvil aún sin determinar. Motor extraído por CA con resultado/método/código seguro, panel y registro por CA, distinción de evidencia caducada durante consulta. 236 pruebas pasan; APK62 compilado, prueba física pendiente. [Detalle](REVOCACION_CA_DIAGNOSTICO_H9B.md).

2026-10-07 · H9a: APK60 físico reporta TSL indeterminada. Reproducida dependencia DatatypeFactory/Xerces ausente con runtime XML Android14; reemplazo por lector estricto de fechas y diagnóstico por etapa. 233 pruebas/Debug pasan, APK61 compilado. DOM Android + BC verifican TSL y ruta; teléfono pendiente. No se relaja confianza ni se declara TSA. [Detalle](TSL_ANDROID_COMPATIBILIDAD_H9A.md).

2026-10-07 · H9: TSL Perú autenticada con ancla AAC independiente, firmante autorizado y CRL; ruta PKIX completa y revocación de CA intermedias. Prueba real JVM TRUSTED/GOOD, 229 pruebas. Android APK60 con panel y rollback persistido; prueba física pendiente. Consulta manual, preflight automático/histórico/TSA pendientes. Usuario confirma H8 en teléfono. [Detalle y límites](CONFIANZA_TSL_CADENA_H9.md).

2026-10-07 · H8: módulo JVM dnie-validation-core, control local previo a CMS, consulta Android manual OCSP/CRL y rechazo de revocación conocida. 215 pruebas pasan y OCSP real firmado devuelve GOOD con confianza UNKNOWN. APK 59 compilado; UI física pendiente. Confianza TSL, cadena completa y preflight online automático siguen pendientes. [Arquitectura, límites y rutas](VALIDACION_CERTIFICADOS_H8.md).

2026-10-07 · H7: usuario confirma PDF Android 58 VÁLIDO y PAdES_BASELINE_B. Perfil DER sin signingTime y /M UTC trasladado a PDF desktop; 104 pruebas y demo sintético .NET pasan. GUI/CLI nuevas en D:/DNIeToolchain/Builds/pades-baseline-b, distribuciones anteriores preservadas. Pendiente clasificación externa de PDF Windows con DNI, revocación/TSL integradas y TSA. [Estado y rutas](PADES_BASELINE_DESKTOP_H7.md).

2026-10-07 · H6 Android: perfil PAdES explícito sin signingTime CMS, DER y /M UTC; CMS .p7s y Windows conservan perfil previo. 189 pruebas finales pasan, APK 58 compilado y pendiente de instalar (ADB sin dispositivo). Checkpoint previo de código/APK 57 en D:. Siguiente: PDF físico firmado en Android y clasificación Baseline B en validador antes de portar a Windows. [Detalle y evidencia](ANDROID_PADES_BASELINE_H6.md).

2026-10-07 · H5: GUI aislada `dnie-signing-ui`, preview/selector y firma privada sin consola; APK 57 implementa escaneo combinado certificados→ICAO con sesiones independientes y cancelación que conserva completos. 265 pruebas pasan. GUI inspeccionada sin DNI; firma GUI pendiente. APK 57 instalado por ADB y versión verificada el 2026-10-07; usuario confirma que funciona el escaneo unificado, sin desglose de bloques ni prueba de cancelación. DNI Bridge/inspección pública Windows son próximos adaptadores, no funciones ya integradas. [Contexto, rutas y límites](INTERFAZ_AISLADA_Y_ESCANEO_UNIFICADO_H5.md).

2026-10-07 · H4 físico validado: trabajo `b9b8c2f6-18c2-4a86-bab4-3e9dc173eb65`, PDF visible 1/1, verificado .NET/certificado DNI y render legible. Posición fija tapa contenido original; siguiente prioridad selector/preview y presentación del nombre del certificado. [Detalle](PDF_VISIBLE_H4.md).

2026-10-07 · H4: sello visible de escritorio modular implementado, page/box/fields/style por argumentos y apariencia del campo firmada incrementalmente. Pruebas sintéticas de giros/recortes, firmas sucesivas y render pasan; falta sello físico con DNI. H3 cierre confirmado manual Ctrl+C. [Uso y límites](PDF_VISIBLE_H4.md).

2026-10-07 · H3 físico validado: trabajo `ed878567-300a-4a38-aeba-e56cd47eb640` 3/3 (dos PDF integrados y un CMS DOCX), verificados .NET con certificado DNI coincidente y rechazo de alteración. Cierre posterior confirmado manual por Ctrl+C; no es incidente de conexión. Sigue pendiente prueba específica de dos lotes consecutivos en mismo TLS. [Evidencia y límites](PDF_INVISIBLE_H3.md).

2026-10-07 · H3: PDF integrado invisible de escritorio implementado en módulo JVM `dnie-pdf-desktop` (PDFBox 3.0.8). Por defecto PDF → `_firmado.pdf`, otros → `.p7s`; `--pdf-mode detached` conserva el modo anterior. Simulación del ejecutable verificada independientemente .NET; prueba física con DNI pendiente. APK instalado 56 y transporte/motor NFC sin cambios funcionales. [Arquitectura, límites y procedimiento](PDF_INVISIBLE_H3.md).

Actualizado: 2026-10-07, America/Bogota. Este archivo es la entrada de contexto para el nuevo extremo documental Windows. Diferenciar motor probado, simulación de escritorio y conexión real al DNI.

Evidencia física rc8: reconexión con PC recordada/misma huella y lote PPTX/PNG/PDF 3/3 con una entrada PIN, CMS verificados .NET y certificado DNI coincidente. Cierre posterior por Ctrl+C del usuario. Secuencia detallada de retirada/estados y dos trabajos exitosos en mismo TLS pendientes. [Evidencia](SESION_CONTINUA_H2.md).

H2 implementado: confianza persistente DPAPI/Keystore, canal continuo y PIN desde Windows por trabajo; APK 55 instalado, 113 pruebas pasan. Implementación y ensayos sintéticos en [SESION_CONTINUA_H2.md](SESION_CONTINUA_H2.md). H2 físico confirmado: 1/1 CMS válido tras un trabajo interrumpido dentro de la misma conexión; causa del primer fallo desconocida. APK 56 instalado añade presencia NFC y recuperación, 116 pruebas pasan; pendiente de ensayo físico rc8. H1b comprobado se conserva.

Estado más reciente: **H1b implementado**: comando `remote`, TLS inverso autenticado, solicitud de un trabajo con huella/token, pantalla Android Certificados → Suite y autorización/PIN local/NFC. APK 54 / `2.0.0-rc6-remote-signing`; primera firma física LAN completada: 1/1 CMS con DNI y verificación independiente .NET, certificado coincidente con el previamente validado y rechazo al alterar contenido. Revocación no comprobada. [Procedimiento y límites](FIRMA_REMOTA_H1B.md).

## Objetivo y alcance

Portar el comportamiento comprobado de Android a una suite local, con motor separado de la futura UI. Procesar originales, CMS/PDF, lotes, resultados y exportación en la PC; Android conserva NFC/PACE y el acceso al DNI por el puente existente. Las firmas con Firma Perú/ReFirma PCX acreditan que el lector puede ejecutar operaciones privadas desde Windows, pero no constituyen una prueba del cliente nuevo de la suite.

Decisión corregida por el usuario el 2026-10-07: **la suite no dependerá de PC/SC ni expondrá el DNI como lector global de Windows**. Usará una conexión privada directa con Android, con originales/CMS/PDF en la PC y PACE/PIN/CAN en el teléfono. El lector existente queda como compatibilidad independiente para ReFirma/Firma Perú. No ampliar el servicio SYSTEM para firmar documentos. Orden: checkpoint/CLI JVM → canal privado → primera firma CMS con DNI → lotes reales → PDF sin visor → contratos locales estables → UI/editor. Plan e inventario MIT: [PLAN_SUITE_FIRMA_PROPIA.md](../architecture/PLAN_SUITE_FIRMA_PROPIA.md).

## Referencias preservadas

- Fork autorizado: `modest-bose-evolution`, rama `refactor/modularizacion`; no tocar base clausurada o Antigravity.
- Motor compartido: `c97e6e3`, tag `checkpoint/signing-core-rc5`. Android `2.0.0-rc5-signing` / APK 53. Lote físico de cuatro archivos y cinco firmas (incluye una anterior) verificados independientemente; evidencia en [MOTOR_FIRMA_LOTES_RC5.md](../architecture/MOTOR_FIRMA_LOTES_RC5.md).
- Plan previo: `f0bf1c9`. Antes del código nuevo se creó `checkpoint/pre-suite-desktop-h0` y ZIP/manifest SHA-256 en `D:/DNIeToolchain/Checkpoints/pre-suite-desktop-h0`.
- Contexto general de app/puente y pendientes que no pertenecen al firmador: [CONTEXTO_ACTUAL.md](../CONTEXTO_ACTUAL.md) y [PENDIENTES_ACTUALES.md](../PENDIENTES_ACTUALES.md). Los PIDs y rutas históricas no se usan para iniciar hosts.

## Hito 0 entregado: ejecutable local y simulación

Nuevo módulo `dnie-signing-desktop`, Kotlin/JVM y Java 17, depende de `dnie-signing-core`. No depende de Android, Compose, WinForms ni PDFBox Android. La aplicación de consola permite únicamente `demo` y ayuda; un comando real no se degrada a simulación.

| Capa / archivo | Responsabilidad |
| --- | --- |
| `cli/Main.kt`, `CliOptions.kt` | Entrada, argumentos y códigos de salida. Nunca recibir PIN/CAN por argumentos. |
| `model/DesktopSigningModels.kt` | Solicitud de archivos/workspace y resultado estructurado. |
| `application/DesktopSigningService.kt`, `OwnedCard.kt` | Coordinar importación, motor inyectado y exportación; conexión cerrada una sola vez, incluso ante error previo al lote. |
| `storage/DesktopWorkspace.kt` | Copiar originales por streaming a carpetas UUID; límites de tamaño/cantidad y rechazo de enlace final. |
| `application/DesktopResultWriter.kt` | Manifest UTF-8 con esquema, backend, estados y rutas relativas; sin credenciales. |
| `pdf/UnavailablePdfEngine.kt` | Contrato PDF todavía no implementado. En este hito todos los archivos producen CMS separado. |
| `demo/SimulatedSigningCard.kt` | Clave/certificado efímeros sintéticos, autorización consumida tras cada firma; no lee certificados Windows ni exporta clave privada. |
| `tools/verify-job.ps1` | Verificación independiente .NET de CMS, certificado del trabajo y rechazo al modificar contenido en memoria. |

La lógica CMS y el lote son los existentes: no se reescribieron hashes/DER ni PIN por operación. Workspace copia antes de abrir PKI, conserva nombres Unicode y separa entradas de igual nombre. El motor publica firmas terminadas, conserva resultados parciales e interrumpe privadas tras fallo. Un trabajo contiene originales, firmas `.p7s`, CER/PEM, ZIP y `result.json` con `backend: SIMULATED`.

El resultado de simulación es válido criptográficamente con su certificado sintético; **no es una firma personal realizada con DNI**. No hay todavía cliente PC/SC, entrada de PIN real, PDF integrado, GUI o instalador público. El ejecutable utiliza la Java instalada; no contiene un runtime Java propio.

## Compilación y ejecución reproducibles

Desde la raíz del fork, PowerShell:

```powershell
$env:GRADLE_USER_HOME = 'C:/Users/LENOVO/.gradle'
.\gradlew.bat --offline '-Pkotlin.incremental=false' '-PdesktopBuildDir=D:/DNIeToolchain/Builds/signing-desktop-h0' :dnie-signing-desktop:test :dnie-signing-desktop:installDist :dnie-signing-core:test :app:assembleDebug

$env:JAVA_HOME = 'C:/Program Files/Microsoft/jdk-17.0.20.101-hotspot'
& 'D:/DNIeToolchain/Builds/signing-desktop-h0/install/dnie-signing-desktop/bin/dnie-signing-desktop.bat' demo --workspace 'D:/DNIeToolchain/SuiteJobs' --input 'D:/ruta/archivo.txt'
& './dnie-signing-desktop/tools/verify-job.ps1' -JobDirectory 'D:/DNIeToolchain/SuiteJobs/UUID-del-trabajo'
```

`installDist` produce el launcher estándar Gradle; no es un administrador del puente, no eleva permisos ni crea servicios. Códigos: 0 completo/ayuda, 1 incompleto/fallo, 2 argumentos inválidos. Ver [README del módulo](../../dnie-signing-desktop/README.md).

## Evidencia de este incremento

Build offline satisfactorio: tests escritorio e instalación ejecutable; pruebas signing-core actualizadas y objetivo APK Debug satisfactorio (sin cambios en Android). Ocho pruebas nuevas: cinco del caso de uso, tres de CLI. Sin fallos, errores u omisiones. Cobertura: binarios por streaming, Unicode/nombres repetidos, originales inmutables, CER/PEM/ZIP, firma alterada, pérdida simulada, rechazo PIN, entrada inválida, cierre único y rechazo de secretos/backend real en argumentos.

Se ejecutó el launcher empaquetado con tres archivos sintéticos (texto UTF-8 con espacios/ñ, binario y vacío). .NET SignedCms verificó los tres CMS, coincidencia del certificado y rechazo al alterar contenido, incluido el original vacío. No se comprobó revocación/confianza del certificado sintético.

Evidencia fuera de Git: `D:/DNIeToolchain/SuiteDemoH0/jobs/3c3f5c0f-d953-475f-aac4-a0636911bd62/independent-verification.json`; build log `D:/DNIeToolchain/Temp/signing-desktop-h0-build.log`. Distribución de laboratorio: `D:/DNIeToolchain/Builds/signing-desktop-h0/install/dnie-signing-desktop`; sus 11 JAR suman 13,867,573 bytes, sin runtime. No asumir tamaño final de instalador.

No se conectó el DNI, no se instalaron APK/servicios ni se modificaron el controlador, minidriver, firewall o sesiones activas. Motor Android y puente quedaron operativamente preservados.

## H1a: contrato privado implementado, todavía sin enlace físico

Módulo JVM `dnie-signing-link`, compartido por escritorio y biblioteca Android. `PrivateSigningCard` adapta el motor de lotes existente; `AuthorizedSigningEndpoint` posee el transceptor preparado y PIN local, con presupuesto de firmas y plazo monotónico. OPEN/AUTHORIZE/CERTIFICATE/SIGN_SHA256/CLOSE; UUID y secuencia estricta, framing versionado y acotado. Sin APDU arbitraria, reset, datos del documento, PIN ni CAN en el enlace.

`SigningDuplex`, `StreamSigningExchange` y `AuthorizedSigningConnection` separan el framing de los transportes. **No autentican ni cifran por sí solos**: el próximo adaptador debe autenticar y proporcionar flujos TLS fijados o tailnet verificada; USB conserva cifrado. UUID no es una credencial. No abrir un socket plano de producción ni publicar el lector como atajo.

PIN incorrecto, pérdida de respuesta, error físico, repetición, vencimiento, agotamiento o cancelación invalidan la aprobación; no reintentar ni reconstruir PACE. El motor PC recibe PIN vacío y solicita autorización local al teléfono. Se conserva el VERIFY preventivo por operación. Aprobación móvil, emparejamiento, red/USB y arbitraje NFC todavía no están conectados a UI. Nunca interpretar este incremento como firma real o transporte seguro terminado.

Pruebas nuevas: 15 de protocolo/extremos/streams y una de dos CMS con motor real, tarjeta simulada y codec en memoria. No se usó DNI/PCSC/red. Build y compilación APK Debug satisfactorios; no se instaló el APK. Checkpoint previo `checkpoint/pre-suite-private-link`, ZIP en D:. [Contrato y límites](../../dnie-signing-link/README.md).

## Siguiente hito y criterios

1. H1b físico completado: solicitud TXT, autorización local y DNI produjeron un CMS verificado independientemente. Evidencia y rutas privadas en [FIRMA_REMOTA_H1B.md](FIRMA_REMOTA_H1B.md).
2. Lote físico remoto completado: 3/3 (TXT/CSV/binario), con verificación .NET de las tres firmas y certificado DNI coincidente. Pendiente cancelación/pérdida física y recuperación con autorización nueva. La ruta no publica lector PC/SC; el lector compatible instalado permanece separado.
3. Identidad permanente/recordar PC y autorizaciones posteriores. H1b usa identidad TLS efímera por trabajo, solicitud cinco minutos, sin acceder a secretos SYSTEM del puente.
4. Extender UX/conexión Tailscale y USB privado, después PDF integrado. LAN/Tailscale por IP privada usan TLS inverso; USB de suite aún no está implementado. Convivencia mediante exclusión explícita del DNI, no compartir sesión APDU.

Después: lotes reales y PDF JVM con corpus Android compartido. Visor/GUI solo después del motor documental. Simulación no sustituye las pruebas físicas.

## Deuda explícita del prototipo

Recovery/catalogación de trabajos incompletos al morir el proceso, ACL/endurecimiento del workspace y reparse points de ancestros, espacio libre, política de actualización, códigos de error públicos estables y cancelación interactiva son pendientes. Los límites por streaming y separación UUID no acreditan todo ese endurecimiento. El overload legado Android sigue como deuda separada. El teléfono autoriza cantidad de operaciones; no puede comprobar visualmente documentos que permanecen exclusivamente en la PC. Strings del PIN requeridas por el transceptor heredado no tienen garantía de borrado de heap.

## Investigación de conformidad PAdES

2026-10-07: PDF válido en validador peruano pero PDF_NOT_ETSI. Hallazgos y pasos B → T → LT → LTA en [plan de conformidad](PADES_CONFORMIDAD_PLAN.md). Investigación normativa delegada; motor aún sin modificación. La confirmación del usuario del escaneo unificado APK 57 queda registrada en H5.
