# Sesión PC/SC integrada 1.8.2

Entrega 2026-10-05, versionCode 35. Corrige dos bloqueos observados en diagnóstico 6: el host esperaba un consumidor local que el launcher no iniciaba y el backend rechazaba reset/power-off. El timeout de esa espera no se resuelve enviando resets periódicos.

## Implementación

- Nuevo comando Windows `serve-pcsc`: autentica TLS/token, abre el backend privado, enlaza un pipe privado dentro del mismo proceso y solicita el ATR al móvil. Solo después adjunta la tarjeta al controlador.
- `PcscHostSession` coordina y cierra transporte/backend; `PcscBackend` traduce los controles y mantiene pulsos de transporte cada diez segundos sin cambiar selección de archivos ni SSC NFC.
- Controles de esta pareja Windows/Android: `4` ATR, `2` reset con respuesta ATR, `0` power-off con confirmación `01`, `1` power-on/reapertura con ATR y `5` heartbeat con confirmación `01`. No usar estos nuevos controles con clientes antiguos que no los implementan.
- Android `RestartablePaceCard` mantiene el CAN solo en memoria de la sesión aprobada, cierra el adaptador anterior y reconstruye PACE mediante un adaptador nuevo del mismo tag. Reset falla y retira sesión si no puede reconstruirse; no responde éxito usando solamente ATR guardado. La selección previa de certificado se invalida al reset/power-off.
- La caducidad del QR limita entrada/autorización nueva; no corta una sesión ya aceptada. Android y Windows limitan la sesión a treinta minutos. Cerrar la pantalla o perder transporte cierra recursos.
- Diagnóstico Android añade importación, autorización NFC, TLS aceptado, token aceptado, PACE listo, reset/power-off y cierre. Windows registra fases, cabecera APDU y status, sin payload de documentos, CAN, PIN ni token.

## Arranque del controlador y prueba en navegador

`emparejar-pc.bat` comprueba lectores, configura ADB reverse y genera QR. El modo por defecto inicia una sesión temporal SYSTEM, necesaria por la ACL original del backend. `start-pcsc-session.ps1` copia solo el motor y sus archivos de runtime a un directorio administrativo protegido en D:, junto a una copia de la configuración privada. No modifica la ACL del controlador, firewall ni modos de arranque.

La tarea no tiene disparador persistente y se elimina después de arrancar. El host usa un solo emparejamiento y tiene plazos propios de terminación. Los registros del modo integrado están en `D:\DNIeBridgePrivileged\<sesión>`. El usuario tiene lectura; Administradores y SYSTEM tienen escritura.

Tras escanear, comparar huella y autorizar, mantener DNI apoyado. Comprobar `pcsc_card_present` en el host antes de iniciar el flujo Ciudadano Digital. `reader-status.ps1` verificó que `DNIe Bridge Android NFC Private Reader 0` figura primero; cddnie selecciona el primer lector. No se deshabilitó Bix ni Windows Hello.

## Evidencia

- Compilación Android y tests de bridge-core, bridge-android y app pasaron; pruebas adicionales verifican canal nuevo por reset, limpieza de selección, power-off/reapertura y fallo cerrado al perder la tarjeta.
- Motor Windows: **63 comprobaciones** de TLS, aislamiento, backend, controles y conexión integrada. Incluye rechazo de token sin abrir backend, ATR inválido, warm reset y heartbeat.
- Controlador instalado + motor integrado + teléfono simulado por TLS: **13 comprobaciones** reales mediante winscard. Conserva respuestas `6A82`, transmite tras 35 segundos sin APDU y tres heartbeats, completa warm reset y power-off y retira tarjeta al desconectar.
- Artefacto local: `build/pcsc-deployment/reader-relay-synthetic-result.json`. Los datos y ATR son explícitamente sintéticos, no pertenecen a un DNI.
- APK 1.8.2 instalado por ADB. No se reinstaló el controlador: los IOCTL de reset/power-off ya existían; faltaba implementarlos en el motor.
- Durante la entrega apareció una sesión real: el host confirmó `peer_authorized`, `pcsc_backend_opened`, `pcsc_card_present`, veinte sondeos APDU y power-off completado. Los sondeos `00A40400` / `00CA7F68` fueron rechazados con `6985` por el perfil de lectura; no se atribuye su origen a cddnie sin captura del proceso. No hubo un reset PC/SC en ese registro real. La reinstalación final del APK coincidió con EOF y retirada; no sirve para medir duración máxima. Se preparó otra sesión para continuar sin nuevas instalaciones.

El ensayo inicial de la nueva prueba esperaba erróneamente dos resets: Windows puede conectar usando el estado/ATR ya adjunto. Se corrigió la prueba para exigir que el warm reset aumente el contador respecto al valor anterior; la segunda ejecución pasó. No se modificó el controlador para forzar ese contador.

## Límites pendientes

Cerrar/reconectar IsoDep y abrir PACE es una adaptación de sesión; [Android no ofrece aquí una primitiva de reset eléctrico de contacto](https://developer.android.com/reference/android/nfc/tech/IsoDep). La reapertura con el mismo tag necesita validación en el teléfono/DNI reales.

El perfil sigue limitado a selección/lectura de certificados. No autoriza PIN, firma o PACE iniciados por la PC, ni afirma compatibilidad completa con el Java del middleware. El siguiente ensayo debe registrar qué comandos pide el navegador/host y dónde se detiene. No falsificar ATR de DNI por contacto ni respuestas `9000` para superar reconocimiento.
