# Especificación Técnica R10 (Base `dnie-suite-main (7).zip` / v3.1.2 / APK 86): Acabado Visual de Nivel Estudio y Fluidez a 60 FPS en Android y Windows

## 1. Contraste de `dnie-suite-main (7).zip` (`R9` · `v3.1.2` · `APK 86`)

### 1.1. Lo que R9 (`SISTEMA_DISENO_GUIA_R9.md`) dejó implementado y verificado
En `dnie-suite-main (7).zip`, el agente local completó la base estructural de R9 con **252 pruebas JVM/Android unitarias aprobadas**, **48 comprobaciones de `Windows Manager`** y **8 pruebas instrumentadas en dispositivo (`APK 86`)**:
* Creación de `/DESIGN.md` en la raíz del repositorio y alineación de tokens hexadecimales base Claro/Oscuro (`#0B1326` / `#131B2E` / `#6B9EFF`) entre `Theme.kt`, `AetherSwingTheme.kt` y `AetherControls.cs`.
* Tipografía tabular (`tnum`) en `Type.kt`, retiro de `.uppercase()` + `Monospace` en `FeatureScaffold.kt`, y creación de `AetherCard.kt`, `AetherMotion.kt`, `AndroidMotionPreference.kt` y `NfcOperationVisual.kt`.
* Guía de 3 pasos en Android (`OnboardingGuideSheet.kt` + `OnboardingViewModel.kt`) y diálogo de guía rápida en Windows (`SuiteQuickGuideDialog.kt`).

---

### 1.2. Diagnóstico Franco: ¿Por qué las interfaces en `dnie-suite-main (7).zip` aún no se sienten "Ultra-Premium y Fluidas"?

Al inspeccionar el código de renderizado actual en ambos extremos (`SuiteWindow.kt`, `AetherComponents.kt`, `MainForm.Layout.cs`, `AetherControls.cs`, `HomeScreen.kt`, `BridgeScreen.kt`, `RemoteSigningScreen.kt`, `OnboardingGuideSheet.kt`, `DigitalIdCredentialCard.kt` y `SignerControls.kt`), se identifican **7 causas concretas** por las que la app todavía se percibe rígida o básica en varias vistas:

