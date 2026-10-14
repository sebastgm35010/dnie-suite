# Personalización del sello, respuesta táctil y continuidad R7

2026-10-14 · Base R6 / APK82 / commit `d358669`. Checkpoint previo `checkpoint/pre-personalization-r7`. Especificación recibida conservada en [ESPECIFICACION_R7_RECIBIDA.md](ESPECIFICACION_R7_RECIBIDA.md).

2026-10-14 · Personalización R7 / APK83 (3.1.0) compilada debug/release; no instalada: ADB suspendido hasta nuevo aviso del usuario. APK82 sigue siendo la última instalada. Sello con fondo transparente, insignias check/candado, fecha/densidad/colores, plantillas y esquinas al6%; diseño Android persistente, navegación y acciones Mi DNIe. Respuesta táctil por eventos NFC con ajuste de usuario. Windows añade PNG de rúbrica, DnD visual y tema claro/oscuro. 241 JVM pasan/1 omitida por permisos Windows y 44 comprobaciones Manager pasan. Interfaces Windows y accesos actualizados; servicio, driver, identidad y motores criptográficos conservados. Pruebas Android y firma física R7 pendientes.

## Decisiones contrastadas con el código

- R6 permanece cerrado: descubrimiento, TLS, huella/token, USB y política de no repetir firmas no se modifican.
- Los enums históricos conservan sus ordinales; colores e insignias nuevos se añaden al final. QR histórico sigue interno; no reaparece en los selectores.
- R6 ya tenía TransferHandler sobre lista/canvas. R7 añade DropTarget con resaltado al entrar/salir y filtrado PDF; Añadir archivos mantiene los demás formatos CMS.
- La proyección de certificado hacia la vista previa no estaba conectada en MainActivity. R7 la conecta para PDF y lotes, solo con certificado de firma; borra la proyección previa si una lectura nueva no lo incluye. No realiza nuevas APDU ni confunde AUT con FIR.
- Los estados Success/Unlocked propuestos no existen en esas clases. Se emiten eventos tipados desde las ramas reales de finalización; no se deducen de textos localizados ni de recomposiciones.
- PNG de rúbrica nunca se guarda en preferencias. Se conserva la elección del estilo en diseños v2 y se exige cargar/leer de nuevo la imagen al reabrir. Preferencias v1 con estilos antes no disponibles mantienen su fallback histórico.
- Limpieza de blanco usa todos los canales RGB>235, conservando el alfa y evitando borrar colores claros de un logo por su luminancia.

## Capas y comportamiento

### Motor compartido

`SignatureAppearance`, `StampLayoutEngine`, `StampDesignCodec` y `StampPlacementGeometry` permanecen JVM puros. Incluyen TINTED/blanco/transparente, borde independiente, CHECK_SEAL/LOCK_SEAL, índigo/dorado, fecha Perú/corta/ISO con zona Lima, tres densidades y cuatro plantillas. El preset Visto bueno muestra solo nombre, DNI y fecha corta; conserva motivo/lugar/TSA del trabajo, pero oculta los campos que no corresponden. Anclajes a6% se limitan a la página si el sello es demasiado grande.

La transparencia elimina la caja opaca principal; los trazos y medallones conservan su propia tinta. Los adaptadores PDF pintan la escena existente; no se cambian CMS, ESSCertIDv2/IssuerSerial, TSA, verificación de firma devuelta, DocMDP ni ByteRange.

### Android

`AndroidStampPreferences` usa un codec común y escritura ordenada/conflada fuera de composición. Cache de último diseño evita leer una preferencia anterior cuando se cambia de documento inmediatamente. No serializa PIN/CAN, imagen DG7, certificado ni metadata del QR. Se restauran diseño/campos/posición/TSA, limitando página al PDF nuevo.

`ReadingFeedbackEvents` está en dominio; Activities recogen eventos en STARTED y `ReadingFeedback` aplica el adaptador Vibrator. Eventos sin replay evitan pulsos al volver a la pantalla. Inicio65ms, avance25ms (acotado100ms), éxito doble45/110ms y error doble70/85ms, con pausas y amplitudes si el dispositivo las soporta; patrón sin amplitudes en el fallback. Configuración permite desactivarlos.

Certificados e ICAO avisan al terminar y cerrar el lector; bóveda tras cerrar tarjeta/limpiar secretos. Lote Android confirma éxito solo con todos firmados y avisa error en parcial. Suite cuenta firmas exitosas por lote, no confunde una espera sin DNI con éxito; eventos de preparación/pérdida de tarjeta separados. Bridge avisa disponibilidad PACE, cierre limpio y errores; no interpreta cada APDU como firma completada. Cancelación local no anuncia éxito.

