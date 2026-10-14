# Especificación Técnica R8 (Base `dnie-suite-main.zip` / APK 83): Rediseño de Pantallas, Flujos de Navegación, Firma Guiada y Detección NFC Continua

## 1. Contraste y Auditoría de la Versión R7 (`dnie-suite-main.zip` · APK 83)

### 1.1. Lo que R7 (`PERSONALIZACION_SELLO_HAPTICA_R7.md`) implementó y dejó verificado
Tras auditar el código fuente de `dnie-suite-main.zip` contra la entrega anterior (`dnie-suite-main (5).zip`), se confirma que la iteración R7 quedó sólidamente implementada y probada (`241` tests JVM en `61` suites + `44` comprobaciones de `Windows Manager`):
* **Motor de sello y codec compartido (`SignatureAppearance.kt`, `StampLayoutEngine.kt`, `StampDesignCodec.kt`, `StampPlacementGeometry.kt`):** Fondo transparente (`TRANSPARENT`), blanco (`SOLID_WHITE`) y suave (`TINTED`), control de borde (`showBorder`), nuevas insignias (`CHECK_SEAL`, `LOCK_SEAL`), nuevos colores (`ROYAL_INDIGO`, `AMBER_GOLD`), 3 formatos de fecha (`CLASSIC_PE`, `COMPACT_HUMAN`, `ISO_8601`), 3 densidades (`COMPACT`, `NORMAL`, `SPACIOUS`), 4 plantillas (`StampTemplate`) y anclaje a las 4 esquinas al `6%`.
* **Respuesta háptica desacoplada (`ReadingFeedbackEvents.kt`, `ObserveReadingFeedback.kt`, `ReadingFeedback.kt`):** Eventos sin replay (`START`, `PROGRESS_TICK`, `SUCCESS`, `ERROR`) conectados a `MainActivity`, `BridgeActivity` y `RemoteSigningActivity`, con interruptor en `SettingsScreen.kt`.
* **Continuidad Android ↔ Windows (`AndroidStampPreferences.kt`, `StampImageLoader.kt`, `PdfDropSupport.kt`, `AetherSwingTheme.kt`, `AetherControls.cs`):** Persistencia v2 del diseño del sello en Android, proyección del certificado de firma leído hacia la vista previa de `SignerViewModel`, carga de rúbrica PNG en Windows con limpieza de fondo blanco (`RGB > 235`), Drag & Drop con resaltado y tema Claro/Oscuro en `SuiteWindow` y `MainForm`.

---

### 1.2. Diagnóstico de UX en R7: Por qué las pantallas y flujos actuales aún se sienten recargados o cortados

Al recorrer línea por línea las pantallas de `dnie-suite-main.zip` (`SignerScreen.kt`, `NfcTapPromptSheet.kt`, `CredentialsDialog.kt`, `HomeScreen.kt`, `Screen.kt`, `NavGraph.kt`, `ActivityNavigation.kt`, `IdentityScreen.kt`, `CertificatesScreen.kt` y `SuiteWindow.kt`), se identifican **5 problemas estructurales de experiencia de usuario** que debemos resolver en **R8**:

