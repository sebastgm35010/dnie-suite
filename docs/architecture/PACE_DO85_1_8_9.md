# Corrección de DO85 y textos del puente — 1.8.9

La prueba real de 1.8.8 en modo `pace_android_translated` avanzó hasta el comando 77. El comando protegido `0C CB 3F FF` llevaba DO85 (16 bytes), DO97 extendido y DO8E. El decodificador solo aceptaba DO87 y rechazó el sobre con `IllegalArgumentException` aproximadamente 2 ms después; el transporte se cerró a continuación. Esta captura no demuestra una pérdida física NFC.

DO85 contiene un criptograma sin el indicador de relleno de DO87; **no es texto en claro**. Para instrucciones impares, ISO/IEC 7816-4:2005 §6.3 contempla la encapsulación de datos BER-TLV con DO85. Referencia: [documento ISO, copia pública](https://www.freecalypso.org/pub/GSM/ISO7816/ISO_7816-4_2005.pdf).

Cambios:

- El extremo PACE del PC admite DO85, autentica su MAC antes de descifrarlo y rechaza la coexistencia DO85/DO87. Conserva SSC, relleno y Le extendido.
- El canal físico genera DO85 para instrucciones impares y admite respuestas DO85 autenticadas. El extremo traducido responde con DO85 para esas instrucciones. Las instrucciones pares conservan DO87.
- Prueba con dos sesiones independientes y GET DATA extendido: mismo comando lógico recibido por el chip simulado, respuesta DO85, contadores utilizables en el siguiente READ. Prueba de alteración de DO85: rechazo sin envío al chip.
- Recuperadas las cadenas de texto de BridgeScreen y BridgeViewModel dañadas por interpretaciones repetidas de UTF-8 como Windows-1252. Las ediciones de texto usan UTF-8 explícito.

No se modificaron los controladores oficiales ni la base clausurada. La compatibilidad con IDplug y Ciudadano Digital requiere otra prueba real; resolver este fallo de formato no garantiza que sus siguientes comandos sean compatibles.

Captura privada: `D:\DNIeToolchain\LiveCaptures\d8f4c208ba014b2392f56e605f89b193`. No incorporar registros personales a Git.

Validación: compilación offline correcta en 1 min 50 s. Protocolo, bridge-core, bridge-android y app: 170 pruebas aprobadas, 2 de interoperabilidad omitidas, cero fallos. `tools/check_architecture.py` aprobado. Revisión de fuentes del puente sin patrones de codificación dañada. APK generado en `app/build/outputs/apk/debug/app-debug.apk` con versionCode 42. Instalación pendiente: ADB muestra `sebas-poco:45539` offline y rechaza reconexión al nombre y a `192.168.1.11:45539`; se solicitó el nuevo puerto al usuario.
