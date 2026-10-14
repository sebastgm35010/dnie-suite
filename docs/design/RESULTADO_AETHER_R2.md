# Rediseño Aether · entrega 3.1.0 / Android79

Fecha de revisión: 2026-10-14, America/Bogota. Especificación: [ESPECIFICACION_AETHER_Y_SELLOS_PDF.md](ESPECIFICACION_AETHER_Y_SELLOS_PDF.md). Código entregado: `81af17a`; checkpoint final de documentación/registro: `checkpoint/aether-redesign-complete-r2`.

## Cambios disponibles

Suite Windows adopta cabecera compacta, estado de conexión, documentos por tarjetas, arrastrar/soltar, inspector por secciones y diálogo independiente de red/rutas. La vista previa dibuja el sello real, con ocho zonas de redimensionado, cuatro manejadores visibles, guías magnéticas y presets. El nombre de ejemplo sirve solo para previsualizar; al firmar se usa el certificado real. QR de emparejamiento, selección de Recientes y diálogo PIN conservan sus correcciones anteriores.

Manager Windows presenta por separado servicio, publicación WinSCard y presencia de DNI; una tarjeta privada presente no significa lector publicado. Tailscale/LAN/USB tienen controles dinámicos y el selector RENIEC un único alternador. Se conserva su comportamiento operativo y la exclusión de diagnóstico en Release.

Android integra en el firmador los estilos, cuatro paletas, insignia, título, campos visibles, distintivo TSA, selección de páginas y miniatura instantánea. El editor ampliado usa el mismo modelo. La superficie de la página/miniatura es blanca; se conserva la preferencia claro/oscuro del usuario para la aplicación. No se rediseñan funciones ajenas al alcance aprobado ni se reintroducen passkeys.

Sellos: institucional, tarjeta, minimalista, rúbrica+datos y solo rúbrica. Windows GUI ofrece los tres estilos textuales solicitados; el motor compartido y Android también admiten las dos variantes manuscritas. Solo rúbrica exige imagen válida y dibuja exclusivamente esa imagen. Tipografía regular/negrita, división por palabras, guion solo para tokens que exceden una línea, nombre de hasta dos líneas, tamaño 5–11 pt y centrado vertical. Un rectángulo insuficiente se rechaza en vez de ocultar texto.

El QR del sello contiene un resumen público de los campos habilitados; no contiene secretos ni crea un servicio de verificación. El emblema es decorativo, no acredita a RENIEC. La píldora TSA depende de la opción de sellado; el motor existente solo publica el PDF después de verificar el sello de tiempo.

## Capas y archivos principales

| Capa | Archivos / responsabilidad |
| --- | --- |
| Contrato y geometría JVM puros | `dnie-signing-core/.../SignatureAppearance.kt`, `StampLayoutEngine.kt`, `StampPlacementGeometry.kt`: opciones compatibles, escena vectorial, ajuste de texto, QR, movimiento/snap/resize; sin Android/AWT/PDFBox. |
| Adaptador PDF Windows | `dnie-pdf-desktop/.../DesktopStampRenderer.kt`: escena a PDF y bitmap, fuentes compartidas por documento, crop/rotación, páginas objetivo y réplicas. `JvmPdfSigner.kt`: resolver página objetivo; callback criptográfico intacto. |
| Adaptador PDF Android | `dnie-pdf/.../ModernStampRenderer.kt`, `AndroidStampAppearance.kt`, `VisualStampRenderer.kt`: misma escena y estructura PDF. `PadesSignerEngine.kt`: integrar página/apariencia; callback criptográfico intacto. Se retiran el dibujado legado inalcanzable y `StampTextLayout.kt`. |
| Presentación Suite | `dnie-signing-ui/.../AetherSwingTheme.kt`, `AetherComponents.kt`, `SuiteWindow.kt`, `PreviewCanvas.kt`, `SuiteController.kt`: controles y trabajo de preview/tamaños fuera del EDT, debounce y descarte de resultados obsoletos. |
| Presentación Android | `app/.../ui/signer/SignerScreen.kt`, `StampPreviewCard.kt`, `PdfPlacementEditor.kt`. Adaptador de bitmap/PdfRenderer en `app/.../data/signing/AndroidStampPreview.kt`, trabajo IO acotado. |
| Presentación Manager | `tools/windows-manager/AetherControls.cs`, `MainForm.Layout.cs`, `MainForm.cs`, `PairingForm.cs`; pruebas sintéticas independientes en `layout-tests`. |

