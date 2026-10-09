# Android 3.0 R3 · disposición Aether y sesión del puente

Fecha: 2026-10-09. Checkpoint previo: `checkpoint/pre-android-redesign-r3-apk74`.

## Comparación con el prototipo

Referencia de solo lectura: `D:/stitch_suite_criptogr_fica_y_firma_digital` (Inicio, certificados, firma/lotes, bóveda y conexión PC).

- Inicio: texto corto, tarjetas de resultados de última lectura, accesos directos Conectar PC/Firmar PDF, filas compactas con iconos.
- Firma: selector PDF individual/Lotes en una superficie azul común. Resultado completado con marca de comprobación; límites y salida .p7s se mantienen visibles.
- Certificados: titular, vencimiento y exportación visibles; emisor, serie y huella en detalles desplegables. Validación presenta comprobaciones individuales y conserva todas las evidencias bajo un desplegable.
- Identidad y bóveda: descripción breve, tareas existentes y navegación común conservadas.
- Puente: configuración más corta y huella desplegable. La comparación de huella continúa indicada antes de autorizar. Desarrollo sigue opt-in en debug.

No se adoptan identidades ficticias, DNI4, confianza eIDAS, LTA, biometría, passkeys ni otros indicadores simulados del prototipo. La apariencia sigue Claro/Oscuro/Del sistema elegida por el usuario.

## Pantalla completa de sesión

`BridgeSessionStage` contiene IDLE, PREPARING, WAITING_CARD, CONNECTING_PC, OPENING_CARD, ACTIVE y STOPPING. Los eventos reales de `BridgeConnection` actualizan el estado desde el ViewModel. Importar un emparejamiento puede estar ocupado sin abrir una sesión.

`BridgeSessionScreen` reemplaza la configuración durante la autorización/sesión para los tres transportes (Tailscale, Wi-Fi local, accesorio USB directo). Diagrama PC–teléfono–DNI, estados breves y un único botón Detener sesión. Atrás también detiene; STOPPING impide repetir la acción. onPause sigue cancelando al salir o bloquear.

Un listener preparado no significa PC conectada. OPENING_CARD confirma conexión con PC, pero no tarjeta preparada. ACTIVE confirma que la tarjeta del puente está abierta; el modo Windows directo se presenta como disponible para Windows, sin afirmar que Android abrió PACE. Retirada/cierre y rearme vuelven a WAITING_CARD sin reutilizar operaciones.

`StatusCheck` es presentación reutilizable. Una marca indica el paso específico completado; leer un certificado no equivale a verificar su confianza. Las evidencias de revocación/cadena conservan su vigencia y sus límites.

## Vibración

`data/feedback/ReadingFeedback` encapsula Android Vibrator. Lectura física aceptada en MainActivity, BridgeActivity y RemoteSigningActivity emite un pulso de 90 ms; no vibra por recomponer la UI. Es best-effort y nunca condiciona el acceso al chip.

En el teléfono, dumpsys muestra UNKNOWN=OFF y TOUCH=OFF, frente a PHYSICAL_EMULATION=HIGH. El código anterior no declaraba uso. Android 13+ declara respuesta a interacción física (`USAGE_PHYSICAL_EMULATION`); versiones anteriores usan sonificación. No se alteran ajustes del dispositivo ni se ignoran políticas del sistema. Prueba aislada del adaptador en el teléfono: el sistema registra `finished`, `PHYSICAL_EMULATION`, pulso de 90 ms y duración total de 107 ms. Los eventos antiguos de 70 ms figuran como TOUCH `ignored_for_settings`. Evidencia filtrada a esta app: `D:/DNIeToolchain/Diagnostics/apk75/haptic-proof.txt`. La percepción al leer DNI requiere confirmación del usuario.

## Capas e invariantes

Compose presenta estado; ViewModel traduce fases de transporte. Vibración es un adaptador Android común. No cambian motores CMS/PDF, confianza, bóveda, NFC/PACE, PIN, contadores ni transportes. FLAG_SECURE, autorización explícita, cancelación y limpieza de credenciales se conservan.

## Verificación y entregable

- Build debug, APK auxiliar, Kotlin release y manifest release: correctos (`apk75-palette-build.txt`). Diagnóstico persistente/provider/Activity siguen excluidos de release.
- JVM app: 80 casos, 79 pasan y uno omitido por permisos Windows; cero fallos/errores.
- Android final: **OK (33 tests)**, 42.283 s (`apk75-final-instrumented.txt`). Incluye pantalla de sesión para los tres transportes: número de acciones, progresión de comprobaciones, parada y estado deteniendo.
- Bóveda, identidad, PDF/lotes, navegación/retorno Bridge y Suite, selección de apariencia y recreación cubiertos por pruebas existentes. Arquitectura y diff sin errores.
- Revisión visual de Inicio, certificados, identidad, firma y configuración en teléfono; bóveda y pantallas reales Bridge/Suite conservan FLAG_SECURE. Captura del puente es **fixture aislado**, no prueba de conexión real: `D:/DNIeToolchain/Diagnostics/apk75/bridge-session-fixture.png`.
- APK instalada como actualización conservando datos. Versión 75 / `3.0.0-alpha03-sessions`.
- APK: `D:/DNIeToolchain/AndroidPackages/dnie-evolution-3.0.0-alpha03-sessions-debug.apk`.
- SHA256: `A3D52FF68F9D5E8DD03D951E64022CAAD2442176390B59F6B985625F6278C5F0`.

Pendientes: conexión/retirada/reaproximación real del DNI en cada transporte, percepción háptica al comenzar esas lecturas, biblioteca con datos reales y rendimiento en sesiones prolongadas. Los tests sintéticos no acreditan esas interacciones físicas.