| # | Extremo | Qué ocurre hoy en el código de `dnie-suite-main (7).zip` | Por qué rompe la sensación Premium y cómo lo resolvemos en R10 |
| :--- | :--- | :--- | :--- |
| **1** | **Windows (`DNIe Suite`)** | **`SuiteWindow.kt:342` usa `JOptionPane.showConfirmDialog` nativo de Java para pedir el PIN de firma** (y en las líneas `253` y `267` para confirmar desconexión/cierre). | En el momento culminante de firmar, salta una ventana emergente gris de Java Swing con botones `OK` / `Cancel` nativos. En R10 se reemplaza por un **`AetherPinDialog` modal propio** con diseño Aether, icono de escudo, foco directo y botones estilizados. |
| **2** | **Windows (`DNIe Suite`)** | **Cero animación en controles Swing (`AetherComponents.kt`) y bordes cuadrados sobre tarjetas redondeadas (`DocumentCardCellRenderer:103`)**:<br>• `DocumentCardCellRenderer` aplica `BorderFactory.createLineBorder(primary, 1)` (un rectángulo de esquinas rectas a 90°) sobre un `AetherCardPanel` redondeado (`16px`).<br>• El badge `"PDF"` es un `JLabel` sin fondo.<br>• Los botones y chips cambian de color en 1 solo frame seco al pasar el ratón. | • Crear **`AetherSwingAnimator` (60 FPS / 16 ms)** para interpolar suavemente colores de hover/press (`140 ms`) en botones, chips y selectores de color.<br>• Rediseñar **`DocumentCardCellRenderer`** con barra de acento lateral redondeada, fondo `primaryContainer` suave al seleccionar, píldora para `"PDF"` y píldora Esmeralda para `"✓ Firmado"`. |
| **3** | **Windows (`DNIe Bridge`)** | **En `MainForm.Layout.cs:23-25`, la cabecera es un `FlowLayoutPanel` vertical que apila el botón `"Modo oscuro"` debajo del subtítulo**, y las 3 tarjetas `HealthCard` (`AetherControls.cs:87`) solo pintan un punto de `8x8 px` con mucho espacio vacío. | • Estructurar la cabecera de `MainForm` en 2 columnas (Marca + subtítulo a la izquierda; píldora de estado + botón de tema a la derecha).<br>• Enriquecer **`HealthCard`** con banda superior de color semántico, icono vectorial en medallón redondeado (`32x32 px`), etiqueta de estado en píldora y hover suave a 60 FPS en `AetherButton`. |
| **4** | **Android (`HomeScreen.kt`)** | **En `HomeScreen.kt:80-87`, `BentoCard` tiene `heightIn(min = 150.dp)` pero apila un icono pequeño de `24dp` y dos textos arriba**, dejando vacía la mitad inferior de cada tarjeta. | • Diseñar `BentoCard` con estructura Bento real (`Arrangement.SpaceBetween`): fila superior con el icono dentro de un **medallón cuadrado redondeado (`42.dp`, `primaryContainer`)** + flecha `↗` sutil a la derecha, y bloque inferior con título en negrita y subtítulo. |
| **5** | **Android (`BridgeScreen.kt` y `RemoteSigningScreen.kt`)** | **Pantallas de conexión PC rezagadas visualmente:**<br>• Siguen pidiendo el CAN en un `OutlinedTextField` vacío aunque el usuario ya haya guardado su CAN en `CanStorage` (no usan `SavedCanChip`).<br>• Los botones `"Configuración de conexión"`, `"Comparar huella"` y los datos de la PC vinculada son textos sueltos sin tarjetas de estado. | • Integrar **`SavedCanChip`** en `BridgeScreen` y `RemoteSigningScreen` (autocompletando desde `CanStorage` para conectar en 1 toque).<br>• Crear **`LinkedPcCard`** con badge `"TLS Pinning activo"`, dirección en píldora monoespaciada, huella SHA-256 en contenedor técnico y barra de acción inferior fija. |
| **6** | **Android (`SignerControls.kt` y `SignerScreen.kt`)** | **Cambio de pestañas en seco (`when (tab)` en `SignerControls.kt:40`) y lista sin transición de elementos:**<br>• Cambiar entre `Apariencia`, `Datos y TSA` y `Páginas` corta en seco.<br>• El selector `TabRow` usa la línea inferior estándar de Material en lugar de un **selector segmentado tipo píldora deslizante (`AetherSegmentedTabs`)**. | • Crear **`AetherSegmentedTabs`** con indicador tipo píldora animada (`animateDpAsState` con `aetherSpring`).<br>• Envolver el contenido de las pestañas en `AnimatedContent` con deslizamiento horizontal suave + `fadeIn`, y añadir `Modifier.animateItem()` en los ítems de `SignerScreen`. |
| **7** | **Android (`OnboardingGuideSheet.kt` y `DigitalIdCredentialCard.kt`)** | • En `OnboardingGuideSheet.kt:101`, `GuideIllustration` dibuja trazos sueltos sin marco ni ondas animadas.<br>• En `DigitalIdCredentialCard.kt:38-39`, los chips de verificación (`Control MRZ` y `Firma EF.SOD`) se apilan en filas separadas y los datos personales son una lista simple. | • Envolver `GuideIllustration` en un **lienzo escénico (`surfaceContainerLow`, radio `16.dp`)** con ilustración detallada de DNIe + ondas NFC animadas.<br>• Dar a `DigitalIdCredentialCard` diseño de **Credencial Oficial** con cabecera tintada, rejilla `2×2` de datos y badges Esmeralda en una sola fila (`FlowRow`). |

---

## 2. Eje 1: Transformación Visual y Fluidez a 60 FPS en Windows (`DNIe Suite` y `DNIe Bridge`)

