# Especificación Técnica R9 (Base `dnie-suite-main (6).zip` / v3.1.1 / APK 85): Sistema `DESIGN.md`, Calibración Claro/Oscuro, Animaciones con Física y Guía de Primer Inicio

## 1. Comparativa de las 4 Skills y Marcos de Diseño Investigados

Se han analizado las cuatro fuentes solicitadas ([Taste Skill](https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2FLeonxlnx%2Ftaste-skill), [Vercel Web Design Guidelines](https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2Fvercel-labs%2Fagent-skills%2Fblob%2Fmain%2Fskills%2Fweb-design-guidelines%2FSKILL.md), [Awesome DESIGN.md](https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2Fvoltagent%2Fawesome-design-md) y [getdesign.md](https://www.google.com/search?q=https%3A%2F%2Fgetdesign.md%2Fwhat-is-design-md)) y se han dejado instaladas en tu entorno las skills **`design-taste-frontend`** y **`web-design-guidelines`**.

| Marco / Skill | Qué aporta al ecosistema `DNIe Suite & Evolución` | Reglas clave que adoptamos en R9 |
| :--- | :--- | :--- |
| **1. [Taste Skill (Leonxlnx/taste-skill)](https://www.google.com/search?q=https%3A%2F%2Fwww.tasteskill.dev%2Fdocs)**<br>*(Anti-Slop Frontend Framework v1 & v2)* | Evita los patrones repetitivos generados por IA ("AI slop") mediante **3 Candados (`The Locks`)**, reglas estrictas de tipografía/superficie y micro-interacciones con física de resorte (`spring`). | • **Prohibición de *eyebrows* en monoespaciado en mayúsculas** encima de cada título (`FeatureScaffold.kt` lín. 24).<br>• **Escalera tonal de superficies** en lugar de sombras pesadas o bordes dobles.<br>• **Física de resorte (`spring`)** y animaciones exclusivamente en la capa de dibujo (`graphicsLayer` / `transform` + `opacity`). |
| **2. [Web Design Guidelines (vercel-labs/agent-skills)](https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2Fvercel-labs%2Fweb-interface-guidelines)**<br>*(Vercel Interface & Accessibility Audit)* | Audita ergonomía táctil, estados de foco por teclado, estabilidad de números dinámicos y claridad de formularios. | • **Números tabulares (`fontFeatureSettings = "tnum"`)** obligatorios en conteos, pesos `KiB`, páginas, `CAN`/`PIN` y porcentajes.<br>• **Áreas de toque `>= 44dp` (móvil) y `>= 36px` (PC)** con anillo de foco visible.<br>• Respeto a preferencias de **movimiento reducido** del sistema. |
| **3. [Awesome DESIGN.md (VoltAgent/awesome-design-md)](https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2Fvoltagent%2Fawesome-design-md)**<br>*(Sistemas reales: [Linear](https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2Fvoltagent%2Fawesome-design-md%2Fblob%2Fmain%2Fdesign-md%2Flinear.app%2FDESIGN.md), [Claude](https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2Fvoltagent%2Fawesome-design-md%2Fblob%2Fmain%2Fdesign-md%2Fclaude%2FDESIGN.md), Stripe, Apple)* | Define cómo un archivo **`DESIGN.md` en la raíz del repositorio** complementa a `AGENTS.md`: mientras `AGENTS.md` define la arquitectura y reglas de compilación, `DESIGN.md` fija la paleta exacta, escala tipográfica, elevación y componentes. | • Incorporar **`DESIGN.md` en la raíz del monorepo** junto a `AGENTS.md`.<br>• Adoptar de **Linear `DESIGN.md`** la **escalera de 4 niveles de superficie (`canvas → surface-1 → surface-2 → surface-3`)** con borde sutil (`hairline`) en modo oscuro. |
| **4. [getdesign.md](https://www.google.com/search?q=https%3A%2F%2Fgetdesign.md%2Fwhat-is-design-md)**<br>*(Estándar `DESIGN.md` de 9 secciones)* | Estandariza las **9 secciones obligatorias** que todo agente local (`Codex`, `Claude Code`, `Cursor`) lee antes de tocar código de interfaz. | • Estructurar el nuevo `DESIGN.md` del proyecto con las 9 secciones canónicas para garantizar paridad entre Android (`Compose`), Windows (`Swing`) y `DNIe Bridge` (`WinForms`). |

---

## 2. Auditoría de `dnie-suite-main (6).zip` (`v3.1.1` / `APK 85` / `R8`) bajo los 4 Marcos de Diseño

### 2.1. Lo que ya quedó implementado y verificado en `dnie-suite-main (6).zip` (No reabrir)
* **Flujos y Pantallas R8 (`PANTALLAS_FLUJOS_NFC_R8.md`):**
  - `DnieOperationSheet.kt` unifica en un solo `ModalBottomSheet` las 5 fases (`CREDENTIALS`, `AWAITING_TAG`, `TAG_ENGAGED`, `COMPLETED`, `ERROR`), manteniéndose visible durante toda la firma (`AwaitingCard` + `Signing`).
  - `SavedCanChip` en `CredentialsDialog.kt` muestra el CAN recordado como píldora compacta (`CAN guardado: ••••56`) y lleva el foco directo al PIN.
  - `SignerScreen.kt` + `SignerControls.kt` + `SignerResultActions.kt` eliminaron el scroll anidado, subieron `StampPreviewCard` arriba de los controles, agruparon las opciones en 3 pestañas (`Apariencia`, `Datos y TSA`, `Páginas y Titular`) y añadieron la barra inferior fija (`SignerActionBar`).
  - `Screen.kt` y `ActivityNavigation.kt` comparten los mismos 5 destinos (`Inicio`, `Mi DNIe`, `Firmar`, `Bóveda`, `PC`) y `DigitalIdCredentialCard.kt` presenta la credencial visual en `IdentityScreen.kt`.
* **Versionado y Firma Estable `3.1.1` (`VERSIONADO_Y_FIRMA_3_1_1.md`):**
  - `gradle.properties` (`3.1.1` / `APK 85`), script `tools/bump-version.py --next-patch` y verificador `tools/android/verify-debug-pair.ps1` funcionando con APK 85 instalada por ADB y las 4 pruebas instrumentadas R8 pasando en `9.22 s`.

### 2.2. Hallazgos Visuales y de Experiencia en `dnie-suite-main (6).zip` para resolver en R9 (`v3.1.2` / `APK 86`)

| Archivo y línea en `dnie-suite-main (6).zip` | Qué detectan `Taste Skill` y `Vercel Guidelines` | Solución de Diseño en R9 |
| :--- | :--- | :--- |
| **`FeatureScaffold.kt:24-25`** | Usa `Text(category.uppercase(), fontFamily = FontFamily.Monospace)` encima del título en todas las pantallas. Es el patrón #1 prohibido por *Taste Skill* (*"No monospace eyebrows outside real code blocks"*). | Cambiar el subtítulo de categoría a `FontFamily.SansSerif` en formato oración (`category`, sin `.uppercase()`), peso `Medium`, y reservar `Monospace` exclusivamente para huellas SHA-256, números de serie, IPs y bloques técnicos. |
| **`Type.kt:9-73`** | Ningún estilo tipográfico declara `fontFeatureSettings = "tnum"`. Cuando cambian los contadores (`1/3`, `248 KiB`, `4/6 dígitos`), los números se desplazan horizontalmente. | Añadir `fontFeatureSettings = "tnum"` (números tabulares) en `bodyMedium`, `bodySmall`, `labelLarge`, `labelSmall` y `titleMedium` de `Type.kt`. |
| **`Theme.kt:53-82` vs `AetherSwingTheme.kt:19-22` y `AetherControls.cs:8-18`** | **Desfase entre el Modo Oscuro de Android y el de Windows:**<br>• Android usa `background = #101A29`, `surface = #172539`, `primary = #B4C5FF`.<br>• Windows usa `background = #0B1326`, `surface = #131B2E`, `primary = #4C8EFF`.<br>• Además, las tarjetas en modo oscuro carecen de un borde fino superior/perimetral (`hairline`) que separe niveles de superficie como en *Linear DESIGN.md*. | Unificar la **Escalera de Superficies Aether (Claro y Oscuro)** en un archivo `DESIGN.md` oficial y alinear `Theme.kt`, `AetherSwingTheme.kt` y `AetherControls.cs`, añadiendo borde sutil `1.dp` (`outlineVariant.copy(alpha = 0.35f)`) a las tarjetas en modo oscuro. |
| **`DnieOperationSheet.kt:38-57`, `SignerControls.kt:39`, `HomeScreen.kt:78`** | • `DnieOperationSheet` usa un único círculo con pulso pequeño (`1.0f..1.07f`) y `AnimatedContent` por defecto.<br>• `BentoCard` en `HomeScreen.kt` y el cambio de pestañas en `SignerControls.kt` no tienen micro-interacción táctil ni transición con física de resorte (`spring`). | • Dotar a `DnieOperationSheet` de **doble onda concéntrica de radar**, transición `spring(stiffness = 380f, dampingRatio = 0.82f)` entre fases y animación de escala/check al completar.<br>• Añadir `animateContentSize(spring(...))` y feedback de presión (`scale = 0.98f`) en tarjetas interactivas. |
| **`HomeScreen.kt` y `SettingsScreen.kt`** | **Ausencia de Guía de Primer Inicio (`Onboarding`):** Un usuario nuevo no sabe en qué zona del teléfono apoyar el DNIe, dónde está impreso el CAN de 6 dígitos en su tarjeta ni que puede conectar su PC sin configurar IPs. | Crear una **Guía Interactiva de Primer Inicio de 3 Pasos (`OnboardingGuideSheet.kt` + tarjeta de bienvenida en `HomeScreen.kt`)** que permita entender el sensor NFC, guardar opcionalmente el CAN en ese instante y conocer el flujo móvil/PC, sin bloquear ningún test instrumentado en arranque en frío. |

---

## 3. Eje 1: Creación de `DESIGN.md` en la Raíz del Repositorio (Estándar `getdesign.md` / `VoltAgent`)

Siguiendo la arquitectura de [Awesome DESIGN.md](https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2Fvoltagent%2Fawesome-design-md), crear el archivo **`DESIGN.md`** en la raíz del proyecto (junto a `AGENTS.md`) y referenciarlo desde `AGENTS.md` con una línea (`- Para decisiones visuales, paleta Claro/Oscuro, tipografía y animaciones, seguir DESIGN.md.`).

Estructura y tokens exactos que debe contener `/DESIGN.md`:

```markdown
# DESIGN.md — Sistema de Diseño Aether (DNIe Evolución & DNIe Suite)

## 1. Visual Theme & Atmosphere
Identidad visual institucional moderna ("Precision Civic Craft"): sobria, confiable, rápida de escanear y libre de ornamentos artificiales. Combina la claridad editorial en Modo Claro con la jerarquía por escalera de superficies de Linear en Modo Oscuro.

## 2. Color Palette & Roles (Paridad Android Compose · Windows Swing · Windows WinForms)

### Modo Claro (Aether Light)
| Rol Semántico | Hex | Uso Funcional |
| :--- | :--- | :--- |
| `canvas` (`background`) | `#F8F9FF` | Fondo base de pantalla y ventana principal |
| `surface-1` (`surface`) | `#FFFFFF` | Tarjetas principales, paneles de documentos y hojas modales |
| `surface-2` (`surfaceContainer`) | `#EFF4FF` | Contenedores secundarios, fondos de pestañas e inputs inactivos |
| `surface-3` (`primaryContainer`) | `#E5EEFF` | Tarjetas Hero, chips seleccionados y resaltados de navegación |
| `hairline` (`border`) | `#DCE9FF` | Bordes de 1dp/1px en tarjetas, inputs y divisores sutiles |
| `primary` | `#004AC6` | Acción primaria (botones CTA, foco, enlaces, acento de marca) |
| `secondary` (`emerald`) | `#006A63` | Confirmación criptográfica, DNIe conectado, PAdES-T verificado |
| `secondaryContainer` | `#D0F7F0` | Fondo de tarjetas de éxito ("Firma completada", "CAN guardado") |
| `ink` (`onSurface`) | `#0B1C30` | Títulos y texto principal de alto contraste |
| `ink-muted` (`onSurfaceVariant`) | `#434655` | Subtítulos, etiquetas secundarias y metadatos |
| `outline` | `#737686` | Bordes de controles deshabilitados e iconos secundarios |
| `amber` (`warning`) | `#D97706` | Estado de espera ("Buscando teléfono", avisos de intentos PIN) |
| `error` | `#BA1A1A` | Errores de validación, pérdida de contacto NFC o firma fallida |

### Modo Oscuro (Aether Dark — Escalera de 4 Niveles de Superficie)
| Rol Semántico | Hex | Uso Funcional |
| :--- | :--- | :--- |
| `canvas` (`background`) | `#0B1326` | Fondo base profundo con tinte azul nocturno (nunca `#000000` puro) |
| `surface-1` (`surface`) | `#131B2E` | Tarjetas principales y paneles elevados (+1 paso de luminancia) |
| `surface-2` (`surfaceContainer`) | `#1D2840` | Contenedores internos, pestañas y cajas de entrada (+2 pasos) |
| `surface-3` (`primaryContainer`) | `#243554` | Tarjeta Hero activa, selección de lista y chips activos (+3 pasos) |
| `hairline` (`border`) | `#2E3C56` | Borde fino de 1dp/1px obligatorio en tarjetas sobre fondo oscuro |
| `primary` | `#6B9EFF` | Acento primario luminoso con contraste WCAG AA sobre `#0B1326` |
| `onPrimary` | `#00174B` | Texto oscuro nítido sobre botones primarios en modo oscuro |
| `secondary` (`emerald`) | `#67D9C8` | Indicador de éxito, chip NFC activo y verificación correcta |
| `secondaryContainer` | `#123D39` | Superficie de éxito post-firma y píldora de CAN guardado |
| `ink` (`onSurface`) | `#EAF1FF` | Títulos y texto principal en modo oscuro |
| `ink-muted` (`onSurfaceVariant`) | `#A9B5CB` | Texto secundario y descripciones |
| `outline` | `#8D94A6` | Iconos secundarios y bordes neutros |
| `amber` (`warning`) | `#FBBF24` | Estados de espera y advertencias no destructivas |
| `error` | `#FFB4AB` | Mensajes de error legibles sobre superficies oscuras |

## 3. Typography Rules
- **Familia principal:** Sans-Serif humanista (`Roboto` / `System Sans` en Android, `Segoe UI` en Windows, `Liberation Sans` en PDFs).
- **Regla Anti-Slop de Monoespaciado:** Prohibido usar `FontFamily.Monospace` en encabezados o etiquetas de categoría (`FeatureScaffold`). Reservar `Monospace` únicamente para huellas SHA-256, números de serie X.509, direcciones IP y bloques hexadecimales.
- **Números Tabulares (`tnum`):** Activar siempre `fontFeatureSettings = "tnum"` en tamaños de archivo, contadores de páginas, progreso de lotes y longitudes de PIN/CAN.

## 4. Component Stylings & Shape Lock
- **Escala única de radios (`Shape Lock`):**
  - `8dp / 8px`: Campos de texto, combos, botones compactos de escritorio.
  - `12dp / 12px`: Botones principales móviles, tarjetas de documento y tarjetas Bento.
  - `16dp`: Tarjetas Hero (`Home`, `Credencial Digital`) y paneles principales.
  - `24dp` (superior): Hojas inferiores modales (`DnieOperationSheet`, `OnboardingGuideSheet`).
- **Tarjetas en Modo Oscuro:** Toda tarjeta sobre `canvas` (`#0B1326`) usa relleno `surface-1` (`#131B2E`) con un borde fino de `1dp` en `hairline` (`#2E3C56`) para definir su geometría sin depender de sombras negras.

## 5. Motion & Micro-Interactions
- **Física de resorte (`Spring Physics`):** Usar `spring(stiffness = 380f, dampingRatio = 0.82f)` en transiciones de contenido, pestañas y hojas modales.
- **Capa de dibujo (`Drawing Layer`):** Las animaciones repetitivas (como el radar NFC o el pulso de estado) se aplican mediante `graphicsLayer { scaleX; scaleY; alpha }` sin provocar recomposiciones de texto.
- **Respeto a Accesibilidad:** Si la escala de animación del sistema es `0f` (movimiento reducido), desactivar bucles infinitos y mostrar estados estáticos claros.

## 6. Do's and Don'ts
- **DO:** Mostrar el estado real de la operación NFC hasta que el usuario pueda retirar físicamente su DNIe.
- **DO:** Colapsar datos ya recordados (como el CAN) en píldoras compactas con acción `"Cambiar"`.
- **DON'T:** Nunca usar texto en mayúsculas con fuente monoespaciada como decoración sobre los títulos.
- **DON'T:** Nunca anidar listas con scroll vertical dentro de columnas con scroll vertical.
- **DON'T:** Nunca inventar progreso porcentual por documento cuando el motor subyacente está ejecutando un paso atómico indeterminado (PACE o TSA).
```

---

## 4. Eje 2: Animaciones Fluidas y Micro-interacciones (`DnieOperationSheet`, `NavGraph`, `SignerScreen`, `HomeScreen`)

Aplicando las reglas de `Taste Skill` y `Vercel Web Design Guidelines`, implementar un conjunto de utilidades de animación limpias en `app/src/main/java/pe/gob/reniec/dnie/ui/design/AetherMotion.kt`:

### 4.1. Especificación de `AetherMotion.kt` (Android Compose)
1. **Detección de movimiento reducido del sistema (`rememberReducedMotion()`):**
   - Leer `Settings.Global.ANIMATOR_DURATION_SCALE` (o `LocalConfiguration`) para que, si el usuario desactivó animaciones en Android (o en pruebas instrumentadas donde las animaciones se apagan), los pulsos infinitos queden en reposo (`scale = 1f`) sin consumir frames ni bloquear `ComposeTestRule.waitForIdle()`.
2. **Transición con Física de Resorte en `DnieOperationSheet.kt` (línea 38):**
   - Mejorar `AnimatedContent(targetState = phase)` con un `transitionSpec` basado en resorte suave:
     ```kotlin
     AnimatedContent(
         targetState = phase,
         transitionSpec = {
             (fadeIn(tween(180)) + scaleIn(initialScale = 0.96f, animationSpec = spring(stiffness = 400f, dampingRatio = 0.82f)))
                 .togetherWith(fadeOut(tween(120)))
         },
         label = "dnie-operation-phase"
     )
     ```
   - **Doble anillo de radar NFC en `AWAITING_TAG` y `TAG_ENGAGED`:**
     - En `AWAITING_TAG`: Dos ondas concéntricas en `primary` que se expanden suavemente (`1.0f -> 1.45f` con `alpha 0.35f -> 0f` en `graphicsLayer`).
     - En `TAG_ENGAGED` (*"DNIe detectado · mantenlo inmóvil"*): El color del icono y del anillo transiciona suavemente con `animateColorAsState` hacia **`secondary` (Esmeralda `#006A63` / `#67D9C8`)**, dando una señal visual inmediata de que el teléfono ya está leyendo el chip y no debe moverse.
     - En `COMPLETED`: El check verde (`Icons.Default.CheckCircle`) entra con un resorte elástico (`scaleIn` con `dampingRatio = Spring.DampingRatioMediumBouncy`).
3. **Micro-interacción táctil en `BentoCard` (`HomeScreen.kt`) y suavizado en `SignerControls.kt`:**
   - En `BentoCard` (`HomeScreen.kt` lín. 78): usar un `MutableInteractionSource` con `collectIsPressedAsState()` y `animateFloatAsState(if (pressed) 0.97f else 1f, spring(stiffness = 500f))` aplicado en `Modifier.graphicsLayer { scaleX = s; scaleY = s }` para que cada tarjeta Bento responda físicamente al tacto.
   - En `SignerControls.kt` (lín. 39): añadir `Modifier.animateContentSize(spring(stiffness = 400f, dampingRatio = 0.85f))` al contenedor de las pestañas (`Apariencia`, `Datos y TSA`, `Páginas y Titular`) y al desplegar los campos `Motivo` / `Lugar`.
   - En `NavGraph.kt` (lín. 122–125): mantener el cambio instantáneo entre las 5 pestañas inferiores, pero usar un `fadeIn(tween(140))` / `fadeOut(tween(100))` ligero para la navegación hacia pantallas secundarias (`settings`, ` Onboarding`).

---

## 5. Eje 3: Guía Interactiva de Primer Inicio (`OnboardingGuideSheet.kt` + Tarjeta de Bienvenida)

### 5.1. Diseño Seguro para el Usuario y para los Tests Instrumentados
* **Por qué no debe ser un popup automático bloqueante al abrir la app:** Si `MainActivity` abriera un `ModalBottomSheet` automáticamente en el primer arranque en frío, bloquearía las pruebas instrumentadas existentes (`HomeNavigationTest`, `RedesignContinuityTest`) que buscan botones en `HomeScreen`.
* **Solución elegante y no intrusiva:**
  1. En `AppearancePreferences.kt`, añadir `onboardingDismissed: StateFlow<Boolean>` (guardado en `SharedPreferences("appearance")` bajo la clave `"onboarding_dismissed"`, `false` por defecto) y `fun dismissOnboarding()`.
  2. **En `HomeScreen.kt`:** Cuando `!onboardingDismissed && !savedCan && !loaded`, mostrar debajo de la tarjeta principal una **Tarjeta Compacta de Primeros Pasos (`FirstRunWelcomeCard`)**:
     - Título: **"¿Primera vez usando tu DNIe en el teléfono?"**
     - Subtítulo: *"Aprende en 3 pasos dónde apoyar tu DNIe, cómo guardar tu CAN y cómo firmar en móvil o PC."*
     - Acciones: **`[ Ver guía rápida (3 pasos) ]`** (abre `OnboardingGuideSheet`) y **`[ Ocultar ]`** (`onDismissOnboarding`).
  3. **En `SettingsScreen.kt`:** Añadir también un botón **`"Ver guía de primer inicio"`** para que el usuario pueda consultarla en cualquier momento.

### 5.2. Contenido de los 3 Pasos de `OnboardingGuideSheet.kt`
Un `ModalBottomSheet` limpio con indicador de 3 puntos (`● ○ ○`) y transición `AnimatedContent`:

```
┌──────────────────────────────────────────────────────────┐
│  PASO 1 DE 3 · TU DNIe Y EL CÓDIGO CAN                   │
│  ┌────────────────────────────────────────────────────┐  │
│  │  [Ilustración vectorial de tarjeta DNIe]           │  │
│  │  • El código CAN tiene 6 dígitos y está impreso    │  │
│  │    en la esquina inferior derecha del frente.      │  │
│  │  • No es una clave secreta: sirve para abrir el    │  │
│  │    canal cifrado PACE con el teléfono.             │  │
│  │  [ Opcional: Guardar mi CAN de 6 dígitos ahora ]   │  │
│  └────────────────────────────────────────────────────┘  │
│  [ Saltar guía ]                          [ Siguiente → ]│
├──────────────────────────────────────────────────────────┤
│  PASO 2 DE 3 · CÓMO APOYAR EL DNIe EN EL SENSOR NFC      │
│  ┌────────────────────────────────────────────────────┐  │
│  │  [Ilustración de teléfono + DNIe al reverso]       │  │
│  │  1. Apoya el DNIe en el reverso superior/central.  │  │
│  │  2. Siente la 1.ª vibración al conectar y          │  │
│  │     MANTENLO INMÓVIL durante la firma.             │  │
│  │  3. Retíralo solo tras el DOBLE PULSO de éxito.    │  │
│  └────────────────────────────────────────────────────┘  │
│  [ ← Anterior ]                           [ Siguiente → ]│
├──────────────────────────────────────────────────────────┤
│  PASO 3 DE 3 · FIRMA EN EL MÓVIL O EN TU PC WINDOWS      │
│  ┌────────────────────────────────────────────────────┐  │
│  │  • En tu teléfono: Firma PDFs con sello a medida   │  │
│  │    y sello de tiempo RENIEC (PAdES-T).             │  │
│  │  • En tu PC Windows: Usa DNIe Suite o el lector    │  │
│  │    virtual para ReFirma por Wi-Fi, Tailscale o USB │  │
│  │    con detección automática en red.                │  │
│  └────────────────────────────────────────────────────┘  │
│  [ ← Anterior ]                    [ ¡Empezar a usar! ✓ ]│
└──────────────────────────────────────────────────────────┘
```
* **Valor práctico inmediato:** En el **Paso 1**, el usuario puede escribir y guardar su CAN de 6 dígitos ahí mismo (usando `CanStorage.saveCan`), de modo que cuando salga de la guía ya tenga el `SavedCanChip` listo para toda la app.

---

## 6. Eje 4: Unificación de Tema Claro y Oscuro en Android y Windows (`Theme.kt`, `AetherSwingTheme.kt`, `AetherControls.cs`)

1. **Alinear `Theme.kt` (Android) con la escalera de superficies oscura de `DESIGN.md`:**
   - Actualizar `DarkColorScheme` en `app/src/main/java/pe/gob/reniec/dnie/ui/theme/Theme.kt`:
     - `background = Color(0xFF0B1326)` (alineado con Windows)
     - `surface = Color(0xFF131B2E)` (alineado con Windows)
     - `surfaceContainerLowest = Color(0xFF080E1C)`
     - `surfaceContainerLow = Color(0xFF131B2E)`
     - `surfaceContainer = Color(0xFF1D2840)`
     - `surfaceContainerHigh = Color(0xFF243554)`
     - `surfaceContainerHighest = Color(0xFF2E4063)`
     - `outlineVariant = Color(0xFF2E3C56)`
   - En `DnieTheme` (`Theme.kt` lín. 114): sincronizar también `window.navigationBarColor = colorScheme.surface.toArgb()` y `isAppearanceLightNavigationBars = !resolvedDark` para que la barra inferior del sistema Android nunca quede blanca en modo oscuro ni negra en modo claro.
2. **Borde fino de elevación (`hairline`) en `FeatureSection` (`FeatureScaffold.kt`) y `BentoCard` (`HomeScreen.kt`):**
   - Añadir en `FeatureSection` y `BentoCard` un `border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))` y limpiar el *eyebrow* de `FeatureScaffold.kt` (línea 24) eliminando `fontFamily = FontFamily.Monospace` y `.uppercase()`.
3. **Pulido en Windows (`SuiteWindow.kt` y `MainForm.Layout.cs`):**
   - En `SuiteWindow.kt` (línea 57) y `MainForm.Layout.cs`: mostrar en el botón de tema un indicador claro (`"☾ Modo oscuro"` / `"☀ Modo claro"`) y añadir en el menú de ayuda o cabecera de `SuiteWindow.kt` un botón discreto **`"?"` (`Guía rápida`)** que muestre un diálogo Aether de 3 tarjetas explicando: *1. Conectar teléfono (automático en Wi-Fi/Tailscale), 2. Arrastrar PDFs y elegir plantilla, 3. Autorizar con PIN y mantener el DNIe inmóvil*.

---

## 7. Plan de Implementación R9 Paso a Paso para el Agente Local (`v3.1.2` / `APK 86`)

| Paso | Archivos objetivo | Qué implementar y cómo verificarlo |
| :--- | :--- | :--- |
| **Paso 1 · `DESIGN.md` Raíz, Tipografía Tabular (`tnum`) y Paridad Claro/Oscuro** | `DESIGN.md`, `AGENTS.md`, `app/.../ui/theme/Theme.kt`, `Type.kt`, `FeatureScaffold.kt`, `dnie-signing-ui/.../AetherSwingTheme.kt`, `tools/windows-manager/AetherControls.cs` | • Crear `DESIGN.md` en la raíz del proyecto con las 9 secciones del sistema Aether.<br>• Añadir `fontFeatureSettings = "tnum"` en `Type.kt` y quitar `FontFamily.Monospace` + `.uppercase()` del subtítulo de categoría en `FeatureScaffold.kt`.<br>• Alinear los colores de `DarkColorScheme` en `Theme.kt` (`#0B1326` / `#131B2E` / `#1D2840` / `#2E3C56`) con Windows y colorear también `navigationBarColor`. |
| **Paso 2 · Animaciones Fluidas con Física (`AetherMotion.kt`) y Doble Radar en `DnieOperationSheet`** | `app/.../ui/design/AetherMotion.kt`, `app/.../ui/components/DnieOperationSheet.kt`, `HomeScreen.kt`, `SignerControls.kt`, `NavGraph.kt` | • Crear `AetherMotion.kt` respetando `ANIMATOR_DURATION_SCALE` (movimiento reducido).<br>• En `DnieOperationSheet.kt`, añadir transición `spring` entre fases, doble anillo concéntrico de radar en `graphicsLayer` y transición de color a Esmeralda (`secondary`) en `TAG_ENGAGED`.<br>• Añadir micro-interacción táctil de presión en `BentoCard` y `animateContentSize` en `SignerControls.kt`. |
| **Paso 3 · Guía Interactiva de Primer Inicio en Android y Windows** | `app/.../ui/onboarding/OnboardingGuideSheet.kt`, `AppearancePreferences.kt`, `HomeScreen.kt`, `HomeRoute.kt`, `SettingsScreen.kt`, `dnie-signing-ui/.../SuiteWindow.kt` | • Crear `OnboardingGuideSheet.kt` con los 3 pasos (CAN con guardado directo opcional, posición NFC + vibraciones, y firma móvil/PC).<br>• Mostrar la tarjeta no bloqueante `FirstRunWelcomeCard` en `HomeScreen.kt` (cuando no se ha descartado) y el acceso `"Ver guía de primer inicio"` en `SettingsScreen.kt`.<br>• Añadir el botón `"Guía rápida"` en la cabecera de `SuiteWindow.kt`. |
| **Paso 4 · Versionado `3.1.2` (`APK 86`) y Verificación Completa** | `gradle.properties`, tests unitarios e instrumentados | • Ejecutar `python tools/bump-version.py --next-patch` (`3.1.2` / `86`).<br>• Verificar que pasen todas las pruebas JUnit JVM, `Windows Manager`, tests unitarios Android y las pruebas instrumentadas en ADB tras comprobar el par de APKs con `tools/android/verify-debug-pair.ps1`. |