| # | Problema detectado en el código de R7 (`dnie-suite-main.zip`) | Ubicación exacta en el código | Impacto en el usuario |
| :--- | :--- | :--- | :--- |
| **1** | **El modal NFC (`NfcTapPromptSheet`) se cierra de golpe apenas acercas el DNIe durante la firma PDF** | `SignerScreen.kt` lín. 225:<br>`NfcTapPromptSheet(isShowing = state is SignerUiState.AwaitingCard, ...)`<br>`SignerViewModel.kt` lín. 198:<br>`_uiState.value = SignerUiState.Signing(...)` | Cuando el usuario acerca el DNIe, el estado pasa de `AwaitingCard` a `Signing`. Como el modal solo se muestra cuando `state is AwaitingCard`, **la hoja NFC desaparece justo cuando empieza la firma criptográfica** y el progreso queda escondido al final del scroll vertical (lín. 200–205). El usuario cree que ya terminó y retira el DNI antes de tiempo, provocando errores de lectura. |
| **2** | **Salto desconectado entre `CredentialsDialog` (AlertDialog) y `NfcTapPromptSheet` (BottomSheet), pidiendo el CAN completo cada vez** | `CredentialsDialog.kt` lín. 89 (`AlertDialog`) + `NfcTapPromptSheet.kt` lín. 82 (`ModalBottomSheet`) | Primero se abre una ventana flotante en el centro de la pantalla (con un bloque rojo intimidante que habla de `"error 69 83"` y muestra la caja del CAN completa incluso cuando el CAN ya estaba guardado), luego se cierra y salta otra hoja desde abajo. Y al terminar, se cierra sin mostrar una pantalla de **"✓ ¡Listo! Ya puedes retirar tu DNIe"**. |
| **3** | **`SignerScreen.kt` es un muro vertical largo con scroll anidado y sin barra de acción fija** | `SignerScreen.kt` lín. 41 (`Column(...verticalScroll)`) + lín. 57 (`LazyColumn(...heightIn(max = 320.dp))`) + lín. 211 (`Button("Firmar PDF")` al final) | Hay una `LazyColumn` metida dentro de un `Column` con `verticalScroll`, seguida de decenas de `FilterChip`, textos, tarjeta de certificados y recién al fondo del todo el botón `"Firmar PDF"`. Además, tras firmar, los botones `"Guardar"` y `"Compartir"` son pequeños `TextButton` escondidos arriba dentro de la fila del archivo (lín. 71–82). |
| **4** | **Barra de navegación inferior inconsistente y pantalla de Inicio (`HomeScreen.kt`) triplicada** | `Screen.kt` lín. 42 (`Home, Identity, Certificates, Signer, Vault` — **5 ítems sin PC**) vs `ActivityNavigation.kt` lín. 13 (`Home, Certificates, Signer, Pc` — **4 ítems sin Identidad ni Bóveda**) | La barra inferior cambia de cantidad de pestañas y de íconos cuando pasas a `Conexión PC`. Además, `HomeScreen.kt` repite las mismas opciones 3 veces (en la tarjeta superior, en los 2 botones medios y en las 6 tarjetas `ModuleCard` inferiores). |
| **5** | **`IdentityScreen.kt` no usa modal NFC y muestra bloques crudos ICAO (`DG1`, `EF.COM`, `EF.SOD`) con fechas `AA/MM/DD`** | `IdentityScreen.kt` lín. 66–74 (usa un `Card` simple en vez de `NfcTapPromptSheet`) y lín. 89–144 (`IdentityBlockCard`) | Es la única pantalla de la app que no muestra el radar NFC al acercar el DNIe, y presenta los datos personales como un volcado técnico de grupos ICAO en lugar de una **Tarjeta de Identidad Digital** limpia. |

---

## 2. Diseño de Nuevas Pantallas y Flujos para R8

### 2.1. Flujo 1: Hoja Continua de Operación DNIe (`DnieOperationSheet.kt`) — De PIN/CAN a Detección NFC, Progreso en Vivo y Confirmación de Retiro

En lugar de saltar de un `AlertDialog` a un `ModalBottomSheet` que se cierra a mitad de la firma, **todas las operaciones con tarjeta** (`Firma PDF`, `Lotes`, `Certificados`, `Identidad`) usarán una **única hoja inferior modal con transición animada (`AnimatedContent`) a través de 4 etapas claras**:

```
┌──────────────────────────────────────────────────────────────────────┐
│  ETAPA 1: CREDENCIALES      ETAPA 2: ACERCAR DNIe                    │
│  ┌──────────────────────┐   ┌──────────────────────┐                 │
│  │ Firmar: contrato.pdf │   │    ((  [NFC]  ))     │                 │
│  │ CAN: [••••56 Cambiar]│ → │  Acerca tu DNIe al   │ →               │
│  │ PIN: [ • • • • • • ] │   │ reverso del teléfono │                 │
│  │ [ Continuar a NFC ]  │   │ [ Cancelar ]         │                 │
│  └──────────────────────┘   └──────────────────────┘                 │
│                                                                      │
│  ETAPA 3: EN CURSO (¡NO SE CIERRA!)   ETAPA 4: COMPLETADO / ÉXITO    │
│  ┌──────────────────────┐             ┌──────────────────────┐       │
│  │   (( [DNIe OK] ))    │             │      (  ✓  )         │       │
│  │ ¡DNIe detectado!     │      →      │  ¡Firma completada!  │       │
│  │ No retires tu DNIe…  │             │ Ya puedes retirar    │       │
│  │ ████████████░░░░ 2/3 │             │     tu DNIe.         │       │
│  │ Firmando y sellando… │             │ [Guardar] [Compartir]│       │
│  └──────────────────────┘             └──────────────────────┘       │
└──────────────────────────────────────────────────────────────────────┘
```

