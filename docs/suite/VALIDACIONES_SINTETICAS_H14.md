# H14 · Validaciones sintéticas de fallos

2026-10-07. El usuario autoriza ejecutar pruebas antes de conversar sobre el siguiente paso. Base `25497c6`. Alcance: pruebas deterministas con certificados, claves, respuestas y tarjetas simuladas; sin usar el DNI del titular, consultar su PIN, iniciar/parar Bridge o alterar la sesión GUI existente. No se implementan funciones nuevas de recuperación o preflight online automático.

## Matriz de controles

| Grupo | Fallos y comportamiento verificado | Evidencia automatizada |
| --- | --- | --- |
| TSA | Timeout, respuesta vacía/malformada/excesiva, nonce/imprint incorrectos, token falsificado/no confiable, tiempo/política inválidos y sellado duplicado | TimestampServiceTest |
| PDF Android y Windows | Fallo TSA después de operación privada: sin PDF publicado ni repetición automática de la firma; opción apagada sin petición TSA; documento firmado íntegro con token sintético | ConfiguredPdfSigningTest, JvmPdfSignerTest |
| Lote Windows | Timeout TSA de un PDF: sin resultado parcial ni degradación a CMS; TXT posterior sigue firmándose, CMS verificado con original/certificado sintético y ZIP conservado | DesktopPdfSigningTest |
| Certificados | Vencido, aún no vigente, uso incorrecto, CA usada como titular y revocación autenticada | CertificateValidationTest |
| OCSP/CRL | Caducados, falsificados, certificado/issuer equivocado, responder no autorizado, respuesta malformada y red no disponible; UNKNOWN no se convierte en GOOD ni borra revocación conocida | CertificateValidationTest, OcspValidatorTest |
| Cadena/TSL | CA intermedia revocada, evidencia ausente, TSL no disponible/alterada/caducada, rollback, servicio retirado y XML inseguro | CertificateTrustInspectorTest, TrustedListVerifierTest |
| PIN/transporte/lote | Rechazo de PIN y pérdida de transporte no reabren ni repiten operación privada; conservar documentos ya completos; cancelación y cierre invalidan autorización | BatchCardSessionTest, BatchSignerTest, SigningStreamsTest, ContinuousProtocolTest |
| PACE/Bridge | Estado/reset/cierre, conexión y autenticación simulados, invalidación de canal y framing de transporte | Tests protocol y bridge-android |
| Coordinación GUI | Puente activo/desconocido bloquea conexión privada antes de abrir transporte; permiso liberado ante fallo; acciones de cambio de modo bloqueadas con suite activa | DesktopProductCoordinatorTest, SuiteControllerModeTest |

## Casos nuevos

- Timeout RFC3161: una sola petición, sin llegar a verificación de confianza y sin modificar el CMS original.
- Respuestas vacía, malformada y mayor del límite: rechazo antes de consultar confianza TSA.
- Lote Windows PDF + TXT con TSA simulada en timeout: dos operaciones privadas totales (una por documento), una petición TSA, PDF FAILED sin salida, TXT SIGNED verificable y ZIP disponible, sin archivos `.part` publicados.

Las claves sintéticas existen solo en fixtures de test; no son fallback del producto ni se usan para firmar en una sesión real. No se lanzan solicitudes al servicio TSA real en esta batería.

## Límites que siguen abiertos

La recuperación que conserve PDF/CMS pendiente y permita reintentar **solo el sello** sigue sin implementarse. Los tests prueban el fallo seguro actual y la conservación de resultados completos, no esa recuperación futura.

La validación online automática del titular antes de cada firma sigue sin implementarse. Se prueban validadores criptográficos, rechazo de revocación conocida y controles locales existentes; no se afirma una consulta online automática nueva ni validación histórica completa.

No se mide latencia/perfil de CPU, desconexión física NFC/USB, cambio de modo Bridge↔Suite con el DNI ni interoperabilidad real contra navegador/lector instalado. Las pruebas opt-in que requieren fixtures externos se registran separadamente como omitidas, sin presentarlas como superadas. La firma física PDF-T y CMS-B ya validada por el usuario es evidencia independiente de esta batería.

## Resultado de ejecución

Gradle BUILD SUCCESSFUL en2m45s, tareas de test forzadas a ejecutarse con `--rerun` (sin tomar resultados de test de caché). 330 pruebas superadas, 2 omitidas por fixtures externos, 0 fallos y 0 errores.

| Módulo | Pasan | Omitidas |
| --- | ---: | ---: |
| dnie-validation-core | 42 | 0 |
| dnie-signing-core | 10 | 0 |
| dnie-signing-link | 23 | 0 |
| dnie-protocol | 86 | 0 |
| dnie-bridge-android | 26 | 2 |
| dnie-pdf | 23 | 0 |
| app | 76 | 0 |
| dnie-pdf-desktop | 13 | 0 |
| dnie-signing-desktop | 24 | 0 |
| dnie-desktop-orchestration | 5 | 0 |
| dnie-signing-ui | 2 | 0 |

Pruebas omitidas:

- `pe.gob.reniec.dnie.bridge.android.CrossLayerInteropTest.browserToNativePcscToMobileEngine`
- `pe.gob.reniec.dnie.bridge.android.WindowsInteropTest.tlsAndReadPolicyAgainstWindowsFixture`

Log: `D:/DNIeToolchain/Temp/synthetic-validation-h14.log`. Resumen completo: `D:/DNIeToolchain/Builds/synthetic-validation-h14/summary.json`. El compilador Kotlin reinició su daemon tras un primer arranque fallido; la compilación y toda la batería finalizaron correctamente. No se cambia código de producción ni se requiere reinstalar APK/Windows.
