# Especificación Técnica de Rediseño Visual Aether: Cuadro de Firma PDF, DNIe Suite Windows y Android

## 1. Objetivo y Límites de Arquitectura (Backend Intacto)

Esta especificación define el rediseño gráfico y de experiencia de usuario para eliminar la apariencia rígida y monótona actual del **cuadro/sello de firma PDF** (tanto en Android como en Windows) y de las **interfaces de escritorio Windows** (`dnie-signing-ui` y `tools/windows-manager`), alineándolas con el lenguaje visual **Aether** ya estrenado en Android (`app/src/main/java/pe/gob/reniec/dnie/ui/theme/Theme.kt`).

### Invariantes de seguridad y arquitectura (`tools/check_architecture.py`)
1. **Cero cambios en motores criptográficos, transporte o controlador:** No se modifican `dnie-core`, `dnie-protocol`, `dnie-signing-link`, `DesktopSuiteSession`, `WindowsBridgeGateway`, `tools/windows-bridge` ni `tools/windows-pcsc-driver` (`2.0.0.4`).
2. **Separación estricta de capas:**
   - `dnie-signing-core` (JVM puro): aloja únicamente los modelos de configuración (`SignatureAppearance`, `StampStyle`, `StampColorTheme`, `StampBadgeType`, `StampPageTarget`) y el motor puro de partición de líneas y geometría (`StampLayoutEngine`), sin importar clases de AWT, Swing, Android ni PDFBox.
   - `dnie-pdf` (Android) y `dnie-pdf-desktop` (Windows JVM): consumen `StampLayoutEngine` para dibujar el flujo vectorial PDF (`PDAppearanceStream`) y para generar la imagen de vista previa en vivo.
   - `dnie-signing-ui` (Swing) y `app` (Jetpack Compose): contienen exclusivamente vistas, temas y eventos de interacción fuera del hilo de renderizado.

---

## 2. Paleta y Tokens Visuales Unificados (Aether Design System)

Para que Windows (`dnie-signing-ui` y `windows-manager`), Android (`app`) y el propio **sello visual dentro del PDF** hablen el mismo idioma visual, se estandarizan los siguientes tokens extraídos de `Theme.kt`:

| Token Semántico | Hexadecimal | Uso en Interfaces (Windows / Android) | Uso en el Cuadro de Firma PDF (Tema Azul Aether) |
| :--- | :--- | :--- | :--- |
| `Primary` | `#004AC6` | Botón primario (`Firmar lote`, `Conectar`), bordes activos, manejadores del sello | Barra lateral de acento (`3.5 pt`), título superior `FIRMADO DIGITALMENTE` e icono vectorial |
| `PrimaryContainer` | `#E5EEFF` | Botones secundarios, chips seleccionados (`FilterChip`), fondo de relleno del sello al arrastrar | Fondo de la píldora de verificación `PAdES-T · TSA RENIEC` |
| `OnPrimaryContainer` | `#00174B` | Texto sobre botones secundarios y chips activos | Texto dentro de la píldora `PAdES-T` |
| `Secondary` (Éxito/Activo) | `#006A63` | Indicador `Conectado`, `Lector Publicado`, check de documento firmado | Icono de verificación criptográfica o tema Esmeralda |
| `SecondaryContainer` | `#D0F7F0` | Fondo de píldora de estado conectado (`Status Pill`) | Fondo suave del check de validez |
| `Background` | `#F8F9FF` | Fondo general de la ventana (`SuiteWindow` y `MainForm`) | Color de fondo suave de la tarjeta del sello (`INSTITUTIONAL`) |
| `Surface` | `#FFFFFF` | Fondo de tarjetas de paneles, lista de documentos y diálogos | Fondo del sello en estilos `MODERN_CARD` y `SLATE_MONO` |
| `SurfaceContainer` | `#EFF4FF` | Fondo del lienzo detrás de la página PDF (`PreviewCanvas`), cabeceras de sección | Fondo de la columna izquierda del emblema en el sello |
| `SurfaceContainerHighest` | `#DCE9FF` | Bordes de tarjetas inactivas, separadores sutiles | Línea divisoria vertical (`0.6 pt`) entre emblema y datos del firmante |
| `OnSurface` | `#0B1C30` | Títulos principales, nombre de archivo, **Nombre del firmante** | **Nombre completo del titular** en negrita dentro del sello |
| `OnSurfaceVariant` | `#434655` | Subtítulos, etiquetas de formulario, metadatos secundarios | Filas de `DNI`, `Motivo`, `Lugar` y `Fecha/Hora` en el sello |
| `Outline` | `#737686` | Bordes de inputs de texto y controles deshabilitados | Borde perimetral exterior del sello (`0.6 pt`) |
| `Error` | `#BA1A1A` | Alertas, desconexión o error de validación | Reservado para alertas de UI (nunca en el sello firmado) |

