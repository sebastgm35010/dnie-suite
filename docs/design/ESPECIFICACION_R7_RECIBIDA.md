# Especificación Técnica R7 (Base `dnie-suite-main (5).zip` / APK 82): Personalización Avanzada del Sello, Vibración Háptica, Continuidad de Diseño y Navegación

## 1. Diagnóstico de la Línea Base Actual (`dnie-suite-main (5).zip` · Red Coordinada R6 / APK 82)

Todo este documento parte **exclusivamente del código actual en `dnie-suite-main (5).zip`** (`versión 3.1.0`, `APK 82`, posterior a `RED_COORDINADA_R6.md`).

### 1.1. Lo que ya quedó implementado y cerrado en `dnie-suite-main (5).zip` (No reabrir)
* **Descubrimiento y Red Coordinada (`dnie-network-core` + `tools/windows-bridge/NetworkDiscovery.cs`):** Descubrimiento UDP autenticado con HMAC-SHA256 (`puerto 39445`) sobre difusión LAN y pares de Tailscale (`AndroidNetworkPresence.kt`), permitiendo dejar vacío el campo de teléfono en `DNIe Suite` y `DNIe Bridge`.
* **Unificación de Transporte en `DNIe Bridge` (`MainForm.cs` y `BridgeScreen.kt`):** Reducción a dos modos visibles (`Red (Wi-Fi / Tailscale)` y `Cable USB`) con certificado TLS fijado por huella SHA-256 y token persistente.
* **Limpieza del QR en UI y Renderizado de Firmas Previas (`AndroidStampPreview.kt`):** Retiro de `QR_VERIFICATION` de los selectores de usuario (`SuiteWindow.kt` lín. 48 y `SignerScreen.kt` lín. 122), eliminación del flujo manual `Cotejar QR` y renderizado mediante PDFBox en Android cuando el PDF importado ya contiene firmas o anotaciones previas.

### 1.2. Qué revela la auditoría de `dnie-suite-main (5).zip` para esta nueva etapa (R7)

