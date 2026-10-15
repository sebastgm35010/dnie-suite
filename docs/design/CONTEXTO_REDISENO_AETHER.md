# Rediseño Aether completo · contexto de ejecución

2026-10-15 · R10 / Android 3.1.3 (APK 87) instalada por ADB sin borrar datos, misma firma y auxiliar compatible. Selector segmentado compartido, Bento/credencial/guía refinados, CAN compacto y PC vinculada con acciones fijas. Suite incorpora diálogo PIN propio y hover finito; Bridge mejora cabecera y tarjetas de salud. 254 pruebas JVM aprobadas, 1 omisión preexistente, 52 comprobaciones Manager y 14 instrumentadas Android aprobadas. Comparación puntual de cuadros por VSYNC: P95 8,88→8,94 ms; no garantiza 60 FPS constantes. Motores, servicio y controlador sin cambios. Pendientes pruebas físicas NFC/TSA/USB/suspensión y documentos representativos. Ver `docs/design/ACABADO_FLUIDEZ_R10.md` y `docs/operations/ACABADO_R10_RESULTADOS.json`. Los párrafos siguientes conservan historial.

2026-10-15 · R9 / Android3.1.2 (APK86) instalada por ADB sin borrar datos y con firma compatible; auxiliar actualizado con la misma firma. DESIGN.md, paleta compartida claro/oscuro, categorías sans y números tabulares; radar NFC/resortes con movimiento reducido y guía optativa CAN/NFC/móvil-PC. Suite/gestor Windows compilados y accesos actualizados. 252 pruebas JVM pasan/1 omitida preexistente,48 comprobaciones Manager y8 instrumentadas Android pasan. Motores, servicio y driver sin cambios. NFC/TSA/háptica físicos y regresión USB/suspensión pendientes. Ver `docs/design/SISTEMA_DISENO_GUIA_R9.md` y `docs/operations/DISENO_R9_RESULTADOS.json`. Los párrafos siguientes conservan historial.

2026-10-14 · Continuación QR R5: usuario autoriza QR tras cierre R4. Formato público DNIEQR1, huellas completas derivadas en ambos motores, muestras no cotejables y pantalla Android separada. 207 JVM pasan/1 omitida, 6 Android pasan; APK81 instalada, Suite/CLI distribuidas. Cotejar no valida firma ni papel. Ver [QR_COTEJO_R5.md](QR_COTEJO_R5.md).

Inicio: 2026-10-14, America/Bogota. Especificación autorizada: ESPECIFICACION_AETHER_Y_SELLOS_PDF.md. Objetivo completo: sellos vectoriales compartidos Android/Windows, modelos/layout puros, preview WYSIWYG y editor con selección de páginas/presets, Suite Swing Aether y Manager WinForms Aether, controles/miniatura Android, pruebas/builds y revisión visual. Usuario trabajará asincrónicamente: resolver decisiones rutinarias sin depender de respuesta.

Checkpoint previo: checkpoint/pre-aether-redesign-20261013. Suspensión driver queda aplazada; conservar 2.0.0.4. No modificar motores criptográficos, transporte, autorización ni driver. Preservar sesión privada Suite, datos/identidades existentes, rechazo sin replay, perfiles PAdES B/T y separación motores/UI. No reintroducir passkeys.

## Entregables y auditoría

- [x] Modelos de apariencia/color/insignia/páginas con compatibilidad de llamadas existentes.
- [x] Layout puro compartido: palabras, guion para tokens largos, ajuste 5–11 pt, centrado, geometría/colores.
- [x] Sellos vectoriales en ambos PDF: institucional, tarjeta, minimalista, rúbrica+datos y solo rúbrica; fuentes sans regular/negrita; emblema, QR público y badge TSA.
- [x] Primera/última/específica/todas las páginas: una firma criptográfica con un widget y réplicas gráficas firmadas, sin multiplicar operaciones DNI.
- [x] Suite: tema/componentes, cabecera, red/rutas modal, tarjetas de archivos/drop, inspector, modos/TSA, campos, pie.
- [x] Windows preview real con layout idéntico, sombra, cuatro esquinas/bordes, cursores, snap y presets/páginas.
- [x] Android: estilo/color/insignia/páginas/título, miniatura instantánea y editor coherente, conservar claro/oscuro del usuario.
- [x] Manager: tres tarjetas de salud independientes, transporte segmentado/filas dinámicas, selector alternador.
- [x] Regresiones significativas, arquitectura, compilación Android/Windows, paquetes producción y evidencias visuales sintéticas.
- [x] Cierre de despliegue Windows, registro SQL final y documentación de resultados.