Interfaz: dos bloques plegables de estilo/contenido, ubicación y editor a pantalla completa; presets, fondos, densidad, fechas, bordes, cuatro insignias visibles; acciones Firmar PDF/Bóveda/Puente PC/Firma remota. Barra inferior marca Conexión PC en Activities separadas.

### Windows

`StampImageLoader` valida PNG<=512KiB y metadata antes de decodificar, dimensiones<=4096 y4MP; usa memoria, sin cache temporal ImageIO. Limpieza/decodificación fuera del EDT. Estilos Rúbrica+datos y Solo rúbrica disponibles; imagen mantenida en memoria durante la sesión. Antes de firmar se espera a terminar su carga.

`PdfDropSupport` resuelve interacción nativa, mientras el controlador prepara archivos y conserva límite de lote/preflight. Secciones plegables, preferencias compartidas y tema claro/oscuro persistente. AetherSwingTheme y Aether C# aplican paleta Android sin cambiar colores del documento PDF ni estado de conexión.

La interfaz Bridge R7 trabaja con el servicio R6 mediante el contrato existente. Se actualizan accesos directos de usuario y se reemplaza solo el gestor inactivo tras comprobar puente Stopped/CardPresent=false. El servicio, driver y almacenes de confianza permanecen en su ubicación anterior, sin reinstalación ni elevación.

## Resultados verificables

- 242 casos JUnit en61 suites:241 pasan,0 fallos,0 errores y1 omitido: `RetiredCredentialDataTest` con enlace de directorio Windows sin privilegio. Pruebas de todos los módulos afectados y apps de escritorio.
- Casos nuevos: escena transparente/bordes, insignias/fechas/densidades y límites, compatibilidad de preferencias/ordinales y exclusión de datos transitorios, presets/esquinas, PNG y alfa/límites, tema/controles desktop, PDF de dos páginas con contenido inferior conservado/una firma CMS verificada, eventos sin replay/lote parcial.
- Manager44 comprobaciones Release,0 llamadas de servicio/tarjeta en pruebas de presentación. Incluye paleta oscura/restauración. Capturas de ventanas propias verificadas.
- APK83 debug/release y auxiliar instrumental compilados. Mismo applicationId/firmante que82. Release sin diagnósticos/cotejador retirado. **No se ejecutó ADB ni se instaló APK83 o auxiliar.**
- PDF sintético R7 con candado índigo y transparencia: CMS verificado,1 operación de firma para2 páginas, widget único y réplica visual. Certificado sintético, sin identidad RENIEC; no acredita confianza TSL ni una firma DNI física nueva.
- Windows interfaces compiladas/entregadas sin cambiar servicio/driver. El estado previo del puente era Stopped, CardPresent=false y lector publicado. No se realizaron operaciones NFC.

## Rutas

```text
D:/DNIeToolchain/AndroidPackages/
  dnie-evolution-3.1.0-v82-network-r6-debug.apk
  dnie-evolution-3.1.0-v83-personalization-r7-debug.apk
D:/DNIeToolchain/Builds/personalization-r7/
  signing-ui/install/dnie-signing-ui/
  signing-desktop/install/dnie-signing-desktop/
  native-package/Release-7300fff641da4c74825616edda158f9e/
  visual-suite/
  visual-manager/
  visual-pdf/r7-transparente-sintetico-firmado.pdf
G:/Mi unidad/BOT TRADER/DNI_CODEX/revision-personalizacion-r7-20261014/
  INFORME_LOCAL_R7.md
  resultados.json
  evidencias/
  SHA256SUMS.json
```

APK81 se conserva en D:/DNIeToolchain/Checkpoints/pre-personalization-r7; directorio de entrega mantiene solo82+83.

## Pendiente al volver ADB

Instalar APK83 y ejecutar pruebas de navegación/preferencias/háptica en teléfono. Comprobar percepción real y respeto al ajuste/sistema, PDF/lote PAdES-T con DNI/TSA, PNG Windows en firma real y arrastre desde Explorer. Mantener pendientes R6: firma/lectura por LAN/Tailscale/USB y cambio físico de red. No inferir rendimiento real Android por pruebas JVM; medir escritura/desplazamiento cuando vuelva el dispositivo. Driver suspensión continúa aplazado salvo reproducción real.
