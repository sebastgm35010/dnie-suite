# Especificación Técnica de Pulido Visual Aether (Iteración R4): Corrección de Botones en DNIe Bridge, Utilidad Real del QR y Acabado Gráfico de Suite

## 1. Diagnóstico del Código Actual (`dnie-suite-main (3).zip` · Entrega R2/R3)

Tras auditar todos los módulos modificados en `dnie-suite-main (3).zip` (`dnie-signing-core`, `dnie-pdf`, `dnie-pdf-desktop`, `dnie-signing-ui`, `app` y `tools/windows-manager`), la base arquitectónica y las 191 pruebas unitarias/sintéticas más las pruebas instrumentadas en Android79 están bien estructuradas y respetan el aislamiento del backend y la norma ISO 32000-2 §12.7.5.5 (un único widget de firma y anotaciones `Stamp` para páginas adicionales).

Sin embargo, la inspección directa del código de renderizado revela **tres problemas concretos** que explican los defectos visuales que observas y la poca utilidad actual del QR en el sello:

---

## 2. Por qué aparecen "marcas negras en los botones" de `DNIe Bridge` (`tools/windows-manager`) y cómo corregirlo

### 2.1. Causa raíz en `tools/windows-manager/AetherControls.cs`
En `AetherControls.cs` (líneas 20–34), `AetherButton` hereda de `System.Windows.Forms.Button` y sobrescribe `OnPaintBackground` y `OnPaint`:

```csharp
internal sealed class AetherButton : Button {
    public AetherButton(){FlatStyle=FlatStyle.Flat;FlatAppearance.BorderSize=0;DoubleBuffered=true;}
    protected override void OnPaintBackground(PaintEventArgs e){e.Graphics.Clear(Parent?.BackColor??Aether.Background);}
    protected override void OnPaint(PaintEventArgs e){
        var g=e.Graphics;g.SmoothingMode=System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
        using var path=Aether.Rounded(new RectangleF(.5f,.5f,Width-1,Height-1),7);
        ...
        using var fill=new SolidBrush(color);g.FillPath(fill,path);
```

En WinForms (.NET 8 / GDI+), ocurren cuatro cosas simultáneas en este código:
1. **`ButtonBase` con `DoubleBuffered = true` no invoca `OnPaintBackground` antes de `OnPaint`:** La clase base `System.Windows.Forms.Button` gestiona internamente su fondo en `WM_PAINT` sobre un `BufferedGraphics` recién alojado en memoria (cuyos píxeles iniciales son `0x00000000`, **negro puro**). Al sobrescribir `OnPaint` sin llamar a `base.OnPaint(e)` ni hacer `e.Graphics.Clear(...)` dentro del propio `OnPaint`, **las 4 esquinas exteriores al arco redondeado (`radius = 7`) nunca se pintan y quedan en negro puro**, formando marcas negras en las esquinas de cada botón y un halo negro sucio en los píxeles semitransparentes del antialiasing.
2. **Lo mismo ocurre en `HealthCard.OnPaint` (línea 48) y `AetherCard.OnPaint` (línea 38):** `HealthCard` hereda de `Control` y no limpia el rectángulo completo con `Aether.Background` dentro de `OnPaint` antes de llamar a `g.FillPath(white, path)`.
3. **Paneles contenedores intermedios con color de fondo incorrecto (`MainForm.Layout.cs` líneas 28–52):** Dentro de `configuration` y `selectorCard` (que son `AetherCard` con fondo blanco `#FFFFFF`), los paneles hijos `config`, `segments`, `pcRow`, `phoneRow`, `usbRow`, `actions`, `selectorLayout` y `selectorActions` tienen el `BackColor` por defecto heredado del `Form` (`Aether.Background = #F8F9FF` en lugar de `Aether.Surface = #FFFFFF`). Por eso, incluso si un botón limpiara su fondo con `Parent.BackColor`, pintaría esquinas grisáceas dentro de una tarjeta blanca y los paneles rectangulares hijos muerden el borde redondeado de `AetherCard`.
4. **Pérdida del indicador de transporte activo cuando el puente está corriendo (`MainForm.cs` línea 105):** Cuando el puente pasa a `Running`, `method.Enabled` se pone en `false`, y `AetherButton.OnPaint` (línea 29) ejecuta `var color = Enabled ? BackColor : Aether.Container;`. Esto **borra el color azul primario del segmento seleccionado** (`Tailscale`, `Wi-Fi LAN` o `USB directo`), haciendo que los 3 botones se vean iguales y deshabilitados sin saber qué transporte está activo.