---

## 3. Rediseño del Cuadro / Sello de Firma PDF (Android y Windows Suite)

### 3.1. Por qué el sello actual se ve poco estético (Diagnóstico de código)
1. **En Windows (`DesktopStampRenderer.kt`):**
   - Dibuja un rectángulo blanco con borde negro puro (`Color.BLACK`) y texto negro plano sin ningún color ni insignia.
   - Carga únicamente `LiberationSans-Regular.ttf` (línea 62): el encabezado `"FIRMADO DIGITALMENTE"`, el nombre del ciudadano y los metadatos tienen **exactamente el mismo grosor**, perdiendo toda jerarquía visual.
   - **Corta las palabras a la mitad:** el bucle de ajuste de línea (líneas 67–72) itera carácter por carácter (`for (cp in row.codePoints())`), partiendo palabras a mitad de sílaba cuando llegan al margen derecho.
2. **En Android (`ModernStampRenderer.kt` y `VisualStampRenderer.kt`):**
   - `ModernStampRenderer.kt` usa `PDType1Font.COURIER` y `COURIER_BOLD` (fuente monoespaciada tipo consola/máquina de escribir) sobre caja blanca y negra.
   - En `VisualStampRenderer.kt` (líneas 226–346) existe un método `renderStampContent` con colores institucionales y emblema, pero está dentro de un bloque `else` que solo se ejecuta cuando `appearance == null` (línea 168), por lo que nunca se activa cuando el usuario configura su firma.

### 3.2. Nuevo Contrato Personalizable en `dnie-signing-core` (`SignatureAppearance.kt`)
Ampliar `SignatureAppearance` y `PdfSigningOptions` manteniendo valores por defecto compatibles hacia atrás con todos los tests existentes:

```kotlin
package pe.gob.reniec.dnie.pdf

enum class StampStyle(val label: String) {
    INSTITUTIONAL("Institucional"),
    CARD("Tarjeta Clásica"),
    MINIMAL("Minimalista"),
    INK("Rúbrica + Datos"),
    HANDWRITTEN_ONLY("Solo Rúbrica")
}

enum class StampColorTheme(
    val label: String,
    val accentRgb: Int,      // Barra lateral, emblema y encabezado
    val backgroundRgb: Int,  // Fondo suave de la tarjeta
    val borderRgb: Int,      // Borde exterior y línea divisoria
    val titleRgb: Int,       // Nombre del firmante
    val bodyRgb: Int         // Metadatos (DNI, fecha, motivo, lugar)
) {
    AETHER_BLUE("Azul Aether", 0x004AC6, 0xF8F9FF, 0xB8CCF0, 0x0B1C30, 0x434655),
    CRIMSON_SEAL("Rojo Institucional", 0xB91C1C, 0xFFFBFB, 0xE5C4C4, 0x1E293B, 0x475569),
    EMERALD_TRUST("Verde Verificado", 0x006A63, 0xF4FBF9, 0xB2DFDB, 0x00201D, 0x37474F),
    SLATE_MONO("Monocromo Impresión", 0x1E293B, 0xFFFFFF, 0x64748B, 0x0F172A, 0x334155)
}

enum class StampBadgeType(val label: String) {
    SEAL_VECTOR("Escudo DNIe"),
    QR_VERIFICATION("QR de Verificación"),
    NONE("Sin insignia")
}

enum class StampPageTarget(val label: String) {
    SPECIFIC_PAGE("Página seleccionada"),
    FIRST_PAGE("Primera página"),
    LAST_PAGE("Última página"),
    ALL_PAGES("Todas las páginas")
}

data class SignatureAppearance(
    val style: StampStyle = StampStyle.INSTITUTIONAL,
    val colorTheme: StampColorTheme = StampColorTheme.AETHER_BLUE,
    val badge: StampBadgeType = StampBadgeType.SEAL_VECTOR,
    val customHeader: String = "FIRMADO DIGITALMENTE",
    val showDni: Boolean = true,
    val showDate: Boolean = true,
    val showTime: Boolean = true,
    val showReason: Boolean = true,
    val showLocation: Boolean = true,
    val showTsaBadge: Boolean = true,
    val showHandwritten: Boolean = false,
    val handwrittenPng: ByteArray? = null
) {
    val requiresHandwritten: Boolean get() = showHandwritten || style == StampStyle.HANDWRITTEN_ONLY
}
```