| Área auditada en `dnie-suite-main (5).zip` | Estado actual en el código | Oportunidad de mejora en R7 |
| :--- | :--- | :--- |
| **1. Personalización del Sello PDF** (`SignatureAppearance.kt`, `StampLayoutEngine.kt`, `SuiteWindow.kt`, `SignerScreen.kt`) | • Tras retirar el QR, el selector de insignia solo ofrece **2 opciones** (`Escudo DNIe` y `Sin insignia`).<br>• El fondo del sello en `INSTITUTIONAL` y `CARD` **siempre es opaco** (`c.backgroundRgb` o `0xFFFFFF`), tapando texto, firmas o sellos que estén debajo en el PDF.<br>• El formato de fecha es fijo (`dd/MM/yyyy HH:mm:ss UTC-05`).<br>• En Windows (`SuiteWindow.kt` lín. 47) **no existen** los estilos con rúbrica (`INK`, `HANDWRITTEN_ONLY`) ni se puede cargar una imagen PNG de rúbrica/sello, a diferencia de Android. | • Añadir **fondo transparente (`StampBackgroundMode`)**, **nuevas insignias vectoriales (`CHECK_SEAL`, `LOCK_SEAL`)**, **formatos de fecha/hora seleccionables** y **densidad tipográfica**.<br>• Añadir **Presets rápidos de 1 clic** y **botones de anclaje a las 4 esquinas**.<br>• Habilitar en `SuiteWindow.kt` (Windows) la carga de **imagen PNG de rúbrica/logo** para paridad total con Android. |
| **2. Vibración / Respuesta Háptica** (`ReadingFeedback.kt`, `SettingsScreen.kt`) | • `ReadingFeedback.kt` (lín. 12–28) solo tiene un método `start(context)` que emite un único pulso plano de `90 ms`.<br>• Solo se invoca al detectar el tag NFC en `MainActivity.kt` (lín. 180), `BridgeActivity.kt` (lín. 41) y `RemoteSigningActivity.kt` (lín. 36).<br>• Cuando el usuario tiene el DNI apoyado detrás del teléfono (sin ver bien la pantalla), **no recibe confirmación táctil cuando la firma/lectura termina con éxito ni cuando falla o se mueve el DNI**. | • Ampliar `ReadingFeedback.kt` con **patrones hápticos diferenciados**: *Lectura iniciada* (toque seco), *Avance de lote* (tick sutil), *Éxito / Retirar DNI* (doble pulso ascendente) y *Error / Tarjeta perdida* (alerta corta).<br>• Añadir control de activación en `SettingsScreen.kt` (`AppearancePreferences.kt`). |
| **3. Continuidad de Diseño (Android ↔ Windows)** (`SignerViewModel.kt`, `SuiteDesignPreferences.kt`, `AetherSwingTheme.kt`, `AetherControls.cs`) | • Windows (`SuiteDesignPreferences.kt`) guarda y restaura todo el diseño del sello, pero **Android (`SignerViewModel.kt`) pierde toda la configuración del sello** cada vez que se cierra la app o se cambia de PDF.<br>• Android soporta Tema Claro, Oscuro y Sistema (`SettingsScreen.kt`), mientras que Windows (`SuiteWindow` y `DNIe Bridge`) solo tiene tema claro fijo. | • Crear **`AndroidStampPreferences.kt`** en Android para persistir el estilo, color, insignia, fondo, campos, motivo, lugar, TSA y posición del sello igual que en Windows.<br>• Añadir soporte de **Modo Oscuro Aether (`Aether Dark`)** en `SuiteWindow.kt` y `MainForm.cs`. |
| **4. Navegación, Flujos e Interfaces** (`ActivityNavigation.kt`, `CertificatesScreen.kt`, `PreviewCanvas.kt`, `SignerScreen.kt`) | • En `ActivityNavigation.kt` (lín. 18), todos los ítems de la barra inferior tienen `selected = false` dentro de `BridgeActivity` y `RemoteSigningActivity`, perdiendo la referencia visual de que estás en `Conexión PC`.<br>• En `CertificatesScreen.kt` (lín. 63–65), los callbacks `onOpenVault`, `onOpenBridge` y `onOpenSuite` se reciben desde `NavGraph.kt` pero **no están conectados a ningún botón**.<br>• En `PreviewCanvas.kt` (Windows), no se pueden arrastrar y soltar archivos PDF (`Drag & Drop`) desde el Explorador de Windows. | • Marcar activo el ítem `Conexión PC` en `ActivityNavigation.kt`.<br>• Mostrar acciones rápidas post-lectura en `CertificatesScreen.kt` (*Guardar en Bóveda*, *Firmar PDF*, *Usar en PC*).<br>• Habilitar **Drag & Drop de PDFs** sobre `PreviewCanvas` y lista en Windows, y agrupar los controles del sello en secciones plegables/limpias. |

---

## 2. Eje 1: Personalización Avanzada del Sello PDF (`dnie-signing-core`, `dnie-signing-ui`, `app`)

### 2.1. Ampliación del Modelo Puro `SignatureAppearance.kt` (`dnie-signing-core`)
Sin introducir dependencias de plataforma y manteniendo intacta la regla de arquitectura (`tools/check_architecture.py`), ampliar `SignatureAppearance.kt` con valores por defecto 100% retrocompatibles con los tests actuales:

```kotlin
enum class StampBadgeType(val label: String) {
    SEAL_VECTOR("Escudo DNIe"),
    CHECK_SEAL("Sello de verificación"),
    LOCK_SEAL("Candado criptográfico"),
    QR_VERIFICATION("QR de verificación"), // Mantenido por compatibilidad interna de tests
    NONE("Sin insignia")
}

enum class StampBackgroundMode(val label: String) {
    TINTED("Fondo suave"),
    SOLID_WHITE("Fondo blanco"),
    TRANSPARENT("Transparente (sin fondo)")
}

enum class StampDateFormat(val label: String, val datePattern: String, val timePattern: String) {
    CLASSIC_PE("Estándar (14/10/2026)", "dd/MM/yyyy", "HH:mm:ss 'UTC-05'"),
    COMPACT_HUMAN(" Corto (14 oct. 2026)", "dd MMM yyyy", "HH:mm"),
    ISO_8601("ISO (2026-10-14)", "yyyy-MM-dd", "HH:mm:ssXXX")
}

enum class StampDensity(val label: String, val lineSpacing: Float) {
    COMPACT("Compacto", 1.22f),
    NORMAL("Equilibrado", 1.34f),
    SPACIOUS("Amplio", 1.46f)
}

enum class StampColorTheme(
    val label: String, val accentRgb: Int, val backgroundRgb: Int,
    val borderRgb: Int, val titleRgb: Int, val bodyRgb: Int
) {
    AETHER_BLUE("Azul Aether", 0x004AC6, 0xF8F9FF, 0xB8CCF0, 0x0B1C30, 0x434655),
    CRIMSON_SEAL("Rojo institucional", 0xB91C1C, 0xFFFBFB, 0xE5C4C4, 0x1E293B, 0x475569),
    EMERALD_TRUST("Esmeralda", 0x006A63, 0xF4FBF9, 0xB2DFDB, 0x00201D, 0x37474F),
    ROYAL_INDIGO("Índigo notarial", 0x3730A3, 0xF5F6FF, 0xC7D2FE, 0x1E1B4B, 0x312E81),
    AMBER_GOLD("Dorado ejecutivo", 0x92400E, 0xFFFBEB, 0xFDE68A, 0x451A03, 0x57534E),
    SLATE_MONO("Monocromo impresión", 0x1E293B, 0xFFFFFF, 0x64748B, 0x0F172A, 0x334155)
}
```

