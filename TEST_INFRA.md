# E2E Test Infra: Peru DNIe v2/v3 Native Android App

## Ejecución en el fork modular

Las pruebas del núcleo/APDU/criptografía están en `dnie-core`; las de NFC abstracto y sesiones en `dnie-protocol`; las de PDF en `dnie-pdf`; las de ViewModels en `app`. Ejecutar todos los grupos:

```powershell
.\gradlew.bat :dnie-core:test :dnie-protocol:test :dnie-vault-core:test :dnie-icao:test :dnie-pdf:testDebugUnitTest :app:testDebugUnitTest :app:exportBranchApk --console=plain
```

La validación de tarjeta física de la 1.0.8 corresponde a la base clausurada. Este APK reorganizado requiere repetir la firma real antes de considerarlo validado en el DNI.

El resto del documento conserva las notas de infraestructura originales; las rutas que mencionan solo `app/src/test` deben interpretarse según el módulo propietario indicado arriba.

## Test Philosophy
- **Requirement-Driven & Opaque-Box**: Tests are derived strictly from `ORIGINAL_REQUEST.md` and `dnie_peru_android_spec.md`, without coupling to internal private methods.
- **Hardware-Decoupled Verification**: Incorporates a high-fidelity `MockIsoDepCardChannel` capable of emulating the RENIEC PKI applet, sequential certificate TLV responses, PIN verification status words (`90 00`, `63 CX`, `69 83`), and RSA-2048 modular exponentiation signatures. This allows 100% deterministic local test execution and CI/CD validation.
- **Cryptographic Conformance**: Real BouncyCastle JCA cryptographic verification of output signatures, ASN.1 structures, CMS containers, and PDFBox incremental revision checks.

## Feature Inventory Mapping
| # | Feature | Requirement Source | Tier 1 | Tier 2 | Tier 3 |
|---|---------|-------------------|:------:|:------:|:------:|
| F1 | IsoDep Reader Mode & Timeouts | ORIGINAL_REQUEST §R1, R3 | 5 | 5 | ✓ |
| F2 | Abstracción APDU ISO 7816-4 | ORIGINAL_REQUEST §R1 | 5 | 5 | ✓ |
| F3 | Selección Applet PKI RENIEC | ORIGINAL_REQUEST §R1 | 5 | 5 | ✓ |
| F4 | Extracción Certificado Firma (00 1D) | ORIGINAL_REQUEST §R1 | 5 | 5 | ✓ |
| F5 | Extracción Certificado Autenticación (00 1C) | ORIGINAL_REQUEST §R1 | 5 | 5 | ✓ |
| F6 | Extracción Certificado Cifrado (00 1B) | ORIGINAL_REQUEST §R1 | 5 | 5 | ✓ |
| F7 | Procesamiento TLV y Trimming DER | ORIGINAL_REQUEST §R1 | 5 | 5 | ✓ |
| F8 | Verificación PIN en Tarjeta (0x81) | ORIGINAL_REQUEST §R1 | 5 | 5 | ✓ |
| F9 | Manejo de Contadores y Bloqueo (63 CX, 69 83) | ORIGINAL_REQUEST §R1 | 5 | 5 | ✓ |
| F10 | Configuración MSE RSA-2048 | ORIGINAL_REQUEST §R1 | 5 | 5 | ✓ |
| F11 | Cómputo Firma PSO RSA-2048 | ORIGINAL_REQUEST §R1 | 5 | 5 | ✓ |
| F12 | Event Bus de Diagnóstico APDU | ORIGINAL_REQUEST §R3 | 5 | 5 | ✓ |
| F13 | Inicialización Criptográfica BouncyCastle | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ |
| F14 | Parser e Inspector X.509 | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ |
| F15 | Extractor DNI de Ciudadano | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ |
| F16 | Exportador Certificados .cer / .pem | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ |
| F17 | ContentSigner DNIe BouncyCastle | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ |
| F18 | Generador Contenedor CMS / PKCS#7 | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ |
| F19 | Motor PAdES PDFBox Android | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ |
| F20 | Guardado Incremental PDF | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ |
| F21 | Estampado Visual de Firma (Presets) | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ |
| F22 | Firma Invisible de PDF | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ |
| F23 | Firma de Hashes Crudos | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ |
| F24 | Toolchain Gradle & Plugins | ORIGINAL_REQUEST §R4 | 5 | 5 | ✓ |
| F25 | AndroidManifest & Permisos | ORIGINAL_REQUEST §R4 | 5 | 5 | ✓ |
| F26 | Reglas ProGuard / R8 | ORIGINAL_REQUEST §R4 | 5 | 5 | ✓ |
| F27 | Tema e Identidad Material 3 | ORIGINAL_REQUEST §R3 | 5 | 5 | ✓ |
| F28 | Flujo Seguro de PIN en 2 Pasos | ORIGINAL_REQUEST §R3 | 5 | 5 | ✓ |
| F29 | Ciclo de Vida NFC Reader Mode | ORIGINAL_REQUEST §R3 | 5 | 5 | ✓ |
| F30 | Pantalla Inspector de Certificados | ORIGINAL_REQUEST §R3 | 5 | 5 | ✓ |
| F31 | Pantalla Firmador de PDFs | ORIGINAL_REQUEST §R3 | 5 | 5 | ✓ |
| F32 | Consola Diagnóstica APDU | ORIGINAL_REQUEST §R3 | 5 | 5 | ✓ |
| F33 | Arquitectura MVI-MVVM Reactiva | ORIGINAL_REQUEST §R3 | 5 | 5 | ✓ |

## Test Architecture
- **Location**: `app/src/test/java/pe/gob/reniec/dnie/`
- **Runner**: JUnit 4 / Kotlin Test / Coroutines Test (`kotlinx-coroutines-test`)
- **Invocation**: `./gradlew test` or individual test tasks.
- **Pass/Fail Semantics**: All test suites must execute with 0 failures, 0 errors, and strict exit code 0.

## Real-World Application Scenarios (Tier 4)
| # | Scenario | Features Exercised | Complexity |
|---|----------|--------------------|------------|
| 1 | Lectura Completa de Identidad y Exportación de Certificados | F1, F3, F4, F5, F6, F7, F14, F15, F16, F30 | High |
| 2 | Firma Digital PAdES Visible de Contrato PDF con Preset Inferior-Derecha | F1, F3, F4, F8, F10, F11, F13, F17, F18, F19, F20, F21, F28, F31 | High |
| 3 | Firma Digital PAdES Invisible y Preservación de Firma Previa Incremental | F8, F10, F11, F18, F19, F20, F22 | High |
| 4 | Flujo de Reintentos de PIN y Alerta de Bloqueo de Tarjeta | F8, F9, F12, F28, F32 | Medium |
| 5 | Firma Criptográfica de Hashes Crudos y Verificación CMS Detached | F10, F11, F17, F18, F23 | Medium |
| 6 | Diagnóstico en Tiempo Real y Registro de Trazas APDU | F2, F3, F4, F12, F32 | Medium |

## Coverage Thresholds
- Tier 1: ≥5 test cases per feature (happy path isolation).
- Tier 2: ≥5 boundary, corner, and error condition tests per feature area.
- Tier 3: Pairwise combination test cases across hardware, crypto, and PDF engines.
- Tier 4: ≥6 realistic end-to-end application scenarios.
- Tier 5: Adversarial edge-case hardening.