### 3.3. Diseño Gráfico Vectorial del Nuevo Sello PDF (`INSTITUTIONAL`, `CARD`, `MINIMAL`, `INK`)

| Elemento Visual | Especificación de Renderizado Vectorial (`PDPageContentStream` / `Graphics2D`) |
| :--- | :--- |
| **Contenedor y Bordes (`INSTITUTIONAL` y `CARD`)** | Rectángulo con **esquinas redondeadas** (`radius = minOf(5f, height * 0.08f)`) trazado con curvas Bézier.<br>• En `INSTITUTIONAL`: fondo `colorTheme.backgroundRgb`, borde perimetral de `0.6 pt` en `colorTheme.borderRgb` y **franja vertical izquierda de acento** de `3.5 pt` de ancho rellena con `colorTheme.accentRgb`.<br>• En `CARD`: fondo blanco `#FFFFFF`, borde `0.7 pt` en `colorTheme.borderRgb`, sin franja lateral. |
| **Estilo `MINIMAL`** | Sin fondo opaco ni recuadro cerrado. Únicamente una **barra vertical izquierda de `2.0 pt`** en `colorTheme.accentRgb` (de `padding` a `height - padding`) que ancla visualmente el bloque de texto sobre la página sin tapar el fondo del documento. |
| **Columna Izquierda: Insignia / Rúbrica / QR** | Se dibuja cuando el ancho del sello sea `>= 110 pt` y `badge != NONE` (o `requiresHandwritten == true`):<br>• **Ancho de columna:** `26%` del ancho total (en `SEAL_VECTOR` o `QR_VERIFICATION`) o `34%` (si incluye rúbrica manuscrita `INK`), separada del texto por una línea vertical fina (`0.5 pt`, `colorTheme.borderRgb`) con margen superior/inferior de `6 pt`.<br>• **Opción `SEAL_VECTOR`:** Medallón vectorial de doble anillo concéntrico en `colorTheme.accentRgb`, escudo estilizado central con check de verificación vectorial y leyenda limpia `"DNIe"` en negrita.<br>• **Opción `QR_VERIFICATION`:** Matriz QR generada en memoria (reutilizando `io.nayuki.qrcodegen.QrCode` en Desktop o matriz compacta en `dnie-signing-core`) con el resumen público: `DNI:<dni>\|NOMBRE:<nombre>\|FECHA:<iso8601>\|PAdES`. Se dibuja como módulos vectoriales (`addRect`) nítidos a cualquier zoom, nunca como mapa de bits borroso. |
| **Columna Derecha: Jerarquía Tipográfica** | Fuentes Sans-Serif reales en ambas plataformas (**Windows:** `LiberationSans-Regular.ttf` + `LiberationSans-Bold.ttf` incluidas en `pdfbox-resources`; **Android:** `PDType1Font.HELVETICA` + `PDType1Font.HELVETICA_BOLD` en lugar de `COURIER`):<br>1. **Fila 1 (Cabecera + Badge TSA):** `customHeader` en `Bold`, tamaño `0.74 * baseSize`, color `colorTheme.accentRgb`. Si `reniecTimestamp && showTsaBadge`, dibuja al lado o al pie una píldora vectorial redondeada con el texto `"PAdES-T · TSA RENIEC"`.<br>2. **Fila 2 (Nombre del Firmante):** `signerName` en **`Bold`**, tamaño `1.05 * baseSize`, color `colorTheme.titleRgb` (`#0B1C30`). Puede ocupar hasta 2 líneas con word-wrap limpio sin truncar apellidos.<br>3. **Filas 3..N (Metadatos):** Tamaño `0.82 * baseSize`, color `colorTheme.bodyRgb` (`#434655`). Las etiquetas (`"DNI: "`, `"Motivo: "`, `"Lugar: "`, `"Fecha: "`) se dibujan en `Bold` y el valor en `Regular` en la misma línea. |

