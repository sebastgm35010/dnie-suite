# PAdES Baseline B: Android confirmado y traslado a escritorio

2026-10-07. Usuario aporta validación física del PDF firmado en Android APK 58: VÁLIDO, PAdES_BASELINE_B, integridad y raíz TSL reconocidas, RSA/SHA256; sin TSA. Generado 13:07:07 y validado 13:07:41, UTC−05. Se registra resultado reportado, no validación local nueva del artefacto personal. [Detalle Android](ANDROID_PADES_BASELINE_H6.md).

Según el orden autorizado, se aplica en JvmPdfSigner el perfil PADES_BASELINE_B ya probado en Android y /M UTC. CMS separado conserva su perfil por defecto. No cambian sello visible, conexión privada, protocolo, tarjeta, servicio Windows ni lector compatible.

## Checkpoints

Previo: checkpoint/pre-desktop-pades-baseline-2026-10-07, sobre commit 1638a66. D:/DNIeToolchain/Checkpoints/pre-desktop-pades-baseline-2026-10-07 contiene source.zip y gui-before.zip (distribución anterior completa). Checkpoint Android previo y APK 57 permanecen guardados.

## Verificación y distribución

104 pruebas sin fallos: core 50, link 23, PDF desktop 9, signing desktop 22. Se añadieron comprobaciones de DER canónico, ausencia de signingTime, vínculo ESS, UTC y ByteRange a los casos de firmas visibles/invisibles/sucesivas y giro/recorte; verificaciones criptográficas existentes preservadas. Temporales de pruebas/distribuciones en D: mediante desktopBuildDir; no repetir pruebas de ACL sobre G: virtual.

Log D:/DNIeToolchain/Temp/desktop-pades-baseline-build.log. GUI y CLI compiladas. Distribuciones nuevas aisladas, conservando las anteriores:

- GUI: D:/DNIeToolchain/Builds/pades-baseline-b/signing-ui/install/dnie-signing-ui/DNIe Suite.lnk
- CLI: D:/DNIeToolchain/Builds/pades-baseline-b/signing-desktop/install/dnie-signing-desktop/bin/dnie-signing-desktop.bat

Estas rutas contienen el perfil nuevo; las rutas anteriores signing-desktop-h0 y signing-ui mantienen sus binarios anteriores. No se interrumpieron procesos ni se lanzó una conexión DNI automáticamente. Usar la nueva distribución para la prueba siguiente; identidad PC guardada y workspace pueden reutilizarse.

Ejecutable demo real, tarjeta/certificado simulados: trabajo a4cab15e-be29-440e-8ef6-071d1957e309, PDF visible 1/1. Evidencia D:/DNIeToolchain/PadesBaselineEvidence/jobs/. Verificador independiente .NET SignedCms confirma integridad, certificado de ese trabajo y rechazo de alteración. No comprueba confianza/revocación ni clasificación PAdES por portal; no confundir con DNI real. Comprobación estructural de perfil en pruebas locales.

## Pendientes reales

1. PDF nuevo firmado desde GUI/CLI Windows con DNI y validado externamente como PAdES_BASELINE_B; Android ya confirmado para el artefacto aportado.
2. Motor de validación de cadena/revocación y autenticación XML TSL; consulta CRL puntual previa no es integración de producción.
3. TSA autorizado configurable para T, luego evidencia DSS del PDF LT/LTA; sin prometer TSA RENIEC sin autorización ni firma cualificada eIDAS/acreditación IOFE.

No requiere APK distinto de 58 para este traslado. Se conserva el motor de firma del teléfono.