#### Especificación técnica detallada de `DnieOperationSheet` y mejoras en `CredentialsDialog` / `NfcTapPromptSheet`:
1. **Píldora compacta de CAN recordado (`SavedCanChip`):**
   - En `CredentialsDialog.kt` (líneas 122–173): si `CanStorage.loadSavedCan(context)` ya tiene los 6 dígitos guardados y el usuario no ha pulsado `"Cambiar CAN"`, **no mostrar el `OutlinedTextField` grande del CAN ni el `Checkbox`**. En su lugar, mostrar una píldora limpia en la parte superior:
     `[ 🪪 CAN guardado: ••••56   |   Cambiar ]`
   - Esto reduce a la mitad la altura visual del diálogo en el 95% de los usos cotidianos y lleva el foco directamente al campo **PIN de Firma** (cuando se requiere PIN) o permite pasar en 1 toque al escaneo NFC (cuando solo se requiere CAN).
2. **Aviso de intentos de PIN limpio y humano:**
   - Reemplazar el bloque rojo alarmista de 6 líneas (`"¡ATENCIÓN! MÁXIMO 3 INTENTOS ... (error 69 83)"`, líneas 178–210 de `CredentialsDialog.kt`) por un banner compacto de 2 líneas con icono ámbar/secundario:
     *"Ingresa tu PIN de firma (6 dígitos). Tras 3 intentos fallidos seguidos, el chip se bloquea por seguridad."*
3. **Mantener el `NfcTapPromptSheet` abierto durante toda la fase `Signing` / `Reading` (`NfcSessionStage`):**
   - Evolucionar `NfcTapPromptSheet.kt` para recibir el estado real de la sesión:
     ```kotlin
     enum class NfcSheetPhase {
         AWAITING_TAG,   // Ondas azules pulsantes: "Acerca tu DNIe a la parte trasera"
         TAG_ENGAGED,    // Ondas esmeralda/azul continuas + progreso: "¡DNIe conectado! Manténlo inmóvil…"
         COMPLETED,      // Check verde: "¡Listo! Ya puedes retirar tu DNIe"
         ERROR           // Icono de alerta: "Se perdió el contacto con el DNIe · Reintentar"
     }
     ```
   - **En `SignerScreen.kt` (línea 225):**
     - Cambiar `isShowing = state is SignerUiState.AwaitingCard` por `isShowing = state is SignerUiState.AwaitingCard || state is SignerUiState.Signing`.
     - Cuando `state is SignerUiState.AwaitingCard`, `phase = NfcSheetPhase.AWAITING_TAG`.
     - Cuando `state is SignerUiState.Signing`, `phase = NfcSheetPhase.TAG_ENGAGED`, mostrando dentro del propio BottomSheet el mensaje en vivo (`progress.message`, ej. `"Abriendo sesión y verificando PIN…"`, `"Firmando 1 de 3…"`, `"Solicitando sello de tiempo RENIEC…"`) y una barra de progreso determinista (`if (progress.total > 1) progress.completed.toFloat() / progress.total`) junto con el botón `"Cancelar"`.
   - **En `IdentityScreen.kt` (líneas 66–74):**
     - Usar también `NfcTapPromptSheet` cuando `state.awaitingCard || state.reading` sea `true`, logrando 100% de consistencia visual en toda la app.

---

### 2.2. Flujo 2: Rediseño de la Pantalla de Firma PDF en Android (`SignerScreen.kt`) — Lienzo Protagonista, Pestañas Compactas y Barra Fija

