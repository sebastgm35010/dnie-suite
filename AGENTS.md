# Fork de evolución

- Para decisiones visuales, paleta Claro/Oscuro, tipografía y animaciones, seguir `DESIGN.md`.

Repositorio privado unificado: `sebastgm35010/dnie-suite`, rama principal `main`. Leer `README.md` y `docs/operations/GITHUB_Y_COMPILACION_MONOREPO.md` para compilar un clon. Mantener motores, plataformas e interfaces separados dentro del monorepo; claves privadas, datos de ejecución y compilaciones quedan fuera de Git.

Esta carpeta es el fork autorizado para nuevas funcionalidades y refactorización.
La base 1.0.8 permanece clausurada en `G:\Mi unidad\BOT TRADER\DNI_CODEX\modest-bose`: no modificarla.
No modificar la copia de Antigravity.

- Al retomar, leer `docs/CONTEXTO_ACTUAL.md`, `docs/operations/RETOMAR_TRAS_REINICIO_RC4.md` y `docs/PENDIENTES_ACTUALES.md`; usar `docs/architecture/ARQUITECTURA_ACTUAL_2_0.md` como mapa de capas vigente. Los PIDs/sesiones de informes antiguos no son estado actual. Windows operativo usa el gestor nativo y el servicio, sin lanzar hosts BAT históricos en paralelo.
- Para trabajar en la suite propia, leer además `docs/suite/CONTEXTO_SUITE_FIRMA.md` y mantenerlo actualizado. Distinguir siempre simulación del ejecutable local y firma real con DNI; nunca introducir fallback automático a la tarjeta simulada.

- Mantener las invariantes de IAS → PKI → PACE → PKI protegido.
- Mantener token PACE, MAC, contador SSC y GET RESPONSE físico previo al desenvolvimiento.
- Separar núcleo JVM, protocolo, transporte Android, PDF e interfaz sin dependencias circulares.
- Mantener separadas las funciones del motor y la interfaz: Compose presenta estado y emite acciones; ViewModels coordinan casos de uso y cancelación; repositorios/adaptadores resuelven almacenamiento y APIs Android; motores implementan criptografía y formatos. Las nuevas funciones de bóveda deben respetar esos límites y no usar ViewModels como servicios de otras pantallas.
- Por preferencia explícita del usuario, la bóveda no usa biometría. Mantener el DNI como requisito para descifrar; no incorporar bloqueo biométrico sin una nueva instrucción del usuario.
- Ejecutar las pruebas de todos los módulos afectados y compilar el APK después de una refactorización.
- La identidad de la app evolucionada debe permitir coexistir con DNIe Modest Bose 1.0.8.
- No incluir keystores, PIN/CAN, logs personales ni APK de terceros en Git.
- Leer y escribir fuentes y textos con UTF-8 explícito, especialmente en scripts Python y PowerShell de Windows. No reinterpretar UTF-8 como la página de códigos del sistema.
- ICAO usa una conexión nueva y su propia sesión; no reutilizar la sesión IAS/PKI ni cambiar su secuencia de firma.
- Los datos ICAO y las imágenes se procesan por bloques. Los errores de archivo no abortan otros bloques; una pérdida de transporte o integridad SM detiene la sesión y conserva solo bloques completos.
- No registrar MRZ, datos personales o imágenes ICAO en claro en la consola. No confundir hashes/firma de EF.SOD con confianza en el emisor o autenticación del chip.

- Registro SQL de arquitectura/estado: `docs/project-registry/project.sqlite3`. Al retomar, consultar `python tools/project-registry/registry.py status` y `check`; consultar `feature <id>`/`search <nombre>` antes de localizar motores. No sustituye los documentos vigentes. Tras cambiar arquitectura/estado, mantener `tools/project-registry/catalog.json` y documentos, preparar archivos nuevos en Git, ejecutar `refresh`/`check` y versionar base + exportación SQL. Procedimiento en `docs/project-registry/README.md`. No confundir imports léxicos con llamadas resueltas ni snapshot con servicios vivos.

- Bóveda: no asumir conservación de privadas al renovar certificados en el mismo DNI. Respaldos actuales ligados a la misma pública no recuperan tras eliminar su privada; migración de dos claves disponibles no equivale a renovación destructiva. Mantener bóveda sin biometría. Usuario autoriza respaldo autosuficiente con contraseña para recuperar archivos. Android68 exporta toda la bóveda a DNIEBKP2 e importa recifrando para DNI de destino comprobado. UUID de contenido estables evitan duplicados y journal persistente resuelve interrupciones. Flujo NFC/SAF físico pendiente. Ver `docs/architecture/BOVEDA_RESPALDO_UNIFICADO_R6.md`. El desbloqueo cotidiano mantiene CIF hard y PIN.

