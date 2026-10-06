# Lector virtual existente y tarjeta vacía

Se encontró la instalación previa del usuario: `Bix Virtual Smart Card Reader`, dispositivo `ROOT\SMARTCARDREADER\0000`, estado PnP OK, driver 1.13.48.938 (`oem209.inf`). Windows informa `IsSigned=False`; se reutilizó el dispositivo ya activo sin reinstalar, importar certificados ni cambiar políticas de firma. La configuración existente `C:\Windows\BixVReader.ini` declara un lector contactless, RPC_TYPE=2 y TCP_PORT=35963.

WinSCard enumera `Virtual Smart Card Architecture Virtual PCD Contactless 0`. Se añadió un cliente de tarjeta simulada independiente en `tools/host-diagnostics/dummy-card.py`, sin instalar paquetes ni copiar código de terceros. Implementa el [protocolo público vpcd](https://frankmorgner.github.io/vsmartcard/virtualsmartcard/api.html) con longitud de dos bytes, controles de energía/reset/ATR y respuesta APDU. Se conecta únicamente a 127.0.0.1; no abre un servidor.

La tarjeta usa ATR genérico T=1 `3B80800101`, sin identidad RENIEC, certificados, claves, PACE ni datos personales. SELECT/lectura responden 6A82; autenticación/PIN/firma responden 6985; otras instrucciones 6D00. Nunca devuelve 9000. No registra datos APDU completos: solo cuatro bytes de cabecera, tamaño y SW.

## Resultado real

`probe-virtual-reader.py` seleccionó únicamente el lector virtual, abrió la tarjeta mediante SCardConnectW y transmitió SELECT a través de SCardTransmit. Resultado: protocolo 2 (T=1), respuesta 6A82, conexión correcta. El contexto y el handle se liberaron. Windows también hizo consultas automáticas al insertar la tarjeta; no se atribuyen esas consultas a Ciudadano Digital.

Dos pruebas unitarias cubren rechazo de todas las instrucciones, comandos truncados, rechazo de operaciones sensibles y checksum ATR. No se modificó ni recompiló Android.

La simulación quedó activa por un máximo de 30 minutos desde su arranque. Registro privado: `build/host-diagnostics/dummy-20261005-172856/dummy-card.jsonl`; crear `stop.txt` en esa carpeta retira la tarjeta al cerrar la conexión. El lector instalado permanece disponible. No se detienen otros programas ni se reinicia SCardSvr.

## Límites para el siguiente flujo

Hay lector y tarjeta presentes para PCSC; eso no garantiza que Ciudadano Digital acepte el lector, reconozca la tarjeta o complete validación. Se espera que la tarjeta vacía falle al solicitar funciones de DNI. No se presenta un ATR Perú ni se simula una autenticación exitosa para saltar la validación del sistema.

El driver existente escucha en `0.0.0.0:35963`, según la inspección real, y su enlace vpcd no tiene cifrado propio. Esta configuración solo se usa con la tarjeta vacía; antes de conectar un DNI real habrá que aislar/hacer local este tramo y diseñar el transporte autenticado del puente. No se cambió el firewall ni se expuso un servidor nuevo.

El middleware IDplug está ahora presente en `C:\Program Files (x86)\IDEMIA\IDPlugClassic`; no se atribuye su instalación a este trabajo, que no ejecutó el MSI nuevamente. Falta verificar versión/registro y repetir el flujo del componente con captura activa.
