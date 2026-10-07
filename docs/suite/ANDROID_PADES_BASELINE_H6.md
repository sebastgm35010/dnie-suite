# Android PAdES: primer incremento de conformidad

2026-10-07. Checkpoint previo `checkpoint/pre-android-pades-baseline-2026-10-07`, commit 247f3c0; fuente ZIP, Git bundle verificado y APK 57 conservados en D:/DNIeToolchain/Checkpoints/pre-android-pades-baseline-2026-10-07/.

## Cambio acotado

`CmsSignatureBuilder` ofrece perfiles explícitos CMS_DETACHED (comportamiento anterior por defecto) y PADES_BASELINE_B. El perfil PDF elimina signingTime incluso cuando BouncyCastle lo genera automáticamente y serializa SignedData completo en DER. La vinculación ESSCertIDv2 SHA256 al certificado y el proceso de firma/verificación de tarjeta se conservan. La documentación del constructor ya no declara cumplimiento legal sin evaluación.

`dnie-pdf/PadesSignerEngine` selecciona el perfil PAdES para ETSI.CAdES.detached. Un SubFilter legacy adbe.pkcs7.detached conserva CMS anterior. La hora /M se serializa en UTC, manteniendo el instante; el texto visible sigue usando su fecha local. No se modifica PACE/SSC/PIN, transportes, sello visual ni ubicación. Código Windows/desktop sin cambio de perfil ni distribución nueva.

APK 58 / 2.0.0-rc10-android-pades. No implementa TSA, OCSP/CRL de producción ni confianza TSL autenticada. No afirmar clasificación Baseline B antes de validar un PDF físico nuevo. El perfil expresa intención estructural, no acreditación IOFE ni firma cualificada europea.

## Verificación requerida

- Núcleo: DER canónico, ausencia de signingTime, ESSCertIDv2 correcto, certificados suministrados y firma íntegra; rechazo de alteración. Test CMS separado legado mantiene signingTime y verificación.
- PDF Android: estructura y CMS de firmas visibles/invisibles/sucesivas, /M UTC, ESS, integridad y rechazo de alteración.
- Regresión app y compilación APK; comprobación de compatibilidad del núcleo con módulos consumidores sin desplegar Windows.
- Prueba física: crear PDF nuevo con Firma PDF de Android (no suite remota Windows), visible o invisible; validar en el mismo portal y comprobar formato PAdES_BASELINE_B. Exportar resultado y diagnóstico sin compartir PIN/CAN. Archivos antiguos no cambian de clasificación; no intentar modificar su contenedor firmado.

Referencia: [plan general](PADES_CONFORMIDAD_PLAN.md), [investigación normativa](INVESTIGACION_PADES_PERU_ETSI.md), [comprobación puntual CRL](VERIFICACION_CERTIFICADO_CRL_2026_10_07.md).
## Resultado de la revisión

189 pruebas sin fallos en la ejecución final: dnie-core 50, dnie-pdf 20, app 74, dnie-signing-link 23, dnie-signing-desktop 22. APK Debug compilado, versionCode 58 y versionName 2.0.0-rc10-android-pades comprobados en metadata. Las pruebas de desktop son regresión del perfil existente; no se sustituyó su distribución instalada.

Primera ejecución desktop en G: tuvo cuatro fallos (temporales de identidad DPAPI/ACL y trabajos PDF). Repetición con -PdesktopBuildDir=D:/DNIeToolchain/Builds/android-pades-consumer-tests pasó todos los casos, sin cambiar motor/test; no ocultar esa limitación de unidad virtual. Logs en D:/DNIeToolchain/Temp/android-pades-build.log, android-pades-consumer-regression.log (fallido G:) y android-pades-consumer-regression-d-drive.log (final correcto).

APK: D:/DNIeToolchain/AndroidPackages/dnie-evolution-2.0.0-rc10-android-pades-debug.apk
SHA256: C71982E723D575FAC9BAE14C61650B3AF60427FBE277133F93F234C077A01330.

No instalado: teléfono primero offline y luego ausente de ADB. Prueba física y clasificación externa pendientes. Las comprobaciones ASN.1/PDF propias no sustituyen una evaluación independiente completa de conformidad.
## Prueba física reportada: Baseline B confirmado

2026-10-07: usuario recibió APK 58 por Telegram y aporta resultado de su PDF nuevo firmado desde Android. Firma generada 13:07:07 UTC−05; validada 13:07:41 UTC−05. Portal reporta VÁLIDO, integridad preservada, una firma válida, PAdES_BASELINE_B, RSA with SHA256 y raíz en TSL. No hay TSA; fecha declarada del equipo. Coincide certificado de firma y cadena ya observados, sin almacenar datos personales del reporte en Git.

La clasificación Baseline B queda confirmada para este artefacto según el resultado aportado; no es homologación del producto, conformidad exhaustiva de todos los casos ni validación local independiente del PDF, que no fue adjuntado en esta prueba. El estado «no instalado/pendiente» anterior corresponde al momento de publicar el APK; esta prueba posterior acredita su uso reportado. Según el orden autorizado, se inicia traslado del mismo perfil explícito a PDF desktop, manteniendo .p7s y transportes.