### 2.1. Reemplazo de `JOptionPane` por Diálogos Modales Aether (`AetherPinDialog.kt` en `dnie-signing-ui`)
En `SuiteWindow.kt` (línea 342), eliminar el `JOptionPane.showConfirmDialog` para pedir el PIN y crear `AetherPinDialog`:
* **Diseño de `AetherPinDialog`:**
  - `JDialog(owner, "Autorizar firma con DNIe", true)` con fondo `AetherSwingTheme.background` y tarjeta central `AetherCardPanel`.
  - Cabecera con icono vectorial de candado/escudo en un medallón `primaryContainer` de `44x44 px`, título `"Autorizar firma digital"` (`Segoe UI Bold 18pt`) y subtítulo `"Se firmarán N documento(s) con tu DNIe conectado en Android"`.
  - Contenedor del campo `JPasswordField` con fuente grande (`18pt`), borde redondeado de `2px` en `AetherSwingTheme.primary` cuando tiene el foco, botón mostrar/ocultar PIN y nota suave de seguridad.
  - Pie con `AetherButton("Cancelar", ButtonVariant.GHOST)` y `AetherButton("Firmar ahora", ButtonVariant.PRIMARY)` (`Enter` confirma por defecto, `Escape` cancela y limpia el array de `Char` inmediatamente).

### 2.2. Motor de Micro-Animación a 60 FPS en Swing (`AetherSwingAnimator.kt`) y Rediseño de Tarjetas de Documento
1. **Interpolación suave de Hover y Selección (`AetherSwingAnimator`):**
   - Añadir un helper ligero basado en `javax.swing.Timer(16)` que interpole un factor `hoverProgress: Float` (`0f` a `1f` en `140 ms`) cuando el ratón entra/sale de un `AetherButton`, `AetherToggleChip` o `ColorSwatchSelector`:
     ```kotlin
     fun blend(from: Color, to: Color, fraction: Float): Color {
         val t = fraction.coerceIn(0f, 1f)
         return Color(
             (from.red + (to.red - from.red) * t).toInt(),
             (from.green + (to.green - from.green) * t).toInt(),
             (from.blue + (to.blue - from.blue) * t).toInt(),
             (from.alpha + (to.alpha - from.alpha) * t).toInt()
         )
     }
     ```
   - Esto elimina el parpadeo brusco de Swing y hace que todos los botones, chips de plantillas y selectores se sientan suaves como una interfaz web moderna.
2. **Rediseño de `DocumentCardCellRenderer` (`AetherComponents.kt` lín. 97–117):**
   - **Prohibir `BorderFactory.createLineBorder` sobre `AetherCardPanel`**: `AetherCardPanel` debe recibir un parámetro `selected: Boolean = false` y pintar él mismo en `paintComponent`:
     - Si `selected == true`: fondo `AetherSwingTheme.primaryContainer` (o mezcla suave al `35%`), borde redondeado `RoundRectangle2D` de `1.5f` en `AetherSwingTheme.primary` y una **barra vertical izquierda redondeada de `4px` de ancho** en `AetherSwingTheme.primary`.
     - El badge `"PDF"` / `"DOC"` se pinta dentro de un rectángulo redondeado (`38x24 px`, radio `8px`) con fondo `primaryContainer` y texto `primary`.
     - El estado `"✓ Firmado"` se pinta con color `AetherSwingTheme.secondary` (Esmeralda), diferenciándolo al instante de `"Pendiente"`.
3. **Cabecera y Secciones Plegables del Inspector Derecho (`SuiteWindow.kt` lín. 98 y 147):**
   - Dar a la barra superior (`header`) un fondo `AetherSwingTheme.surface` con una línea inferior de `1px` en `AetherSwingTheme.border` para separarla visualmente del área de trabajo.
   - En las tarjetas plegables del inspector derecho (`Modalidad de firma`, `Estilo y plantilla`, `Insignia y contenido`), reemplazar el texto `caption + "  -"` por un encabezado de ancho completo con fuente `Segoe UI Semibold 14pt` y un icono chevron vectorial (`▾` / `▸`) alineado a la derecha.

