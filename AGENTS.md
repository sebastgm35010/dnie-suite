# Fork de evolución

Esta carpeta es el fork autorizado para nuevas funcionalidades y refactorización.
La base 1.0.8 permanece clausurada en `G:\Mi unidad\BOT TRADER\DNI_CODEX\modest-bose`: no modificarla.
No modificar la copia de Antigravity.

- Al retomar, leer `docs/CONTEXTO_ACTUAL.md`, `docs/operations/RETOMAR_TRAS_REINICIO_RC4.md` y `docs/PENDIENTES_ACTUALES.md`; usar `docs/architecture/ARQUITECTURA_ACTUAL_2_0.md` como mapa de capas vigente. Los PIDs/sesiones de informes antiguos no son estado actual. Windows operativo usa el gestor nativo y el servicio, sin lanzar hosts BAT históricos en paralelo.
- Para trabajar en la suite propia, leer además `docs/suite/CONTEXTO_SUITE_FIRMA.md` y mantenerlo actualizado. Distinguir siempre simulación del ejecutable local y firma real con DNI; nunca introducir fallback automático a la tarjeta simulada.

- Mantener las invariantes de IAS → PKI → PACE → PKI protegido.
- Mantener token PACE, MAC, contador SSC y GET RESPONSE físico previo al desenvolvimiento.
- Separar núcleo JVM, protocolo, transporte Android, PDF e interfaz sin dependencias circulares.
- Mantener separadas las funciones del motor y la interfaz: Compose presenta estado y emite acciones; ViewModels coordinan casos de uso y cancelación; repositorios/adaptadores resuelven almacenamiento y APIs Android; motores implementan criptografía y formatos. Las nuevas funciones de bóveda y passkeys deben respetar esos límites y no usar ViewModels como servicios de otras pantallas.
- Por preferencia explícita del usuario, la bóveda no usa biometría. Mantener el DNI como requisito para descifrar; no incorporar bloqueo biométrico sin una nueva instrucción del usuario.
- La autorización explícita de biometría para passkeys no se extiende a la bóveda de archivos. Mantener la delegación opcional, por operación y mediante CryptoObject de Keystore; no permitir crear, respaldar o migrar sin DNI. Conservar la elegibilidad de respaldo de credenciales antiguas (BE=0); no cambiarla retroactivamente.
- Ejecutar las pruebas de todos los módulos afectados y compilar el APK después de una refactorización.
- La identidad de la app evolucionada debe permitir coexistir con DNIe Modest Bose 1.0.8.
- No incluir keystores, PIN/CAN, logs personales ni APK de terceros en Git.
- Leer y escribir fuentes y textos con UTF-8 explícito, especialmente en scripts Python y PowerShell de Windows. No reinterpretar UTF-8 como la página de códigos del sistema.
- ICAO usa una conexión nueva y su propia sesión; no reutilizar la sesión IAS/PKI ni cambiar su secuencia de firma.
- Los datos ICAO y las imágenes se procesan por bloques. Los errores de archivo no abortan otros bloques; una pérdida de transporte o integridad SM detiene la sesión y conserva solo bloques completos.
- No registrar MRZ, datos personales o imágenes ICAO en claro en la consola. No confundir hashes/firma de EF.SOD con confianza en el emisor o autenticación del chip.

- Registro SQL de arquitectura/estado: `docs/project-registry/project.sqlite3`. Al retomar, consultar `python tools/project-registry/registry.py status` y `check`; consultar `feature <id>`/`search <nombre>` antes de localizar motores. No sustituye los documentos vigentes. Tras cambiar arquitectura/estado, mantener `tools/project-registry/catalog.json` y documentos, preparar archivos nuevos en Git, ejecutar `refresh`/`check` y versionar base + exportación SQL. Procedimiento en `docs/project-registry/README.md`. No confundir imports léxicos con llamadas resueltas ni snapshot con servicios vivos.

- Bóveda/passkeys: no asumir conservación de privadas al renovar certificados en el mismo DNI. Respaldos actuales ligados a la misma pública no recuperan tras eliminar su privada; migración de dos claves disponibles no equivale a renovación destructiva. Mantener BE/BS y bóveda sin biometría. Usuario autoriza respaldo autosuficiente con contraseña para recuperar archivos; no extender a passkeys. Android68 exporta toda la bóveda a DNIEBKP2 e importa recifrando para DNI de destino comprobado. UUID de contenido estables evitan duplicados y journal persistente resuelve interrupciones. Flujo NFC/SAF físico pendiente. Ver `docs/architecture/BOVEDA_RESPALDO_UNIFICADO_R6.md`. El desbloqueo cotidiano mantiene CIF hard y PIN.

- Bóveda R7: elegir URI SAF antes de pedir PIN/contraseña y exigir botón explícito de autorización; no iniciar NFC automáticamente desde callback del selector. onPause limpia PIN por seguridad. APK69 compilada; instalación/pruebas instrumentadas pendientes por ADB, última instalada conocida APK68. Ver `docs/architecture/BOVEDA_SELECTOR_Y_AUTORIZACION_R7.md`.