Y en `SignatureAppearance`:
```kotlin
data class SignatureAppearance(
    val style: StampStyle = StampStyle.INSTITUTIONAL,
    val showDni: Boolean = true,
    val showDate: Boolean = true,
    val showTime: Boolean = true,
    val showReason: Boolean = true,
    val showLocation: Boolean = true,
    val showHandwritten: Boolean = false,
    val handwrittenPng: ByteArray? = null,
    val colorTheme: StampColorTheme = StampColorTheme.AETHER_BLUE,
    val badge: StampBadgeType = StampBadgeType.SEAL_VECTOR,
    val customHeader: String = "FIRMADO DIGITALMENTE",
    val showTsaBadge: Boolean = true,
    val backgroundMode: StampBackgroundMode = StampBackgroundMode.TINTED,
    val dateFormat: StampDateFormat = StampDateFormat.CLASSIC_PE,
    val density: StampDensity = StampDensity.NORMAL,
    val showBorder: Boolean = true
)
```

### 2.2. Nuevas Insignias Vectoriales y Modo Transparente en `StampLayoutEngine.kt`
1. **Fondo transparente para no tapar el documento (`StampBackgroundMode.TRANSPARENT`):**
   - Cuando `a.backgroundMode == StampBackgroundMode.TRANSPARENT`, `fill` del contenedor principal pasa a `null` (sin caja opaca), dibujando únicamente la barra de acento lateral (`3.5f`) y el borde perimetral opcional (`if (a.showBorder) c.borderRgb else null`). Esto es clave cuando el usuario coloca su firma sobre una línea de firma existente en un contrato u oficio.
2. **Dos nuevas insignias vectoriales en `StampLayoutEngine.kt`:**
   - **`CHECK_SEAL` ("Sello de verificación"):** Medallón doble concéntrico con fondo suave (`c.backgroundRgb`), anillo exterior punteado/continuo en `c.accentRgb`, círculo interior relleno en `c.accentRgb` y un check blanco centrado de trazo limpio (sin el texto `"DNIe"`, ideal para uso profesional general).
   - **`LOCK_SEAL` ("Candado criptográfico"):** Medallón circular con cuerpo de candado relleno en `c.accentRgb`, arco superior en trazo grueso y ojo de cerradura en blanco (`0xFFFFFF`).
3. **Formato de fecha y densidad configurables:**
   - Usar `a.dateFormat.datePattern` y `a.dateFormat.timePattern` en lugar de las cadenas fijas de las líneas 97–98 de `StampLayoutEngine.kt`.
   - Usar `a.density.lineSpacing` en lugar del multiplicador fijo `1.34f` en las líneas 114 y 132.

### 2.3. Paridad de Rúbrica / Imagen PNG y Presets en `DNIe Suite` Windows (`SuiteWindow.kt`) y Android (`SignerScreen.kt`)
1. **Carga de imagen/rúbrica PNG en `SuiteWindow.kt` (Windows):**
   - Habilitar los estilos `StampStyle.INK` ("Rúbrica + datos") y `StampStyle.HANDWRITTEN_ONLY` ("Solo rúbrica") en el selector `style` de `SuiteWindow.kt` (línea 47).
   - Añadir un botón secundario `"Imagen de rúbrica…"` que abra un `JFileChooser` para elegir un PNG local (validando `<= 512 KiB` y dimensiones razonables) con una opción de **"Limpiar fondo blanco automáticamente"** (convirtiendo píxeles claros `L > 235` a transparentes en un `BufferedImage` ARGB antes de codificar el PNG para `handwrittenPng`).