Hoy `SignerScreen.kt` obliga a hacer scroll por toda la pantalla para configurar el sello y encontrar el botón `"Firmar PDF"`. En **R8**, reorganizamos `SignerScreen.kt` en una estructura limpia sin scroll anidado y con **Barra Inferior de Acción Fija (`Sticky Bottom Action Bar`)**:

```
┌──────────────────────────────────────────────────────────┐
│ ← Firma Digital            [ PDF individual | Por lotes ]│
├──────────────────────────────────────────────────────────┤
│ 📄 contrato_arrendamiento.pdf (248 KiB · 4 págs.) [Cambiar]│
│                                                          │
│ ┌──────────────────────────────────────────────────────┐ │
│ │   VISTA PREVIA DEL SELLO (140 dp, cuadrícula suave)  │ │
│ │   ┌──────────────────────────────────────────────┐   │ │
│ │   │  [Escudo]  FIRMADO DIGITALMENTE              │   │ │
│ │   │            JUAN PEREZ GOMEZ · DNI 12345678   │   │ │
│ │   └──────────────────────────────────────────────┘   │ │
│ │  Pág. 4 (Última) · [↖] [↗] [↙] [↘] · [🔍 En página] │ │
│ └──────────────────────────────────────────────────────┘ │
│                                                          │
│ Plantillas: ( Institucional ) ( Ejecutivo ) ( Visto B° ) │
│                                                          │
│ [ 🎨 Apariencia ]  [ 📝 Datos y TSA ]  [ 📄 Páginas ]    │
│ ┌──────────────────────────────────────────────────────┐ │
│ │ (Solo se muestra la pestaña activa, sin scroll largo)│ │
│ └──────────────────────────────────────────────────────┘ │
├──────────────────────────────────────────────────────────┤
│ [ 🕒 PAdES-T RENIEC: Activo ]     [ ✍️ Firmar PDF (1) ] │ ← BARRA FIJA
└──────────────────────────────────────────────────────────┘
```

#### Cambios concretos en `SignerScreen.kt` (Android):
1. **Eliminar el `LazyColumn` anidado dentro del `Column(verticalScroll)` (líneas 57–89):**
   - En modo **PDF individual**, mostrar una única tarjeta horizontal compacta con el nombre del archivo, páginas, peso y botón `"Cambiar"`.
   - En modo **Lotes**, mostrar una lista limpia con tarjetas compactas de una sola línea y contador de seleccionados/firmados.
2. **Subir la Vista Previa del Sello (`StampPreviewCard`) arriba de los controles y añadirle controles rápidos de posición:**
   - Hoy `StampPreviewCard` está al final de todos los chips (línea 171), por lo que cuando tocas un color o una plantilla arriba no ves el resultado sin bajar.
   - En R8, **`StampPreviewCard` va inmediatamente debajo del documento seleccionado**, de modo que cualquier cambio de plantilla, color o insignia se ve en tiempo real sin mover la pantalla.
   - Integrar en el pie de `StampPreviewCard` los 4 botones rápidos de esquina (`↖`, `↗`, `↙`, `↘`) y el botón `"Ubicar sobre el PDF"` (que abre `PdfPlacementEditor`).
3. **Reemplazar los dos acordeones largos por 3 pestañas horizontales compactas (`SecondaryTabRow` / `SingleChoiceSegmentedButtonRow`):**
   - **Pestaña `Apariencia`:** Plantillas rápidas (en fila horizontal deslizable `LazyRow`), Estilo (`Institucional`, `Tarjeta`, `Minimalista`, `Rúbrica`), Color, Fondo (`Suave`, `Blanco`, `Transparente`) y Borde.
   - **Pestaña `Datos y TSA`:** Switch `Sello de tiempo RENIEC (PAdES-T)`, Insignia (`Escudo`, `Verificación`, `Candado`, `Ninguna`), Formato de fecha, Chips de campos visibles (`DNI`, `Fecha`, `Hora`, `Motivo`, `Lugar`) y campos de texto solo para los chips activos.
   - **Pestaña `Páginas y Titular`:** Selector `Aplicar en` (`Página seleccionada`, `Primera`, `Última`, `Todas`), firma manuscrita DG7 y previsualización/exportación del certificado público.
