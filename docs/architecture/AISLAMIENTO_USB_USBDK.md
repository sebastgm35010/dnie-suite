# Aislamiento del motor USB de Windows

2026-10-06. Complementa CORRECCION_USB_AOA_RC4.md; APK Android 52 conserva la misma versión.

## Diagnóstico

La prueba física sin DNI se detuvo en `usb_redirect_initial`, inmediatamente antes de `UsbDk_StartRedirect`. No llegó a consulta AOA, TLS, PC/SC ni PACE. Antes, el broker llamaba directamente a la biblioteca nativa y quedaba en Stopping al agotarse la parada.

El código público del helper muestra que StartRedirect llama al controlador para añadir la redirección. Referencia de implementación: [UsbDkHelper.cpp](https://github.com/daynix/UsbDk/blob/master/UsbDkHelper/UsbDkHelper.cpp). El registro identifica el límite de llamada, no la instrucción interna del controlador que se bloquea.

## Separación y cancelación

- El broker conserva IPC, estado, identidad y coordinación. USB ejecuta el motor existente en un auxiliar del mismo DNIeBridge.Agent.exe instalado, bajo SYSTEM y con ruta fija. LAN y Tailscale mantienen su ejecución actual.
- El auxiliar recibe solo el serial elegido; obtiene la identidad TLS del almacenamiento protegido. Stdout transmite únicamente nombres de etapa; elimina APDU, extras y payloads. No expone token, PIN o CAN.
- Un Job Object cierra el auxiliar al terminar el broker. La parada pide cancelación normal y espera tres segundos antes de solicitar terminación del auxiliar; espera cinco segundos adicionales para comprobar su salida.
- Las etapas de llamada nativa de preparación tienen un límite de 30 segundos. Esperar al teléfono, a autorización Android o a NFC no recibe ese límite. No se repiten firmas ni START_ACCESSORY por timeout.
- Si el proceso sigue listado después de solicitar su cierre, queda retenido en cuarentena dentro del broker y se impide abrir otro auxiliar hasta que desaparezca del listado del sistema. No basta HasExited: se observó un proceso terminado reteniendo un hilo nativo. Antes de abrir USB también se comprueba la ausencia de otro proceso DNIeBridge.Agent, cubriendo auxiliares residuales después de actualizar/reiniciar el broker. Los eventos tardíos dejan de modificar el estado. Se indica la etapa bloqueada y la necesidad de reconectar el cable. Esto no garantiza que Windows pueda cancelar inmediatamente una llamada del controlador.

## Validación y límites

Motor Windows: 127 comprobaciones satisfactorias. Control Release/Debug: 39, incluyendo proceso que ignora cancelación, salida inesperada, watchdog y filtro de datos. Compilación sin advertencias/errores y límites de arquitectura correctos. Estas pruebas no simulan un hilo bloqueado dentro del kernel; esa limitación corresponde a las observaciones físicas.

Prueba física de la primera iteración aislada: `usb_redirect_initial` permaneció bloqueado. El watchdog mantuvo el broker consultable y declaró Failed; la terminación del auxiliar no concluyó en los cinco segundos previstos. La primera iteración mostró un timeout genérico; el ajuste final conserva la etapa y bloquea nuevos auxiliares. El usuario reconectó el cable; después desapareció el auxiliar anterior y el teléfono volvió a VID_2717/PID_FF48 y ADB disponible. No hubo DNI publicado ni operación privada.

Pendiente: probar apertura y cancelación físicas del paquete final; lograr AOA/TLS y lectura/firma USB. No declarar USB estable por el aislamiento. No se modificaron firewall, minidriver, CertPropSvc, SCardSvr ni WUDFHost.

Segunda validación: después de reconectar, preparación llegó a `usb_redirect_accessory`. Una parada finalizó en 0,16 s; al repetir terminó en 5,02 s con error genérico procedente del drenado del canal de etapas después del cierre. Se acotó y canceló ese drenado para que no convierta una parada en fallo. En r2 se repitieron dos ciclos: parada en 3,02 s desde usb_redirect_initial y 0,017 s desde usb_retry, ambos Stopped sin error ni tarjeta. Sin embargo Windows aún listaba un auxiliar con un hilo; r4 incorpora la exclusión por listado y la comprobación entre reinicios del broker. No afirmar que esos ciclos resolvieron el bloqueo del controlador.

Paquete r4 instalado con éxito. Comprobación posterior sin tarjeta: inicio aceptado y parada Stopped/idle, sin error; gestor abierto. El auxiliar residual anterior ya había desaparecido al comprobar r4, por lo que la exclusión de un proceso residual entre reinicios no se reprodujo físicamente en esa comprobación. No se inicia automáticamente otra sesión USB.

Distribución: `D:/DNIeToolchain/DesktopPackages/Native-Release-usb-isolated-r4`. Evidencia técnica sin secretos fuera de Git: `D:/DNIeToolchain/Checkpoints/usb-isolation`.