### 3.4. Motor de Word-Wrap Compartido en `dnie-signing-core` (`StampLayoutEngine.kt`)
Para eliminar el corte de palabras a mitad de sílaba de `DesktopStampRenderer.kt`, se extrae a `dnie-signing-core` un calculador puro de líneas que recibe una función de medición `(text: String, bold: Boolean, fontSize: Float) -> Float`:
- Parte siempre por espacios (`\\s+`). Solo si una palabra individual aislada es más ancha que toda la caja disponible, la divide con guion.
- Ajusta `baseSize` mediante búsqueda binaria entre `5.0 pt` y `11.0 pt` para aprovechar el espacio vertical del rectángulo elegido por el usuario (centrando verticalmente el bloque de texto dentro de la altura útil `height - 2 * padding` para que no quede pegado al borde superior y vacío abajo).

---

## 4. Rediseño de la Vista Previa Interactiva del Sello (Windows y Android)

### 4.1. En Windows Suite (`PreviewCanvas.kt` + `DesktopStampRenderer.kt`)
Actualmente `PreviewCanvas.kt` solo pinta un cuadro azul plano con el string `"Área del sello"`. Se reemplazará por una vista previa **WYSIWYG en tiempo real**:

1. **Hoja PDF con elevación:**
   - Fondo del lienzo en `#EFF4FF` (`SurfaceContainer`).
   - La página PDF se dibuja con una sombra proyectada suave (`Color(11, 28, 48, 28)` desplazada `(0, 4)` px y borde `#CBDBF5`).
2. **Renderizado en vivo del sello sobre la página:**
   - Añadir en `DesktopStampRenderer` una función `renderPreviewImage(widthPx: Int, heightPx: Int, name: String, dni: String, options: PdfSigningOptions): BufferedImage` que genera en memoria la imagen del sello usando exactamente el mismo layout, colores (`StampColorTheme`), estilo e insignia seleccionados.
   - `PreviewCanvas` dibuja esa imagen dentro del rectángulo `placement`, rodeada por un borde de selección punteado en `#004AC6` (`1.5 px`) y **4 manejadores circulares blancos con borde azul (`10 px`)** en las esquinas (permitiendo redimensionar desde cualquier esquina o borde, con cursor dinámico `Cursor.MOVE_CURSOR` y `Cursor.SE_RESIZE_CURSOR`).
3. **Guías de alineación magnéticas (Snap Guides):**
   - Al arrastrar el sello cerca del centro horizontal/vertical de la página o de los márgenes estándar (`5%` de cada borde), se dibuja una línea guía fina en `#004AC6` y se hace un ligero *snap* para que colocar firmas alineadas sea inmediato.
4. **Barra superior del visor enriquecida:**
   - Controles de página: `[ ◀ ] Página [ 1 ] de 11 [ ▶ ]`.
   - Selector `Aplicar en`: `Página actual` | `Primera página` | `Última página` | `Todas las páginas`.
   - Botones de posición rápida (Presets `SignaturePreset`): `[ ↖ Sup. Izq ]` `[ ↗ Sup. Der ]` `[ ↙ Inf. Izq ]` `[ ↘ Inf. Der ]`.