4. **Tarjeta Protagonista de Resultado Post-Firma (`SignedResultHeroCard`) + Barra Inferior Fija (`Sticky Action Bar`):**
   - Fijar en la parte inferior del `FeatureScaffold` una barra de acción (`Surface(tonalElevation = 3.dp)`) que siempre esté al alcance del pulgar:
     - **Antes de firmar:** Muestra el resumen (`"1 PDF listo · Sello visible"`) y el botón principal **`"Firmar PDF"`** (o **`"Firmar N archivos"`**).
     - **Después de firmar (`workspace.artifacts.any { it.status == SIGNED }`):** Muestra en la parte superior de la pantalla una **Tarjeta Esmeralda de Documento Firmado** y en la barra inferior los dos botones principales lado a lado: **`[ Guardar PDF firmado ]`** (`Button`) y **`[ Compartir ]`** (`FilledTonalButton`), más `"Firmar otro"`.

---

### 2.3. Flujo 3: Unificación de la Navegación Inferior y Rediseño de `Inicio` e `Identidad` (`Screen.kt`, `NavGraph.kt`, `ActivityNavigation.kt`, `HomeScreen.kt`, `IdentityScreen.kt`)

#### A. Barra de Navegación Inferior Única de 5 Destinos en Toda la App
* **Problema en R7:** `Screen.items` (en `Screen.kt` lín. 42) tiene `[Inicio, Identidad, DNIe, Firma, Bóveda]` (sin `PC`), mientras que `ActivityNavigation.kt` tiene `[Inicio, Mi DNIe, Firma PDF, Conexión PC]` (4 ítems).
* **Solución en R8:**
  1. Unificar la barra inferior en **5 destinos consistentes** tanto en `MainActivity` (`Screen.items`) como en `ActivityNavigation.kt`:
     - **`Inicio`** (`Screen.Home`)
     - **`Mi DNIe`** (`Screen.Certificates` — que integra en su parte superior un selector segmentado **`[ Certificados | Identidad ICAO ]`** para cambiar en 1 toque entre los certificados X.509 y la ficha de identidad ICAO, manteniendo intacta la ruta `Screen.Identity` en `NavGraph.kt` para compatibilidad con todos los tests existentes).
     - **`Firmar`** (`Screen.Signer` / `Screen.BatchSigner`)
     - **`Bóveda`** (`Screen.Vault`)
     - **`PC`** (`Screen.Pc`)
  2. Así, el usuario **siempre ve exactamente los mismos 5 iconos en la barra inferior**, esté en `Inicio`, en `Firmar`, en `Bóveda`, en `Puente PC` o en `Suite remota`.

#### B. Rediseño de `HomeScreen.kt` (Inicio limpio tipo Bento Grid, sin triplicar botones)
* En lugar de listar las mismas acciones en 3 bloques distintos, estructurar `HomeScreen.kt` en **3 bloques limpios y jerárquicos**:
  1. **Cabecera + Tarjeta Hero de Estado del DNIe (`DnieStatusHeroCard`):**
     - Si aún no se ha leído el DNIe en la sesión: Muestra `"Tu DNIe en tu teléfono"` con el botón principal **`"Leer mi DNIe ahora"`** y un indicador de si el CAN ya está recordado.
     - Si ya se leyeron los certificados o la identidad en la sesión (`summary.certificates > 0 || summary.identityAvailable`): Muestra un badge verde **`"● Datos cargados en sesión"`**, el conteo de certificados e identidad listos para previsualizar firmas, y botones rápidos **`"Ver mi DNIe"`** / **`"Actualizar lectura"`**.
  2. **Cuadrícula Bento `2 × 2` de Tareas Principales (`QuickActionBentoGrid`):**
     - **Tarjeta 1 · `Firmar PDF`** (*"Sello a medida y PAdES-T"* → `onPdf`)
     - **Tarjeta 2 · `Firma por lotes`** (*"Varios archivos con 1 PIN"* → `onBatch`)
     - **Tarjeta 3 · `Conectar con PC`** (*"Lector virtual y Suite Windows"* → `onPc`)
     - **Tarjeta 4 · `Bóveda segura`** (*"Cifrar archivos con tu DNIe"* → `onVault`)
  3. **Tarjeta compacta de Identidad y Certificados + Aviso de respaldo de bóveda.**

