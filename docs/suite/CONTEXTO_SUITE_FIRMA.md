# Contexto vigente de la suite propia de firma

Actualizado: 2026-10-07, America/Bogota. Este archivo es la entrada de contexto para el nuevo extremo documental Windows. Diferenciar motor probado, simulación de escritorio y conexión real al DNI.

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

1. Transporte privado bajo usuario normal, emparejamiento/identidad propios sin acceso a secretos SYSTEM del puente, servicio claramente distinto de DNIBRDG1; autenticación antes de crear el endpoint.
2. Caso de uso Android para aprobación de lote/PIN local y sesión NFC/PACE exclusiva; no consumir el transceptor de un ViewModel ajeno ni compartir SSC con un puente activo.
3. Comando real sin fallback a demo: preparar original en PC, mostrar y autorizar lote, leer certificado, generar `.p7s` y verificar independientemente .NET.
4. Prueba física sin lector instalado/publicado, cancelación y pérdida después de PSO sin reenvío. Después extender a lotes y a LAN/USB/Tailscale. Convivencia mediante exclusión explícita del DNI, no compartir sesión APDU.

Después: lotes reales y PDF JVM con corpus Android compartido. Visor/GUI solo después del motor documental. Simulación no sustituye las pruebas físicas.

## Deuda explícita del prototipo

Recovery/catalogación de trabajos incompletos al morir el proceso, ACL/endurecimiento del workspace y reparse points de ancestros, espacio libre, política de actualización, códigos de error públicos estables y cancelación interactiva son pendientes. Los límites por streaming y separación UUID no acreditan todo ese endurecimiento. El overload legado Android sigue como deuda separada. El teléfono autoriza cantidad de operaciones; no puede comprobar visualmente documentos que permanecen exclusivamente en la PC. Strings del PIN requeridas por el transceptor heredado no tienen garantía de borrado de heap.
