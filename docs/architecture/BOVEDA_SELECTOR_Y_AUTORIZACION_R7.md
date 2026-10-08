# Selector SAF antes de credenciales y autorización · R7

2026-10-08. Checkpoint previo `checkpoint/pre-vault-picker-r7` sobre `ad6a34b`. APK69 / `2.0.0-rc21-vault-picker` compilada; instalación y ejecución instrumentada pendientes de ADB.

## Error reportado y causa

Usuario reporta destino exportado con cero bytes y ausencia de solicitud de DNI al regresar del selector. `MainActivity.onPause` cancela operaciones y llama `VaultViewModel.cancel`, que limpia PIN. R6 recogía credenciales antes de abrir CreateDocument/OpenDocument y llamaba export/import automáticamente desde LaunchedEffect del URI. Al volver, el PIN ya estaba vacío: credentialsValid rechazaba el inicio, sin estado awaitingCard. CreateDocument había creado un destino vacío; no era un respaldo ni prueba de que el motor hubiera terminado.

Las pruebas R6 cubrían motor, repositorio con URI file y navegación, sin pausa/reentrada del selector. No acreditaban el recorrido real SAF/NFC.

## Corrección

`VaultRecoveryControls` pide primero destino/respaldo y almacena únicamente el URI mediante rememberSaveable. El callback del selector no autoriza ni inicia criptografía. Tras elegir archivo aparecen CAN/PIN y contraseña, y el botón explícito **Autorizar exportación con DNI** o **Autorizar importación con DNI**. Después se cierra teclado y se muestra la espera de tarjeta; las entradas desaparecen durante operación. Elegir destino se identifica como paso pendiente, nunca como exportación terminada.

Contraseñas usan remember (no saved state), se limpian al abrir otro selector y al entregar la acción; ViewModel sigue siendo dueño del borrado de buffers de contraseña y claves. No se relaja la cancelación NFC ni el borrado de PIN en onPause. Motores, formato DNIEBKP2, journal y transporte sin cambios.

## Validación y pendientes

`:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`: BUILD SUCCESSFUL; app 79 pruebas, sin fallos/errores. Pruebas instrumentadas compiladas: export/import con registro ActivityResult aislado que simula pausa limpiando PIN, comprueba ausencia de autorización al recibir URI y espera de tarjeta tras introducir credenciales y pulsar explícitamente. Navegación actualizada para el paso inicial sin credenciales.

ADB perdió conexión; intentos al puerto anterior LAN/Tailscale y descubrimiento mDNS no encontraron conexión válida. No instalada APK69 ni ejecutadas sus pruebas de interfaz todavía. APK instalada conocida previa: 68.

Artefacto: `D:/DNIeToolchain/AndroidPackages/dnie-evolution-2.0.0-rc21-vault-picker-debug.apk`. Pendiente reconectar ADB, instalar principal/auxiliar, ejecutar clases VaultArchivePickerInstrumentedTest y VaultNavigationInstrumentedTest, y confirmar exportación real desde SAF con DNI. El archivo vacío reportado no contiene un respaldo recuperable; no se modifica automáticamente ni se borra sin conocer su ruta.
