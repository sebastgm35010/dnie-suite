# Project: Peru DNIe v2/v3 Native Android App (IsoDep NFC & PAdES Digital Signer)

## Architecture
- **Platform**: Android Native (Kotlin 2.0.20, AGP 8.5.2, Gradle 8.9 Wrapper, minSdk 24, compileSdk 34, targetSdk 34).
- **Presentation**: Jetpack Compose BOM 2024.09.02 + Material 3, Navigation Compose, Lifecycle ViewModel Compose.
- **Hardware Layer**: Android NFC framework (`android.nfc.tech.IsoDep`, `NfcAdapter.enableReaderMode`), ISO 7816-4 APDU protocol engine.
- **Security & Cryptography**: BouncyCastle 1.78.1 (`bcprov-jdk18on`, `bcpkix-jdk18on`), JCA Provider isolation, ASN.1 DER parser, CMS / PKCS#7 detached container generator, RFC 5035 `signingCertificateV2`.
- **Document Engine**: TomRoush `pdfbox-android` 2.0.27.0, PAdES Baseline (`ETSI.CAdES.detached` / `Adobe.PPKLite`), incremental save (`saveIncremental`) preserving revision history.
- **Design Pattern**: Clean Architecture with MVI-MVVM, immutable `StateFlow<UiState>`, one-way data flow, reactive APDU event bus.

