# Traducción PACE/SM: primer perfil experimental

2026-10-06. Versión prevista 1.8.8-pace-translation, versionCode 41. El núcleo de firma/certificados conserva su apertura IAS → PKI → PACE → PKI protegido. La traducción es una opción separada del puente, desactivada por defecto; no modifica el modo raw de comparación.

## Inspección de IDplug

Se inspeccionaron los binarios instalados 1.8.0.459 con dumpbin, sin modificarlos ni copiar código propietario al proyecto. PeruMinidriver64.dll exporta CardAcquireContext y usa carga dinámica; idplug-peru-minidriver64.dll exporta CardAcquireContext y referencia WinSCard.dll con SCardTransmit, SCardControl y SCardReconnect. Se generó desensamblado del wrapper pequeño y listados de importaciones/exportaciones privados en la captura be2a11014ad446ef848eab085bdb3d23. No se dispone del fuente original ni de símbolos privados de IDplug; la inspección estática no demuestra por sí sola qué función envía cada APDU.

La captura ETW previa mostró esas dos DLL cargadas dentro de CertPropSvc PID 2716. Por tanto el cliente puede existir sin IDplug UI abierto. La secuencia registrada se usa como contrato observado: MSE perfil PACE IM AES-256/CAN/curva 13, GA nonce/mapeo/clave/token y SELECT protegido. No suponer almacenamiento del CAN.

## Arquitectura

Windows ↔ PACE/SM terminal ↔ Android traductor ↔ APDU lógica ↔ PACE/SM físico ↔ DNI.

Las dos sesiones tienen claves, nonces, claves efímeras y SSC independientes. PACE del PC termina en el motor JVM PaceTranslationEndpoint y nunca se reenvía al chip. El canal físico se abre primero con la ruta funcional PaceBridgeChannel. TerminalSecureMessaging valida MAC/SSC, descifra el comando, lo entrega al canal lógico real y protege la respuesta autenticada. Las consultas previas al PACE pasan por el canal lógico ya protegido por Android.

No se simula una firma, certificado ni resultado de operación del DNI. Las respuestas de autenticación del PC son generadas por el extremo criptográfico del gateway, con nonce fresco y verificación del token, después de abrir PACE físico. Esto no es prueba de PACE directo entre PC y chip ni satisface automáticamente un protocolo que vincule identidad del chip a sus claves efímeras.

Perfil inicial: IM AES-256, brainpoolP256r1/13 y contraseña CAN. Perfiles diferentes reciben 6A80. Tres fallos de autenticación/malformación limitan posteriores negociaciones de esa instancia. PACE anidado dentro del SM establecido se rechaza con estado autenticado 6A80: no se reenvía y no cambia claves físicas. PIN y otras APDU lógicas posteriores siguen hacia el DNI, sin validar PIN ni firmar localmente.

Las respuestas SM largas se fragmentan con 61xx cuando supera Le; GET RESPONSE del PC vacía la misma respuesta autenticada sin avanzar SSC ni enviar un GET RESPONSE adicional al DNI. El GET RESPONSE físico necesario para el canal del DNI sigue siendo responsabilidad del motor existente.

Reset/power-off retiran el contexto del PC y reconstruyen el canal físico antes de volver a autorizar PACE del PC. Cierre borra buffers de claves, nonce, CAN y continuaciones del gateway. Los escalares BigInteger de Bouncy Castle son inmutables: se retiran referencias, no se promete borrado completo del heap. No se persisten claves ni se registran secretos. El registro APDU identifica pace_android_translated, incluido opening_failed.

Protocolo de referencia: [BSI TR-03110](https://www.bsi.bund.de/EN/Themen/Unternehmen-und-Organisationen/Standards-und-Zertifizierung/Technische-Richtlinien/TR-nach-Thema-sortiert/tr03110/TR-03110_node.html), partes 2 y 3; SM requiere contexto/SSC y verificación autenticada, no basta con eliminar CLA 0C. Implementación propia sobre el motor existente, sin incorporar fuente de IDplug.

## Pruebas y límites

Pruebas JVM con PaceHandler como cliente: autenticación mutua completa, comando/respuesta exactos, múltiples SSC, estado de aplicación autenticado, payload extendido, respuesta de 256 bytes con continuación, CAN incorrecto, MAC alterado, replay, dos sesiones cifradas encadenadas y rechazo de PACE anidado manteniendo el canal previo.

Esta batería no sustituye vectores externos independientes ni compatibilidad real con el minidriver. Falta observar si Windows verifica el token, avanza por SELECT/READ y qué variantes posteriores solicita. No reinstalar el controlador ni relajar MAC/SSC para superar un fallo. Pendiente validar también reset/power-off del wrapper de traducción y el flujo físico de certificados/firma a través de Windows.

Validación completada: 71 pruebas protocol, 10 bridge-core, 20 bridge-android aprobadas (2 interop omitidas) y 67 app: 168 aprobadas, cero fallos. Incluye dos pruebas nuevas del wrapper: power-off reconstruye físico antes de reautenticar PC y reset rechaza claves PC antiguas antes de transmitir al DNI. Límites de arquitectura correctos. APK ensamblado e instalado por ADB Success; dumpsys confirma 1.8.8-pace-translation (41). No se cambiaron drivers, servicios ni firewall.

Captura real preparada: emparejamiento ac35feda645f46debb625991ef90f884; D:\DNIeToolchain\LiveCaptures\d8f4c208ba014b2392f56e605f89b193. El usuario debe dejar desactivado PACE en Windows/raw, activar «Traducir PACE del middleware · experimental», comprobar CAN local y usar nueva aproximación del DNI. Primero observar cliente automático antes de abrir UI IDplug. Resultado físico pendiente.

Limitación adicional inicial: SM se valida para CLA 0C/1C (canal lógico base) y TLV canónicos DO87/DO97/DO8E. No se promete canal lógico suplementario, 3DES, GM, chip authentication ni PACE anidado. Los listados/disassembly de DLL siguen privados en D:, no forman parte de la implementación.
