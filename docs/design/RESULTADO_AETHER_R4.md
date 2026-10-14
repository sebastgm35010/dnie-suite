# Aether R4 · Bridge y Suite entregados

2026-10-14, America/Bogota. Objetivo: [ALCANCE_AETHER_R4.md](ALCANCE_AETHER_R4.md); referencia externa conservada en [ESPECIFICACION_PULIDO_AETHER_R4.md](ESPECIFICACION_PULIDO_AETHER_R4.md). Código: `ced2b30`. Checkpoints: `checkpoint/pre-aether-r4`, `checkpoint/aether-r4-tested` y `checkpoint/aether-r4-complete`.

## Resultado de cada requisito

| Requisito | Implementación y evidencia |
| --- | --- |
| Fondo y esquinas Bridge | `AetherControls.cs` limpia en OnPaint la superficie del padre; ButtonBase activa Opaque, por lo que el OnPaintBackground anterior no bastaba. Prueba inicia un bitmap negro y comprueba la esquina pintada. Tarjetas también se limpian explícitamente. |
| Transporte activo | Propiedad IsSelectedSegment mantiene azul/texto blanco aunque el cambio de transporte esté deshabilitado; prueba los tres transportes y todos sus botones durante Running. |
| Inputs y superficies | Marcos AetherInputBox conservan controles nativos, foco/teclado y ComboBox editable; teléfono sin borde hundido. Paneles interiores con superficie blanca explícita. Capturas individuales Tailscale/LAN/USB revisadas. |
| Bandeja | Minimizar oculta la ventana; doble clic/menú la restaura. Cerrar a bandeja es optativo. Menú reutiliza acciones existentes de iniciar/detener/QR/selector; salir del gestor conserva el puente del servicio. Prueba minimizar, restaurar y cerrar optativamente sin iniciar host. |
| Probar lector | Botón consulta el servicio/estado existente con ReaderPublished de WinSCard; no transmite APDU, no resetea ni conecta tarjeta. No agrega código al driver/backend. Las pruebas de pintura usan estado sintético, no se presentan como prueba con tarjeta real. |
| Emblema | Circle y Path con relleno en escena pura; medallón, escudo lleno, check blanco y píldora DNIe en ambos adaptadores PDF. Galería vectorial y pruebas de integridad/rotaciones; emblema decorativo, no sello gubernamental. |
| Hoja de muestra | Canvas A4 interactivo antes del primer PDF; preview del sello real, drag/resize/snap/presets y drop de archivos. Prueba ventana vacía, cambio de estilo y bitmap recibido asíncronamente. |
| Controles Suite | AetherComboBox, AetherSpinner y scrollbar de 8 px sin botones tradicionales. Inspector y diálogo avanzado usan presentación común. |
| Preferencias | SuiteDesignPreferences guarda solo configuración pública en un único valor versionado, con debounce en executor, límites/fallback de lectura y cierre idempotente. Estilo, paleta, insignia, título, campos, motivo/lugar, TSA, objetivo/página/posición y nombre de muestra sobreviven a reapertura. No serializa PIN/CAN/privada/certificado/rúbrica; rutas/confianza de PC no se borran. Pruebas de codec, corrupción y ventana reabierta; pruebas usan nodos aislados. |
| Restablecer | Botón restaura el diseño permitido sin cambiar el emparejamiento; prueba de controles, geometría y preview actualizado. |
| Zoom y páginas | Zoom 50–300% relativo al ajuste; Ajustar vuelve a 100% y el visor desplaza cuando excede el viewport. Geometría normalizada de firma no cambia por zoom. Elegir Última página salta a ella al cargar, pero permite navegar a otras sin cambiar el destino de firma; regresión 3→2 con objetivo LAST. |
| Nombre/DNI tras lote | CompletedBatchPresentation lee fuera de EDT el certificado público ya exportado (máximo64 KiB), sin nuevas APDU ni cambios de protocolo. Actualiza el preview; prueba con certificado sintético exportado y ventana real. El certificado real sigue determinando los datos finales. |
| Abrir resultados | Doble clic y menú contextual abren el path devuelto por el lote o su carpeta, sin reconstruir nombres. I/O/lanzamiento fuera de EDT; prueba de doble clic con opener inyectado y ruta distinta al nombre original. |
| Arquitectura/producción | 136 hashes protegidos coinciden; callbacks de firma PDF intactos y sin diff en protocolo, núcleo CMS, enlace, sesión Suite, gateway, backend/driver. Release Android excluye clases/entradas debug y no es debuggable; Manager Release excluye DebugForm y rechaza --layout-test. Identidad debug Android coincide con APK79. |
| Entrega | Windows actualizado con instalador estándar y bridge detenido; dos archivos privados de identidad comparados antes/después, 13 archivos del runtime idénticos y cero comandos de driver. Acceso Suite actualizado. Android80 instalada con reemplazo y dos pruebas instrumentadas superadas. SQL/documentos y GitHub privado actualizados. |

## Pruebas y compilación