### 4.2. En Android (`SignerScreen.kt` y `PdfPlacementEditor.kt`)
1. **Tarjeta de Vista Previa Instantánea en `SignerScreen.kt`:**
   - Dentro de la sección *"Configurar firma"* (justo encima del botón *"Colocar sello · pantalla completa"*), añadir un contenedor `ElevatedCard` con fondo de cuadrícula sutil que muestra el `Bitmap` del sello renderizado en vivo (`VisualStampRenderer.previewPdf`).
   - Así, cuando el usuario toca los chips de **Estilo** (`Institucional`, `Tarjeta`, `Minimalista`, `Rúbrica`), **Color** (`Azul Aether`, `Rojo`, `Esmeralda`, `Monocromo`), **Insignia** (`Escudo DNIe`, `QR`, `Sin insignia`) o los datos (`DNI`, `Fecha`, `Motivo`), ve el cambio gráfico en tiempo real sin tener que abrir y cerrar el diálogo de página completa.
2. **Selector de página para lotes en Android:**
   - Permitir elegir también en Android si el sello se estampa en la `Última página`, `Primera página`, `Todas` o en la página específica elegida en el visor.

---

## 5. Rediseño Estético de `DNIe Suite` en Windows (`dnie-signing-ui`)

Para que `SuiteWindow.kt` deje de verse como un formulario gris de los años 2000 sin añadir librerías externas pesadas, se introduce un paquete ligero de componentes propios en `dnie-signing-ui` (`AetherSwingTheme.kt` y `AetherComponents.kt`):

### 5.1. Componentes Base Swing Aether (`AetherComponents.kt`)
* **`AetherCardPanel`:** `JPanel` con `isOpaque = false` que sobrescribe `paintComponent` con `RenderingHints.KEY_ANTIALIASING = VALUE_ANTIALIAS_ON`, dibujando un `RoundRectangle2D` (`arc = 16 px`) relleno con `#FFFFFF` y borde de `1 px` en `#DCE9FF`.
* **`AetherButton`:** Botón redondeado (`arc = 12 px`) sin el bisel clásico de Metal/Windows, con tres variantes:
  - `PRIMARY`: fondo `#004AC6` (`hover #003A9B`), texto blanco en `Segoe UI Semibold 13pt`.
  - `SECONDARY`: fondo `#E5EEFF` (`hover #D0E1FF`), texto `#00174B`.
  - `OUTLINE` / `GHOST`: fondo transparente, borde `#737686` o `#DCE9FF`, texto `#0B1C30`.
* **`AetherStatusPill`:** Etiqueta redondeada tipo cápsula con punto indicador de color:
  - Desconectado: fondo `#EFF4FF`, punto `#737686`, texto `#434655`.
  - Esperando móvil / QR activo: fondo `#FFF8E1`, punto `#D97706`, texto `#92400E`.
  - Conectado: fondo `#D0F7F0`, punto `#006A63`, texto `#00201D`.
* **`ColorSwatchSelector`:** Fila de círculos de color clicables (`#004AC6`, `#B91C1C`, `#006A63`, `#1E293B`) con anillo exterior de selección para elegir el `StampColorTheme` del sello de un vistazo.

### 5.2. Nueva Distribución de `SuiteWindow.kt` (3 Zonas Limpias)
1. **Cabecera Compacta (1 sola barra de 64 px de alto):**
   - **Izquierda:** Icono vectorial azul + título `DNIe Suite 3.1` (`Segoe UI Bold 22pt`, `#0B1C30`) + subtítulo `"Firma digital PAdES y lotes privados"`.
   - **Derecha:** `AetherStatusPill` (estado actual) + campo compacto `IP Android` + botones `Conectar Android` (`PRIMARY`), `Mostrar QR` (`SECONDARY`), `Desconectar` (`OUTLINE`) y un botón discreto `⚙ Red y Rutas` que abre un diálogo modal limpio con las rutas avanzadas (`Carpeta de resultados`, `Archivo de identidad DPAPI`, `Huella SHA-256 de la PC` con botón *Copiar* y el `BridgePanel`).
   - **Beneficio visual inmediato:** Desaparecen de la vista principal las cajas de texto gigantes con rutas de disco (`D:/DNIeToolchain/...`) y cadenas hexadecimales que saturaban toda la parte superior.