### 2.3. Acabado Premium en `DNIe Bridge` Windows (`MainForm.Layout.cs` y `AetherControls.cs`)
1. **Cabecera de 2 Columnas (`MainForm.Layout.cs` lín. 23–25):**
   - Reemplazar el `FlowLayoutPanel` vertical por un `TableLayoutPanel` de 2 columnas (`100%` izquierda, `AutoSize` derecha):
     - Izquierda: Título `"DNIe Bridge"` (`Segoe UI Bold 22pt`) + subtítulo `"Lector virtual PC/SC y enlace seguro con tu teléfono"`.
     - Derecha: El botón `themeToggle` (`"☾ Modo oscuro"` / `"☀ Modo claro"`) alineado verticalmente al centro, evitando que quede colgado como un tercer renglón debajo del subtítulo.
2. **Tarjetas de Salud (`HealthCard` en `AetherControls.cs` lín. 75–90) con Iconografía Vectorial y Banda de Estado:**
   - Dibujar en la parte superior de cada `HealthCard` una **banda de acento de `3px`** con el color semántico del estado (`Good` = Esmeralda, `Ready` = Azul Primario, `Waiting` = Ámbar, `Error` = Rojo, `Idle` = Borde).
   - A la izquierda del título, dibujar un **medallón redondeado de `34x34 px`** con fondo tintado suave (`Color.FromArgb(28, color)`) y un icono vectorial GDI+ (Escudo para *Servicio base*, Lector para *Lector Windows PC/SC*, Tarjeta con chip para *Tarjeta DNIe*).
3. **Transición Suave de Hover en `AetherButton` (`AetherControls.cs` lín. 46–64):**
   - Añadir un `System.Windows.Forms.Timer { Interval = 16 }` que interpole `hoverAmount` (`0f..1f`) al entrar/salir con el ratón y pinte un sutil anillo de elevación en el botón primario (`Iniciar puente`).

---

## 3. Eje 2: Acabado Premium, Píldoras Deslizantes y Coherencia en Android (`DNIe Evolución`)

### 3.1. Selector de Pestañas tipo Píldora Deslizante (`AetherSegmentedTabs`) y Transición de Pestañas en `SignerControls.kt`
* **Problema actual:** Tanto el conmutador superior `PDF individual / Lotes` (`NavGraph.kt`), `Certificados / Identidad ICAO` (`NavGraph.kt`) como el `TabRow` de `SignerControls.kt` (línea 34) se ven distintos entre sí y cambian el contenido de golpe.
* **Solución en `app/src/main/java/pe/gob/reniec/dnie/ui/design/AetherSegmentedTabs.kt`:**
  1. Crear un componente compartido **`AetherSegmentedTabs(items: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit)`**:
     - Contenedor exterior en `surfaceContainer` con `RoundedCornerShape(12.dp)` y padding interno de `4.dp`.
     - Indicador activo con fondo `surface` (en modo claro) o `primaryContainer` (en modo oscuro), borde fino de `1.dp` en `outlineVariant` y posición horizontal animada con **`animateDpAsState(..., aetherSpring(reduced))`**.
  2. **En `SignerControls.kt` (líneas 34–40):**
     - Usar `AetherSegmentedTabs` para `["Apariencia", "Datos y TSA", "Páginas y Titular"]`.
     - Envolver el bloque `when (tab)` en un **`AnimatedContent(targetState = tab, transitionSpec = { aetherTabTransition(reduced) })`** (en `AetherMotion.kt`) que deslice suavemente hacia la izquierda o derecha (`slideInHorizontally { if (targetState > initialState) it / 6 else -it / 6 } + fadeIn()`) según el índice de la pestaña.
  3. **En `SignerScreen.kt` (línea 48):**
     - Añadir `Modifier.animateItem()` a los elementos dinámicos del `LazyColumn` (`SignedResultHeroCard`, filas de documentos en lote y tarjeta de vista previa) para que al agregar/quitar archivos o completar una firma la lista se reordene con física de resorte.