## Code Layout
```
app/
├── build.gradle.kts
├── proguard-rules.pro
└── src/
    ├── main/
    │   ├── AndroidManifest.xml
    │   ├── res/
    │   │   ├── values/
    │   │   │   ├── colors.xml
    │   │   │   ├── strings.xml
    │   │   │   └── themes.xml
    │   │   └── xml/
    │   │       └── file_paths.xml
    │   └── java/pe/gob/reniec/dnie/
    │       ├── DnieApplication.kt
    │       ├── MainActivity.kt
    │       ├── core/
    │       │   ├── apdu/
    │       │   │   ├── CommandApdu.kt
    │       │   │   ├── ResponseApdu.kt
    │       │   │   ├── StatusWord.kt
    │       │   │   └── ApduConstants.kt
    │       │   ├── nfc/
    │       │   │   ├── DnieTransceiver.kt
    │       │   │   ├── IsoDepCardChannel.kt
    │       │   │   ├── NfcReaderManager.kt
    │       │   │   └── CardProtocolException.kt
    │       │   └── logging/
    │       │       ├── ApduLogEntry.kt
    │       │       └── ApduLogger.kt
    │       ├── crypto/
    │       │   ├── CryptoProviderInitializer.kt
    │       │   ├── DnieContentSigner.kt
    │       │   ├── CmsSignatureBuilder.kt
    │       │   ├── X509CertificateParser.kt
    │       │   ├── CertificateExporter.kt
    │       │   └── RawHashSigner.kt
    │       ├── pdf/
    │       │   ├── PadesSignerEngine.kt
    │       │   ├── SignaturePreset.kt
    │       │   ├── VisualStampRenderer.kt
    │       │   └── PdfSignResult.kt
    │       ├── domain/
    │       │   ├── model/
    │       │   │   ├── CertificateInfo.kt
    │       │   │   ├── CertificateType.kt
    │       │   │   ├── CitizenData.kt
    │       │   │   ├── PinVerificationResult.kt
    │       │   │   └── SigningRequest.kt
    │       │   └── usecase/
    │       │       ├── ReadCertificatesUseCase.kt
    │       │       ├── VerifyPinUseCase.kt
    │       │       ├── SignPdfUseCase.kt
    │       │       └── SignHashUseCase.kt
    │       └── ui/
    │           ├── theme/
    │           │   ├── Color.kt
    │           │   ├── Theme.kt
    │           │   └── Type.kt
    │           ├── navigation/
    │           │   ├── Screen.kt
    │           │   └── NavGraph.kt
    │           ├── components/
    │           │   ├── PinEntryDialog.kt
    │           │   ├── NfcTapPromptSheet.kt
    │           │   ├── CertificateCard.kt
    │           │   └── HexViewer.kt
    │           ├── certificates/
    │           │   ├── CertificatesViewModel.kt
    │           │   └── CertificatesScreen.kt
    │           ├── signer/
    │           │   ├── SignerViewModel.kt
    │           │   └── SignerScreen.kt
    │           └── console/
    │               ├── ConsoleViewModel.kt
    │               └── ConsoleScreen.kt
    └── test/java/pe/gob/reniec/dnie/
        ├── apdu/
        │   ├── ApduCommandResponseTest.kt
        │   └── MockIsoDepCardChannel.kt
        ├── nfc/
        │   └── DnieTransceiverTest.kt
        ├── crypto/
        │   ├── X509CertificateParserTest.kt
        │   ├── CmsSignatureBuilderTest.kt
        │   └── RawHashSignerTest.kt
        ├── pdf/
        │   ├── PadesSignerEngineTest.kt
        │   └── VisualStampRendererTest.kt
        └── e2e/
            ├── DnieEndToEndScenarioTest.kt
            └── BoundaryAndCornerCasesTest.kt
```

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| F1 | IsoDep Reader Mode & Timeouts | Configuración `enableReaderMode` con flags A/skip-NDEF, timeout 5000-10000ms | M2 | spec_miner_nfc |
| F2 | Abstracción APDU ISO 7816-4 | Clases `CommandApdu`, `ResponseApdu`, `StatusWord` y parsing de SW | M2 | spec_miner_nfc |
| F3 | Selección Applet PKI RENIEC | APDU SELECT con AID `E8 28 BD 08 0F D2 50 47 65 6E 65 72 69 63` | M2 | spec_miner_nfc |
| F4 | Extracción Certificado Firma | SELECT EF `00 1D` y lectura secuencial de bloques `0x00E4` | M2 | spec_miner_nfc |
| F5 | Extracción Certificado Autenticación | SELECT EF `00 1C` y lectura secuencial de bloques `0x00E4` | M2 | spec_miner_nfc |
| F6 | Extracción Certificado Cifrado | SELECT EF `00 1B` y lectura secuencial de bloques `0x00E4` | M2 | spec_miner_nfc |
| F7 | Procesamiento TLV y Trimming DER | Extracción de carga útil tag `0x53`, recorte exacto según ASN.1 length y padding clean | M2 | spec_miner_nfc |
| F8 | Verificación PIN en Tarjeta | APDU VERIFY `00 20 00 81` con codificación ASCII y validación | M2 | spec_miner_nfc |
| F9 | Manejo de Contadores y Bloqueo | Detección `63 CX` (reintentos restantes) y alarma `69 83` (tarjeta bloqueada) | M2 | spec_miner_nfc |
| F10 | Configuración MSE RSA-2048 | APDU `00 22 41 B6 06 80 01 8A 84 01 81` para preparar DST | M2 | spec_miner_nfc |
| F11 | Cómputo Firma PSO RSA-2048 | APDU `00 2A 9E 9A 33` con prefijo DER SHA-256 de 19b + hash 32b, retorno 256b | M2 | spec_miner_nfc |
| F12 | Event Bus de Diagnóstico APDU | Emisión reactiva de comandos, respuestas, tiempos y status words | M2 | spec_miner_nfc |
| F13 | Inicialización Criptográfica BouncyCastle | Inserción en posición 1 de `BouncyCastleProvider` y configuración de seguridad | M3 | spec_miner_crypto |
| F14 | Parser e Inspector X.509 | Extracción de Subject, Issuer, Serial, Fechas, KeyUsage y huella SHA-256 | M3 | spec_miner_crypto |
| F15 | Extractor DNI de Ciudadano | Algoritmo multi-nivel para extraer DNI desde Subject DN SERIALNUMBER, CN o SAN | M3 | spec_miner_crypto |
| F16 | Exportador Certificados .cer / .pem | Generación de archivos binarios DER (.cer) y Base64 PEM (.pem) | M3 | spec_miner_crypto |
| F17 | ContentSigner DNIe BouncyCastle | Adaptador de `org.bouncycastle.operator.ContentSigner` conectado a firma en tarjeta | M3 | spec_miner_crypto |
| F18 | Generador Contenedor CMS / PKCS#7 | Creación de `SignedData` detached con `signingCertificateV2`, signingTime y digest | M3 | spec_miner_crypto |
| F19 | Motor PAdES PDFBox Android | Implementación de `SignatureInterface` e integración PAdES con PDFBox Android | M3 | spec_miner_crypto |
| F20 | Guardado Incremental PDF | Uso estricto de `saveIncremental` para no invalidar firmas previas o historial | M3 | spec_miner_crypto |
| F21 | Estampado Visual de Firma | Presets (Inferior-Derecha, Inferior-Izquierda, Superior-Derecha, Personalizado) y layout Perú | M3 | spec_miner_crypto |
| F22 | Firma Invisible de PDF | Configuración de firma digital sin creación de widget de anotación visual | M3 | spec_miner_crypto |
| F23 | Firma de Hashes Crudos | Servicio para firmar resúmenes SHA-256 crudos o generar contenedores .p7s | M3 | spec_miner_crypto |
| F24 | Toolchain Gradle & Plugins | Gradle 8.9, AGP 8.5.2, Kotlin 2.0.20 + plugin `org.jetbrains.kotlin.plugin.compose` | M1 | explorer_app_arch |
| F25 | AndroidManifest & Permisos | Permisos NFC, VIBRATE, feature NFC obligatoria, FileProvider y singleTop | M1 | explorer_app_arch |
| F26 | Reglas ProGuard / R8 | Preservación de BouncyCastle, PDFBox Android y Coroutines en compilación Release | M1 | explorer_app_arch |
| F27 | Tema e Identidad Material 3 | Paleta institucional, tipografía, dynamic color y modo oscuro en Compose | M4 | explorer_app_arch |
| F28 | Flujo Seguro de PIN en 2 Pasos | Captura y validación de PIN en UI antes de solicitar aproximación física de tarjeta | M4 | explorer_app_arch |
| F29 | Ciclo de Vida NFC Reader Mode | Manejo seguro de attach/detach y prevención de `TagLostException` | M4 | explorer_app_arch |
| F30 | Pantalla Inspector de Certificados | Visualización tabulada de certificados, estados, datos del titular y exportación SAF | M4 | explorer_app_arch |
| F31 | Pantalla Firmador de PDFs | Selección SAF de archivo, selección de preset visual, preview y botón de firma | M4 | explorer_app_arch |
| F32 | Consola Diagnóstica APDU | Stream reactivo, formato hex resaltado, vibración/audio y exportación de logs | M4 | explorer_app_arch |
| F33 | Arquitectura MVI-MVVM Reactiva | ViewModels con `StateFlow`, estados inmutables y separación estricta de capas | M4 | explorer_app_arch |
| F34 | Suite de Pruebas E2E Multi-Tier | Pruebas Tiers 1-4 basadas en requerimientos opacos sin depender de hardware | M5 | test_infra |
| F35 | Simulador Mock IsoDep / APDU | Tarjeta DNIe virtual para testing determinista y CI/CD | M5 | test_infra |
| F36 | Validación Criptográfica PAdES | Verificación de contenedores CMS generados, firma RSA y estructura PDF | M5 | test_infra |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Project Scaffolding & Gradle Build | Gradle 8.9, AGP 8.5.2, Kotlin 2.0, Compose, Manifest, ProGuard, Dependencias | none | DONE |
| M2 | NFC & APDU Protocol Transceiver | `CommandApdu`, `ResponseApdu`, `DnieTransceiver`, cert extraction, PIN, RSA-2048 | M1 | DONE |
| M3 | Cryptography & PDF Signing Engine | BouncyCastle, X.509 parser, CMS container, PAdES incremental save, PDFBox stamps | M1 | DONE |
| M4 | Jetpack Compose UI & ViewModels | M3 Theme, 2-step PIN flow, Cert Inspector, PDF Signer, APDU Console | M2, M3 | DONE |
| M5 | E2E Testing & Final Integration | Pass 100% E2E tests (Tiers 1-4) & Adversarial Coverage Hardening (Tier 5) | M2, M3, M4 | IN_PROGRESS |

