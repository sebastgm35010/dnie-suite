# ICAO en DNIe Evolucion

## Alcance

Versión `1.1.1-icao-dev`, versionCode 11, paquete `pe.gob.reniec.dnie.evolution.debug`. Desarrollo realizado exclusivamente en este fork. La base validada 1.0.8 y la carpeta de Antigravity no se modifican.

La pestaña **Identidad** pide CAN y abre una sesión propia de lectura. No verifica PIN, no firma y no entra en IAS/PKI. La sesión de firma existente conserva sus fuentes y comandos.

## Bloques independientes

| Bloque | Lectura e interpretación |
| --- | --- |
| EF.COM | Versión LDS y tags anunciados. Si no se puede interpretar, se intentan los bloques soportados individualmente. |
| DG1 | MRZ TD1, TD2 y TD3; nombres, documento, nacionalidad, sexo, nacimiento, vencimiento, emisor y datos opcionales. Comprobaciones de dígitos de control separadas de la lectura. |
| DG11 | Campos personales adicionales; datos UTF-8, fechas ASCII/BCD y nombres repetidos. |
| DG12 | Expedición, autoridad, observaciones y otros campos publicados por el emisor. |
| EF.SOD | CMS/LDSSecurityObject, firma con DSC incluido y hashes de los grupos completos. |
| DG2 | Extracción de imágenes faciales ISO 19794-5 FAC; JPEG, JP2 y codestream JPEG2000. |
| DG5 | Retratos mostrados, si existen. |
| DG7 | Imagen de firma manuscrita, si existe. |
| DG13 | Lectura binaria independiente; interpretación propietaria pendiente. |

Los grupos anunciados en COM que no tienen lector se muestran en el inventario. No se accede a huellas/iris, no se realiza autenticación de terminal y no se implementan todas las aplicaciones opcionales de ICAO.

Cada bloque mantiene estado, contenido interpretado, bytes completos y resultado de hash por separado. Estados: pendiente, leyendo, disponible, ausente, restringido, error e interrumpido. Un `6A82`, `6982`/`6985`/`6986`, un archivo truncado o un error de interpretación afecta ese bloque. Un fallo de transporte/MAC detiene nuevos comandos, marca pendientes como interrumpidos y conserva lo completado.

Los bloques se publican durante la lectura. EF.SOD se lee antes de las imágenes para poder comprobar los datos ya obtenidos aun si la fotografía tarda o se interrumpe.

## Separación de responsabilidades

- `dnie-icao/IcaoModels.kt`: archivos, resultados y contrato de lector.
- `BerTlv.kt`: BER de longitud definida con límites de tamaño/anidamiento.
- `MrzParser.kt`: información MRZ y dígitos de control; conserva las fechas de dos dígitos sin inventar el siglo. El número de documento se muestra sin asumir que sea el número de DNI.
- `IcaoParser.kt`: inventario, campos e imágenes. Los campos binarios desconocidos se indican sin interpretarlos como texto personal.
- `SodVerifier.kt`: firma CMS y comparación de hashes sobre todo el grupo, incluida su cabecera BER.
- `IcaoFileReader.kt`: SELECT/READ, bloques de 223 bytes, longitud BER, offsets efectivos, EOF y corrección `6Cxx` acotada.
- `IcaoDocumentReader.kt`: sesión independiente, publicación progresiva, errores locales y cierre de transporte.
- `app/ui/identity`: presentación, estado/cancelación y codecs Android. El módulo ICAO no depende de Android.

## Sesión y archivos grandes

Secuencia: SELECT ICAO en claro (P2=0C), MF, CardAccess, PACE con los parámetros anunciados, SELECT ICAO protegido (P2=00), archivos. CardAccess usa READ BINARY B1 y desenvuelve DO53, como en los logs previos. No se negocia una suite supuesta si CardAccess no anuncia una compatible.

Los grupos usan B0 hasta el límite de 32 KiB y B1 con DO54/DO53 a partir de ese punto. El offset puede tener tres bytes para archivos superiores a 64 KiB. Se mantiene el canal `PaceSecureChannel` existente, con MAC obligatorio, SSC único y reunión de fragmentos físicos `61xx` antes de desenvolver. No se modificó su implementación.

Límite por archivo: 1 MiB. Las imágenes decodificadas tienen un límite de 4 millones de píxeles y se reducen para presentación cuando el codec lo permite. Si falla la decodificación, el bloque leído se conserva y se puede guardar la imagen original.

