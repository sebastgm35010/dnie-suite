# Registro de cambios y hallazgos: de la copia de Antigravity a la firma funcional por NFC

## Resultado alcanzado

La versión **1.0.8**, `versionCode 9`, lee los tres certificados personales del DNIe v3 probado y firma un PDF por NFC usando el celular. El último log confirma autenticación PACE, PIN aceptado y una firma RSA de 256 bytes con estado `9000`. La firma extraída del log se verificó matemáticamente con la clave pública del certificado de firma y el hash enviado a la tarjeta: **válida**.

El usuario confirmó que el PDF fue aprobado por los verificadores de Certezia y del Estado peruano. Esa aprobación externa fue comunicada por el usuario; aquí se verificó directamente la firma del log, no el archivo PDF ni los reportes externos.

Este registro reconstruye el trabajo de esta conversación desde la copia de la base de Antigravity. Distingue las correcciones implementadas, las exploraciones que no resolvieron el problema y la evidencia que permitió llegar a una app funcional.

## 1. Procedencia y alcance

| Elemento | Referencia |
| --- | --- |
| Carpeta de origen, llamada rama 1/mainline por el usuario | `C:\Users\LENOVO\Documents\antigravity\modest-bose` |
| Carpeta propia donde se hicieron los cambios | `G:\Mi unidad\BOT TRADER\DNI_CODEX\modest-bose` |
| Rama Git observada en ambas carpetas al redactar este registro | `master` |
| HEAD de la copia propia, sobre el que están los cambios locales | `eeee933a014239ea2afc51a46e402c2f36f8b0d5` |
| HEAD observado actualmente en Antigravity | `d63ef7518b8bbef7ea10db1202a59d16dcf314ec` |
| Versión funcional conservada | `1.0.8`, código `9`, APK debug |

La carpeta de Antigravity se consultó para comparar implementaciones y **no se modificó**. Los HEAD anteriores son referencias verificadas al redactar el documento; no prueban que el estado actual de Antigravity sea idéntico al que existía cuando se hizo la copia inicial.

Las correcciones de esta sesión permanecen como cambios locales y archivos nuevos en la copia propia. Este documento no representa una serie de commits ya creada ni atribuye fechas o versiones exactas a etapas para las que no quedó ese dato registrado.

## 2. Qué ya existía en la base

La copia ya contenía un proyecto Android/Kotlin, interfaz de lectura de certificados, consola APDU, verificación de PIN y un motor de firma PDF/PAdES. También existían canales NFC/IsoDep, modelos de certificados, construcción CMS y una primera implementación de PACE y Secure Messaging.

Por tanto, no se construyó toda la app desde cero. El trabajo decisivo consistió en corregir la criptografía y el transporte, identificar la aplicación personal correcta del DNIe v3 y conectar su perfil APDU con el flujo PDF existente.

## 3. Problema inicial

El CAN del usuario era correcto. Sin embargo, la app no conseguía acceder a los certificados personales y aparecían errores de selección, negociación PACE y respuesta segura.

Las primeras aproximaciones mezclaban elementos de perfiles diferentes:

- Aplicación ICAO para datos del documento y aplicación PKI para certificados y firma del titular.
- Parámetros y pasos de PACE Generic Mapping e Integrated Mapping.
- Identificadores de archivos y comandos de firma de v2 con los requeridos por v3.
- Extracción del certificado institucional de EF.SOD como alternativa al certificado personal.

El aprendizaje principal fue que abrir un canal seguro con éxito no demuestra que se haya abierto el contexto de seguridad necesario para la firma personal.

## 4. Correcciones iniciales de PACE y del canal seguro

### 4.1 Criptografía y negociación

Se corrigieron `PaceProtocol.kt` y `PaceHandler.kt`:

- Implementación real de **PACE IM ECDH AES-256**, OID `0.4.0.127.0.7.2.2.4.4.4`, con curva `brainpoolP256r1`, identificador `13`.
- Corrección del mapeo integrado, su PRF y codificación de puntos según la especificación ICAO.
- Separación de GM e IM. En IM se envía el nonce del terminal con tag `81` y se recibe `82` vacío; el acuerdo de claves usa `83/84`.
- Longitud de claves AES derivada de la suite elegida, no inferida del tamaño del nonce. Soporte de 16, 24 y 32 bytes y derivación adecuada para AES-192/256.
- Validación de la curva y de los puntos, pertenencia al grupo y rechazo de reflexión de claves.
- CAN de exactamente seis dígitos ASCII, sin registrar su valor.
- Validación obligatoria del token de tarjeta `86`, de ocho bytes, mediante comparación en tiempo constante. Un `9000` sin token válido no basta.
- Para la ruta ICAO se usó la suite anunciada por EF.CardAccess. Más adelante se añadió un perfil explícito IAS/PKI v3, verificado en Certezia, para ejecutar PACE sin cambiar a MF/ICAO.

