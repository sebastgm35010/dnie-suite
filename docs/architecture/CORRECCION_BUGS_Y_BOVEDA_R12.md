# Especificación e Informe de Entrega R12: Corrección Integral de Bugs y Estabilización de Bóveda

- **Versión de Entrega:** `v3.1.5` / `APK 89` (Android evolucionada)
- **Fecha de Validación:** 18 de octubre de 2026
- **Estado:** Implementado y Verificado al 100%

---

## 1. Resumen Ejecutivo de la Entrega

La iteración R12 consolida la estabilidad crítica, concurrencia, gestión de recursos SAF y la experiencia de usuario de la Bóveda en DNIe Suite Evolution (`3.1.4` → `3.1.5`, APK 88 → APK 89). Se resolvieron exhaustivamente los 17 defectos catalogados en la especificación técnica en tres bloques funcionales:

1. **Bloque A (Concurrencia, SAF y Conectividad - 4 Bugs):** Eliminación de asunciones inseguras de nulidad (`!!`) en ContentResolver, sincronización atómica y descarte garantizado de credenciales en `ContinuousSigningConnection`, cancelación segura y liberación de descriptores de socket en `BridgeTransportAdapter`, y aislamiento de fallos de aprovisionamiento en `BridgeAndroidAdapter`.
2. **Bloque B (Bóveda UI, Estados y Navegación - 8 Bugs):** Exportación individual de copias cifradas (`.dnievault`) desde el menú de cada archivo, lectura y retención no destructiva del CAN de sesión, alternancia limpia entre Exportación e Importación de respaldos sin bifurcación de diálogos, alineación visual del header de herramientas (`VaultHero`), visualización elástica de etiquetas en `FlowRow`, scroll vertical y tipografía monoespaciada en el diálogo de detalles, soporte de navegación atrás (`BackHandler`) hacia la vista de archivos sin abandonar la bóveda, y zoom táctil 2.5x con recentrado dinámico en el visor de imágenes.
3. **Bloque C (Ciclo de Vida, Almacenamiento y Concurrencia - 5 Bugs):** Prevención de fugas de memoria con reciclado de bitmaps en `VaultPreviewActivity`, borrado atómico garantizado de archivos temporales en `VaultFileStore.restore()`, prevención de excepciones `NoSuchElementException` en operaciones de lote sobre IDs inexistentes, resguardo del CAN de sesión durante aperturas por clave local, y formateo de fechas con Locale dinámico en `CertificateCard`.

---

## 2. Detalle de Correcciones Implementadas

### Bloque A: Concurrencia, SAF y Conectividad
- **BUG-01 (`RemoteSigningAndroidAdapter.kt`):** Se reemplazó `context.contentResolver.openInputStream(uri)!!` por una verificación explícita que lanza `IOException("No se pudo abrir el archivo de solicitud")` si el resolver retorna `null`. Se añadió prueba unitaria `RemoteSigningAndroidAdapterTest`.
- **BUG-02 (`ContinuousSigningConnection.kt`):** Sincronización atómica de `endpoint` con `stateLock`, captura local de `activeEndpoint` para prevenir condiciones de carrera con cierres concurrentes, y garantía de limpieza de memoria (`credentials.fill('\u0000')`) en bloques `finally` y `close()`. Se añadió prueba de concurrencia `concurrentCloseDuringCardAcquireAndProcessNeverThrowsNpe`.
- **BUG-03 (`BridgeTransportAdapter.kt`):** Gestión de estado atómico con `@Volatile closed`, sincronización de ciclo de vida de listeners y sockets de trabajadores, y cierre determinista de ParcelFileDescriptors y canales TCP al cancelar la sesión.
- **BUG-04 (`BridgeAndroidAdapter.kt` & `BridgeViewModel.kt`):** Introducción de la excepción de dominio `BridgeProvisionFileException` ante la inaccesibilidad de URIs de emparejamiento, diferenciando errores de lectura de fallos de formato criptográfico. Se añadió `BridgeAndroidAdapterTest`.

