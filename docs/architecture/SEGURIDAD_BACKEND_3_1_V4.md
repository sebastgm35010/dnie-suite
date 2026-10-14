# Versión 3.1 · integración de seguridad backend v4

2026-10-13, America/Bogota. Integración autorizada de los 18 candidatos externos v4, ya revisados en copia aislada. Android pasa a `3.1.0`, versionCode 78; Suite JVM y ensamblados Windows a `3.1.0`. Se mantienen aplicación, certificado de actualización y almacenamiento de la app existente.

## Checkpoint previo

- Git: `checkpoint/pre-3.1-v4`, base `186951b028c837506941bad89e91f95cf5b252f2`.
- ZIP de fuentes rastreadas: `D:/DNIeToolchain/Checkpoints/pre-3.1-v4/source-186951b.zip`.
- SHA-256 del ZIP: `C6D431F386636605931AC9ACBB06040C7AD67BF7326B14C0033594097747C137`.
- Manifiesto de recuperación: `D:/DNIeToolchain/Checkpoints/pre-3.1-v4/checkpoint-manifest.json`.
- Manifest público del servicio anterior conservado en `installed-before.json` dentro de ese checkpoint. Sus binarios versionados permanecen en Windows.
- El archivo ajeno no rastreado `docs/project-registry/registry-1a4ty8tu.sqlite3` se conserva sin modificar.

## Cambios e invariantes

1. `PhoneSigningTransport` y `PinnedBridgeClient` mantienen el receptor disponible tras rechazar conexiones previas a autenticación. Sus plazos absolutos y por intento cierran candidatos lentos sin repetir operaciones privadas. En el puente, el plazo comienza después de aceptar el socket, no mientras se espera a la PC. Los temporizadores se cancelan al autenticar; las sesiones continuas autenticadas conservan su comportamiento.
2. `PcIdentity.VerifyLegacyProvenance`, usado por `ServiceInstaller`, distingue permisos del directorio y exposición de secretos. Rechaza lectura/escritura de terceros sobre JSON/PFX y contempla propietario, enlaces y archivo incompleto. Los tests reactivan y comprueban herencia antes del caso heredado.
3. `DocumentImportService` y `DesktopWorkspace` calculan SHA-256 durante la importación. `SigningDocument`, `CancellableSigningInput` y `BatchSigner` vinculan tamaño/resumen del original y comprueban integridad antes de solicitar la firma. `SignedArtifact` y `SignedBatchExporter` también comprueban el resultado firmado al exportar; esto cubre PDF y CMS y sustituciones del mismo tamaño.
4. `WindowsPrivateStore.runBoundedProcess` limita entrada, ejecución y salida de DPAPI mediante un presupuesto total, termina el hijo al fallar y limpia buffers transitorios.
5. Versionado común Gradle y metadatos .NET 3.1.0; títulos Suite/Bridge 3.1. No se modifican PACE/SM físico, el driver, el cifrado de bóveda, la política de Tailnet ni se reintroducen passkeys. Se mantienen debug y producción separados.

La validación externa inicial y sus sondas están en `G:/Mi unidad/BOT TRADER/DNI_CODEX/revision-externa-modificaciones-20261013/revision-local-v4-resultados-20261013/`. Los 18 candidatos fueron incorporados sin correcciones manuales; las modificaciones adicionales son nomenclatura/metadatos y documentación del despliegue.

## Pruebas de la integración

- Arquitectura: `tools/check_architecture.py`, sin infracciones.
- 180 casos JVM, 177 aprobados y 3 omitidos: app 80+1 omitido, puente Android 32+2 omitidos, núcleo de firma 10, enlace 24, escritorio 29 y UI Windows 2 aprobados.
- Windows: 134 comprobaciones sintéticas del puente aprobadas, gestor/servicio Release y adaptador Suite compilados.
- Android: debug, release y auxiliar de tests compilados. Firma del APK debug verificada y mismo certificado que APK77.
- Producción: manifest release sin debug/actividad/proveedor de diagnóstico; clases release sin implementación de diagnóstico; paquete nativo sin PDB; cuatro comandos de desarrollo del host Release rechazados con código 2.
- Instalación ADB `-r` satisfactoria y versión instalada comprobada: 78 / 3.1.0.
- Teléfono: tres pruebas instrumentadas de presentación Suite/Bridge aprobadas en 5,294 s, fixtures sin NFC real. Inicio de MainActivity satisfactorio, 1007 ms de tiempo total observado en ese arranque; no es una medición general de rendimiento.
- Windows: servicio actualizado desde la terminal administrativa tmux. Metadatos de archivos de identidad sin cambios, servicio Running, host Stopped y sin tarjeta. Suite/gestor nuevos abren y muestran 3.1. Los metadatos no equivalen a una comparación criptográfica de sus contenidos.

No se realizó una nueva firma física con DNI ni una prueba USB/NFC durante este despliegue. Esos escenarios siguen pendientes, diferenciados de las firmas reales acreditadas anteriormente.

## Artefactos y operación vigente

```text
D:/DNIeToolchain/
├── AndroidPackages/
│   ├── dnie-evolution-3.1.0-debug.apk
│   └── dnie-evolution-3.0.0-alpha05-mobility-debug.apk
├── Builds/version3.1/
│   ├── dnie-evolution-3.1.0-release.apk
│   ├── signing-desktop/install/dnie-signing-desktop/bin/dnie-signing-desktop.bat
│   ├── signing-ui/install/dnie-signing-ui/DNIe Suite.lnk
│   ├── bridge-integration/
│   └── logs/
├── DesktopPackages/Native-3.1.0/
└── Checkpoints/pre-3.1-v4/
```

El directorio `AndroidPackages` se limpió después de instalar y comprobar 3.1.0: se retiraron 26 APK antiguos, 775007510 bytes (739 MiB), conservando los dos indicados. Los resultados de compilación release y test están fuera de ese directorio de distribución.

Los accesos de escritorio **DNIe Suite** y **DNIe Bridge** apuntan a la versión nueva. El servicio instalado se consulta mediante `D:/DNIeBridgeApp/installed.json`; el directorio observado es `D:/DNIeBridgeApp/versions/aa63552c8833477abc031583c3ce3831`. No reutilizar PIDs históricos ni iniciar hosts BAT antiguos en paralelo.

Compilaciones y logs están en D:. AGP mantuvo sus rutas finales de APK bajo `app/build/outputs/apk` en G:; las copias de distribución se verificaron desde esos artefactos. El script de Gradle externo dirige el resto de resultados nuevos a `D:/DNIeToolchain/Builds/version3.1`.

Resultados reproducibles y huellas de artefactos: [ENTREGA_3_1_RESULTADOS.json](../operations/ENTREGA_3_1_RESULTADOS.json). El registro SQL incluye esta implementación y conserva los pendientes físicos; los binarios, logs crudos, claves y datos de DNI permanecen fuera de Git.
