# DNIe Suite

Monorepo de Android, Suite de firma digital para Windows y puente de interoperabilidad NFC–PC/SC para el DNI electrónico peruano (DNIe v3). Versión funcional: **3.1.6**.

El teléfono actúa como lector seguro de proximidad NFC/PACE y transceptor criptográfico del DNIe. La Suite procesa y estampa documentos PDF en la PC mediante un enlace TLS privado con el dispositivo móvil. El módulo Bridge proporciona emulación de lector PC/SC virtual en Windows para permitir firma en aplicaciones oficiales como ReFirma (RENIEC) y Firma Perú (PCM).

---

## Documentación Principal

- **[Arquitectura del Sistema (ARQUITECTURA.md)](ARQUITECTURA.md)**: Visión global de capas, módulos, protocolos criptográficos (IAS/PKI/PACE), bóveda segura y flujos de firma.
- **[Sistema de Diseño Aether (DESIGN.md)](DESIGN.md)**: Especificación de paletas (Claro/Oscuro), tipografía, animaciones Organic Motion a 120 Hz e interfaz de usuario en Compose, Swing y WinForms.

---

## Organización Modular

```text
app/                          Android: interfaz Compose, ViewModels y adaptadores
dnie-core/                    Criptografía CMS/CAdES, modelos y envolturas ASN.1
dnie-protocol/                Controlador DNIe v3, protocolo PACE y Secure Messaging (SM)
dnie-nfc/                     Adaptador NFC Android (IsoDep/ReaderMode)
dnie-icao/                    Lectura y validación de datos de identidad ICAO eMRTD
dnie-signing-core/            Motor de firma por lotes y especificación de contratos PDF
dnie-network-core/            Descubrimiento autenticado y transporte de red
dnie-signing-link/            Canal de comunicación seguro teléfono–Suite PC
dnie-pdf/                     Adaptador de firma PAdES para Android
dnie-pdf-desktop/             Adaptador de firma PAdES JVM (PDFBox 3.x)
dnie-signing-desktop/         Consola y backend de firma para PC
dnie-signing-ui/              Interfaz gráfica de usuario de escritorio (Swing)
dnie-desktop-orchestration/   Coordinación de servicios entre Suite y Bridge
dnie-bridge-core/             Contratos y protocolos de transceptor de tarjeta
dnie-bridge-android/          Transporte del puente en Android (Wi-Fi/Tailnet/USB)
dnie-vault-core/              Bóveda local de archivos con cifrado asimétrico
dnie-vault-recovery/          Exportación e importación de respaldos autónomos DNIEBKP2
dnie-validation-core/         Validación de certificados, TSL, OCSP y CRL
tools/windows-*/              Gestor nativo Windows, servicio de fondo y driver PC/SC
tools/build_suite.py          Herramienta unificada de compilación
```

---

## Compilación

Requiere Python 3.10+, JDK 17 y Android SDK 34 o .NET SDK 8 según el módulo a compilar:

```powershell
# Mostrar el plan de compilación sin modificar el sistema
python tools/build_suite.py --component all --output D:/DNIeToolchain/Builds/clon --plan

# Compilar todos los componentes principales (Android, Suite y Bridge)
python tools/build_suite.py --component all --output D:/DNIeToolchain/Builds/clon --test

# Compilar componentes individuales
python tools/build_suite.py --component android --output D:/DNIeToolchain/Builds/android
python tools/build_suite.py --component suite --output D:/DNIeToolchain/Builds/suite
python tools/build_suite.py --component bridge --output D:/DNIeToolchain/Builds/bridge
```

---

## Invariantes de Seguridad

- **Custodia en el Chip:** Las claves privadas de firma y autenticación residen exclusivamente dentro de la tarjeta inteligente; el software nunca las extrae ni las almacena en memoria de host.
- **Cero Persistencia de Secretos:** Los códigos PIN son manipulados exclusivamente en estructuras temporales en memoria y se limpian al salir de pantalla o al pausar la aplicación.
- **Validación Continua:** Las capas de software se verifican de manera automatizada mediante:
  ```powershell
  python tools/check_architecture.py
  ```