2. **Presets Rápidos de 1 Clic (Plantillas de Sello):**
   - Añadir en la parte superior del panel de diseño (tanto en `SuiteWindow.kt` como en `SignerScreen.kt`) una fila de chips rápidos:
     - **`Institucional`**: `INSTITUTIONAL` + `AETHER_BLUE` + `SEAL_VECTOR` + `TINTED`.
     - **`Ejecutivo transparente`**: `MINIMAL` + `ROYAL_INDIGO` + `CHECK_SEAL` + `TRANSPARENT`.
     - **`Visto bueno compacto`**: `CARD` + `EMERALD_TRUST` + `CHECK_SEAL` + solo Nombre, DNI y Fecha corta (`COMPACT`).
     - **`Impresión B/N`**: `CARD` + `SLATE_MONO` + `NONE` + `SOLID_WHITE`.
3. **Botones de Anclaje Rápido en el Visor (`Esquinas de página`):**
   - En la barra superior del `PreviewCanvas` (`SuiteWindow.kt`) y en el editor a pantalla completa de Android (`PdfPlacementEditor.kt`), añadir 4 botones compactos de posición rápida que coloquen el sello respetando un margen limpio del `6%`:
     - `↖ Sup. Izq.` (`left = 0.06f, top = 0.06f`)
     - `↗ Sup. Der.` (`left = 1f - width - 0.06f, top = 0.06f`)
     - `↙ Inf. Izq.` (`left = 0.06f, top = 1f - height - 0.06f`)
     - `↘ Inf. Der.` (`left = 1f - width - 0.06f, top = 1f - height - 0.06f`)

---

## 3. Eje 2: Sistema de Vibración Háptica Semántica (`ReadingFeedback.kt` y `SettingsScreen.kt`)

### 3.1. Por qué es importante mejorar `ReadingFeedback.kt`
En el flujo real con NFC, el usuario coloca el DNIe en la parte trasera del teléfono y debe mantenerlo inmóvil varios segundos mientras se ejecuta PACE y la firma RSA/CMS. Hoy solo siente una vibración al principio (`ReadingFeedback.start`), pero **no tiene retroalimentación táctil cuando la operación termina con éxito** (para saber que ya puede retirar el DNI sin mirar la pantalla) **ni cuando ocurre un corte de lectura**.

### 3.2. Diseño de `ReadingFeedback.kt` con 4 Patrones Semánticos
Actualizar `app/src/main/java/pe/gob/reniec/dnie/data/feedback/ReadingFeedback.kt`:

```kotlin
package pe.gob.reniec.dnie.data.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object ReadingFeedback {
    @Volatile var enabled: Boolean = true

    /** 1. Al detectar físicamente el chip NFC (toque inicial firme y corto). */
    fun start(context: Context) = vibrate(
        context,
        timings = longArrayOf(0, 65),
        amplitudes = intArrayOf(0, 190)
    )

    /** 2. Avance intermedio (ej. PACE establecido o un documento firmado dentro de un lote). */
    fun progressTick(context: Context) = vibrate(
        context,
        timings = longArrayOf(0, 25),
        amplitudes = intArrayOf(0, 95)
    )

    /** 3. Operación completada con éxito: doble pulso ascendente ("Ya puedes retirar tu DNIe"). */
    fun success(context: Context) = vibrate(
        context,
        timings = longArrayOf(0, 45, 65, 110),
        amplitudes = intArrayOf(0, 140, 0, 255)
    )

    /** 4. Error o pérdida de contacto NFC: doble vibración seca de advertencia. */
    fun error(context: Context) = vibrate(
        context,
        timings = longArrayOf(0, 70, 55, 85),
        amplitudes = intArrayOf(0, 255, 0, 220)
    )

    private fun vibrate(context: Context, timings: LongArray, amplitudes: IntArray) {
        if (!enabled) return
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= 26) {
                val effect = if (vibrator.hasAmplitudeControl()) {
                    VibrationEffect.createWaveform(timings, amplitudes, -1)
                } else {
                    VibrationEffect.createWaveform(timings, -1)
                }
                if (Build.VERSION.SDK_INT >= 33) {
                    vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK))
                } else {
                    vibrator.vibrate(effect)
                }
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(timings.sum())
            }
        }
    }
}
```

