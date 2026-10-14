# QR funcional en la Suite Windows · R1

2026-10-13, America/Bogota. Checkpoint previo `checkpoint/pre-suite-qr-r1`. El botón Abrir QR ya existía, pero dependía del script PowerShell, del QRCoder instalado por Bridge y de crear un PNG con CreateNew; reabrir un PNG existente fallaba. Además permanecía desactivado antes de preparar una conexión.

## Flujo vigente de la GUI

1. Indicar IP de Android y revisar la dirección PC.
2. Pulsar **Abrir QR**: prepara la misma conexión privada que Conectar Android, con la identidad existente, y muestra el QR en una ventana local.
3. En Android, Suite → **Escanear solicitud de la PC**. Comparar huella y recordar la PC.
4. Mientras se espera Android se puede cerrar y volver a abrir el QR. Cerrar la ventana QR no cancela la conexión; **Desconectar** sí la cancela.
5. Al autenticar Android, cancelar o cerrar Suite, se retira el QR. En conexiones posteriores se puede seguir usando Conectar PC guardada.

`PairingQrImage` valida el TXT DNISUITE2 y su huella, limita la lectura a 1024 bytes y genera una imagen exclusivamente en memoria. `PairingQrDialog` posee su ventana, generación asíncrona y retirada; limpia los píxeles al cerrar y descarta resultados tardíos. SuiteWindow solo coordina estado/acciones. No se sube el emparejamiento a un servicio QR ni se crea un PNG persistente. El archivo TXT privado y su limpieza siguen perteneciendo a DesktopSuiteSession.

Dependencia de presentación: [Project Nayuki QR Code generator](https://github.com/nayuki/QR-Code-generator), `io.nayuki:qrcodegen:1.8.0`, MIT. Licencia de esa versión tomada del sources.jar publicado en Maven Central y distribuida en `licenses/qr/NAYUKI-MIT.txt`. ZXing se usa solo en tests para decodificar independientemente el QR generado. La GUI ya no incluye el script de QR. El helper histórico de consola permanece separado y sigue pendiente de modernización.

## Bloqueo adicional corregido

Se observó la Suite anterior sin ventana, con `suite-bridge-control` bloqueado en `WindowsBridgeGateway.call → readNBytes(257)` tras open-manager y `suite-ui-jobs` esperando el lock del coordinador. El gestor nativo había heredado stdout: salir del adaptador no cerraba esa tubería. Esto podía impedir preparar una conexión y, por tanto, mostrar QR.

WindowsBridgeGateway ahora, después de verificar salida y plazo del adaptador, lee solo los bytes ya disponibles de su respuesta terminada en LF, acotada a 256 bytes. No espera EOF de un descendiente. Mantiene los verbos permitidos y la decodificación estricta de estados; no modifica el protocolo PC–Android ni las operaciones de firma.

## Verificación y entrega

- 7 tests UI, 29 escritorio y 9 orquestación: 45 aprobados, sin omitidos ni fallos.
- Decodificación del QR a exactamente DNISUITE2, huella incorrecta, tamaño/datos inválidos, reapertura sin PNG y limpieza de píxeles.
- Prueba del botón Swing real: crea identidad DPAPI de prueba en carpeta temporal, inicia espera local sin teléfono/DNI, abre/cierra/reabre QR y lo retira al desconectar. No usa la identidad real del usuario ni confirma emparejamiento Android físico.
- Prueba Windows con proceso hijo que conserva stdout durante cinco segundos: la llamada termina antes de tres segundos. Otras pruebas rechazan respuesta parcial, vacía, excesiva o múltiple.
- GUI/CLI compiladas, arquitectura y diff comprobados. Resultados: [QR_SUITE_R1_RESULTADOS.json](../operations/QR_SUITE_R1_RESULTADOS.json).

Distribución GUI: `D:/DNIeToolchain/Builds/suite-qr-r1/signing-ui/install/dnie-signing-ui`. El acceso directo del escritorio **DNIe Suite** apunta aquí y se abrió esta versión. La ventana anterior se cerró; su proceso antiguo, ya sin ventanas ni conexiones y bloqueado en la lectura descrita, se retiró. El gestor Bridge y el servicio continúan independientes. Android y el controlador no se actualizan en esta iteración.