### 3.2. Rediseño de las Tarjetas Bento y Hero en `HomeScreen.kt`
1. **`BentoCard` con medallón de icono y proporción equilibrada (`HomeScreen.kt` lín. 80–87):**
   ```kotlin
   @Composable
   private fun BentoCard(title: String, description: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
       Card(onClick = onClick, modifier = modifier.heightIn(min = 148.dp)) {
           Column(
               Modifier.fillMaxSize().padding(16.dp),
               verticalArrangement = Arrangement.SpaceBetween
           ) {
               Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                   Surface(
                       color = MaterialTheme.colorScheme.primaryContainer,
                       shape = RoundedCornerShape(12.dp),
                       modifier = Modifier.size(42.dp)
                   ) {
                       Box(contentAlignment = Alignment.Center) {
                           Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                       }
                   }
                   Icon(Icons.Default.ArrowOutward, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
               }
               Spacer(Modifier.height(14.dp))
               Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                   Text(title, style = MaterialTheme.typography.titleMedium)
                   Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
               }
           }
       }
   }
   ```
2. **Tarjeta Hero `"Tu DNIe en tu teléfono"` (`HomeScreen.kt` lín. 32–43):**
   - Añadir en la fila superior del Hero el icono dentro de un medallón junto a una **píldora de estado a la derecha**:
     - Si `loaded`: píldora Esmeralda (`secondaryContainer`) con texto `"● Sesión lista"`.
     - Si `savedCan`: píldora suave (`surface`) con texto `"● CAN guardado"`.
     - Si no: píldora `"Lectura NFC"`.

### 3.3. Modernización Visual de `BridgeScreen.kt` y `RemoteSigningScreen.kt` (Integración de `SavedCanChip` y Tarjetas de Enlace)
1. **Autocompletar y mostrar `SavedCanChip` en `BridgeScreen.kt` (lín. 60) y `RemoteSigningScreen.kt` (lín. 43):**
   - Si `state.can` está vacío al abrir la pantalla y `CanStorage.loadSavedCan(context)` tiene un CAN de 6 dígitos guardado, cargarlo mediante `LaunchedEffect(Unit) { val saved = CanStorage.loadSavedCan(context); if (saved.length == 6 && state.can.isBlank()) onCan(saved) }`.
   - Cuando `state.can.length == 6` y el usuario no ha pulsado `"Cambiar"`, mostrar **`SavedCanChip(state.can, saved = ...)`** en lugar del `OutlinedTextField` de contraseña. Así la experiencia de CAN es 100% uniforme en las 5 pestañas de la app.
2. **Tarjeta de PC Vinculada (`LinkedPcCard`) y Chips de Configuración en `BridgeScreen.kt` (lín. 33–57):**
   - Reemplazar los dos `TextButton` apilados (`"Configuración de conexión"` y `"Configuración avanzada"`) por una fila de `FilterChip` / `AssistChip` compactos con icono (`Icons.Default.Tune`, `Icons.Default.Settings`).
   - Mostrar los datos de `"PC vinculada"` dentro de un contenedor `Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(12.dp))` con la dirección IP y la huella SHA-256 en caja monoespaciada limpia.

### 3.4. Ilustraciones Escénicas en `OnboardingGuideSheet.kt` y Credencial Oficial en `DigitalIdCredentialCard.kt`
1. **En `OnboardingGuideSheet.kt` (`GuideIllustration`, líneas 100–123):**
   - Envolver el `Canvas` en un contenedor `Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant))` de `136.dp` de alto.
   - Dibujar:
     - **Paso 0 (CAN):** Tarjeta DNIe con cabecera azul, icono de chip dorado/ámbar a la izquierda, líneas de datos y un **recuadro resaltado en la esquina inferior derecha** con 6 puntos y un halo pulsante suave (cuando `!reduced`) indicando la ubicación del CAN.
     - **Paso 1 (Sensor NFC):** Silueta del teléfono de perfil/reverso con la tarjeta DNIe apoyada en la mitad superior y **2 anillos concéntricos Esmeralda (`secondary`)** irradiando desde el punto de contacto.
     - **Paso 2 (Móvil + PC):** Teléfono y monitor de PC conectados por un arco punteado con un icono de escudo central.