### 3.3. Puntos Exactos de Integración Háptica en la App Android
1. **En `SignerViewModel.kt` (`onCardDetected`, líneas 182–245):**
   - Al completar cada documento de un lote de más de 1 archivo: llamar a `ReadingFeedback.progressTick(app)`.
   - Cuando el lote termina con todos los documentos firmados (`DocumentSignStatus.SIGNED`): llamar a `ReadingFeedback.success(app)`.
   - Cuando ocurre una excepción o queda algún documento en `FAILED`: llamar a `ReadingFeedback.error(app)`.
2. **En `MainActivity.kt` / `IdentityViewModel` / `CertificatesViewModel` / `VaultViewModel`:**
   - Al pasar al estado de éxito (`IdentityUiState.Success`, `CertificatesUiState.Loaded`, `VaultUiState.Unlocked`): disparar `ReadingFeedback.success(context)`.
   - Al caer en estado de error (`Error`): disparar `ReadingFeedback.error(context)`.
3. **En `RemoteSigningActivity.kt` y `BridgeActivity.kt`:**
   - Observar cambios en `state.cardState` (`SuiteCardState`):
     - `PREPARING -> READY`: `ReadingFeedback.progressTick(this)`
     - Finalización de un trabajo de firma en Suite o desconexión limpia tras uso: `ReadingFeedback.success(this)`
     - `FAILED`: `ReadingFeedback.error(this)`
4. **Control de Usuario en `SettingsScreen.kt` (`AppearancePreferences.kt`):**
   - Añadir en `AppearancePreferences` la propiedad `hapticsEnabled: StateFlow<Boolean>` (persistida en `SharedPreferences`, `true` por defecto y sincronizada con `ReadingFeedback.enabled`).
   - En `SettingsScreen.kt` (línea 32), añadir una sección **"Respuesta táctil (NFC)"** con un `Switch` para *"Vibrar al detectar el DNIe, confirmar firma o alertar errores"*.

---

## 4. Eje 3: Continuidad de Diseño y Navegación entre Android y Windows

### 4.1. Persistencia del Diseño del Sello en Android (`AndroidStampPreferences.kt`)
* **Problema de continuidad actual:** En Windows (`SuiteWindow.kt`), `SuiteDesignPreferences` recuerda todas tus preferencias del sello entre sesiones. En cambio, en Android (`SignerViewModel.kt`), cada PDF importado recibe un `PdfSigningOptions()` virgen y si cierras la app pierdes tu configuración de estilo, color, insignia, título, motivo, lugar y posición.
* **Solución:**
  1. Crear `app/src/main/java/pe/gob/reniec/dnie/data/preferences/AndroidStampPreferences.kt` que serialice las preferencias de diseño del sello en `SharedPreferences("dnie_stamp_prefs")` (reutilizando la misma estructura de claves limpias que `SuiteDesignPreferences.kt`: `style`, `color`, `badge`, `bgMode`, `dateFormat`, `density`, `header`, `reason`, `location`, `tsa`, `tsaBadge`, `dni`, `date`, `time`, `showReason`, `showLocation`, `target`, `left`, `top`, `width`, `height`).
  2. En `SignerViewModel.kt`:
     - Al importar documentos en `importDocuments` (línea 63), inicializar `doc.options` con el diseño guardado por el usuario en `AndroidStampPreferences` en lugar del `PdfSigningOptions()` estático por defecto.
     - Cada vez que el usuario modifique su sello en `updateOptions` (línea 108), guardar automáticamente las preferencias visuales por defecto para futuros documentos, e incluir en `SignerScreen.kt` un botón de texto discreto `"Restaurar diseño predeterminado"`.

### 4.2. Corrección de Estado Activo en la Barra de Navegación (`ActivityNavigation.kt`) y Acciones Post-Lectura (`CertificatesScreen.kt`)
1. **Resaltar la pestaña `"Conexión PC"` cuando estás en Puente o Suite (`ActivityNavigation.kt`):**
   - En `app/src/main/java/pe/gob/reniec/dnie/ui/navigation/ActivityNavigation.kt` (líneas 10–25), actualmente `selected = false` está fijo para los 4 botones, por lo que al entrar a `BridgeScreen` o `RemoteSigningScreen` la barra de navegación inferior pierde la selección azul de `"Conexión PC"`.
   - Cambiar la firma a `@Composable fun ActivityNavigation(active: Screen = Screen.Pc)` y usar `selected = screen == active` en el `NavigationBarItem`. Así la transición entre `PcConnectionScreen` (`MainActivity`) y `BridgeActivity` / `RemoteSigningActivity` se percibe como una única navegación continua sin saltos visuales.
