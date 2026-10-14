# Aether R4 · Bridge y Suite; QR enriquecido posterior

Objetivo autorizado: «ok, traza un objetivo hasta la suite y qr despues». Fecha: 2026-10-14, America/Bogota. Referencia externa: [ESPECIFICACION_PULIDO_AETHER_R4.md](ESPECIFICACION_PULIDO_AETHER_R4.md). Checkpoint previo: `checkpoint/pre-aether-r4`.

## Criterios de entrega de esta etapa

1. Bridge: fondo explícito en pintura de botones/tarjetas, superficies coherentes, transporte seleccionado legible aun deshabilitado e inputs con marco Aether.
2. Bridge: minimizar a bandeja, cierre a bandeja opcional y acciones equivalentes a botones existentes; diagnóstico del lector mediante la consulta WinSCard existente, sin conectar/resetear/transmitir a la tarjeta.
3. Sello decorativo: medallón circular, escudo relleno, check blanco y etiqueta; adaptación vectorial idéntica en Android/Windows, sin afirmar certificación institucional.
4. Suite: hoja A4 de muestra interactiva y sello renderizado antes de cargar PDF, drop de documentos, controles ComboBox/Spinner/scroll coherentes.
5. Suite: diseño persistente (estilo, color, insignia, título, campos, motivo, lugar, TSA, páginas, posición y nombre de muestra), escritura fuera de EDT, restauración de valores válidos y botón de restablecer. No almacenar PIN, CAN, privada, certificado ni imagen manuscrita en preferencias de diseño; no alterar la confianza de PC.
6. Suite: zoom/ajustar con desplazamiento, navegación de página independiente de la elección de dónde firmar.
7. Suite: completar nombre/DNI de previsualización a partir del certificado público exportado después de un lote; no extender el protocolo ni leer la tarjeta solo para obtener esos datos. El certificado real sigue determinando la firma final.
8. Suite: doble clic y menú contextual para abrir el resultado devuelto por el lote o su carpeta, sin adivinar nombres y sin I/O en el renderizador.
9. Regresión: pruebas unitarias/UI y nativas, revisión de imágenes, builds Android debug/release y distribuciones Windows; producción mantiene diagnóstico de desarrollo excluido. Verificar fuentes criptográficas/transportes/driver sin cambios.
10. Entrega: actualizar productos locales cuando estén inactivos, conservar identidad y binarios del servicio, APK con identidad de instalación existente, documentación/SQL/evidencias en G y GitHub privado. Pruebas con DNI real se distinguen de sintéticas.

La persistencia descrita se implementa para Suite Windows. No incorpora almacenamiento biométrico ni cambios en bóveda. Las preferencias visuales Android adicionales no forman parte de esta etapa de escritorio.

## Siguiente etapa, fuera de este objetivo

QR enriquecido, nuevos metadatos de certificado/documento y lector/cotejador Android. Conservar por ahora el payload público anterior y el QR de emparejamiento existente. Antes de diseñar la siguiente fase, distinguir resumen legible, cotejo de hashes completos y validación criptográfica del PDF/CMS; una lectura de QR no acredita una firma ni autentica una copia impresa. El hash del original necesita el original o un mecanismo de identificación de revisión, y validar un certificado requiere su DER/cadena y evidencias verificadas, no solo su serie.

La investigación de suspensión del driver continúa aplazada por el usuario. No se instala ni modifica el controlador en este objetivo.