2. **En `DigitalIdCredentialCard.kt` (líneas 14–44):**
   - Añadir una cabecera institucional con icono `Icons.Default.Badge` y subtítulo en color `primary`.
   - Organizar los campos (`Documento`, `Nacimiento`, `Vencimiento`) en tarjetas internas `surfaceContainerLow` de 2 columnas con `fontFeatureSettings = "tnum"`.
   - Agrupar los badges `"✓ Control MRZ verificado"` y `"✓ Firma EF.SOD íntegra"` en un único `FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp))` estilizados con `secondaryContainer` para que luzcan como sellos criptográficos reales y no como botones sueltos.

---

## 4. Plan de Implementación R10 Paso a Paso para el Agente Local (`v3.1.3` / `APK 87`)

| Paso | Archivos objetivo | Qué implementar y cómo verificarlo |
| :--- | :--- | :--- |
| **Paso 1 · `DNIe Suite` Windows: `AetherPinDialog`, Animador 60 FPS y Tarjetas de Documento** | `dnie-signing-ui/.../AetherPinDialog.kt`, `AetherSwingAnimator.kt`, `AetherComponents.kt`, `SuiteWindow.kt` | • Reemplazar el `JOptionPane` de PIN en `SuiteWindow.kt` por `AetherPinDialog` modal estilizado (conservando la limpieza inmediata del `CharArray` del PIN al confirmar/cancelar/cerrar).<br>• Añadir interpolación suave de hover (`16 ms` / `140 ms`) en `AetherButton`, `AetherToggleChip` y `ColorSwatchSelector`.<br>• Rediseñar `DocumentCardCellRenderer` sin `LineBorder` cuadrado: usar barra lateral redondeada, fondo `primaryContainer` suave y píldoras para `"PDF"` y `"✓ Firmado"`.<br>• Estilizar los encabezados plegables del inspector con chevron vectorial (`▾`/`▸`). |
| **Paso 2 · `DNIe Bridge` Windows: Cabecera en 2 Columnas, `HealthCard` Vectorial y Hover Suave** | `tools/windows-manager/MainForm.Layout.cs`, `AetherControls.cs`, `MainForm.cs` | • Reorganizar la cabecera de `MainForm.Layout.cs` para alinear el título a la izquierda y el botón de tema a la derecha.<br>• Mejorar `HealthCard.OnPaint` con banda superior de estado (`3px`), medallón redondeado (`34x34 px`) con icono vectorial GDI+ y tipografía jerárquica.<br>• Añadir transición suave de hover en `AetherButton`. |
| **Paso 3 · Android: `AetherSegmentedTabs`, `BentoCard`, `BridgeScreen` / `RemoteSigningScreen` con `SavedCanChip`** | `app/.../ui/design/AetherSegmentedTabs.kt`, `AetherMotion.kt`, `HomeScreen.kt`, `SignerControls.kt`, `SignerScreen.kt`, `BridgeScreen.kt`, `RemoteSigningScreen.kt` | • Crear `AetherSegmentedTabs` con indicador animado y transición horizontal `AnimatedContent` entre las 3 pestañas de `SignerControls.kt`, más `Modifier.animateItem()` en `SignerScreen.kt`.<br>• Rediseñar `BentoCard` en `HomeScreen.kt` con medallón de icono `42.dp`, flecha `ArrowOutward` y distribución `SpaceBetween`.<br>• Integrar `SavedCanChip` (con carga inicial desde `CanStorage`) y tarjeta `LinkedPcCard` en `BridgeScreen.kt` y `RemoteSigningScreen.kt`. |
| **Paso 4 · Android: Escenas en `OnboardingGuideSheet`, `DigitalIdCredentialCard` y Versionado `3.1.3` (`APK 87`)** | `app/.../ui/onboarding/OnboardingGuideSheet.kt`, `app/.../ui/identity/DigitalIdCredentialCard.kt`, `gradle.properties` | • Enriquecer `GuideIllustration` con marco `surfaceContainerLow`, chip dorado/azul y ondas animadas.<br>• Refinar `DigitalIdCredentialCard` con rejilla de datos y `FlowRow` de sellos Esmeralda.<br>• Incrementar versión con `python tools/bump-version.py --next-patch` (`3.1.3` / `87`), ejecutar tests JVM, `Windows Manager` y pruebas instrumentadas en ADB verificadas con `verify-debug-pair.ps1`. |