2. **Columna Izquierda (`280 px`): Lote de Documentos con Drag & Drop y Tarjetas:**
   - Soporta **Arrastrar y Soltar (`DropTarget`)** archivos PDF o documentos directamente desde el Explorador de Windows.
   - Cuando la lista está vacía, muestra un recuadro punteado amigable: *"Arrastra tus PDFs aquí o pulsa + Añadir archivos"*.
   - Cada elemento del `JList` se dibuja con `DocumentCardCellRenderer`: tarjeta con esquinas redondeadas, badge `[PDF]` en azul o `[DOC]` en gris, nombre del archivo en negrita, segunda línea con tamaño en KiB/MiB y estado (`● Pendiente`, `✓ Firmado`, `⚠ No disponible`).
   - Si el usuario aún no ha leído su certificado, incluye un campo opcional *"Nombre para previsualizar el sello"* (que solo afecta al lienzo de vista previa; el PDF firmado final siempre usa el CN real extraído del certificado del DNIe).
3. **Columna Derecha (`300 px`): Inspector Visual de Firma:**
   - Organizado en 3 tarjetas `AetherCardPanel` con espaciado limpio:
     - **Tarjeta 1 · Modalidad Criptográfica:** Selector (`Sello visible`, `PDF invisible`, `Firma separada .p7s`) + Toggle estilizado `Sello de tiempo RENIEC (PAdES-T)` con nota informativa suave.
     - **Tarjeta 2 · Apariencia del Sello:** Selector de `Estilo` (`Institucional`, `Tarjeta Clásica`, `Minimalista`), selector de `Insignia` (`Escudo DNIe`, `QR de Verificación`, `Sin insignia`) y selector visual de paleta `Color del sello` (`ColorSwatchSelector`).
     - **Tarjeta 3 · Datos Visibles:** Chips seleccionables (`DNI`, `Fecha`, `Hora`, `Motivo`, `Lugar`), más campos estilizados para `Título del sello`, `Motivo` y `Lugar`.
4. **Barra Inferior (Footer de Acción):**
   - Mensaje de estado claro a la izquierda + botón `Abrir carpeta de salida` (`SECONDARY`) + botón prominente `Firmar lote (N archivos)` (`PRIMARY`, `44 px` de alto).

---

## 6. Rediseño Estético de `DNIe Bridge Manager` (`tools/windows-manager`)

En `MainForm.Layout.cs` y `MainForm.cs` (.NET 8 WinForms), se moderniza la ventana sin añadir dependencias NuGet externas:

1. **Cabecera Aether + 3 Tarjetas de Salud (`HealthCard`):**
   - Fondo del formulario en `#F8F9FF` (`Color.FromArgb(248, 249, 255)`).
   - En la parte superior, una fila de **3 tarjetas blancas redondeadas** con borde `#DCE9FF` e indicador circular de color:
     - **Tarjeta 1 · Servicio Base:** `Activo (SYSTEM)` (Verde `#006A63`) vs `No instalado / Detenido` (Gris/Rojo), con el botón integrado `Instalar / Reparar` solo cuando hace falta o en el pie.
     - **Tarjeta 2 · Lector Windows (PC/SC):** `Publicado en WinSCard` (Verde `#006A63`) vs `Ausente de WinSCard` (Ámbar `#D97706`) vs `Sin verificar`.
     - **Tarjeta 3 · Tarjeta DNIe:** `DNI Presente` (Verde `#006A63`) vs `Esperando DNI` (Azul `#004AC6`) vs `Puente detenido` (Gris).