Estas correcciones hicieron posible autenticar sesiones con la tarjeta física; se comprobaron también con pruebas criptográficas y un vector oficial de mapeo integrado AES-128.

### 4.2 Sesión y validación de respuestas

Se corrigió `PaceSecureChannel.kt` y su uso en `DnieTransceiver.kt`:

- Retención de una misma sesión segura y de su contador SSC durante las operaciones.
- Verificación de DO99 y DO8E, sus tamaños y su orden, antes de descifrar datos.
- Validación del MAC, del TLV y del relleno; rechazo de respuestas incompletas o manipuladas.
- Invalidación de la sesión cuando ocurre un error que impide continuar de forma consistente.
- Registro separado del comando lógico y del tráfico protegido mediante `SM WIRE`.

La tarjeta real repetía ciertos errores, como `6A82`, tanto en el estado externo como dentro de DO99. Se ajustó la aceptación de esa variante **solo después de verificar el MAC y comprobar que ambos estados coinciden**. No se desactivó la autenticación para ocultar el error.

## 5. Qué se eliminó o dejó de usarse como solución

| Comportamiento anterior o hipótesis | Cambio y motivo |
| --- | --- |
| Extraer el primer certificado CMS/EF.SOD y tratarlo como certificado personal | Se retiró ese fallback de `X509CertificateParser.kt` y de la ruta de certificados. EF.SOD contenía el certificado del emisor del documento, no el del titular. |
| Permitir lectura personal, PIN o firma cuando solo se había encontrado ICAO | Se bloqueó ese uso. ICAO puede servir para datos del documento, pero no demuestra acceso a PKI personal. |
| Interpretar un error externo de Secure Messaging antes de autenticarlo | Se exige verificar DO99/DO8E y su MAC. |
| Aceptar autenticación PACE sin comprobar el token de tarjeta | Se exige tag `86` correcto; se retiró la aceptación flexible que podía producir un falso éxito. |
| Abrir PACE en ICAO y después seleccionar IAS para firmar | Se sustituyó en la ruta v3 por IAS → PKI → PACE → PKI seguro. La ruta ICAO se conserva para el perfil legado y diagnóstico correspondiente. |
| Enviar DigestInfo del perfil legado como entrada de PSO v3 | En v3 se envían los 32 bytes del hash SHA-256. La ruta v2 conserva su construcción existente. |
| Registrar el contenido de VERIFY PIN | `ApduLogger.kt` oculta el PIN en el registro del comando. |
| Suponer ausencia total de PKI por fallos en AIDs o EF.DIR | Los resultados se trataron como fallos en las condiciones probadas, no como prueba de que NFC no permite firmar. |

No se borró el motor PDF/PAdES ni se reemplazó la arquitectura de la app. Se conservó la infraestructura que ya funcionaba y se corrigió el acceso a la tarjeta.

## 6. Exploraciones y comparación con otras ramas

Se añadió `CardDirectoryProbe.kt` para explorar EF.DIR `2F00`, AIDs anunciados y directorios conocidos, con selección y lectura sin PIN. Se contemplaron archivos transparentes y registros y se validó la estructura BER.

En la tarjeta probada, después de PACE ICAO, varios AIDs, EF.DIR y directorios respondían `6A82`. ICAO y EF.SOD seguían accesibles. Estos sondeos ayudaron a delimitar el problema, pero no fueron la solución final.

La comparación de la otra carpeta mostró avances de lectura de DG1 y EF.SOD, pero también una interpretación incorrecta: asignar datos del ciudadano a un certificado institucional o mostrarlo como firma/autenticación/cifrado. Leer datos biográficos no cambia a quién pertenece un certificado. No se trasladó ese comportamiento a la copia propia.

El usuario confirmó que podía firmar con Certezia y Watana usando el mismo DNI y el NFC del celular. Esa evidencia corrigió la hipótesis de que se necesitaría un lector USB con contacto: el problema estaba en nuestro flujo de acceso.

## 7. Documentación oficial y diagnóstico v3

