# Consulta UID del lector y cierre reproducible

Fecha de cliente: 2026-10-05. Versión 1.8.5-reader-uid, versionCode 38.

El segundo archivo dnie-apdu-pace-android.jsonl2.json tiene 24 eventos. Los primeros 12 coinciden exactamente con el anterior y se excluyen. La sesión nueva vuelve a mostrar cinco comandos: cuatro respuestas 6A86 y un fallo en FFCA000000. En la primera sesión el fallo aparece 17 ms después del comando; en la segunda, 11 ms. No hay controles reset/power-off en estas dos sesiones. El host registra las cuatro respuestas y luego EOF. No se dispone de excepción específica en esos archivos y no se confirma pérdida física de NFC.

FFCA000000 es GET UID, una consulta del lector contactless, según [ACS ACR128, sección 6.1](https://downloads.acs.com.hk/drivers/en/API_ACR128.pdf). Antes se enviaba por el canal PACE como si fuera una instrucción del applet, lo que puede producir una respuesta no protegida que el motor rechaza correctamente. Esa explicación es una hipótesis apoyada por la repetición, no una excepción capturada del primer fallo.

## Cambio

ContactlessReaderCard, en bridge-core y sin APIs Android, responde a GET UID usando exclusivamente el Tag.id que proporciona el adaptador Android. No transmite esa consulta al DNI ni altera SSC. El registro APDU envuelve al adaptador de lector, por lo que conserva comando y respuesta local reales. El resto de instrucciones pasa al canal PACE existente.

Solo se aceptan UID Type A de 4, 7 o 10 bytes. Si no están disponibles se devuelve función no soportada. No se fabrican UID ni ATS, ni se interpretan historicalBytes como ATS completo. Un Le insuficiente devuelve 6C con longitud requerida; un comando con longitud distinta de cinco devuelve 6700. GET ATS se declara no soportado. Al cerrar se borra la copia del UID.

BridgeApduTrace añade failureType con la clase de excepción, sin mensajes, secretos ni cuerpos adicionales. Se mantiene la omisión de datos PIN, el archivo privado y la exportación. No se relaja MAC ni se aceptan SW sin autenticación como si fueran respuestas seguras.

Pruebas nuevas: la consulta UID no llama al canal de tarjeta; una instrucción posterior sí lo llama; reset y close se delegan; Le insuficiente, falta de UID y ATS no inventan datos ni tocan PACE. Falta confirmar con DNI real que la sesión sobrevive al GET UID. Los errores independientes de selección de lector en Java oficial y detección de token de IDplug siguen pendientes.

Validación completada: diez pruebas bridge-core, 17 bridge-android ejecutadas (dos optativas omitidas) y dos del registro de la app, sin fallos. Comprobación de arquitectura correcta, APK compilado/exportado y ADB confirmó instalada 1.8.5-reader-uid, versionCode 38. Se preparó captura normal con QR nuevo para el siguiente ensayo real; no se afirma aún que se haya eliminado el corte físico en el dispositivo.

## Resultado real: consulta UID superada y PACE del middleware observado

El archivo dnie-apdu-pace-android3.jsonl.json contiene 140 eventos. Los primeros 24 coinciden exactamente con el archivo anterior y se excluyen. La sesión nueva contiene 116 eventos, 56 comandos y 56 respuestas, sin transport_failed. El usuario informó que apareció una ventana CAN del middleware y que no estaba seguro del valor introducido.

Ocho consultas FFCA000000 devolvieron el UID real con 9000, en 0–1 ms registrados en el teléfono. La lectura del registro confirma que se superó el punto donde las dos sesiones anteriores se cerraban; no demuestra todavía compatibilidad completa.

Se observa por primera vez en estos ensayos ampliados el PACE del cliente PC: MSE 0022C1A4 (secuencia 32) y tres GENERAL AUTHENTICATE 10860000 (33–35) devuelven 9000; la autenticación final 00860000 (36) devuelve 6300. Después aparecen 12 comandos con CLA 0C y respuesta 6A87. El resto incluye lecturas y selecciones exitosas. Totales SW: 38 veces 9000, cuatro 6A86, una 6300, doce 6A87 y una 6A82.

No se puede atribuir 6300 exclusivamente a un CAN incorrecto: la sesión usa PACE local y ahora el middleware también intenta negociar PACE mediante APDU lógicas protegidas por el teléfono. Después envía SM ya protegido, que el adaptador actual vuelve a envolver. Esa arquitectura no traduce las dos sesiones y puede producir incompatibilidad incluso con CAN correcto. Los 6A87 son respuestas lógicas observadas; no se infiere una causa única solo de su SW.

El cierre registrado es power-off solicitado/completado y session_closed después, sin fallo de comando. Coincide con la detención/exportación informada por el usuario. Antes de otro cambio, definir un único propietario PACE: cliente PC directo (con UID del lector adaptado), o teléfono con un cliente PC que use APDU lógicas. Mantener PACE físico Android con clientes que exigen su propio PACE requiere una capa explícita de terminación/traducción, no simplemente desbloquear comandos ni fabricar 9000.

El usuario confirmó después que el CAN introducido en la ventana del middleware era incorrecto. Se prepara un ensayo de control con el CAN correcto y la misma 1.8.5; comparar especialmente el último GENERAL AUTHENTICATE y los comandos SM posteriores antes de atribuir los fallos únicamente a la arquitectura de doble sesión. No se almacena el CAN ni se pide enviarlo al chat.