- Bóveda R7: elegir URI SAF antes de pedir PIN/contraseña y exigir botón explícito de autorización; no iniciar NFC automáticamente desde callback del selector. onPause limpia PIN por seguridad. APK69 instalada por ADB; 3 pruebas instrumentadas de interfaz pasan. Selector simulado; SAF real con DNI físico pendiente. Ver `docs/architecture/BOVEDA_SELECTOR_Y_AUTORIZACION_R7.md`.



- Retiro R3 autorizado por el usuario: no reintroducir passkeys ni sus módulos/dependencias. Mantener la migración de borrado de datos internos y aliases exactos, sin borrar bóveda/CAN/emparejamientos. Ver `docs/architecture/RETIRO_PASSKEYS_R3.md`; checkpoint previo `checkpoint/pre-remove-passkeys-apk70`.

- Versionado desde 3.1.1: fuente única `gradle.properties` (`dnie.versionName`, `dnie.androidVersionCode`). Cada entrega nueva incrementa patch y código mediante `python tools/bump-version.py --next-patch`; no incrementar por cada compilación/reintento. Conservar keystore de actualización y verificar app debug + auxiliar androidTest con `tools/android/verify-debug-pair.ps1` antes de instalar. Nunca cambiar firma para sortear un bloqueo del instalador.

- R11 Experiencia Flagship y Fluid UI (3.1.4 / APK 88): selectores compactos, indicadores de longitud de cápsula PIN/CAN y campos `AetherTextField` preservan accesibilidad y semántica nativa; ningún indicador visual expone secretos ni sustituye la confirmación del DNIe. El ajuste magnético (`snap`) en el editor PDF opera sobre geometría relativa normalizada. El emparejamiento QR no autoriza PCs automáticamente al copiar la huella. Toda animación (resortes `aetherSpring`, micro-escala táctil `aetherPressScale`, transiciones `AnimatedContent`/`AnimatedVisibility`/`animateContentSize`/`animateItem`, curvas asimétricas y feedback de presión en Swing y WinForms) se ejecuta en la capa de presentación/dibujo y se desactiva con movimiento reducido.

## Coordinación activa de Subagentes Flash (Orquestador -> Ejecutores)
- **[Agente-Android-FluidUI]**: COMPLETADO — Pulido Organic Motion / Fluid UI en `app/src/main/java/pe/gob/reniec/dnie/ui/**` (AetherMotion, AetherCard, AetherSegmentedTabs, ConnectionSessionScreen, CredentialsDialog, CertificateCard, HomeScreen, CertificatesScreen, IdentityScreen, SignerControls, SignerResultActions, StampPreviewCard, PdfPlacementEditor, VaultScreen, VaultComponents, VaultRecoveryControls); `:app:compileDebugKotlin`, `:app:compileDebugAndroidTestKotlin` y `:app:testDebugUnitTest` exitosos sin regresiones.
- **[Agente-Windows-FluidUI]**: COMPLETADO — Pulido Organic Motion / Fluid UI en `dnie-signing-ui` (Swing) y `tools/windows-manager` (WinForms): AetherSwingAnimator, micro-escala táctil 0.985x, halos de foco suaves, rotación continua 0°-90° de chevrons, cápsulas PIN animadas, scrollbar oscuro adaptativo, manijas y sombra difusa PDF, temporizador de copia y tarjetas de salud con interpolación suave; pruebas unitarias y de layout aprobadas (25/25 Swing, 52/52 WinForms).
- **[Agente-Registro-Verificador]**: COMPLETADO — Sincronización de documentación R11 (EXPERIENCIA_FLAGSHIP_R11.md, CONTEXTO_ACTUAL.md, PENDIENTES_ACTUALES.md, README.md), catálogo y SQLite project-registry; verificación de arquitectura (check_architecture.py OK), 217 tareas Gradle exitosas (254 tests JVM, :app:testDebugUnitTest y :dnie-signing-ui:test 25/25), APKs debug y androidTest empaquetados y certificados verificados idénticos (SHA-256 16cda33b...), pruebas de layout Windows Manager (52/52), registro actualizado con flagship_r11, refresh y check con 0 cambios e integridad OK.