## Integridad, confianza y privacidad

La UI distingue:

1. Lectura completa del bloque.
2. Coincidencia o discrepancia con el hash publicado en EF.SOD.
3. Verificación de la firma de EF.SOD usando su certificado incluido.
4. Confianza en el emisor: pendiente, sin almacén CSCA/validación de cadena.

La coincidencia de hashes o la firma correcta del DSC incluido no se presentan como autenticidad plenamente acreditada. Tampoco se comprueba resistencia a clonación del chip. EF.SOD nunca se usa como certificado personal del titular.

MRZ, datos e imágenes permanecen en memoria. El botón **Borrar datos** elimina la referencia al documento en la UI; no promete borrado forense de memoria. Solo la acción explícita **Guardar imagen original** escribe una imagen mediante SAF. Los logs del lector ocultan los datos de respuestas en claro y mantienen estados/tamaños; el log físico del canal seguro contiene las envolturas cifradas.

## Dependencia JPEG2000

`com.github.Tgo1014:JP2ForAndroid:1.0.4`, fork de JP2ForAndroid/OpenJPEG, permite visualizar JPEG2000. JitPack está limitado al grupo de esa dependencia; no se cambiaron versiones de Kotlin/Android del proyecto para incorporar el codec.

La dependencia contiene código nativo. La prueba en el teléfono conectado de páginas de 4 KiB no demuestra compatibilidad con dispositivos de páginas de 16 KiB; esa compatibilidad queda pendiente. Si la carga del codec falla, se muestra el estado de imagen leída y se conserva su exportación. Licencias y procedencia: [THIRD_PARTY_ICAO.md](THIRD_PARTY_ICAO.md).

## Validación

Las 153 pruebas JVM/Android locales pasan: núcleo 41, protocolo 55, ICAO 27, PDF 7 y app 23. Cubren MRZ, imágenes, fechas, BER inválido, hashes alterados, confianza pendiente, errores por bloque, pérdida de NFC, offsets superiores a 64 KiB, cancelación y callbacks tardíos. APK debug compilado y firmado con la identidad de evolución.

Tres pruebas instrumentadas pasaron en el teléfono conectado con Android 16: JPEG y JPEG2000, incluido el motor nativo (`OK (2 tests)`), y navegación a Identidad/solicitud de CAN antes de leer (`OK (1 test)`). El ejecutor UTP de Gradle falló al arrancar y se ejecutaron directamente con `adb shell am instrument`. La lectura del DNI físico todavía necesita probarse con CAN y una nueva aproximación NFC.

APK final instalado en el teléfono conectado. SHA-256 del APK: `D70114EC255DB23DD661EACCE617624F1EC889E85784828C080EDF6599AFD948`. La firma conserva el certificado de desarrollo de evolución, SHA-256 `16cda33b24d0e5f3fea46b766f9e9f198a581721464938bc8068fda398036350`. La base sigue en `0138298e078183a4b729f3f7ca758c3a591d1e9a` y su APK conserva SHA-256 `B5E3754E409A5BED57E01A4810C7FC5F1D0E1597E44C8B1E3E0D4E21A571BA94`.

Para compilar:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot'
.\gradlew.bat :dnie-core:test :dnie-protocol:test :dnie-icao:test :dnie-pdf:testDebugUnitTest :app:testDebugUnitTest :app:exportBranchApk --console=plain
```

Para probar: instalar `app/build/outputs/apk/evolution/dnie-evolution-debug.apk`, abrir **Identidad → Leer DNI**, ingresar CAN y mantener el DNI junto al sensor. Tras la lectura, revisar el estado de cada bloque y, si hay errores, exportar la consola para el diagnóstico. No es necesario facilitar el CAN ni el PIN en mensajes.

## Fuentes

- [ICAO 9303, parte 10](https://www.icao.int/sites/default/files/publications/DocSeries/9303_p10_cons_en.pdf): archivos LDS, grupos de datos, imágenes y comandos para offsets grandes.
- [JP2ForAndroid original](https://github.com/ThalesGroup/JP2ForAndroid) y [fork distribuido](https://github.com/Tgo1014/JP2ForAndroid): codec JPEG2000.
- Logs proporcionados anteriormente por el usuario: evidencia de CardAccess con B1/DO53, PACE IM AES-256 y SELECT ICAO protegido. No se copiaron datos personales a las pruebas ni a la documentación.