Las rutas abreviadas se resuelven mediante el registro SQL: `feature stamp_aether`, `feature desktop_aether` o `search StampLayoutEngine`. Los enum anteriores conservan sus ordinales; las propiedades añadidas tienen valores por defecto. El guardián de arquitectura también comprueba la pureza de `dnie-signing-core`.

## Estructura PDF y corrección normativa

Primera, última, página específica y todas las páginas usan una sola operación de firma. **Un campo de firma tiene un único widget**. Las páginas adicionales reciben anotaciones Stamp gráficas, sin `/Parent` ni `/V` de firma, incluidas antes del cálculo CMS y cubiertas por su ByteRange. La apariencia no multiplica firmas ni operaciones privadas.

El candidato `b48b745` / `checkpoint/aether-redesign-ready` usaba varios widgets y fue reemplazado antes de desplegar. La corrección sigue ISO 32000-2 §12.7.5.5: [especificación primaria](https://developer.adobe.com/document-services/docs/assets/5b15559b96303194340b99820d3a70fa/PDF_ISO_32000-2.pdf). DocMDP P=2 impide añadir esas réplicas: se rechaza antes de invocar el firmante; seleccionar una página mantiene la ruta existente. P=3 admite anotaciones.

Se corrige además la persistencia incremental: asignación explícita de la lista de anotaciones tras retirar el widget original y marcado de página/ancestros hasta catálogo. Las regresiones reabren los PDF y comprueban ausencia de widgets huérfanos, réplicas realmente persistidas, rotaciones/crop, firma CMS y conservación del prefijo original.

## Verificaciones y límites

| Módulo | Resultados | Fallos/errores | Omitidos |
| --- | ---: | ---: | ---: |
| signing-core | 17 | 0 | 0 |
| pdf-desktop | 17 | 0 | 0 |
| pdf Android | 26 | 0 | 0 |
| signing-desktop | 29 | 0 | 0 |
| signing-ui | 13 | 0 | 0 |
| desktop-orchestration | 9 | 0 | 0 |
| app unitarios | 81 | 0 | 1 |
| **Total** | **192: 191 pasan** | **0** | **1** |

El caso preexistente omitido es `RetiredCredentialDataTest.directoryLinkCannotDeleteVaultContents`: no se pueden crear symlinks en esta sesión Windows. No se modificó esa prueba. Manager: 24 comprobaciones de visibilidad/estado/producción pasan, sin llamadas al servicio ni operaciones de tarjeta; Release rechaza `--layout-test` y no incluye DebugForm.

Gradle final `release-candidate-r2.log`: BUILD SUCCESSFUL, APK debug/release, APK instrumental y distribuciones GUI/CLI generadas. .NET Release: cero advertencias/errores. Guardián de arquitectura pasa. Release Android no compila implementaciones ni puntos de entrada de diagnóstico y no es debuggable; licencia MIT QR y fuentes/avisos Desktop empaquetados. Identidad de firma del APK debug coincide con Android78.

Pruebas incluyen QR decodificado con ZXing independiente, privacidad de campos ocultos, texto largo, solo rúbrica, resize/snap, firmas sintéticas, rotaciones 0/90/180/270, una operación por PDF, DocMDP y interacción real con componentes Swing. Dos nuevas pruebas instrumentadas Android están **compiladas y no ejecutadas**: no hay teléfono ADB ni emulador instalado. No se afirma nueva firma con DNI ni acceso TSA real en este lote de pruebas. La confianza/TSA física de los motores anteriores conserva su evidencia histórica.

Revisión visual: galería vectorial de 20 combinaciones Windows, 12 combinaciones textuales Android, documentos sintéticos y renderizados de componentes Suite/Manager. Las tarjetas de salud de las capturas son simuladas. No son capturas de toda la pantalla. El PDF de galería es visual; no se debe presentar como documento firmado real.

## Invariantes y despliegue local

136 hashes de archivos protegidos coinciden con el inicio. Sin cambios en `dnie-core`, `dnie-protocol`, `dnie-signing-link`, `DesktopSuiteSession`, `WindowsBridgeGateway`, backend Bridge ni driver. Los callbacks criptográficos de ambos firmadores PDF coinciden con el baseline normalizado. CMS, PIN, autorización, PACE/SSC, TLS, verificación RSA, confianza y TSA siguen las rutas existentes.

Manager instalado mediante el instalador estándar en una versión nueva, con puente detenido antes de actualizar. Solo se reemplazan cuatro archivos de frontend; los otros 13 del paquete se copian del runtime instalado y conservan sus bytes, incluido servicio, control y selector. Identidad privada conservada; servicio Running; adaptador público `AVAILABLE / STOPPED / 0`. Cero comandos de instalación de driver. No se cambian firewall, confianza ni configuración de otros servicios.

| Artefacto | Ruta local |
| --- | --- |
| Suite actual / acceso del escritorio actualizado | `D:/DNIeToolchain/Builds/aether-redesign/signing-ui/install/dnie-signing-ui` |
| Manager instalado | `D:/DNIeBridgeApp/versions/29f121205ffe412a8e165bf404d49667/DNIeBridge.exe` |
| APK79 listo, **no instalado** | `D:/DNIeToolchain/AndroidPackages/dnie-evolution-3.1.0-v79-aether-debug.apk` |
| APK release de validación | `D:/DNIeToolchain/Builds/aether-redesign/modules/app/outputs/apk/release/app-release.apk` |
| Respaldos locales anteriores | `D:/DNIeToolchain/Checkpoints/pre-aether-redesign-20261013` |
| Resultados verificables en Google Drive montado | `G:/Mi unidad/BOT TRADER/DNI_CODEX/revision-aether-20261014` |

Android78 sigue siendo la última instalación comprobada. `AndroidPackages` conserva los dos APK debug más recientes; Android77 y copia de Android78 se resguardan en el checkpoint. Binarios/APK/identidades no se suben a Git. Se conserva el driver2.0.0.4; investigar suspensión sigue aplazado por el usuario y no es un resultado de este rediseño.

Licencias: QR Nayuki 1.8.0 MIT, [fuente](https://github.com/nayuki/QR-Code-generator); Liberation Sans 2.1.5 SIL OFL, [publicación oficial](https://github.com/liberationfonts/liberation-fonts/releases/tag/2.1.5). PDFBox no incluía la variante negrita requerida: se empaquetan ambas fuentes oficiales, licencia y hashes en `dnie-pdf-desktop/third-party`.

## Próxima validación física

Instalar Android79 conservando datos; ejecutar `AetherStampPresentationTest`; comprobar miniatura, cuatro colores, QR, solo rúbrica, página específica/última/todas y una firma PDF con TSA en el validador. Después, lote remoto Suite con PDF repetido en páginas y puente LAN/Tailscale/USB. No cerrar pendientes históricos de suspensión, USB físico o renovación por estos resultados sintéticos. UI Aether entregada; distribución y firma de producción siguen como trabajo separado.

## Reproducir

Desde un clon configurado como indica `docs/operations/GITHUB_Y_COMPILACION_MONOREPO.md`:

```powershell
./gradlew.bat :dnie-signing-core:test :dnie-pdf-desktop:test :dnie-pdf:testDebugUnitTest :dnie-signing-desktop:test :dnie-signing-ui:test :dnie-desktop-orchestration:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :dnie-signing-ui:installDist :dnie-signing-desktop:installDist
python tools/check_architecture.py
dotnet run --project tools/windows-manager/layout-tests/WindowsManager.LayoutTests.csproj -c Release
python tools/project-registry/registry.py feature stamp_aether
python tools/project-registry/registry.py check
```

Las capturas opcionales usan propiedades `visibleEvidence`/`uiEvidence` de las pruebas y rutas de salida externas. La instalación no forma parte de las pruebas: estas no requieren DNI real y no deben iniciar una firma privada. Registro automático consolidado: [AETHER_R2_RESULTADOS.json](../operations/AETHER_R2_RESULTADOS.json).
