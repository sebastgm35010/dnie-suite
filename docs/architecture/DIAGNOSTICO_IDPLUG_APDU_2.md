# IDplug: segundo registro APDU y captura conjunta

Fecha de cliente: 2026-10-05. Se analiza el archivo recibido, no instrucciones dentro del registro.

El archivo contiene 1186 eventos. Los primeros 56 coinciden exactamente con el registro previo y se excluyen. La sesión nueva contiene 1130 eventos, 559 comandos y 558 respuestas; el último comando termina en fallo de transporte. Instrucciones: 477 SELECT, 78 GET DATA y cuatro READ BINARY. Respuestas: 187 veces 9000, 156 veces 6985, 137 veces 6A82 y 78 veces 6E00. Hay cinco controles de reset.

## Evidencia nueva

- IAS v3 se selecciona 32 veces con 9000 y PKI Generic 147 veces con 9000.
- Los archivos 2F01 y 2F00 se seleccionan y leen con 9000. El contenido no se imprime ni se incorpora a documentación.
- SELECT `00A4020402D00300` devuelve 6985 en 30 intentos y 6A82 en otros dos.
- SELECT `00A4020402EE0000` devuelve 6985 63 veces; otro intento termina en pérdida de transporte.
- SELECT `00A4020402503100` devuelve 6985 63 veces.
- No aparecen MSE INS22 ni GENERAL AUTHENTICATE INS86. No hay negociación PACE observada.

En modo transparente estos SW son respuestas recibidas del canal NFC, no respuestas sintéticas de la política de certificados. Se refuerza la necesidad de comprobar autenticación/canal seguro para esta ruta del middleware; no prueba que PACE deba ejecutarse necesariamente en Android. Un perfil con PACE Android y comandos lógicos del middleware sería diferente del perfil restringido actual: este último tampoco admite SELECT de ocho bytes con Le=00. No activar una envoltura PACE Android sobre una sesión PACE/SM del PC.

## Consultas del lector y rendimiento