197 resultados JVM/Android unitarios: **196 pasan, 1 omitido preexistente**, cero fallos/errores. Desglose: signing-core18, PDF desktop17, PDF Android26, desktop29, UI17, orquestación9, app81. Omitido: `RetiredCredentialDataTest.directoryLinkCannotDeleteVaultContents`, por permisos Windows para crear symlink; no modificado.

Manager: **60 comprobaciones** pasan, sin llamadas al servicio ni operaciones de tarjeta en ese programa de pruebas. Incluyen pintura sobre bitmap negro, color seleccionado con botón deshabilitado, publicación/presencia independientes, inputs, bandeja y exclusión debug. El adaptador de integración sí se consulta por separado en modo solo lectura. Una prueba adicional pulsa el botón real «Probar lector en Windows» sobre el assembly de producción (hash idéntico al instalado): devuelve servicio disponible y lector publicado en Windows. No conecta tarjeta ni inicia host.

Android físico: `AetherStampPresentationTest` **2/2 pasa, 3,52 s**; cinco estilos, bitmap manuscrito/transparencia y selección de páginas/miniatura Compose. Aplicación principal abierta después; paquete auxiliar retirado. Son pruebas de presentación, sin DNI físico. Arranque normal comprobado; no se detecta fatal en el PID observado, lo cual no sustituye medición exhaustiva de rendimiento.

Gradle `acceptance-r2.log`: BUILD SUCCESSFUL (debug/release/test APK y distribuciones). Ajuste final de navegación/autocompletado: `ui-final.log`, BUILD SUCCESSFUL. .NET Release cero advertencias/errores. Guardián de arquitectura pasa. Pruebas PDF reabren y verifican CMS sintético, prefijo incremental, una firma/widget y réplicas de páginas; no se hacen nuevas solicitudes TSA reales ni firmas privadas del DNI durante esta entrega.

Se conserva el comportamiento de un widget y réplicas gráficas cubiertas por CMS. DocMDP P=2 continúa rechazando réplicas antes de la operación privada. El payload público del QR se mantiene igual; la prueba comprueba el literal anterior.

## Artefactos y estado local

- Suite / acceso del escritorio: `D:/DNIeToolchain/Builds/aether-r4/signing-ui/install/dnie-signing-ui`.
- Manager instalado: `D:/DNIeBridgeApp/versions/ee788ca28de64c8e89adf8f47ce7fc09/DNIeBridge.exe`.
- Android **3.1.0 / APK80 instalada**: `D:/DNIeToolchain/AndroidPackages/dnie-evolution-3.1.0-v80-aether-r4-debug.apk`.
- Respaldos: `D:/DNIeToolchain/Checkpoints/pre-aether-r4`. Se conservan APK79/80 en AndroidPackages; las versiones retiradas del directorio principal quedan en checkpoint.
- Evidencia verificable en Drive: `G:/Mi unidad/BOT TRADER/DNI_CODEX/revision-aether-r4-20261014`, con resultados, logs satisfactorios, capturas de componentes individuales y SHA256SUMS.
- JSON consolidado: [AETHER_R4_RESULTADOS.json](../operations/AETHER_R4_RESULTADOS.json). Servicio Running; host `AVAILABLE / STOPPED / 0` tras actualizar. Identidad conservada y runtime backend idéntico; driver2.0.0.4 sin reinstalar.

## Pendientes conservados

**QR enriquecido se difiere por instrucción explícita del usuario.** No se añade escáner de sellos ni se cambia el QR de emparejamiento. Su futura verificación debe separar cotejo de datos/hashes completos de validación PDF/CMS y no declarar auténtico un papel por leer un QR. Serie/fingerprint no sustituyen DER, cadena y revocación; hash del original no equivale al hash del firmado.

Queda la comprobación de uso con DNI real, TSA y lote remoto tras los cambios de presentación, además de los pendientes físicos anteriores. La suspensión del lector sigue aplazada. Persistencia visual Android adicional y distribución de producción son trabajo separado; no se incorporan biometría, passkeys ni cambios de bóveda.

## Reproducir

```powershell
./gradlew.bat :dnie-signing-core:test :dnie-pdf-desktop:test :dnie-pdf:testDebugUnitTest :dnie-signing-ui:test :dnie-signing-desktop:test :dnie-desktop-orchestration:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :dnie-signing-ui:installDist
dotnet run --project tools/windows-manager/layout-tests/WindowsManager.LayoutTests.csproj -c Release
python tools/check_architecture.py
python tools/project-registry/registry.py feature desktop_aether
python tools/project-registry/registry.py check
```

Las dos pruebas instrumentadas requieren instalar principal y auxiliar debug con la misma firma, teléfono desbloqueado y ejecutar únicamente `pe.gob.reniec.dnie.ui.AetherStampPresentationTest` mediante AndroidJUnitRunner. Las pruebas GUI no firman ni necesitan una tarjeta real. Las propiedades `visibleEvidence`/`uiEvidence` permiten sacar renderizados de componentes a una ruta externa; no capturan todo el escritorio.
