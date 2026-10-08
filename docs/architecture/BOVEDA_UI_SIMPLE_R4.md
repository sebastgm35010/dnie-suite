# Bóveda: pantallas separadas y selección múltiple R4

2026-10-07. Checkpoint anterior: `checkpoint/pre-vault-ui-r4`, sobre `b8576d7`. APK66 / `2.0.0-rc18-vault-ui`.

## Distribución

- Archivos: vista inicial, cifrar/importar, búsqueda, filtros en diálogo, papelera y lista compacta. Cada tarjeta ofrece una acción principal y menú contextual; identificadores, fechas y detalles permanecen en la ficha.
- DNI: CAN/PIN y vinculación/apertura. Elegir descifrado o vista previa desde Archivos lleva aquí automáticamente.
- Recuperar: preparar/abrir paquetes; ayuda desplegable y acceso DNI plegable. No cambia el formato ni las exigencias de recuperación R3.
- Más: respaldo completo, importar respaldo, nota cifrada y configuración de passkeys.

Pantallas internas mantienen la sesión de bóveda; navegar no cancela NFC ni destruye el ViewModel. Cada apartado mantiene su posición de desplazamiento. Salir de la bóveda conserva la cancelación previa. El estado/progreso y cancelar se muestran en cualquier apartado.

## Selección múltiple

«Seleccionar varios» permite marcar archivos o todos los visibles de la lista filtrada. Acciones: reemplazar carpeta/etiquetas, mover a papelera y restaurar desde papelera. Mover/restaurar requiere confirmación; borrar definitivamente sigue individual. Cifrar múltiples fuentes y respaldo completo ya estaban disponibles y se conservan.

No se añadió descifrado/exportación multiselección ni generación masiva de paquetes de recuperación en esta iteración. Las acciones nuevas modifican solo etiquetas/estado de papelera. El motor genérico de lote existente procesa un único trabajo con límite de 1000 archivos, conserva éxitos parciales, detiene tras cancelación y cuenta fallos independientes. No se lanzan llamadas individuales al ViewModel que solo procesarían el primer archivo por estar ocupado.

## Capas

`VaultScreen` coordina navegación/pickers y selección local. `VaultAccessPanel`, `VaultToolsPanel`, `VaultRecoveryControls`, `VaultLibraryControls` y `VaultFileCard` presentan sus funciones. `VaultLibraryActions` emite intención de lote hacia `VaultViewModel`; backend, almacenamiento, cifrado/PACE y formatos permanecen separados. Selección y borradores no se guardan persistentemente; no contiene claves privadas.

## Validación

Pruebas app JVM y compilación APK principal/auxiliar. Test Android `VaultNavigationInstrumentedTest` usa dos archivos ficticios y callbacks; verifica ocultar credenciales/respaldos en Archivos, conservar selección al ir a DNI y volver, confirmar y enviar exactamente un lote con dos IDs. No toca almacenamiento del usuario ni tarjeta.

76 pruebas JVM app ejecutadas sin fallos/errores/omisiones; compilación debug principal y auxiliar correcta. APK66 instalado por ADB, datos preservados. Primer intento de prueba visual falló por ausencia de jerarquía Compose; teléfono reportaba bloqueo visible. Tras desbloquear por indicación del usuario, la prueba instrumentada pasó: OK (1 test), 1.917 s. Comprueba intenciones con fixtures; no mueve archivos reales. No acreditar firma o recuperación real por una prueba de presentación.