### 2.2. Solución exacta para `AetherControls.cs` y `MainForm.Layout.cs`

```csharp
internal static class Aether {
    // Resuelve el color real de superficie subiendo por la jerarquía de contenedores
    public static Color ResolveSurfaceColor(System.Windows.Forms.Control? control) {
        for (var current = control?.Parent; current != null; current = current.Parent) {
            if (current is AetherCard) return Surface;
            if (current is Form) return current.BackColor;
        }
        return Background;
    }
}

internal sealed class AetherButton : Button {
    private bool hover;
    public bool IsSelectedSegment { get; set; }

    public AetherButton() {
        SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint |
                 ControlStyles.OptimizedDoubleBuffer | ControlStyles.ResizeRedraw, true);
        UseVisualStyleBackColor = false;
        FlatStyle = FlatStyle.Flat;
        FlatAppearance.BorderSize = 0;
    }

    protected override void OnPaint(PaintEventArgs e) {
        var g = e.Graphics;
        // 1. Limpiar SIEMPRE todo el lienzo en OnPaint con el color real de la superficie padre
        Color parentSurface = Aether.ResolveSurfaceColor(this);
        g.Clear(parentSurface);
        g.SmoothingMode = SmoothingMode.AntiAlias;
        g.PixelOffsetMode = PixelOffsetMode.HighQuality;

        var rect = new RectangleF(1f, 1f, Width - 2.5f, Height - 2.5f);
        using var path = Aether.Rounded(rect, 8f);

        // 2. Conservar el color primario si es el segmento de transporte activo aunque esté deshabilitado durante ejecución
        Color fillColor = (!Enabled && !IsSelectedSegment)
            ? Aether.Container
            : (hover && Enabled ? ControlPaint.Light(BackColor, 0.08f) : BackColor);

        using (var fill = new SolidBrush(fillColor)) g.FillPath(fill, path);

        // 3. Borde sutil para botones secundarios o foco
        if (Focused || (!IsSelectedSegment && BackColor != Aether.Primary)) {
            Color borderColor = Focused ? Aether.Primary : Aether.Border;
            using var pen = new Pen(borderColor, Focused ? 1.5f : 1f);
            g.DrawPath(pen, path);
        }

        Color textColor = (!Enabled && !IsSelectedSegment)
            ? Color.FromArgb(115, 118, 134)
            : ForeColor;

        TextRenderer.DrawText(
            g, Text, Font,
            new Rectangle(6, 3, Width - 12, Height - 6),
            textColor,
            TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter |
            TextFormatFlags.EndEllipsis | TextFormatFlags.NoPrefix);
    }
}
```

Además, en `MainForm.Layout.cs`:
* Asignar `BackColor = Aether.Surface;` a todos los paneles internos dentro de `AetherCard` (`config`, `segments`, `pcRow`, `phoneRow`, `usbRow`, `actions`, `selectorLayout`, `selectorActions`).
* En `HealthCard.OnPaint` y `AetherCard.OnPaint`: llamar a `e.Graphics.Clear(Aether.Background);` en la primera línea de `OnPaint` antes de dibujar el `GraphicsPath`.
* Envolver los controles `ComboBox` (`pc`, `usb`) y `TextBox` (`phone`) en un contenedor `AetherInputBox` con borde redondeado `#DCE9FF` (`#004AC6` al recibir foco) y fuente `Segoe UI 10.5pt` para eliminar el borde hundido 3D clásico de Windows.

---

## 3. Evaluación Crítica: ¿Sirve de algo el QR de Verificación actual en el sello PDF?

### 3.1. Qué hace hoy el código (`StampLayoutEngine.kt` líneas 139–166)
Actualmente, cuando eliges `StampBadgeType.QR_VERIFICATION`, la función `publicQrPayload` genera un QR con esta cadena de texto:

```text
DNI:12345678|NOMBRE:JUAN PEREZ|FECHA:2026-10-14T09:30:00-05:00|PAdES
```

