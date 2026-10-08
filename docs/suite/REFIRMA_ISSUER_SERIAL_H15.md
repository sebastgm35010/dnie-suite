# H15: identificación del firmante en ReFirma PCX

Fecha: 2026-10-07. Corrección compartida Android / Windows.

## Hallazgo y causa

El PDF aportado por el usuario conserva integridad criptográfica en ambas firmas y tokens TSA. Nuestra firma contiene signingCertificateV2 con hash SHA-256 correcto, pero sin issuerSerial. La segunda firma, añadida por ReFirma, sí incluye ese campo. Ambas identifican un certificado mediante SignerInfo.

Se inspeccionó, sin modificar su instalación, ReFirmaPCX-1.6.jar de AppRENIEC y su DSS 5.4.3. La política reports/constraint-PE.xml establece IssuerSerialMatch con nivel FAIL. IssuerSerialMatchCheck devuelve NO_SIGNING_CERTIFICATE_FOUND cuando no coincide ese atributo. El mensaje no demuestra ausencia del certificado: la prueba de identificación selecciona correctamente el certificado y verifica su hash, pero deja falsas las coincidencias de emisor y serie de nuestra firma anterior.

RFC 5035 recomienda incluir issuerSerial y admite omitirlo en determinados casos: https://www.rfc-editor.org/rfc/rfc5035.html . La omisión explica la diferencia entre la validación web satisfactoria y la política estricta de ReFirma; no se cambia ni se rebaja dicha política.

## Corrección

CmsSignatureBuilder añade IssuerSerial a ESSCertIDv2 en ambos perfiles, CMS separado y PAdES. Emisor tomado del ASN.1 del certificado (JcaX509CertificateHolder), número de serie exacto y hash SHA-256 existente. No modifica PACE, PIN, operación privada, transporte ni integración TSA. No requiere cambiar la interfaz ni la configuración de emparejamiento.

Regresión nueva comprueba ambos perfiles, emisor ASN.1, serie, identificación unívoca del certificado y verificación criptográfica. Los PDF anteriores no se parchean: alterar un atributo firmado exige crear una firma nueva.

## Verificación y límites

- Build Android Debug y distribuciones CLI/GUI Windows: satisfactorio.
- 247 pruebas, cero fallos, errores u omisiones en los nueve módulos seleccionados. Algunos resultados Gradle fueron reutilizados por no haber cambios en su entrada.
- Comparación sintética con el motor real DSS 5.4.3 de ReFirma: antes issuerEqual=false y serialEqual=false; después ambos true. SID, hash y candidato válidos en ambos casos. Documento sintético nuevo: lote 1/1.
- Esa prueba ejecuta identificación e integridad, no el validador completo de confianza/LTV. Pendiente: volver a firmar el acta con DNI real y TSA RENIEC y validar en ReFirma PCX y Firma Perú. No se declara todavía resultado externo VÁLIDO para la versión nueva.
- Evidencias originales, herramientas de inspección, logs y certificados personales permanecen fuera de Git en D:/DNIeToolchain.

## Entregables y checkpoint

Checkpoint previo: checkpoint/pre-refirma-issuer-h15; copia fuente en D:/DNIeToolchain/Checkpoints/pre-refirma-issuer-h15/source.zip.

APK64: D:/DNIeToolchain/AndroidPackages/dnie-evolution-2.0.0-rc16-ess-issuer-debug.apk.
SHA256: DC6D01C7A91DC0EF44491683FE5F3A7C40E94BBFC43361B0875E4D3EED10E37F.

GUI corregida: D:/DNIeToolchain/Builds/refirma-issuer-h15/ABRIR-SUITE-CORREGIDA.bat.
CLI: D:/DNIeToolchain/Builds/refirma-issuer-h15/signing-desktop/install/dnie-signing-desktop/bin/dnie-signing-desktop.bat.
El adaptador opcional Bridge mantiene la distribución H13 existente; no se reinstalan controladores ni se altera el puente.

Checkpoint final previsto: checkpoint/refirma-issuer-serial-h15; copia comprimida en D:/DNIeToolchain/Checkpoints/refirma-issuer-serial-h15/source.zip.