2. **Selector de Transporte por Pestañas / Botones Segmentados (`Tailscale` | `Wi-Fi LAN` | `USB Directo`):**
   - Sustituir el `ComboBox` de método y las filas deshabilitadas por 3 botones segmentados en la parte superior de la tarjeta de configuración.
   - **Visibilidad dinámica (`Visible = true/false`):**
     - Si el usuario elige **Tailscale**: muestra `Host/IP Tailscale PC` y `Host/IP Tailscale Teléfono`; oculta `Dispositivo USB` y oculta `Mostrar QR`.
     - Si el usuario elige **Wi-Fi LAN**: muestra `Interfaz LAN PC` e `IP Wi-Fi Teléfono` + botón `Mostrar QR`; oculta `Dispositivo USB`.
     - Si el usuario elige **USB Directo**: muestra únicamente `Dispositivo USB` + botón `Actualizar USB` + botón `Mostrar QR`; oculta las filas de IP.
3. **Tarjeta Inferior de Integración RENIEC / Bandeja del Sistema:**
   - Tarjeta dedicada para el **Selector inteligente RENIEC/Ciudadano Digital** con un indicador claro (`Activo` / `Inactivo`) y un solo botón alternador (`Activar selector` / `Detener selector`).

---

## 7. Secuencia de Implementación Recomendada para el Agente Local

Para ejecutar este rediseño con riesgo cero de regresión sobre el backend:

| Fase | Módulos a editar | Entregables y pruebas de verificación |
| :--- | :--- | :--- |
| **Fase 1: Motor y Estética del Sello PDF** | `dnie-signing-core`, `dnie-pdf`, `dnie-pdf-desktop` | • Añadir `StampColorTheme`, `StampBadgeType`, `StampPageTarget` y `StampLayoutEngine` (word-wrap por palabras y centrado vertical) en `dnie-signing-core`.<br>• Actualizar `DesktopStampRenderer.kt` (cargar `LiberationSans-Bold.ttf` + `Regular.ttf`, esquinas redondeadas, barra de acento, emblema vectorial / QR vectorial, badge `PAdES-T` y función `renderPreviewImage`).<br>• Actualizar `ModernStampRenderer.kt` en `dnie-pdf` (cambiar `COURIER` por `HELVETICA`/`HELVETICA_BOLD` y aplicar los mismos estilos/colores/insignias).<br>• Ejecutar `./gradlew :dnie-signing-core:test :dnie-pdf:test :dnie-pdf-desktop:test` y `python tools/check_architecture.py`. |
| **Fase 2: Vista Previa WYSIWYG y Rediseño `dnie-signing-ui`** | `dnie-signing-ui` | • Crear `AetherSwingTheme.kt` y `AetherComponents.kt` (`AetherCardPanel`, `AetherButton`, `AetherStatusPill`, `ColorSwatchSelector`, `DocumentCardCellRenderer`).<br>• Actualizar `PreviewCanvas.kt` para mostrar el sello real renderizado en vivo, 4 manejadores circulares, guías de centro/margen y barra de presets (`↖ ↗ ↙ ↘`) + `StampPageTarget`.<br>• Reorganizar `SuiteWindow.kt` (cabecera compacta con diálogo `⚙ Red y Rutas`, Drag & Drop de archivos e inspector derecho por tarjetas).<br>• Ejecutar `./gradlew :dnie-signing-ui:test`. |
| **Fase 3: Vista Previa en Vivo en Android y Rediseño `windows-manager`** | `app`, `tools/windows-manager` | • En `SignerScreen.kt` (Android): añadir selectores de `StampColorTheme`, `StampBadgeType`, `StampPageTarget` y tarjeta de miniatura en vivo del sello.<br>• En `MainForm.Layout.cs` y `MainForm.cs`: aplicar fondo y tarjetas Aether, las 3 tarjetas de salud (`Servicio`, `WinSCard`, `DNI`) y visibilidad dinámica de filas por método (`Tailscale` / `LAN` / `USB`).<br>• Compilar paquete nativo y APK, verificando todas las suites de pruebas. |
