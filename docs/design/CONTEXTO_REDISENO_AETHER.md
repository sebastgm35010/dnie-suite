# Rediseño Aether completo · contexto de ejecución

Inicio: 2026-10-14, America/Bogota. Especificación autorizada: ESPECIFICACION_AETHER_Y_SELLOS_PDF.md. Objetivo completo: sellos vectoriales compartidos Android/Windows, modelos/layout puros, preview WYSIWYG y editor con selección de páginas/presets, Suite Swing Aether y Manager WinForms Aether, controles/miniatura Android, pruebas/builds y revisión visual. Usuario trabajará asincrónicamente: resolver decisiones rutinarias sin depender de respuesta.

Checkpoint previo: checkpoint/pre-aether-redesign-20261013. Suspensión driver queda aplazada; conservar 2.0.0.4. No modificar motores criptográficos, transporte, autorización ni driver. Preservar sesión privada Suite, datos/identidades existentes, rechazo sin replay, perfiles PAdES B/T y separación motores/UI. No reintroducir passkeys.

## Entregables y auditoría

- [x] Modelos de apariencia/color/insignia/páginas con compatibilidad de llamadas existentes.
- [x] Layout puro compartido: palabras, guion para tokens largos, ajuste 5–11 pt, centrado, geometría/colores.
- [x] Sellos vectoriales en ambos PDF: institucional, tarjeta, minimalista, rúbrica+datos y solo rúbrica; fuentes sans regular/negrita; emblema, QR público y badge TSA.
- [x] Primera/última/específica/todas las páginas: una firma criptográfica con apariencias vinculadas, sin multiplicar operaciones DNI.
- [x] Suite: tema/componentes, cabecera, red/rutas modal, tarjetas de archivos/drop, inspector, modos/TSA, campos, pie.
- [x] Windows preview real con layout idéntico, sombra, cuatro esquinas/bordes, cursores, snap y presets/páginas.
- [x] Android: estilo/color/insignia/páginas/título, miniatura instantánea y editor coherente, conservar claro/oscuro del usuario.
- [x] Manager: tres tarjetas de salud independientes, transporte segmentado/filas dinámicas, selector alternador.
- [x] Regresiones significativas, arquitectura, compilación Android/Windows, paquetes producción y evidencias visuales sintéticas.
- [ ] Cierre de despliegue Windows, registro SQL final y documentación de resultados.

No confundir vistas sintéticas con firma DNI nueva ni con suspensión real. QR del sello contiene resumen público, no sustituye validación criptográfica. Preview de nombre opcional no altera CN real firmado.

## Estado comprobado antes del despliegue

Código implementado completo. Gradle conformance-final.log: debug/release/test APK y distribuciones Suite/CLI compiladas. 190 tests JVM/Android unitarios contabilizados: 189 pasan y 1 caso preexistente de symlink de migración se omite por capacidad de plataforma. Manager Release compila sin advertencias: 24 comprobaciones de diseño/producción pasan. No existe teléfono ADB conectado ni emulador instalado: dos pruebas instrumentadas nuevas están compiladas, todavía no ejecutadas. PDF sintético se comprueba criptográficamente; no se afirma firma DNI nueva.

Windows/Android comparten el layout puro; adaptadores PDF dibujan la escena vectorial. Fuentes Desktop se comparten entre páginas; todas las apariencias pertenecen a un campo/firma. UI Swing usa previews asíncronas y debounce, renderizador de lista reutilizable y solo datos de ejemplo hasta tener el certificado. Los datos finales vienen del certificado real. QR es resumen público con campos visibles, no un verificador autónomo.

Runtime de servicio/transportes conservado byte por byte en paquete local nuevo; solo cuatro archivos de frontend Manager se reemplazan. Deploy aún no ejecutado al registrar este checkpoint. Driver intacto. La base anterior/identidades no se eliminan.