La investigación del DNIe v3 encontró el [manual de comandos RENIEC, documento 2025_0014](https://identidad.reniec.gob.pe/documents/d/guest/manual_comandos_dnie_v3), fechado el 2 de julio de 2025.

El manual aportó los AIDs oficiales IAS/PKI, los archivos CE81/CE82/CE83 y los comandos de PIN y firma. También contenía diferencias entre tablas y ejemplos, y no describía de forma suficiente el establecimiento del contexto NFC/PACE. Se contrastaron sus datos con los logs y con la implementación funcional.

Se añadió `V3PkiProbe.kt` para seleccionar y leer los certificados completos sin introducir PIN. Sus lecturas usan B0, bloques de hasta 223 bytes, avance por el número real de bytes recibidos, control de longitud DER y parsing X.509. Una lectura truncada no se reporta como certificado completo.

El diagnóstico mostró dos hechos importantes:

1. **Sin PACE:** SELECT IAS y SELECT PKI oficiales respondían `9000`, pero los certificados devolvían `6985`.
2. **Con PACE ICAO:** SELECT IAS protegido devolvía `6A82` autenticado, aunque ICAO seguía funcionando.

Durante esta etapa también se corrigió un error de nuestro propio sondeo: entrar en IAS antes de leer MF/EF.CardAccess alteraba el contexto y bloqueaba esa lectura. La versión **1.0.6** restauraba ICAO explícitamente con P2=`0C` antes de la ruta diagnóstica ICAO. Eso resolvió el sondeo, pero todavía no abría PKI personal.

## 8. Hallazgo decisivo: análisis de Certezia

Con autorización del usuario se extrajeron por ADB los APK instalados de **Certezia 0.6.6**, paquete `app.certezia.firma`, código de versión `48`. El análisis se realizó sobre copias de esos binarios; no se modificó Certezia ni se accedió a sus datos privados.

Los primeros nombres y cadenas mostraron módulos específicos `dniev3.dart`, `pace.dart` y funciones IAS/PKI/PACE. Las cadenas por sí solas no demostraban el orden de ejecución.

Para reconstruir el flujo compilado Dart AOT se preparó [Blutter](https://github.com/worawit/blutter) en Debian/WSL para Dart `3.12.1`, Android ARM64. Se recuperaron funciones y constantes APDU. No se copió el motor de negocio de Certezia; se implementó el protocolo identificado en nuestro código Kotlin.

La secuencia observada fue:

1. Seleccionar IAS en claro.
2. Seleccionar PKI en claro.
3. Enviar MSE PACE y ejecutar PACE **en ese contexto**, sin seleccionar ICAO ni MF entre esos pasos.
4. Usar mensajería segura para certificados y firma, con reselección PKI protegida antes de PIN/firma.

En nuestra implementación la reselección PKI protegida se hace inmediatamente al completar PACE y se mantiene ese DF durante las lecturas.

Se añadió `V3PkiSession.kt` y se integró tanto en el flujo normal como en el diagnóstico. La versión **1.0.7** confirmó con la tarjeta real la lectura de los tres certificados, la aceptación del PIN y la configuración de firma.

### Perfil APDU v3 aplicado

| Operación | Comando o dato clave |
| --- | --- |
| SELECT IAS en claro | `00A4040C10A000000077030C60000000FE00000500` |
| SELECT PKI en claro; también como comando lógico protegido tras PACE | `00A4040C0EE828BD080FD25047656E65726963` |
| MSE PACE IAS/PKI | `0022C1A412800A04007F0007020204040483010284010D` |
| Suite PACE | IM ECDH AES-256, OID `0.4.0.127.0.7.2.2.4.4.4`, curva `13`, referencia CAN `02` |
| Certificado de firma | CE82; SELECT lógico `00A4020C02CE82`, READ BINARY B0 protegido |
| Certificado de autenticación | CE81 |
| Certificado de cifrado | CE83 |
| VERIFY PIN v3 | `002000030C` + PIN ASCII rellenado con `FF` hasta 12 bytes; formato admitido por la app: 4–8 dígitos ASCII |
| MSE de firma | `002241B606840182800142`; clave `82`, algoritmo `42` |
| PSO de firma | `002A9E9A20` + 32 bytes de hash SHA-256; Le=256 |

Los comandos posteriores a PACE se envuelven en Secure Messaging. La tabla muestra sus APDU lógicos, no el texto en claro transmitido para esas operaciones.

## 9. Último bloqueo: recoger la respuesta completa de firma

En el log de **440 registros, 19:49–19:50**, la versión 1.0.7 avanzó hasta PSO, pero terminó con `TLV truncado o duplicado`.

La tarjeta había devuelto los primeros 256 bytes de la respuesta protegida y estado externo **`6123`**. Ese estado indicaba que quedaban **35 bytes**. La envoltura segura de una firma RSA de 256 bytes ocupaba 291 bytes de datos: incluía el cifrado con relleno y DO87/DO99/DO8E. Nuestro código intentaba analizarla antes de recoger el resto.

El análisis de `transceiveApdu` en Certezia confirmó una continuación física `00 C0 00 00 SW2`, anterior al desenvolvimiento de Secure Messaging.

La versión **1.0.8** corrigió `PaceSecureChannel.kt`:

- Reúne los fragmentos externos `61xx` antes de analizar los objetos seguros.
- Para `6123`, envía **`00C0000023`** al canal físico.
- Ese GET RESPONSE recoge parte de la misma respuesta: no se envuelve como un nuevo comando seguro ni incrementa SSC.
- Verifica el MAC y descifra la respuesta completa una sola vez.
- Conserva límites de tamaño y número de continuaciones y rechaza respuestas truncadas o manipuladas.

Esta fue la última corrección necesaria para la firma funcional observada.

## 10. Identidad propia del APK y herramientas necesarias

Para permitir instalar esta app junto con las de otras ramas se modificaron `app/build.gradle.kts` y `strings.xml`:

- Nombre visible: **DNIe Modest Bose**.
- Application ID base: `pe.gob.reniec.dnie.modestbose`.
- Paquete debug: `pe.gob.reniec.dnie.modestbose.debug`.
- Namespace Kotlin conservado: `pe.gob.reniec.dnie`.
- Keystore de desarrollo propio: `signing/modest-bose-development.jks`, excluido de Git. Debe conservarse para actualizar esta instalación con la misma firma.
- Tarea `exportBranchApk`, que exporta `dnie-modest-bose-debug.apk` a `app/build/outputs/apk/modest-bose`.
- Versión final: `1.0.8`, `versionCode 9`.

Las herramientas necesarias para compilar fueron Android SDK y JDK 17. ADB permitió extraer los APK instalados y distribuir nuestras compilaciones. WSL/Blutter fue necesario para esta investigación del binario; **no es una dependencia de ejecución de nuestra app ni del proceso habitual de compilación Android**.

## 11. Inventario de archivos cambiados

Las rutas de esta tabla son relativas a la raíz de la copia propia.

| Archivo | Tipo de cambio | Responsabilidad |
| --- | --- | --- |
| `app/.../core/crypto/PaceProtocol.kt` | Modificado | IM, derivación y validación criptográfica. |
| `app/.../core/nfc/PaceHandler.kt` | Modificado | Pasos PACE, token obligatorio y perfil IAS explícito. |
| `app/.../core/nfc/PaceSecureChannel.kt` | Modificado | Sesión, MAC/TLV, estado externo y continuación física `61xx`. |
| `app/.../core/nfc/DnieTransceiver.kt` | Modificado | Integración v3, conservación del canal, EFs y selección de comandos por perfil. |
| `app/.../core/nfc/V3PkiSession.kt` | Añadido | Orden IAS/PKI/PACE y comandos de PIN/firma v3. |
| `app/.../core/nfc/V3PkiProbe.kt` | Añadido | Lectura completa y diagnóstico de certificados CE81/82/83. |
| `app/.../core/nfc/CardDirectoryProbe.kt` | Añadido | Exploración de aplicaciones y directorios sin PIN. |
| `app/.../core/logging/ApduLogger.kt` | Modificado | Ocultación del PIN. |
| `app/.../crypto/X509CertificateParser.kt` | Modificado | Eliminación del fallback CMS/EF.SOD como certificado personal. |
| `app/build.gradle.kts` | Modificado | Paquete, firma propia, versiones y exportación APK. |
| `app/src/main/res/values/strings.xml` | Modificado | Nombre de la app. |
| `.gitignore` | Modificado | Exclusión del keystore de desarrollo y artefactos locales correspondientes. |
| Pruebas `PaceProtocolTest.kt` y `DnieTransceiverTest.kt` | Modificadas | Regresiones criptográficas y de integración. |
| Pruebas `PaceHandlerTest.kt`, `PaceSecureChannelTest.kt`, `CardDirectoryProbeTest.kt`, `V3PkiProbeTest.kt`, `V3PkiSessionTest.kt` | Añadidas | Contexto, autenticación, lectura y fragmentación segura. |

En las rutas abreviadas de producción, `app/.../` corresponde a `app/src/main/java/pe/gob/reniec/dnie/`. Las pruebas están bajo `app/src/test/java/pe/gob/reniec/dnie/`.

## 12. Validación y evidencia final

La última compilación completó **120 pruebas, cero fallos y cero errores**. Entre las verificaciones relevantes están:

- PACE IM completo y rechazo de token ausente o incorrecto.
- Selecciones IAS/PKI antes de PACE y reselección protegida usando las claves negociadas.
- Rechazo de MAC falsificado, estados inconsistentes y sesiones inválidas.
- Lecturas de certificados completas, offsets reales, corrección `6Cxx` y manejo de datos truncados.
- Respuesta RSA protegida dividida exactamente en 256 + 35 bytes, continuaciones múltiples, SSC correcto en el siguiente comando y rechazo de fragmentos manipulados o ausentes.

El log final de **265 registros, 19:57:45–19:58:07**, confirma:

| Paso real | Resultado |
| --- | --- |
| PACE IAS/PKI | Token de tarjeta válido, canal AES-256 establecido. |
| Certificado CE82 | X.509 completo, 2403 bytes. |
| Certificado CE81 | X.509 completo, 2346 bytes. |
| Certificado CE83 | X.509 completo, 2321 bytes. |
| VERIFY PIN `03` | `9000`. |
| MSE firma | `9000`. |
| PSO inicial | `6123`, continuación pendiente. |
| GET RESPONSE físico | Recoge 35 bytes y termina en `9000`. |
| PSO reunido, autenticado y descifrado | `9000`, firma de 256 bytes. |
| Verificación local con certificado CE82 y hash PSO | RSA-2048/SHA-256/PKCS#1 v1.5 válida. |
| Verificación del PDF en Certezia y Estado peruano | Aprobada según confirmación del usuario. |

No fue necesario recuperar ninguna clave privada: la operación privada de firma se ejecuta en el chip del DNI.

## 13. Referencias conservadas y compilación reproducible

- [Informe del análisis de Certezia](<G:/Mi unidad/BOT TRADER/DNI_CODEX/analysis/certezia-0.6.6/INFORME.md>).
- [Registro de validación de la versión 1.0.8](<G:/Mi unidad/BOT TRADER/DNI_CODEX/analysis/validated-1.0.8/VALIDACION.md>).
- [APK funcional conservado](<G:/Mi unidad/BOT TRADER/DNI_CODEX/analysis/validated-1.0.8/dnie-modest-bose-1.0.8-debug.apk>).
- Log de lectura/PIN y fallo previo de firma: `C:\Users\LENOVO\.codex\attachments\a17242f9-4485-40d6-bd64-0ae44da6cde4\Texto pegado.txt`.
- Log de firma exitosa: `C:\Users\LENOVO\.codex\attachments\e9194c17-f2b2-47a6-81b9-5463d112372f\Texto pegado.txt`.

SHA-256 del APK funcional conservado:

```text
B5E3754E409A5BED57E01A4810C7FC5F1D0E1597E44C8B1E3E0D4E21A571BA94
```

Desde la raíz de la copia propia, en PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot'
.\gradlew.bat :app:testDebugUnitTest :app:exportBranchApk --console=plain
```

## 14. Hallazgos que deben conservarse en el trabajo siguiente

1. **El orden y el contexto de selección son esenciales:** IAS → PKI → PACE → PKI seguro. Un PACE ICAO correcto no sustituye al PACE del contexto PKI.
2. **Los certificados del titular están en CE81/CE82/CE83.** EF.SOD y DG1 no sustituyen al certificado personal.
3. **El perfil v3 tiene sus propios comandos:** PIN `03` con relleno a 12 bytes, clave de firma `82`, algoritmo `42` y hash de 32 bytes en PSO.
4. **La respuesta segura debe estar completa antes de verificarla.** GET RESPONSE físico para `61xx` recoge fragmentos de una misma respuesta y no avanza SSC.
5. **No relajar la autenticación para hacer desaparecer errores.** Mantener token PACE, MAC, DO99/DO8E y control de sesión.
6. **La versión 1.0.8 es la referencia funcional probada.** Conservar APK, keystore, registros y pruebas al introducir cambios nuevos.

El resultado está confirmado para el DNIe v3 y celular usados en estas pruebas. La ruta v2 conserva cobertura de pruebas simuladas, pero esta sesión no valida firma física con un DNIe v2 ni todos los modelos de celular. Los cambios actuales aún deben registrarse en Git si se quiere una referencia de código versionada además del APK y de este documento.