#### C. Rediseño de `IdentityScreen.kt` (Credencial Digital Visual en lugar de volcado técnico ICAO)
* Cuando `state.document != null` en `IdentityScreen.kt`:
  1. **Tarjeta Principal tipo "Credencial Digital DNIe" (`DigitalIdCredentialCard`):**
     - Combina en una sola tarjeta elegante los datos de `DG1` (Nombres, Apellidos, N.º de DNI, Fecha de nacimiento y vencimiento formateadas de forma legible `DD/MM/AAAA` en vez de `AA/MM/DD`), la fotografía facial (`DG2`) en un marco redondeado a la izquierda y la firma manuscrita (`DG7`) en un recuadro blanco limpio en la parte inferior con botón directo **`"Usar esta rúbrica en mis firmas PDF"`** y **`"Guardar imagen"`**.
     - Incluye dos chips de verificación criptográfica al pie de la credencial: **`✓ Control MRZ verificado`** y **`✓ Firma EF.SOD íntegra`**.
  2. **Sección Plegable `"Detalles técnicos ICAO (DG1–DG14, EF.COM, EF.SOD)"`:**
     - Conserva todas las tarjetas `IdentityBlockCard(block)` dentro de una sección plegable para usuarios avanzados o auditoría, manteniendo toda la información técnica sin saturar la primera vista.

---

### 2.4. Flujo 4: Pulido del Flujo de Firma y Plantillas en `DNIe Suite` Windows (`SuiteWindow.kt`)

En `dnie-signing-ui/src/main/java/pe/gob/reniec/dnie/desktop/ui/SuiteWindow.kt`:
1. **Cuadrícula `2×2` para las Plantillas Rápidas (línea 157):**
   - Actualmente `for (template in StampTemplate.entries) panel.add(AetherButton(template.label, ButtonVariant.GHOST))` apila 4 botones verticales ocupando 160 px de altura en el panel derecho.
   - Agruparlos en un `JPanel(GridLayout(2, 2, 6, 6))` con botones tipo chip seleccionables que resalten qué plantilla coincide con el diseño actual.
2. **Barra de Progreso Visual y Resumen de Lote en el Pie (`footer`, líneas 172–174):**
   - Añadir junto a `status` una barra de progreso `JProgressBar` delgada (`preferredSize = Dimension(180, 6)`) que sea visible mientras `busy == true` (`isIndeterminate = true` al conectar/validar y progreso claro durante la firma).
   - Cuando termina de firmarse un lote (`showCompleted`, línea 341), resaltar el botón `"Abrir carpeta de salida"` en variante `SECONDARY`/`TONAL` y añadir un botón `"Abrir PDF firmado"` para abrir con 1 clic el documento actualmente seleccionado en la lista sin tener que adivinar que existe el doble clic o el clic derecho.

---

## 3. Plan de Implementación R8 Paso a Paso para el Agente Local

