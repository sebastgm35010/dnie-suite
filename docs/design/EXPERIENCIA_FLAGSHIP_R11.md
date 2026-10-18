# Experiencia Flagship R11 · 3.1.4 / APK 88 · Organic Motion y Fluid UI

R11 simplifica la primera capa de la aplicación e integra el pulido visual y de micro-interacciones **Organic Motion / Fluid UI**. Conserva íntegros los motores criptográficos de DNIe, PACE, firma CMS/CAdES/PAdES, TSA, bóveda cifrada y puente PC/SC; transforma radicalmente la experiencia interactiva elevándola a estándares de producto insignia tanto en Android (Jetpack Compose) como en Windows (Swing y WinForms).

---

## 1. Android (Jetpack Compose)

### 1.1. Micro-interacción universal `aetherPressScale`
- Se introduce e integra universalmente el modificador `aetherPressScale()` en todos los componentes interactivos (`AetherCard`, botones primarios, botones de acción en firma, tarjetas de credencial, fichas de bóveda y acciones de sesión).
- Proporciona retroalimentación táctil de compresión suave a 0.98x ante pulsación continuada (`press`), regresando fluidamente con resorte `aetherSpring` amortiguado sin oscilaciones residuales.
- Cumple rigurosamente la directiva de accesibilidad: si el sistema tiene activado movimiento reducido (`rememberReducedMotion()`), la escala permanece estrictamente en 1.0f.

### 1.2. Transiciones orgánicas y dimensionamiento adaptativo
- **`aetherAnimateSize()`**: Los contenedores con expansión dinámica adaptan su tamaño sin saltos geométricos, evitando el relayout abrupto al alternar contenido o revelar detalles técnicos.
- **`AnimatedContent` en pestañas**: La navegación interna en pestañas de Bóveda (`Mis archivos`, `Usar DNI`, `Respaldo`, `Opciones`) y Certificados (`Autenticación`, `Firma`) utiliza transiciones fluidas con fade y slide proporcional.
- **`Crossfade` en imágenes ICAO y sellos**: El intercambio visual de la fotografía ICAO y los sellos vectoriales de rúbrica emplea un desvanecimiento cruzado atenuado, eliminando parpadeos gráficos.
- **`animateItem()` en listas Lazy**: La reordenación, inserción y filtrado de fichas de la biblioteca en Bóveda y elementos de certificados utilizan transiciones nativas de elemento.
- **`VaultHero` con gradiente adaptativo**: La cabecera principal de la Bóveda incorpora un gradiente continuo que reacciona suavemente al tema claro/oscuro del sistema, preservando el contraste de contraste alto de las etiquetas.
- **Guardia `rememberReducedMotion()` en `SessionLinkConnector`**: La visualización activa de enlaces entre PC, Teléfono y DNI respeta el movimiento reducido congelando las ondas cinéticas en un estado estático nítido.
- **Copia con confirmación temporal**: Al copiar huellas dactilares SHA-256 o series de certificados, el botón transmuta temporalmente su icono y texto a confirmación de copiado antes de restaurar su estado original.

---

## 2. Windows (dnie-signing-ui Swing & tools/windows-manager WinForms)

### 2.1. Suite de firma (`dnie-signing-ui` Swing)
- **Desaceleración cúbica en `AetherSwingAnimator`**: Las interpolaciones de movimiento y transición de color aplican curvas de desaceleración cúbica (`cubic-bezier(0.2, 0.8, 0.2, 1.0)` / ease-out cubic), otorgando una sensación natural y física.
- **Micro-escala táctil 0.985x y halos de foco suaves**: Los botones e insumos interactivos reaccionan al presionado y ganancia de foco con un halo difuso azul acento (`#005FB8` / `#6B9EFF`) y reducción de escala geométrica limpia.
- **Rotación continua 0°–90° de chevrons**: Las secciones colapsables y paneles de opciones giran sus flechas indicadoras de manera suave y continua cuadro a cuadro.
- **Cápsulas de PIN animadas**: El diálogo de ingreso de PIN (`AetherPinDialog`) anima la pulsación de dígitos llenando cápsulas visuales que transicionan a tono verde esmeralda al completar la longitud requerida, sin revelar jamás los caracteres ni el secreto en memoria.
- **Scrollbar elástico y corregido**: La barra de desplazamiento oscura elimina artefactos de pintura histórica, con soporte elástico y contraste adaptativo según la superficie.
- **`PreviewCanvas` con sombra multicapa y 8 manijas**: El lienzo de vista previa PDF proyecta una sombra difusa multicapa en el sello seleccionado y expone 8 manijas de redimensionamiento con feedback visual de arrastre.
- **Restauración automática en copias**: Las acciones de copia en diálogos de emparejamiento QR y huellas incorporan temporizador de restauración visual tras 2.5 segundos.

### 2.2. Gestor de conexiones (`tools/windows-manager` WinForms)
- **`AetherScrollPanel` con doble buffer**: Panel de desplazamiento con búfer doble (`DoubleBuffered = true`) que erradica el parpadeo en ventanas de configuración y diagnósticos.
- **Interpolación suave en `HealthCard`**: Las tarjetas de estado de salud del Bridge y lecturas PC/SC interpolan cambios de estado de conexión y métricas sin refrescos bruscos.
- **Controles táctiles y foco**: `AetherControls.cs` homologa la estética Aether con micro-escala táctil, radios redondeados suaves y soporte accesible.

---

## 3. Invariantes de Seguridad y Límites Conservados

1. **Aislamiento criptográfico y de protocolo**: Ningún componente de animación o movimiento interactúa con la máquina de estados DNIe, PACE, canales seguros SM, contadores SSC ni secuencias APDU.
2. **Protección de secretos**: El PIN y el CAN siguen almacenándose en `CharArray` transitorios y limpiándose con ceros tras su uso. Las cápsulas animadas solo reflejan la longitud de caracteres digitados.
3. **Cero automatización de firma**: La copia de huellas o confirmaciones visuales no autoriza equipos PC ni desencadena firmas desatendidas. La firma requiere siempre confirmación explícita del usuario y presencia del DNIe.
4. **Accesibilidad y movimiento reducido**: Todas las animaciones respetan las preferencias del usuario (`reducedMotion` en Android y Windows), sustituyéndose por transiciones instantáneas o estados estáticos cuando está habilitado.
5. **Rendimiento decoupled**: Las animaciones se ejecutan exclusivamente en la capa de composición y renderizado visual, sin bloquear subprocesos de transporte ni cálculo criptográfico.

---

## 4. Verificación y Resultados de Prueba

- **Verificación de límites arquitecturales**: `tools/check_architecture.py` superado exitosamente sin violaciones entre capas.
- **Suites JVM y Compose**:
  - `dnie-core`: pruebas unitarias criptográficas y de contratos aprobadas.
  - `dnie-protocol`: pruebas de protocolo PACE, IAS y SM aprobadas.
  - `dnie-signing-ui`: 25 pruebas unitarias y de interfaz Swing aprobadas.
  - `app`: unitarias de interfaz y ViewModels aprobadas.
- **Herramientas de Windows**: Pruebas de layout de `tools/windows-manager` (52 comprobaciones) superadas.
- **Validación de paquetes Debug**: Verificación de pares APK app/androidTest (`tools/android/verify-debug-pair.ps1`) para compilación compatible.