### 3.2. Veredicto técnico honesto: Hoy es redundante y no aporta valor de verificación
1. **Repite exactamente lo que ya está escrito al lado:** El DNI, el nombre y la fecha ya están impresos en texto legible a 3 milímetros a la derecha del propio QR. Escanear el QR con un teléfono solo muestra esos mismos 4 campos en texto plano.
2. **No vincula el sello con el certificado criptográfico ni con el documento:** Cualquier persona podría generar un QR con ese texto plano sin tener un DNIe. Aunque por la especificación PDF (ISO 32000-2) la apariencia visual (`PDAppearanceStream`) se escribe **antes** de calcular el cifrado RSA (`CMS`) —por lo que un QR dentro del PDF nunca puede contener los bytes finales de la firma RSA—, **sí tenemos disponibles antes de firmar datos criptográficos clave que hoy se están desperdiciando**:
   - El **Número de Serie del Certificado X.509** emitido por RENIEC (`certificate.serialNumber`).
   - La **Huella SHA-256 del Certificado del firmante** (`SHA-256(certificate.encoded)`).
   - El **Resumen SHA-256 del archivo PDF original** (`originalSha256` que ya calcula tu `BatchSigner` / `DesktopWorkspace`).

### 3.3. Cómo transformar el QR para que SÍ tenga utilidad real (Auditoría Impresa + Verificador en la App Android)

1. **Enriquecer el payload criptográfico público del QR (`StampCryptoMetadata`):**
   Pasar opcionalmente a `StampLayoutEngine` los metadatos públicos reales del certificado (`certSerialHex`, `certThumbprintShort`, `docOriginalSha256Short`, `tsaEnabled`):
   ```text
   DNIE1|DNI:12345678|NOM:JUAN PEREZ|FECHA:2026-10-14T09:30:00-05:00|CERT_SN:4A8F...|CERT_SHA:91B2C3D4E5F60718|DOC_SHA:7F83B1657FF1FC53|PERFIL:PAdES-T
   ```
   * **Por qué ahora sí sirve:**
     - **En documentos impresos en papel** (donde se pierde el contenedor digital `.pdf`), el papel conserva el **número de serie exacto del certificado de RENIEC (`CERT_SN`)**, la **huella del certificado (`CERT_SHA`)** y el **hash del documento original antes de firmar (`DOC_SHA`)**, datos que no caben en el texto legible del sello.
     - **En la vista previa (antes de tocar el DNI):** muestra un payload de previsualización marcado explícitamente como `MODO:VISTA_PREVIA`, y al firmar inserta el `CERT_SN` y `CERT_SHA` reales del certificado leído del DNIe.
2. **Lector / Verificador de Sello QR en la App Android (`DNIe Evolución`):**
   - Aprovechando que Android ya incluye el lector QR de cámara (`BridgeQrScanner.kt`) y el validador PKIX/CRL/OCSP (`dnie-validation-core`), añadir en la sección de Certificados/Verificación un botón **"Escanear sello QR de documento"** que decodifique `DNIE1|...` y muestre una tarjeta de verificación con el Titular, DNI, Fecha, Serie del Certificado RENIEC y Hash del documento original.

---

## 4. Defectos Visuales Restantes en `DNIe Suite` y en el Sello PDF (Plan de Mejora R4)

Al inspeccionar el código actual de `StampLayoutEngine.kt`, `SuiteWindow.kt`, `AetherComponents.kt` y `PreviewCanvas.kt`, se identifican **5 puntos específicos** donde la representación gráfica aún se ve rígida o descuidada:

### 4.1. El Emblema "Escudo DNIe" (`StampLayoutEngine.seal`, líneas 167–175) es un boceto rígido de líneas rectas
* **Problema en el código actual:**
  ```kotlin
  out+=StampElement.Box(x+side*.08f,y+side*.08f,side*.84f,side*.84f,side*.42f,null,color,.8f)
  out+=StampElement.Path(listOf(.34f to .25f,.66f to .25f,.64f to .48f,.50f to .58f,.36f to .48f)...,color,.9f,true)
  ```
  - Primero: un `Box` de ancho `0.84` con radio `0.42` (`0.84 / 2`) debería ser un círculo, pero en la línea 170 el segundo anillo tiene ancho `0.72` y radio `0.36`, y el escudo interior son 5 segmentos rectos sin relleno (`Path` abierto/cerrado con solo `stroke`), dejando un aspecto de alambre ("wireframe") muy pobre.
  - Segundo: `StampElement.Path` no soporta relleno (`fill`) ni curvas suaves.