## Interface Contracts

### 1. `DnieTransceiver` ↔ `DnieCardChannel` (Hardware Abstraction)
```kotlin
interface DnieCardChannel {
    fun transceive(command: ByteArray): ByteArray
    fun isConnected(): Boolean
    fun setTimeout(timeoutMs: Int)
}
```

### 2. `DnieTransceiver` ↔ Domain Layer (`ReadCertificates`, `VerifyPin`, `SignHash`)
```kotlin
interface DnieTransceiverInterface {
    suspend fun selectPkiApplet(): Result<Unit>
    suspend fun readCertificate(type: CertificateType): Result<ByteArray>
    suspend fun verifyPin(pin: String): Result<PinVerificationResult>
    suspend fun signSha256Hash(hash32Bytes: ByteArray): Result<ByteArray>
}

enum class CertificateType(val fileId: ByteArray) {
    SIGNATURE(byteArrayOf(0x00, 0x1D)),
    AUTHENTICATION(byteArrayOf(0x00, 0x1C)),
    ENCRYPTION(byteArrayOf(0x00, 0x1B))
}

sealed class PinVerificationResult {
    object Success : PinVerificationResult()
    data class InvalidPin(val attemptsRemaining: Int) : PinVerificationResult()
    object CardBlocked : PinVerificationResult()
}
```

### 3. `PadesSignerEngine` ↔ `DnieTransceiver` (PDF Signing Contract)
```kotlin
interface PadesSignerEngineInterface {
    suspend fun signPdf(
        pdfInputStream: InputStream,
        pdfOutputStream: OutputStream,
        signingCertificateDer: ByteArray,
        cardSigner: suspend (sha256Hash: ByteArray) -> Result<ByteArray>,
        preset: SignaturePreset,
        reason: String,
        location: String
    ): Result<PdfSignResult>
}
```

### 4. `X509CertificateParser` ↔ UI / Domain Models
```kotlin
data class CertificateInfo(
    val type: CertificateType,
    val subjectDn: String,
    val issuerDn: String,
    val citizenName: String,
    val citizenDni: String,
    val serialNumberHex: String,
    val validFrom: Date,
    val validTo: Date,
    val isExpired: Boolean,
    val keyUsageDescription: String,
    val sha256FingerprintHex: String,
    val rawDerBytes: ByteArray
)
```