2. **Conectar los botones de acción rápida en `Mi DNIe` (`CertificatesScreen.kt`):**
   - En `CertificatesScreen.kt` (líneas 63–65), los parámetros `onOpenVault`, `onOpenBridge` y `onOpenSuite` ya llegan desde `NavGraph.kt` (líneas 151–153), pero nunca se renderizan en la pantalla.
   - Dentro del bloque `is CertificatesUiState.Loaded` (después de `CertificateCard`, línea 181), añadir una sección `"Usar este DNIe"` con chips/botones de navegación directa hacia **Bóveda**, **Puente PC** y **Firma remota Suite**, además de inyectar automáticamente el certificado público leído en `signerViewModel.setPublicCertificate(...)` (que ya se hace en `MainActivity`, mostrándoselo al usuario con un chip `"Nombre listo para vista previa de firma PDF"`).

### 4.3. Mejoras de Interfaz y Flujo de Trabajo en `SignerScreen.kt` (Android) y `SuiteWindow.kt` (Windows)
1. **Reorganización visual del configurador de sello en `SignerScreen.kt` y `SuiteWindow.kt`:**
   - A medida que crecen las opciones del sello, evitar una lista vertical interminable dividiendo el panel de configuración en **3 bloques claros y escaneables**:
     - **Bloque A · Estilo y Plantilla:** Presets rápidos de 1 clic + Estilo (`Institucional`, `Tarjeta`, `Minimalista`, `Rúbrica + datos`, `Solo rúbrica`) + Tema de color + Modo de fondo (`Suave`, `Blanco`, `Transparente`).
     - **Bloque B · Insignia y Contenido:** Selector de Insignia (`Escudo DNIe`, `Sello de verificación`, `Candado`, `Sin insignia`) + Chips de datos visibles (`DNI`, `Fecha`, `Hora`, `Motivo`, `Lugar`, `Distintivo TSA`) + Formato de fecha.
     - **Bloque C · Ubicación en Página:** Selector `Aplicar en` (`Página seleccionada`, `Primera`, `Última`, `Todas`) + 4 botones de anclaje rápido a esquinas (`↖ ↗ ↙ ↘`) + Vista previa interactiva.
2. **Soporte `Drag & Drop` de archivos PDF en `DNIe Suite` Windows (`SuiteWindow.kt` y `PreviewCanvas.kt`):**
   - Añadir un `TransferHandler` en `PreviewCanvas` y en `fileList` que acepte `DataFlavor.javaFileListFlavor`. Cuando el usuario arrastre uno o varios archivos `.pdf` desde el Explorador de Windows sobre el lienzo central o la lista izquierda, invocar directamente `session.importFiles(droppedPdfFiles)` y resaltar el borde del lienzo en azul Aether (`#004AC6`) durante el arrastre (`dragEnter` / `dragExit`).
3. **Tema Claro / Oscuro en Windows (`AetherSwingTheme.kt` y `AetherControls.cs`):**
   - Añadir un botón compacto de tema (`☾ Oscuro` / `☀ Claro`) en la esquina superior derecha de `SuiteWindow.kt` y `MainForm.Layout.cs` (guardado en `Preferences` / `ManagerPreferences`), aplicando la misma paleta oscura de Android (`Theme.kt`: fondo `#0B1326`, superficie `#131B2E`, contenedor `#1E293B`, borde `#2E3C56`, texto `#E2E8F0`, acento `#4C8EFF`).

---

## 5. Secuencia de Implementación Recomendada para el Agente Local (Hoja de Ruta R7)

