# Acabado y fluidez R10 · 3.1.3 / APK 87

2026-10-15. Especificación recibida en `ESPECIFICACION_R10_RECIBIDA.md`; checkpoint previo `checkpoint/pre-finish-r10`. R10 modifica presentación e interacción, manteniendo los motores de firma y conexión.

## Android

Selector segmentado compartido para PDF/lotes, certificados/identidad y controles del sello. Indicador animado en dibujo, transición horizontal según dirección y claves estables en los elementos animados de firma. Con movimiento reducido las transiciones y cambios de lista son inmediatos. Tarjetas Bento con medallón, flecha y jerarquía; la cabecera distingue datos cargados de conexión física activa.

Puente y Suite presentan CAN compacto, lectura asíncrona del CAN recordado y acción Cambiar. El componente no inicia conexiones ni persiste cambios por sí solo; una lectura tardía no sobrescribe un campo editado ni actualiza credenciales mientras el control está deshabilitado. PC vinculada muestra dirección y huella desplegable; la etiqueta TLS describe la política al conectar y no afirma autenticación de una sesión todavía inexistente. Acciones principales fijas; sesiones activas conservan sus pantallas completas.

Escenas vectoriales de guía con marco, chip, CAN destacado, ondas y vínculo protegido. Solo dibujan información ilustrativa, sin progreso o autorización. Credencial informativa con cabecera y rejilla; MRZ y firma EF.SOD mantienen su significado limitado y no acreditan confianza CSCA ni autenticidad física del chip. Se conserva la advertencia correspondiente.

## Windows

Diálogo PIN propio y confirmaciones Aether. Se conserva el preflight antes de pedir PIN y la autorización explícita por lote. El campo admite confirmación solo con 4–8 dígitos; Enter confirma, Escape/cancelar/cerrar cancelan. Se limpia el campo al salir; el array devuelto queda bajo responsabilidad del controlador, que lo borra al finalizar. Esto no promete borrar todas las copias que Swing o la JVM puedan haber creado internamente. Se retira el panel PIN anterior y su prueba sustituida.

Animador Swing de interpolación finita a 140 ms con temporizador coalescente de 16 ms. Se detiene al completar, ocultar o retirar el componente. Lee la propiedad de animación Windows cuando la JVM la expone; `-Ddnie.ui.reduceMotion=true` permite desactivar estas transiciones. WinForms consulta la preferencia de animación de menús y detiene/libera sus temporizadores al ocultar/disponer controles. Sin actividad de hover no hay temporizadores animando controles continuamente.

Documentos seleccionados con contorno redondeado, acento lateral y píldoras de tipo/resultado. Cabecera Suite con superficie y separador; secciones plegables con chevron vectorial. Gestor Bridge con cabecera en dos columnas, medallones vectoriales y bandas/etiquetas semánticas. Las etiquetas de salud siguen dependiendo del estado que entrega el servicio; no lo simulan en producción.

## Medición y límites

Un temporizador de 16 ms expresa un objetivo de actualización; no garantiza 60 FPS. Las pruebas de renderizado registran duraciones de cuadros reales de Android, excluyendo primeros dibujos y describiendo muestra/percentiles/umbrales. Ejecutarlas bajo instrumentación afecta los tiempos; no extrapolar a todos los teléfonos, documentos o sesiones NFC. No se cambia la frecuencia de pantalla, escala de animación, modo de ahorro ni políticas del dispositivo para mejorar cifras.

Pruebas sintéticas cubren salidas del diálogo PIN, temporizadores que terminan, selección de documentos, CAN asíncrono, pestañas y semántica de PC vinculada. Las regresiones JVM/PDF/firmador y las instrumentadas R8/R9 siguen formando parte de la entrega. NFC, firma/TSA reales, USB y suspensión requieren pruebas físicas independientes.

## Evidencias

Resultados finales en `docs/operations/ACABADO_R10_RESULTADOS.json`; registros y artefactos en `D:/DNIeToolchain/Builds/finish-r10` y copia de entrega en `G:/Mi unidad/BOT TRADER/DNI_CODEX/revision-acabado-r10-20261015`. Firma de actualización estable, datos de usuario conservados y registro SQL actualizado al finalizar. Distribuciones Windows se reemplazan mediante accesos directos; no se reinstala el servicio SYSTEM ni el controlador.

Referencias: [listas y animación de elementos Compose](https://developer.android.com/develop/ui/compose/lists), [Timer de Swing](https://docs.oracle.com/en/java/javase/11/docs/api/java.desktop/javax/swing/Timer.html).

## Resultado comprobado

- 255 casos JVM/Android unitarios en 65 suites: 254 aprobados, 0 fallos/errores y 1 omisión preexistente por permisos Windows de un enlace de directorio.
- Manager: 52 comprobaciones, sin llamar al servicio ni operar la tarjeta. Diálogo PIN: confirmar/cancelar/Escape/cerrar y borrado de campo aprobados. Selección redondeada y finalización de temporizadores verificadas.
- Android: 14 instrumentadas aprobadas en 29,435 s, incluidas R8/R9 y medición de navegación. Se usan CAN/PIN sintéticos y almacenes inyectados; no se cambia CAN ni ajustes del usuario. Capturas de guía y pantalla Inicio verificadas.
- Debug/release y auxiliar compilados offline. Instalaciones Success; versión instalada 3.1.3 / código 87 y misma identidad de firma que 86. Producción Android sin diagnóstico compilado/manifiesto; exclusiones nativas verificadas separadamente del paquete Windows heredado usado por el verificador general.
- Accesos Windows actualizados con respaldo de los anteriores; procesos de usuario, servicio SYSTEM, controlador e identidad de PC conservados. AndroidPackages conserva 86 y 87; 85 archivada en D.

### Medición puntual de cuadros

Mismo auxiliar y recorrido sobre los ejecutables instalados 86 y 87. FrameMetrics de la ventana real, primer dibujo excluido y muestras deduplicadas por VSYNC previsto. El auxiliar nuevo solo usa APIs estables de MainActivity/Window para medir la base 86. No se modifica el sistema para obtener estas cifras.

| Medida | 3.1.2 / 86 | 3.1.3 / 87 |
| --- | ---: | ---: |
| Muestras | 997 | 900 |
| Mediana | 6,01 ms | 6,13 ms |
| Percentil 95 | 8,88 ms | 8,94 ms |
| Máximo | 140,33 ms | 80,15 ms |
| Más de 16,67 ms | 5 | 2 |

El percentil 95 aumenta en esta ejecución; la mediana es similar y persisten cuadros largos aislados. Una comparación bajo instrumentación no prueba causalidad ni 60 FPS constantes. Pendiente carga representativa con PDF, lotes y DNI físico.

La primera prueba Swing usó un ActionEvent nulo al simular Escape; se corrigió el fixture y se añadió un watchdog. Una medición móvil se interrumpió por bloqueo de pantalla; se repitió tras el aviso y desbloqueo del usuario. No se reinterpretan esos fallos de ensayo como errores del motor.

Revisión final: indicador segmentado correcto en LTR/RTL y coordenadas semánticas alineadas con la capa de dibujo. La prueba de geometría adicional pasó junto a las regresiones; APK 87 se recompiló e instaló con la misma firma y versión, sin borrar datos.

Se conserva también la medición del candidato previo (P95 7,71 ms); la tabla corresponde al APK final. La variación entre ejecuciones impide atribuir una reducción estable al rediseño.
