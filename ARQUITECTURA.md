# Arquitectura de DNIe Suite

Documento canónico y consolidado de arquitectura del sistema para el monorepo DNIe Suite (v3.1.5).

---

## 1. Visión General

**DNIe Suite** es una plataforma integral de software libre para la interacción, lectura, firma digital y custodia de archivos mediante el **DNI electrónico peruano (DNIe v3)**.

La plataforma desacopla el hardware criptográfico (tarjeta con chip NFC) de los entornos de ejecución, permitiendo que un dispositivo móvil Android actúe como lector seguro y transceptor criptográfico para el dispositivo móvil y para computadoras personales conectadas.

```
┌────────────────────────────────────────────────────────┐
│                   DNIe v3 (Chip NFC)                   │
│       IAS / PKI Protegido / PACE / ICAO eMRTD         │
└───────────────────────────┬────────────────────────────┘
                            │ NFC (ISO/IEC 14443 Type A)
                            ▼
┌────────────────────────────────────────────────────────┐
│                     App Android                        │
│  - Jetpack Compose (Aether Fluid UI 120 Hz)            │
│  - Gestor NFC & PACE                                   │
│  - Bóveda Cifrada DNIe (Cero Biometría)                │
│  - Respaldo Autónomo DNIEBKP2                          │
└─────────────┬────────────────────────────┬─────────────┘
              │ TLS Inverso                │ PC/SC Bridge
              ▼                            ▼
┌───────────────────────────┐┌───────────────────────────┐
│     DNIe Suite Desktop    ││    DNIe Bridge Windows    │
│  - Firma PAdES (B-B, B-T) ││  - Lector PC/SC Virtual   │
│  - TSA Oficial (RENIEC)   ││  - ReFirma / Firma Perú   │
│  - Visor & Estampado PDF  ││  - Servicio de Fondo SCM  │
└───────────────────────────┘└───────────────────────────┘
```

---

## 2. Invariantes de Seguridad y Diseño

1. **Soberanía Criptográfica:** Las claves privadas de autenticación y firma nunca salen del chip seguro del DNIe. Las operaciones de cifrado/firma se ejecutan mediante comandos APDU delegados (`PSO: COMPUTE DIGITAL SIGNATURE`).
2. **Secuencia de Protocolo Estricta:** `IAS` → `PKI` → `PACE` → `PKI protegido`. Se mantiene Secure Messaging (SM) activo con token PACE, autenticación de mensaje (MAC), contador SSC secuencial y desenvolvimiento estricto de respuestas.
3. **Bóveda sin Biometría:** Por especificación de seguridad, la bóveda de archivos personales utiliza cifrado asimétrico híbrido vinculado al certificado del DNIe. El descifrado exige presencia física del DNIe y PIN; no se permite desbloqueo biométrico para evitar vulnerabilidades de suplantación en el sistema operativo.
4. **Protección de Secretos:** El PIN y CAN se almacenan únicamente en memoria volátil transitoria y se limpian al salir de la pantalla o pausar la actividad (`onPause`). No se persisten en disco ni se registran en registros del sistema.
5. **Aislamiento de Capas:** Los módulos JVM puros no importan dependencias de la plataforma Android (`android.*`) ni de interfaces de usuario.

---

## 3. Estructura Modular del Monorepo

El proyecto está organizado en módulos desacoplados con responsabilidades únicas:

### A. Núcleo JVM Compartido (Lógica y Protocolos)
- **`dnie-core`**: Modelos de datos comunes, generación de envoltorios criptográficos CMS/CAdES, serialización ASN.1 y operaciones criptográficas estándar.
- **`dnie-protocol`**: Implementación de canales de comunicación ISO 7816, negociación PACE (Password Authenticated Connection Establishment), Secure Messaging (3DES/AES) y sesión IAS/PKI para DNIe v3.
- **`dnie-icao`**: Lectura por bloques de documentos legibles por máquina (eMRTD / DG1, DG2, DG13, EF.SOD) con verificación de integridad de datos.
- **`dnie-validation-core`**: Validación de cadena de certificados X.509, consulta a listas de confianza TSL peruanas, OCSP y listas de revocación CRL.

### B. Bóveda Cifrada y Respaldo
- **`dnie-vault-core`**: Gestión de almacenamiento seguro local. Cifrado híbrido asimétrico respaldado por la clave pública del documento.
- **`dnie-vault-recovery`**: Motor de exportación e importación de copias de seguridad de bóveda (`.dniebkp2`). Permite respaldos autosuficientes con contraseña para contingencias de renovación de plástico DNIe sin depender de servidores externos.

### C. Plataforma Android
- **`app`**: Capa de aplicación Android desarrollada en Jetpack Compose bajo el sistema de diseño **Aether**. Implementa navegación por estados, orquestación de ViewModels y adaptación de ciclo de vida seguro.
- **`dnie-nfc`**: Adaptador de bajo nivel para `IsoDep` y `NfcAdapter` de Android en modo lector.
- **`dnie-pdf`**: Motor de lectura y generación de firmas PAdES en documentos PDF para Android.
- **`dnie-bridge-android`**: Implementación del puente de transceptor sobre red local / Tailnet o USB AOA para comunicar el lector NFC con clientes externos.

### D. Plataforma Desktop y Suite Windows
- **`dnie-pdf-desktop`**: Procesador de documentos PDF basado en Apache PDFBox 3.x para renderizado de sellos visuales e inyección de firmas PAdES Baseline.
- **`dnie-signing-core`**: Orquestación de trabajos de firma por lotes y validación de conformidades ETSI.
- **`dnie-signing-link`**: Protocolo de transporte seguro de un solo uso o sesión continua entre la PC y el dispositivo móvil.
- **`dnie-signing-desktop` & `dnie-signing-ui`**: Aplicación de escritorio para la visualización, colocación interactiva de firma digital y estampado con sellos gráficos.
- **`tools/windows-manager`**: Gestor de integración en Windows (`DNIeBridge.exe`), servicio nativo en segundo plano y compatibilidad PC/SC virtual para ReFirma y Firma Perú.

---

## 4. Flujo de Operaciones

### Firma Digital PAdES en Escritorio
```
Usuario                 Suite Desktop                   Android (NFC)               DNIe (Chip)
  │                           │                               │                          │
  ├─ Selecciona PDF y posición┤                               │                          │
  │                           ├─ Hash SHA-256 del PDF         │                          │
  │                           ├──────── TLS Inverso ─────────►│                          │
  │                           │                               ├─ Solicita PIN al usuario │
  │                           │                               ├─ Verifica PIN ──────────►│
  │                           │                               │◄─ PIN Aceptado ──────────┤
  │                           │                               ├─ PSO: Compute Signature ─►│
  │                           │                               │◄─ Bloque RSA Cifrado ────┤
  │                           │◄─────── Retorna Firma ────────┤                          │
  │                           ├─ Estampa PDF (PAdES Baseline) │                          │
  │                           ├─ Aplica Sello de Tiempo TSA   │                          │
  │◄─ Documento Firmado Guardado                              │                          │
```

---

## 5. Verificación de Integridad

El repositorio cuenta con validadores automatizados para garantizar que la arquitectura se mantenga limpia:

- **Límites de Arquitectura:**
  ```powershell
  python tools/check_architecture.py
  ```
  Comprueba que los módulos JVM no importen capas de plataforma y que no existan ciclos de dependencias entre módulos.
