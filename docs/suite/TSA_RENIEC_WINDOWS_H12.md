# H12 · Sello RENIEC opcional en la suite Windows

2026-10-07. Tras la validación física Android H11, el usuario autoriza trasladar TSA RENIEC a Windows. Base 7b329ee, checkpoint previo `checkpoint/pre-windows-tsa-h12` y `D:/DNIeToolchain/Checkpoints/pre-windows-tsa-h12/source.zip`. No se modifica Android, el transporte privado, PACE, el lector PC/SC ni el servicio del puente.

## Motor e interfaz

`JvmPdfSigner` recibe un `CmsTimestampService` y, solo si `PdfSigningOptions.reniecTimestamp` está activo, añade el token al CMS después de una única operación privada del DNI y antes de publicar el PDF. Reutiliza el módulo JVM de Android: petición RFC3161, SHA256 del valor de firma, nonce, política, firma/ESS, EKU TSA crítico/exclusivo, TSL autenticada, raíz ROOT5 revisada y revocación vigente verificada. No se envía el PDF ni CAN/PIN a la TSA.

`DesktopTimestampServices` mantiene una única instancia de verificación por proceso y conserva secuencia/hash públicos de TSL en preferencias de usuario (`pe/gob/reniec/dnie/suite-trust`). No son credenciales ni sustituyen la firma XML y ancla de confianza. El registro rechaza retrocesos, cambios de hash a igual secuencia y contenido inválido; no es almacenamiento resistente a manipulación por el propietario de la cuenta ni una coordinación transaccional entre múltiples procesos.

Interfaz Swing: casilla **Sello RENIEC · PAdES-T**, desactivada inicialmente, aplicable a PDF visible/invisible. Se deshabilita para firma separada. Consola: `--pdf-tsa reniec`; `none` conserva Baseline B y es el valor por defecto. Valores inválidos, duplicados o uso con `--pdf-mode detached` se rechazan. Ambos flujos utilizan el mismo motor; los archivos no PDF del lote conservan CMS separado sin TSA.

Si la TSA falla, no se publica ese PDF ni se degrada silenciosamente a Baseline B. No se repite automáticamente la firma privada. Los temporales se eliminan; recuperación para reintentar solo el sellado sigue pendiente. Un fallo puede ocurrir después de que el DNI haya firmado: iniciar manualmente otro intento implicaría otra operación privada. Los documentos completos del lote se conservan según el motor existente.

## Artefactos y uso

- Interfaz: `D:/DNIeToolchain/Builds/tsa-reniec-h12/ABRIR-SUITE-TSA.bat`.
- Distribución GUI: `D:/DNIeToolchain/Builds/tsa-reniec-h12/signing-ui/install/dnie-signing-ui`.
- Distribución CLI: `D:/DNIeToolchain/Builds/tsa-reniec-h12/signing-desktop/install/dnie-signing-desktop`.
- Android sigue en APK63; identidad/emparejamiento PC persistente existente, sin crear otra huella.

Para consola, conservar los argumentos habituales `suite --pc ... --phone ... --workspace ... --identity ...` y agregar `--pdf-tsa reniec`, con `--pdf-mode visible` si se desea sello visual. En GUI, activar la casilla y conectar Android desde Suite continua/PC guardada. No requiere elevación ni lector expuesto al sistema.

## Verificación y alcance

76 pruebas sin fallos: validation-core40, PDF desktop13 y signing-desktop23; Swing compila (sin suite de pruebas propia). Pruebas nuevas cubren PDF visible/invisible con token RFC3161 sintético verificado y firma documental íntegra, hash del valor firmado, operación privada única, fallo TSA sin publicación, opción desactivada sin solicitud TSA, persistencia/rechazo de rollback y argumentos inválidos. `installDist` de GUI/CLI correcto; licencias de validación incluidas.

Experimento externo del **ejecutable distribuido**: comando `demo --pdf-mode visible --pdf-tsa reniec` con documento y firmante sintéticos, pero TSA RENIEC real y verificaciones de confianza de producción. Trabajo `b6c31728-cf85-4416-a82f-4e5b00e5e41c`, completos1/1. Verificación local posterior del PDF: PASS, firma CMS y token válidos, imprint coincidente y ByteRange completo; TSA 2026-10-07 18:19:45 UTC-05. Evidencias fuera de Git en `D:/DNIeToolchain/TsaExperiment/h12-live.log`, `h12-verification.log` y `H12Jobs`. El helper de inspección falló inicialmente por casts genéricos Java, se corrigió fuera del repositorio; no fue un fallo del ejecutable.

**Pendiente:** firma Windows con DNI real y validación externa en Firma Perú esperando VÁLIDO, PAdES_BASELINE_T y ambas raíces en TSL Sí. El experimento sintético no establece confianza del firmante ni sustituye esa prueba. Quedan LT/LTA, recuperación de solo sellado, consulta online automática del titular antes de firmar y condiciones de distribución/acceso al proveedor. No declara acreditación IOFE ni clasificación eIDAS.

Checkpoint final: `checkpoint/windows-tsa-reniec-h12`, archivo fuente y manifiesto en `D:/DNIeToolchain/Checkpoints/windows-tsa-reniec-h12`. Distribuciones previas preservadas.