* **Mejora visual (Emblema Institucional de Alta Precisión):**
  - Añadir `fill: Int? = null` a `StampElement.Path` y `StampElement.Circle(cx, cy, r, fill, stroke, thickness)`.
  - Diseñar una **roseta/medallón institucional con fondo suave (`colorTheme.backgroundRgb` o `0xE5EEFF`)**, anillo exterior sólido fino, **escudo relleno con el color de acento (`colorTheme.accentRgb`)**, check de verificación en blanco (`0xFFFFFF`) de trazo grueso en el centro del escudo, y una píldora inferior con el texto `"DNIe"` en negrita.

### 4.2. En `DNIe Suite` (`SuiteWindow.kt`), el lienzo central se ve vacío y muerto cuando no hay PDF cargado
* **Problema actual:** En `PreviewCanvas.kt` (línea 79), cuando abres la aplicación y aún no has seleccionado un PDF (`page == null`), el centro es un rectángulo gris/azul vacío con un texto pequeño en la esquina `(28, 48)`: `"Selecciona un PDF para colocar tu firma"`. Además, si el usuario cambia el estilo, color o insignia a la derecha sin haber abierto un PDF, **`renderStamp()` hace `if (canvas.image == null) return`** (línea 302 de `SuiteWindow.kt`), por lo que no ve ningún cambio visual.
* **Mejora visual (Hoja A4 Virtual de Muestra + Estado Vacío Atractivo):**
  - Cuando `image == null`, `PreviewCanvas` debe dibujar una **hoja A4 blanca de demostración con sombra suave**, líneas simuladas de documento en gris muy tenue (`#EFF4FF`) y un cartel central elegante (*"Arrastra un PDF aquí o pulsa + Añadir archivos · Puedes diseñar y mover tu sello desde ahora"*), permitiendo que **el sello real se renderice y se pueda mover/personalizar sobre esa hoja de muestra incluso antes de cargar el primer PDF**.

### 4.3. Controles `JComboBox`, `JSpinner` y `JScrollPane` en `SuiteWindow.kt` siguen con apariencia vieja de Java Metal
* **Problema actual:** Aunque los botones (`AetherButton`) y chips (`AetherToggleChip`) tienen esquinas redondeadas Aether, los 4 desplegables (`Formato`, `Estilo`, `Insignia`, `Aplicar en`) y el selector numérico `Página` (`JSpinner`) conservan el borde cuadrado gris y el botón de flecha biselado de Java Metal de los años 2000. Además, los `JScrollPane` muestran barras de desplazamiento grises anchas tradicionales.
* **Mejora visual en `AetherSwingTheme.kt` y `AetherComponents.kt`:**
  - Crear `AetherComboBox<T>`: personaliza `BasicComboBoxUI` para dibujar un contenedor redondeado (`arc = 10 px`, fondo `#FFFFFF`, borde `#DCE9FF`, hover `#004AC6`) con una flecha vectorial tipo chevron (`▾`) dibujada con `Graphics2D` anti-aliased y lista desplegable con padding amplio (`EmptyBorder(6, 10, 6, 10)`).
  - Crear `AetherScrollBarUI`: barra de scroll minimalista de `6 px` de ancho con *thumb* redondeado en `#CBDBF5` (hover `#737686`) sin botones de flecha superior/inferior.

### 4.4. Persistencia de Preferencias Visuales del Sello en `SuiteWindow.kt` y `SignerViewModel.kt`
* **Problema actual:** `SuiteWindow.kt` solo guarda en `Preferences` las rutas `pc`, `phone`, `workspace` e `identity` (línea 136). No guarda el `Estilo`, `Color`, `Insignia`, `Título`, `Motivo`, `Lugar`, `TSA`, `Aplicar en` ni la posición `placement`. Cada vez que abres la Suite tienes que volver a configurar tu sello desde cero.
* **Mejora funcional:** Guardar y restaurar automáticamente en `prefs` (`pe/gob/reniec/dnie/suite-ui`) todas las opciones de `SignatureAppearance`, `reason`, `location`, `pageTarget`, `reniecTimestamp`, `previewName` y las coordenadas de `placement`, e incluir un botón discreto `"Restaurar diseño por defecto"`.