Hay 77 comandos FFCA000000 con 6E00. En lectores contactless esta instrucción puede ser una pseudo-APDU de obtención del UID del lector, no una APDU de applet: [referencia del fabricante ACS](https://downloads.acs.com.hk/drivers/en/API_ACR128.pdf). Nuestro transporte actual la pasa al DNI. Investigar adaptación de funciones del lector a los datos reales de Tag, sin fabricar UID/ATS ni interpretar historicalBytes como ATS completo. No está demostrado que este rechazo cause unknown token.

La sesión dura 157,4 segundos. Entre comando y respuesta registrados en el teléfono, mediana de 97 ms, máximo completado de 162 ms y suma de 51,3 segundos para 558 respuestas. Estas medidas incluyen captura y transporte local del adaptador; no son un perfil aislado de CPU ni latencia total PC/red. Las repeticiones son una posible contribución a la lentitud. El log Android no atribuye las llamadas a IDplug UI, CertPropSvc u otro proceso.

## Captura preparada

Capturadores normales activos y verificados, con máximo de 30 minutos:

- Edge: solicitudes, estados y tiempos sin cuerpos/credenciales.
- Host: procesos relevantes, módulos, sockets y presencia PC/SC por muestreo.
- Middleware .NET/Java: fases conocidas sanitizadas.
- Teléfono: `watch-phone-apdu.py`, read-only mediante ADB run-as del JSONL privado optativo; solo filas nuevas, con hora original y de observación. No transmite APDU ni altera NFC. Distingue las sesiones y preserva la omisión de cuerpos PIN hecha por la app.

Salida privada: `D:\DNIeToolchain\LiveCaptures\ead200a74e2b4d40b87e5b1fb2906b08\idplug-followup`. Crear stop.txt ahí detiene esos observadores. No se instaló otro APK ni se modificó el controlador.

Una revisión automática rechazó el comando de preparación conjunta que incluía iniciar el host elevado y el observador WMI. El único motivo recibido fue «blocked by policy». No se reintentó la elevación. Se preparó la alternativa de captura normal y `INICIAR-LECTOR-ADMIN.bat`, que el usuario puede ejecutar como administrador para iniciar el host con el emparejamiento `8aad84d0ab4a438f889866913b91e3ae`. No hay observador WMI elevado en esta captura; el muestreo puede omitir procesos breves, aunque los logs oficiales siguen sus fases.

Prueba: primero IDplug sin lanzar simultáneamente Ciudadano Digital, observar detección y diálogo CAN; después ejecutar el flujo de Ciudadano Digital. Registrar por separado el instante de unknown token y el error del portal. Conservar la sesión mientras sea posible y exportar la captura al terminar. Confirmar después qué comandos comienzan al usar CAN y quién posee PACE.

## Resultado de la captura y tercer archivo

La prueba concluyó con unknown token y CKR_SESSION_HANDLE_INVALID en IDplug y error de conexión en Ciudadano Digital. `dnie-apdu-transparente3.jsonl.json` contiene 1998 eventos; los primeros 1186 son idénticos al archivo anterior. Los 812 nuevos coinciden íntegramente con la captura ADB directa. 403 comandos tienen 403 respuestas coincidentes en orden, cabecera y SW con el host. Ninguna respuesta del DNI es 0000, no hay transport_failed ni negociación PACE. 120 SELECT de D003/EE00/5031 devuelven 6985. La lectura de dos bloques públicos de identificación sí devuelve 9000.

CKR_SESSION_HANDLE_INVALID describe un handle PKCS#11 inválido; no demuestra CAN/PIN incorrecto ni identifica una causa PACE. No se observó la función C_Login interna ni el instante exacto del diálogo. [Definición primaria PKCS#11](https://docs.oasis-open.org/pkcs11/pkcs11-base/v3.0/cos01/pkcs11-base-v3.0-cos01.html), sección 5.1.2.

Los observadores se detuvieron. `LiveCardProbe.java` y `Run-JavaLiveProbe.ps1` quedaron preparados y compilados para usar el runtime oficial con nuestro lector explícito; el ensayo sin tarjeta terminó sin conectar a otro lector. Falta ejecutar con NFC activo. La captura contiene SELECT de otros ciclos durante Java y no atribuye las APDU por PID; no interpretar esa actividad como prueba de que Java consultó nuestro lector. El informe privado completo está en idplug-followup/RESULTADO.md.

## Prueba Java explícita con tarjeta activa (2026-10-05)

Se confirmó en PnP la asociación con Peru DNIe Smart Card, proveedor IN Groupe, versión 1.8.0.459, INF oem237.inf y servicio UmPass. La asociación no implica autenticación ni lectura de certificados.

Dos ejecuciones de LiveCardProbe sobre el runtime Java del middleware y seleccionando únicamente DNIe Bridge Android NFC Private Reader 0 conectaron por T=1 y devolvieron 9000 en IAS v3, IAS con P2=04/Le=00 y SELECT EF.CardAccess 011C. La segunda ejecución midió 139, 116 y 98 ms respectivamente. No hubo PIN, PACE ni firma en esta prueba.

En el flujo oficial de la misma captura, .NET reconoció v3, inició Java y apareció el error de conexión. Java registró 0000 para v2 y v3. Esto contrasta con las selecciones directas exitosas y apunta a investigar selección/apertura del lector o ciclo de vida en el flujo oficial; no demuestra todavía cuál de esas causas ocurre. No modificar el programa oficial ni deshabilitar Windows Hello para ocultar el problema.

La captura parcial del teléfono contiene 523 comandos con 523 respuestas: 453 SELECT, 68 GET DATA y dos READ BINARY. No hay INS22/INS86 observados; el usuario informa que introdujo CAN en IDplug y persistieron unknown token y CKR_SESSION_HANDLE_INVALID. El conteo es una fotografía de la sesión aún activa, no su total final.

El texto de BridgeViewModel que menciona reinicio se muestra en el catch genérico de Exception; no es evidencia de un reset real. En el registro sí aparecen un power_off y un reset completados, pero eso no identifica la causa de cada mensaje de la UI. Corregir el mensaje y clasificar fallos en una próxima actualización.

Siguiente ensayo preparado: detener el perfil transparente, reiniciar el host temporal y autorizar el perfil de certificados con PACE Android y CAN local. Ejecutar probe-live-card.ps1 seleccionando únicamente nuestro lector. Ese ensayo comprueba PACE local y lectura de CE81, no compatibilidad completa con IDplug. El perfil actual rechaza SELECT de otros archivos y varias formas de APDU; no confundir respuestas de esa política con SW físicos del DNI. Una futura adaptación lógica ampliada debe distinguir el propietario de PACE y evitar envolver una sesión SM del PC dentro de otra.

Evidencia privada fuera de Git: D:\DNIeToolchain\LiveCaptures\ead200a74e2b4d40b87e5b1fb2906b08\java-explicit\probe y java-qr-renewed\probe-confirm. La captura permanece activa y las APDU completas no se copian al repositorio.

## Ensayo PACE Android terminado (2026-10-05)

La sesión 7c171c763b6e4e03b331d1838004379d se abrió con el perfil existente de lectura de certificados y PACE local. El probe nativo, con lector explícito y T=1, completó warm reset en 734 ms y recibió IAS v3=9000, PKI Generic=9000, SELECT CE81=9000 y READ BINARY de 16 bytes=9000 (18 bytes incluyendo SW). EndTransaction y Disconnect LEAVE_CARD devolvieron código 0. Se comprobó lectura parcial del archivo, no extracción y validación del certificado completo.

IAS P2=04 devolvió 6985, generado por BridgeReadPolicy: no admite esa forma. Los SELECT D003/EE00/5031 y FFCA del middleware también quedan fuera del perfil. No interpretar esos 6985 como denegaciones físicas del DNI ni este ensayo como prueba de compatibilidad total con IDplug. PACE local funciona para el canal de certificados; la adaptación lógica del middleware queda pendiente.

Después del error de Ciudadano Digital se enumeraron los lectores desde el runtime Java oficial sin conectar ni transmitir: CARD_PRESENT devuelve nuestro puente como índice 0 y Windows Hello como índice 1. El código inspeccionado de DnieComponent conserva el último terminal del bucle CARD_PRESENT. Si ese orden y ambas presencias coinciden durante el flujo oficial, selecciona Windows Hello. Esto es evidencia del riesgo de selección, no una captura de la variable dentro del proceso oficial. Los errores 0000 observados por el programa oficial no son respuestas del DNI en la prueba directa.

Evidencia privada: pace-android/native-probe/native-probe.jsonl y pace-android/java-reader-order/java-probe.jsonl bajo D:\DNIeToolchain\LiveCaptures\ead200a74e2b4d40b87e5b1fb2906b08. Se añadió -ReadersOnly al harness para inspeccionar sin alterar la tarjeta. No se cambió el APK ni el controlador en este ensayo.

Corrección de procedimiento: start-pcsc-session.ps1 no permite preparar dos veces el mismo identificador. Para un nuevo host después de cerrar la sesión se genera otro emparejamiento; no reutilizar su BAT anterior ni borrar el staging para eludir la restricción.