| Paso | Módulos / Archivos objetivo | Qué implementar y cómo verificarlo |
| :--- | :--- | :--- |
| **Paso 1 · Modelo y Motor de Sello Personalizable** | `dnie-signing-core/.../SignatureAppearance.kt`, `StampLayoutEngine.kt`, `StampLayoutEngineTest.kt` | • Añadir `StampBackgroundMode` (`TINTED`, `SOLID_WHITE`, `TRANSPARENT`), `StampDateFormat`, `StampDensity`, `ROYAL_INDIGO`, `AMBER_GOLD` y las insignias `CHECK_SEAL` y `LOCK_SEAL`.<br>• Soportar `fill = null` en el fondo del sello cuando sea `TRANSPARENT`.<br>• Añadir tests unitarios en `StampLayoutEngineTest.kt` verificando que el modo transparente no emita `Box` de fondo opaco y que las nuevas insignias y formatos de fecha se generen sin desbordes. |
| **Paso 2 · Vibración Háptica Semántica y Ajustes** | `app/.../data/feedback/ReadingFeedback.kt`, `AppearancePreferences.kt`, `SettingsScreen.kt`, `SignerViewModel.kt`, `MainActivity.kt`, `BridgeActivity.kt`, `RemoteSigningActivity.kt` | • Ampliar `ReadingFeedback` con `start()`, `progressTick()`, `success()` y `error()`.<br>• Añadir el toggle `"Vibración al leer y firmar con NFC"` en `SettingsScreen.kt` y `AppearancePreferences.kt`.<br>• Disparar `success()` al culminar lecturas/firmas y `error()` ante pérdida de tarjeta o fallo de PIN/CAN. |
| **Paso 3 · Continuidad de Preferencias y Navegación en Android** | `app/.../data/preferences/AndroidStampPreferences.kt`, `SignerViewModel.kt`, `SignerScreen.kt`, `ActivityNavigation.kt`, `CertificatesScreen.kt` | • Persistir y restaurar el diseño del sello en Android con `AndroidStampPreferences`.<br>• Añadir los Presets rápidos, selector de fondo (`Suave`/`Blanco`/`Transparente`), nuevas insignias y botones de esquinas en `SignerScreen.kt` / `PdfPlacementEditor.kt`.<br>• Resaltar el ítem activo (`Screen.Pc`) en `ActivityNavigation.kt` y mostrar los botones de acción rápida en `CertificatesScreen.kt`. |
| **Paso 4 · Paridad de Rúbrica PNG, Drag & Drop y Modo Oscuro en Windows** | `dnie-signing-ui/.../SuiteWindow.kt`, `SuiteDesignPreferences.kt`, `PreviewCanvas.kt`, `AetherSwingTheme.kt`, `tools/windows-manager/AetherControls.cs`, `MainForm.Layout.cs` | • Permitir cargar imagen PNG de rúbrica/logo en `SuiteWindow.kt` y habilitar los estilos `INK` y `HANDWRITTEN_ONLY` en escritorio.<br>• Añadir `TransferHandler` (Drag & Drop de PDFs) sobre `PreviewCanvas` y `fileList`, botones de anclaje a las 4 esquinas y presets rápidos.<br>• Persistir las nuevas propiedades (`bgMode`, `dateFormat`, `density`) en `SuiteDesignPreferences.kt` y añadir conmutador de Modo Claro/Oscuro. |

---

## 6. Pendientes Físicos Vigentes sobre `dnie-suite-main (5).zip` (APK 82+)

| Prioridad | Área | Prueba / Validación pendiente | Criterio de cierre |
| :--- | :--- | :--- | :--- |
| **P1** | **Validación física con DNIe real** | Probar con DNIe físico y certificado real la firma PAdES-T (con sello RENIEC, sello transparente/insignias y réplicas por página) desde Android y desde `DNIe Suite` Windows usando el autodescubrimiento R6. | PDF firmado validado como `PAdES_BASELINE_T` en ReFirma / Firma Perú y respuesta háptica (`start` → `progressTick` → `success`) confirmada en el teléfono. |
| **P1** | **Regresión física del Puente Coordinado (`Wi-Fi` ↔ `Tailscale` ↔ `USB`)** | Iniciar `DNIe Bridge` con el campo de teléfono vacío en Wi-Fi y luego en Tailscale (verificando el autodescubrimiento UDP `:39445` y reconexión TLS), más una sesión por cable USB AOA. | Lectura de certificados y firma en Windows sin re-emparejar al cambiar entre Wi-Fi y Tailscale. |
| **P2** | **Trazas de suspensión/reanudación de Windows (`SCardSvr`)** | Solo si se reproduce en uso diario: capturar el estado de `SCardListReaders` antes y después de suspender Windows con el driver `2.0.0.4`. | Comparar trazas reales antes de tocar `DniBridgeReader.cpp`. |
