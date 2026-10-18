# DNIe Suite

Monorepo privado de Android, Suite de firma Windows y puente NFC–PC/SC. Versión funcional actual: **3.1.4**.

El teléfono gestiona NFC/PACE y las operaciones del DNI. La Suite procesa documentos en la PC mediante su conexión privada al teléfono. El Bridge ofrece, por separado, compatibilidad PC/SC con aplicaciones como ReFirma y Firma Perú. Los motores, transportes e interfaces conservan sus módulos independientes.

## Empezar aquí

- [Experiencia Flagship R11 y Fluid UI (3.1.4 / APK 88)](docs/design/EXPERIENCIA_FLAGSHIP_R11.md). Rediseño de flujos insignia y pulido Organic Motion en Android Compose, Swing y WinForms.
- [Rediseño Aether 3.1: cambios y pruebas](docs/design/RESULTADO_AETHER_R2.md). Android82 instalada; APK83 preparada sin ADB y Windows R7 actualizado. [Pulido R4 y pruebas](docs/design/RESULTADO_AETHER_R4.md). [Red coordinada y simplificación R6](docs/architecture/RED_COORDINADA_R6.md). [Personalización R7 y pruebas](docs/design/PERSONALIZACION_SELLO_HAPTICA_R7.md). QR R5 permanece como referencia histórica.

- [Estado actual](docs/CONTEXTO_ACTUAL.md), [pendientes](docs/PENDIENTES_ACTUALES.md) y [arquitectura](docs/architecture/ARQUITECTURA_ACTUAL_2_0.md).
- [Versión 3.1 y verificaciones](docs/architecture/SEGURIDAD_BACKEND_3_1_V4.md).
- [Compilación y configuración de un clon](docs/operations/GITHUB_Y_COMPILACION_MONOREPO.md).
- [Contexto de la Suite](docs/suite/CONTEXTO_SUITE_FIRMA.md) y [registro SQL consultable](docs/project-registry/README.md).
- [Índice completo de documentación](docs/README.md) y [reglas de trabajo](AGENTS.md).

## Organización

```text
app/                          Android: interfaz, casos de uso y adaptadores
dnie-core/                    CMS/CAdES, modelos y criptografía compartida
dnie-protocol/                DNI v3, PACE y mensajería segura
dnie-nfc/                     Adaptación NFC Android
dnie-icao/                    Lectura de identidad ICAO por bloques
dnie-signing-core/            Motor de firma por lotes y contratos PDF
dnie-network-core/            Descubrimiento autenticado independiente de plataforma
dnie-signing-link/            Protocolo privado teléfono–Suite
dnie-pdf/                     PDF/PAdES Android
dnie-pdf-desktop/             PDF/PAdES JVM
dnie-signing-desktop/         Aplicación y consola de la Suite
dnie-signing-ui/              Interfaz Windows de la Suite
dnie-desktop-orchestration/   Coordinación opcional Suite–Bridge
dnie-bridge-core/             Contratos del puente
dnie-bridge-android/          Transporte y sesión del puente Android
dnie-vault-core/              Bóveda local cifrada
dnie-vault-recovery/          Respaldo y recuperación de bóveda
dnie-validation-core/         Certificados, TSL, OCSP y CRL
tools/windows-*/              Bridge, servicio, gestor, driver y adaptadores
tools/build_suite.py          Entrada común de compilación
docs/                         Contexto, arquitectura, evidencias y pendientes
docs/project-registry/        SQLite y exportación SQL del proyecto
```

Las pruebas permanecen junto a sus módulos (`src/test`, `src/androidTest`, `tests`). No se trasladan motores a carpetas de interfaz ni se duplican fuentes para distribuir los productos por separado.

## Compilar

Requiere Python 3.10+, JDK 17 y, según el componente, Android SDK 34 o .NET SDK 8 con Windows Desktop. El driver requiere además MSVC/SDK/WDK y se compila explícitamente.

```powershell
# Mostrar el plan sin compilar ni instalar nada.
python tools/build_suite.py --component all --output D:/DNIeToolchain/Builds/clon --plan

# Android, Suite y paquete Bridge; resultados fuera del repositorio.
python tools/build_suite.py --component all --output D:/DNIeToolchain/Builds/clon --test

# Productos independientes.
python tools/build_suite.py --component android --output D:/DNIeToolchain/Builds/android
python tools/build_suite.py --component suite --output D:/DNIeToolchain/Builds/suite
python tools/build_suite.py --component bridge --output D:/DNIeToolchain/Builds/bridge
```

La compilación no instala servicios ni controladores, no modifica firewall y no solicita DNI/PIN/CAN. `all` reúne las aplicaciones; el driver es optativo mediante `--component driver`.

Un clon sin keystore privado puede compilar Android debug con la identidad debug local del SDK. Para actualizar la instalación ya existente en un teléfono hay que conservar su keystore original. Release sin una identidad configurada genera un APK sin firmar. La configuración de firma está descrita en [la guía](docs/operations/GITHUB_Y_COMPILACION_MONOREPO.md).

## Código y distribución

Git contiene fuentes, pruebas, licencias de dependencias, documentación y el registro SQL. APK/instaladores se distribuyen como artefactos o Releases; claves, perfiles de emparejamiento, capturas APDU, documentos y bases del usuario permanecen fuera del repositorio.

Las rutas G:/D: de informes históricos documentan este entorno, no son ubicaciones que un clon deba reproducir. El instalador Windows actual conserva su perfil operativo en D:/DNIeBridgeApp; su generalización a equipos sin unidad D: sigue pendiente. La compilación común permite elegir otra ubicación para sus resultados.