### Bloque B: Bóveda UI, Estados y Navegación
- **BUG-05 (`VaultComponents.kt`):** Incorporación de la opción "Exportar copia cifrada (.dnievault)" en el `DropdownMenu` de cada tarjeta de archivo (`VaultFileCard`), permitiendo exportar archivos cifrados individualmente.
- **BUG-06 (`VaultAccessPanel.kt`):** El campo `ConnectionCanField` ahora lee el CAN de sesión mediante `readSavedCan = { state.can }`, manteniendo el checkbox alineado verticalmente.
- **BUG-07 & BUG-08 (`VaultRecoveryControls.kt` & `VaultScreen.kt`):** Integración de `FilterChip` de alternancia en `VaultRecoveryControls` para conmutar entre "Exportar" e "Importar" sin duplicar formularios. Se retiró el botón redundante de retroceso.
- **BUG-09 (`VaultComponents.kt`):** Las etiquetas de archivo se organizan en `FlowRow` elástico, se eliminó el chip redundante de carpeta y se limitó el nombre a 1 línea con elipsis para evitar desbordes en pantallas compactas.
- **BUG-10 (`VaultLibraryControls.kt`):** `VaultDetailsDialog` incorpora `verticalScroll(rememberScrollState())` y renderiza el resumen SHA-256 en fuente `FontFamily.Monospace`.
- **BUG-11 (`VaultScreen.kt`):** Se incluye `VaultHero` en `VaultPage.TOOLS` para mantener coherencia visual con las otras pestañas de la bóveda.
- **BUG-12 (`VaultPreviewImage.kt`):** El visor de imágenes delimita el paneo/zoom al marco visible del bitmap (`ContentScale.Fit`) y agrega `detectTapGestures(onDoubleTap = ...)` para conmutar entre zoom 2.5x centrado y escala 1.0x normal.
- **BUG-16 (`VaultScreen.kt`):** Se configuró `BackHandler(enabled = page != VaultPage.FILES)` para que al presionar Atrás desde Herramientas o Respaldo el usuario retorne a la lista de archivos sin cerrar la sesión de la bóveda.

### Bloque C: Ciclo de Vida, Almacenamiento y Concurrencia
- **BUG-13 (`VaultPreviewActivity.kt`):** Reciclado explícito de `Bitmap` en `LaunchedEffect` y `DisposableEffect`, previniendo saturación de memoria nativa.
- **BUG-14 (`VaultFileStore.kt`):** En `VaultFileStore.restore()`, se garantiza la eliminación del archivo temporal `.part` en un bloque `finally` con verificación `check(temporary.delete() || !temporary.exists())`.
- **BUG-15 (`VaultViewModel.kt` & `AndroidVaultRepository.kt`):** Reemplazo de `.first { ... }` por `.firstOrNull()` en consultas por ID (`organize`, `organizeMany`, `requestPreview`, `details`), emitiendo mensajes de error controlados en lugar de excepciones no capturadas.
- **BUG-11b (`VaultViewModel.kt`):** En `launchDisk`, se preserva el `state.can` preexistente para evitar limpiar el CAN capturado en sesiones DNI.
- **BUG-17 (`CertificateCard.kt`):** Observación dinámica de `LocalConfiguration.current.locales[0]` para actualizar el formato de fecha de certificados ante cambios en la configuración regional del sistema.

---

## 3. Verificación de Arquitectura y Pruebas

1. **Límites de Arquitectura:**
   - Ejecutado `python tools/check_architecture.py`: **Architecture boundaries OK (imports and project dependencies)**.
2. **Suite de Pruebas Unitarias:**
   - Ejecutado `.\gradlew.bat test testDebugUnitTest`: **217 tareas ejecutadas/al día, 100% aprobadas en todos los módulos**.
3. **Suite de Pruebas de Escritorio:**
   - Ejecutado `.\gradlew.bat :dnie-signing-desktop:test`: **32/32 pruebas aprobadas**.
4. **Compilación y Verificación de Paquetes Android:**
   - Ejecutado `.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest`: **APKs compiladas exitosamente**.
   - Ejecutado `.\tools\android\verify-debug-pair.ps1`:
     - Paquete: `pe.gob.reniec.dnie.evolution.debug`
     - Versión: `3.1.5` (Código `89`)
     - Certificado SHA-256: `16cda33b24d0e5f3fea46b766f9e9f198a581721464938bc8068fda398036350`
     - Compatibilidad de firma entre App y AndroidTest: **Verified = true, SameSigner = true**.