### 4.5. Funciones adicionales de alto valor en `DNIe Suite` y `DNIe Bridge`
1. **En `DNIe Suite` (`SuiteWindow.kt`):**
   - **Zoom y Ajuste de Página en el Visor PDF:** Botones `[ - ] [ 100% / Ajustar ] [ + ]` en la barra superior del `PreviewCanvas` para inspeccionar de cerca cómo queda el texto pequeño del sello sobre documentos densos.
   - **Botón "Leer mi nombre y DNI desde el teléfono" o autocompletado tras la primera firma:** Cuando se completa una firma o al conectar con Android, si el teléfono ya conoce el titular del último certificado leído o tras firmar el primer lote, actualizar automáticamente el nombre del titular en la vista previa para que no diga `"NOMBRE DEL TITULAR"`.
   - **Acciones rápidas por documento en la lista izquierda:** Permitir hacer doble clic o clic derecho en un documento ya `"✓ Firmado"` de la lista para **abrir directamente el PDF firmado resultante** en el visor del sistema o ver su ubicación en el Explorador.
2. **En `DNIe Bridge` (`tools/windows-manager`):**
   - **Minimizar a la Bandeja del Sistema (`NotifyIcon`):** Opción o cierre opcional a la bandeja de entrada de Windows con icono de estado en vivo y menú rápido (*Iniciar puente*, *Detener puente*, *Mostrar QR*, *Activar selector RENIEC*, *Salir*), ideal porque el puente se usa en segundo plano mientras firmas en ReFirma o navegadores.
   - **Botón "Probar lector en Windows" (Diagnóstico rápido en 1 clic):** Un botón secundario que verifique al instante si `SCardListReaders` ve el lector y si el servicio responde, mostrando un check claro al usuario sin tocar la tarjeta.

---

## 5. Hoja de Ruta para el Agente Local (Secuencia R4)

| Paso | Archivos objetivo | Qué debe implementar el agente local |
| :--- | :--- | :--- |
| **1. Corregir marcas negras y pulir `DNIe Bridge`** | `tools/windows-manager/AetherControls.cs`, `MainForm.Layout.cs`, `MainForm.cs` | • Llamar siempre a `e.Graphics.Clear(Aether.ResolveSurfaceColor(this))` dentro de `AetherButton.OnPaint`, `HealthCard.OnPaint` y `AetherCard.OnPaint`.<br>• Asignar `BackColor = Aether.Surface` a todos los `TableLayoutPanel`/`FlowLayoutPanel` hijos de `AetherCard`.<br>• Conservar resaltado en `Aether.Primary` el botón del segmento de transporte activo (`IsSelectedSegment = true`) incluso cuando `method.Enabled == false` durante una sesión en curso.<br>• Estilizar las cajas de entrada (`pc`, `phone`, `usb`) con borde redondeado Aether. |
| **2. Emblema relleno de alta precisión y QR con metadatos de certificado** | `dnie-signing-core/.../StampLayoutEngine.kt`, `DesktopStampRenderer.kt`, `ModernStampRenderer.kt` | • Añadir soporte de relleno (`fill`) en `StampElement.Path` / `Circle` para que el emblema `SEAL_VECTOR` dibuje un medallón circular perfecto con fondo tintado, escudo relleno en `accentRgb` y check blanco.<br>• Enriquecer `publicQrPayload` con metadatos públicos verificables (`CERT_SN`, `CERT_SHA`, `DOC_SHA`, `PERFIL:PAdES-T/B`) cuando estén disponibles al firmar. |
| **3. Hoja de muestra en vivo, `AetherComboBox` y persistencia en `DNIe Suite`** | `dnie-signing-ui/.../AetherComponents.kt`, `AetherSwingTheme.kt`, `PreviewCanvas.kt`, `SuiteWindow.kt` | • Dibujar en `PreviewCanvas` una página A4 virtual cuando `image == null` para que la vista previa del sello funcione desde el segundo cero sin requerir abrir un PDF primero.<br>• Reemplazar los `JComboBox` y `JScrollBar` Metal por `AetherComboBox` y `AetherScrollBarUI`.<br>• Persistir en `Preferences` el estilo, color, insignia, campos visibles, motivo, lugar y posición del sello, y permitir abrir un documento firmado con doble clic en la lista. |