No confundir vistas sintéticas con firma DNI nueva ni con suspensión real. QR del sello contiene resumen público, no sustituye validación criptográfica. Preview de nombre opcional no altera CN real firmado.

## Estado comprobado antes del despliegue

Código implementado completo. Gradle conformance-final.log: debug/release/test APK y distribuciones Suite/CLI compiladas. 192 tests JVM/Android unitarios contabilizados: 191 pasan y 1 caso preexistente de symlink de migración se omite por capacidad de plataforma. Manager Release compila sin advertencias: 24 comprobaciones de diseño/producción pasan. No existe teléfono ADB conectado ni emulador instalado: dos pruebas instrumentadas nuevas están compiladas, todavía no ejecutadas. PDF sintético se comprueba criptográficamente; no se afirma firma DNI nueva.

Windows/Android comparten el layout puro; adaptadores PDF dibujan la escena vectorial. Fuentes Desktop se comparten entre páginas; el campo de firma tiene un único widget y las demás páginas reciben réplicas Stamp incluidas en el ByteRange antes de calcular CMS. UI Swing usa previews asíncronas y debounce, renderizador de lista reutilizable y solo datos de ejemplo hasta tener el certificado. Los datos finales vienen del certificado real. QR es resumen público con campos visibles, no un verificador autónomo.

Runtime de servicio/transportes conservado byte por byte en paquete local nuevo; solo cuatro archivos de frontend Manager se reemplazan. Deploy aún no ejecutado al registrar este checkpoint. Driver intacto. La base anterior/identidades no se eliminan.

## Corrección normativa antes del despliegue

Checkpoint aether-redesign-ready/b48b745 queda superado: la revisión ISO 32000-2 §12.7.5.5 detectó que un campo de firma no puede tener múltiples anotaciones. Se sustituye por un widget de firma y réplicas gráficas Stamp en páginas adicionales, sin /Parent ni /V de firma. El mismo CMS cubre esas réplicas. Si DocMDP solo permite llenar/firmar (P=2), repetir anotaciones se rechaza antes de la operación privada; elegir una sola página conserva la ruta permitida. Se actualizan las regresiones de ambas plataformas y se recompilan paquetes. Referencia primaria: https://developer.adobe.com/document-services/docs/assets/5b15559b96303194340b99820d3a70fa/PDF_ISO_32000-2.pdf .

La escritura incremental actual marca los ancestros de páginas para persistir réplicas no enlazadas al campo de firma. Se comprueban posiciones renderizadas y ausencia de anotaciones huérfanas, además de CMS. Miniaturas Android usan superficie de papel blanco para conservar contraste con temas claro/oscuro.

## Cierre R2

2026-10-14: compilación final release-candidate-r2.log satisfactoria, 191 pruebas pasan/1 omitida preexistente, 24 comprobaciones nativas pasan. Manager instalado con identidad y 13 binarios/recursos operativos idénticos; servicio Running, host STOPPED. Suite y acceso del escritorio actualizados. Android79 exportada con la misma identidad debug; no instalada por falta de ADB. Dos pruebas instrumentadas compiladas, sin ejecutar. Guardián de arquitectura y producción Android pasan; 136 hashes protegidos y callbacks criptográficos coinciden. Los estados anteriores de predespliegue de este archivo son historia. [Resultado definitivo](RESULTADO_AETHER_R2.md), [registro JSON](../operations/AETHER_R2_RESULTADOS.json).

## Continuación Android R3

2026-10-14 · Aether Android79 / 3.1.0 instalada por ADB con reemplazo sin borrar datos. Dos pruebas instrumentadas pasan (4,01 s): cinco estilos/rúbrica transparente y selector de páginas/preview Compose. Arranque normal comprobado, sin fatal en el PID observado; auxiliar retirado. Firma con DNI/TSA y regresión física Suite/puente pendientes. [Informe](VALIDACION_ANDROID_AETHER_R3.md).

## Pulido R4 entregado

2026-10-14 · Aether R4 / 3.1.0: Bridge corrige pintura y selección, inputs, bandeja y consulta WinSCard. Suite añade hoja de muestra, controles, preferencias visuales, zoom y apertura/autocompletado de resultados; emblema compartido actualizado. 196 pruebas pasan/1 omitida preexistente, 60 comprobaciones nativas y 2 instrumentadas Android pasan. Windows actualizado con identidad y 13 archivos runtime conservados; APK80 instalada. QR enriquecido diferido; firma física nueva y suspensión siguen pendientes. [Resultado](RESULTADO_AETHER_R4.md).