| Paso | Archivos objetivo | Cambios exactos a implementar y verificar |
| :--- | :--- | :--- |
| **Paso 1 · Hoja NFC Continua (`NfcSheetPhase`) y Píldora de CAN Guardado** | `app/.../ui/components/NfcTapPromptSheet.kt`, `CredentialsDialog.kt`, `SignerScreen.kt`, `IdentityScreen.kt` | • Ampliar `NfcTapPromptSheet` con fases visuales (`AWAITING_TAG`, `TAG_ENGAGED`, `COMPLETED`, `ERROR`) y barra de progreso determinista/indeterminada.<br>• En `SignerScreen.kt` (lín. 225), mantener `NfcTapPromptSheet` visible tanto en `AwaitingCard` como en `Signing` (mostrando el progreso del lote y el mensaje de fase dentro del mismo BottomSheet para que el usuario no retire el DNI antes de tiempo).<br>• En `IdentityScreen.kt`, mostrar `NfcTapPromptSheet` durante `state.awaitingCard || state.reading`.<br>• En `CredentialsDialog.kt`, si ya hay un CAN de 6 dígitos guardado, mostrarlo como píldora compacta `"CAN guardado: ••••56 [Cambiar]"` y suavizar el texto de advertencia del PIN. |
| **Paso 2 · Rediseño de `SignerScreen.kt` (Vista Previa Arriba, Pestañas y Barra Inferior Fija)** | `app/.../ui/signer/SignerScreen.kt`, `StampPreviewCard.kt` | • Eliminar el `LazyColumn` anidado dentro del `Column(verticalScroll)`.<br>• Colocar `StampPreviewCard` inmediatamente debajo del selector de documento con los 4 botones de esquina (`↖ ↗ ↙ ↘`) y el acceso a pantalla completa integrados.<br>• Organizar las opciones del sello en 3 pestañas limpias (`Apariencia`, `Datos y TSA`, `Páginas y Titular`) con fila horizontal de plantillas.<br>• Añadir una **Barra Inferior Fija (`bottomBar`)** con el botón `"Firmar PDF"` y una **Tarjeta de Éxito Post-Firma** con botones destacados `"Guardar"` y `"Compartir"`. |
| **Paso 3 · Navegación Unificada de 5 Pestañas, `HomeScreen` Bento y Credencial en `IdentityScreen`** | `app/.../ui/navigation/Screen.kt`, `NavGraph.kt`, `ActivityNavigation.kt`, `HomeScreen.kt`, `IdentityScreen.kt`, `CertificatesScreen.kt` | • Incluir `Screen.Pc` en `Screen.items` (o alinear `Screen.items` y `ActivityNavigation.kt` en los mismos 5 destinos: `Inicio`, `Mi DNIe`, `Firma`, `Bóveda`, `PC`) e integrar un selector superior `Certificados / Identidad ICAO` en `Mi DNIe`.<br>• Rediseñar `HomeScreen.kt` con la tarjeta de estado de sesión y una cuadrícula `2×2` sin duplicar accesos.<br>• Añadir en `IdentityScreen.kt` la tarjeta resumen `DigitalIdCredentialCard` (foto + datos con fecha `DD/MM/AAAA` + rúbrica + checks MRZ/SOD) arriba del acordeón de bloques técnicos ICAO. |
| **Paso 4 · Cuadrícula de Plantillas y Barra de Progreso/Apertura en `SuiteWindow.kt` (Windows)** | `dnie-signing-ui/.../SuiteWindow.kt` | • Colocar los 4 botones de `StampTemplate` en un `GridLayout(2, 2, 6, 6)`.<br>• Añadir indicador de progreso visual en el `footer` durante operaciones activas y botón directo `"Abrir PDF firmado"` al completar el lote. |

---

## 4. Criterios de Verificación y Pruebas para R8

1. **Pruebas unitarias y de UI en JVM / Android:**
   - Conservar en verde las **241 pruebas JUnit JVM** y las **44 comprobaciones de `Windows Manager`**.
   - Verificar que todos los `testTag` existentes (`nav_home`, `nav_certificates`, `nav_signer`, `nav_vault`, `nav_pc`, `bridge_session`, `bridge_stop`, `theme_*`) y las etiquetas de botones verificadas por tests instrumentados (`"Leer mi DNIe"`, `"Firmar PDF"`, `"Guardar"`, `"Compartir"`, `"Colocar sello · pantalla completa"`) sigan presentes y accesibles.
2. **Validación en dispositivo físico (en cuanto reconectes ADB):**
   - Instalar el APK compilado (`APK 84` tras R8) y comprobar el flujo completo de firma PDF verificando que:
     - El `NfcTapPromptSheet` permanece en pantalla durante todo el proceso de firma (`AwaitingCard` → `Signing`) mostrando el avance real hasta el pulso háptico doble de éxito (`ReadingFeedback.success`).
     - El sello transparente e insignias nuevas se previsualizan arriba sin hacer scroll y el PDF firmado valida en ReFirma / Firma Perú.
