# Suite privada continua H2 — 2026-10-07

## Decisión y capas

Solicitud del usuario: PC recordada, conexión de Suite que admite solicitudes sucesivas individuales o por lote, PIN introducido en Windows. El puente PC/SC compatible permanece independiente. No compartir SSC/NFC ni exponer tarjeta global desde Suite.

- `SuitePairing`: confianza persistente DNISUITE2 (UUID, IPv4 privada PC, huella TLS, token). No contiene PIN/CAN/documentos. QR/TXT es credencial de vinculación, no un documento público.
- `SuitePcIdentity` / `WindowsPrivateStore`: identidad TLS y token cifrados con DPAPI CurrentUser, ACL de usuario; no usa PFX del servicio SYSTEM ni certificados personales Windows. La huella/clave persisten al reiniciar; certificado TLS de transporte con validez de diez años. Identidad diferente del certificado DNI.
- `SuiteTrustStorage`: pareja Android cifrada AES-GCM/Keystore, noBackupFilesDir, con Olvidar PC. Almacén separado del puente compatible.
- `PhoneSigningTransport` / `ReverseTlsExchange`: TCP inicia desde PC y TLS sigue autenticado por huella/token. Modo continuo sin timeout de inactividad tras autenticación; los cinco minutos de preparación solo limitan la espera inicial de conexión. No hay conexión automática ni reenvío después de error de transporte.
- `SuitePackets` / `ContinuousSigningConnection` / `ContinuousSigningClient`: sobre exterior H2 BEGIN/READY/FRAME/REPLY/END/ENDED. BEGIN incluye UUID de trabajo, cantidad 1–50 y PIN ASCII de 4–8 dígitos dentro de TLS. El framing interior H1a conserva presupuesto/secuencia y verificación preventiva por operación. UUID de trabajo repetido invalida conexión; máximo 4096 trabajos por conexión, sin historial ilimitado.
- `SuiteCardSlot`: posee transceptor NFC/PACE y lo presta a los trabajos. Fin normal destruye PIN/presupuesto del lote y conserva tarjeta/canal; rechazo/pérdida invalida tarjeta. Un nuevo tag puede reemplazar una tarjeta desconectada entre trabajos. No reemplazar PACE durante un lote ni cerrar un wrapper duplicado del mismo IsoDep.
- Android `ui/suite`: coordinación de consentimiento, pantalla/estados y ciclo foreground. Adaptadores de almacenamiento/NFC en `data/suite`; el protocolo y el ownership no implementan APIs Android.
- PC `continuous/ContinuousSuiteCommand`: adaptador inicial de terminal, cola local de archivos, Console.readPassword sin eco, motor CMS reutilizado. Futura GUI llama al mismo motor/cliente.

## Uso inicial

APK 55 / `2.0.0-rc7-continuous-suite`. Ejecutar desde terminal interactiva con Java 17 (entrada redirigida rechazada, nunca PIN por argumentos):

```powershell
$env:JAVA_HOME='C:/Program Files/Microsoft/jdk-17.0.20.101-hotspot'
& 'D:/DNIeToolchain/Builds/signing-desktop-h0/install/dnie-signing-desktop/bin/dnie-signing-desktop.bat' suite --pc '192.168.1.5' --phone '192.168.1.11' --workspace 'D:/DNIeToolchain/SuiteContinuousJobs' --identity 'D:/DNIeToolchain/SuiteContinuousState/pc.dpapi'
```

Primera vez: la terminal indica `suite-emparejamiento.txt` y huella. Generar/abrir QR mediante `dnie-signing-desktop/tools/make-pairing-qr.ps1 -PairingFile <ruta>`. Android Certificados → Suite → escanear; comparar huella y pulsar Recordar PC y conectar con CAN correcto. En conexiones posteriores: misma identidad PC y Conectar PC guardada en Android, sin importar otro QR. QR/TXT PC se elimina tras conectar/finalizar; no compartir imágenes/capturas de esa credencial.

Conectados: introducir rutas de archivos, una por línea; línea vacía completa la cola y pide PIN en Windows. Un archivo es solicitud individual; varios, un lote. Acercar/mantener DNI si no está disponible. Tras resultados, terminal vuelve a aceptar archivos y Android sigue conectado; PIN nuevo por trabajo. `salir` termina la conexión. Salir/bloquear Android o Cerrar conexión Suite cierra tarjeta y TLS, conserva confianza.

## Fallos y límites explícitos

Sin inactividad automática entre trabajos. Un trabajo pendiente admite hasta dos minutos para presentar el DNI; el endpoint conserva límite de cinco minutos por lote. Cancelación/EOF/respuesta perdida no reconectan ni repiten comandos; consultar manifest/artefactos antes de enviar trabajo nuevo. PIN incorrecto termina ese lote y descarta tarjeta sin reintentar; no ensayar PIN inválido con DNI real. La UI y Windows distinguen lote incompleto.

PC emparejada es extremo de confianza y recibe el PIN; comprometer esa PC compromete la confidencialidad del PIN introducido allí. No se guarda/recuerda el PIN. Se borran buffers mutables propios; VERIFY heredado requiere String y no se garantiza borrado absoluto de heap. Android autoriza la conexión foreground y cantidad por trabajo; documentos siguen en PC y no se muestran en teléfono.

Primer transporte H2: IPv4 LAN/Tailscale mediante TLS inverso; USB privado de Suite y descubrimiento automático siguen pendientes. La identidad actual fija IPv4 PC: si cambia esa dirección, falta migración del endpoint manteniendo identidad; no reemplazar archivos de identidad automáticamente ni reutilizar token con otro certificado. Una sola PC recordada en Android. CLI inicial, no GUI final ni PDF integrado: produce CMS separado, incluso para PDF. No se modifican servicios, firewall, Windows Hello, CertPropSvc o drivers.

## Validación

Pruebas sintéticas H2: dos trabajos sucesivos y dos CMS por la misma conexión TLS con huella fijada y tarjeta simulada; una sola selección física, PIN verificado por trabajo y cierre físico al desconectar. PIN rechazado no repetido, tarjeta nueva exigida para trabajo posterior; UUID duplicado rechazado antes de segunda adquisición. Persistencia DPAPI comprobada al cargar de nuevo, rechazo de manipulación y de cambio de IPv4. Ownership prueba que callback tag duplicado no sustituye PACE y retirada entre trabajos permite tarjeta nueva. No confundir con evidencia física H1b: firma individual y lote 3/3 ya verificados; H2 requiere ensayo con DNI real después de instalar.


Validación final del incremento: build offline satisfactorio, 113 pruebas sin fallos/omisiones (21 link, 15 desktop, 10 signing-core, 67 app), distribución JVM y APK compilados. APK 55 instalado por ADB; SHA256 `760F6BFAD4174B04782212216D961ACAD7FF6441F1BC5F1E6BF44D650A07CDD6`. Ruta: `D:/DNIeToolchain/AndroidPackages/dnie-evolution-2.0.0-rc7-continuous-suite-debug.apk`. Log técnico: `D:/DNIeToolchain/Temp/suite-continuous-final-build.log`. Sin artefactos privados en Git. La prueba física de sesión continua sigue pendiente.


## Evidencia física H2 — 2026-10-07

Usuario ejecutó APK 55 y CLI `suite`, emparejó mediante TXT e introdujo PIN en Windows. Dentro de la misma conexión, primer trabajo `1c8b4261-4718-40c8-b87e-6ec7f8ed5ee3` terminó INTERRUPTED (0/1, `Private signing session ended`), sin firma publicada. Segundo trabajo explícito `e14af241-7f9d-4022-ba05-a4299dcbf33b` terminó SIGNED (1/1 CMS sobre PPTX), backend PRIVATE_ANDROID. .NET SignedCms confirmó integridad, coincidencia de certificado con el trabajo y rechazo al alterar el original; certificado coincide con el DNI previamente verificado. Revocación no comprobada. Evidencia privada en `D:/DNIeToolchain/SuiteContinuousJobs/<UUID>/`, fuera de Git. No abrir/copiar el contenido del documento para diagnosticar.

El registro del primer trabajo no identifica fase física ni causa: no atribuirlo automáticamente a PIN/NFC/CAN. Añadir diagnóstico de fase sin secretos y distinguir conexión PC de conexión DNI. Reconexión posterior sin QR y dos trabajos físicos exitosos seguidos aún pendientes de evidencia.

## Resiliencia NFC y presencia — APK 56 / rc8-suite-nfc

Estado de conexión PC↔Android independiente de `SuiteCardState`: sin NFC, detectado (IsoDep conectado, PACE aún pendiente), preparando PACE, en uso, listo, retirado o fallido. El adaptador abre IsoDep en IO al descubrir tag; una factoría tardía se descarta si Suite fue cerrada. Monitor cada 750 ms consulta conexión nativa, sin APDU, reset, VERIFY ni modificar SSC; no interviene mientras hay operación activa. La API nativa puede demorar en informar una retirada: el estado es el último observado, no una garantía de próxima operación.

Si la tarjeta desaparece en reposo, descartar solo NFC/PACE, conservar TLS e identidad y esperar una nueva aproximación. `acquire` vuelve a esperar si el tag previo ya no está conectado; nunca prestar el transceptor obsoleto a un nuevo trabajo. Si se pierde durante una operación, detener lote y descartar tarjeta, conservar resultados y admitir otro trabajo explícito tras nuevo tag; no repetir operación ambigua.

BEGIN sin tarjeta espera hasta dos minutos; vencimiento devuelve `NOT_READY`, borra PIN y conserva TLS para otra solicitud explícita. Windows informa que no inició ninguna firma. La nueva distribución JVM y APK 56 deben usarse juntos para esta respuesta nueva. Terminar un lote libera presupuesto/PIN y conserva sesión física solo si sigue conectada.

Diagnóstico móvil persistente añade únicamente `card_state`, `job_requested` (cantidad), `operation_result` (OPEN/AUTHORIZE/CERTIFICATE/SIGN_SHA256 y status) y `nfc_connect_failed`; no guarda PIN/CAN/APDU ni nombres/contenido de documentos. UI conserva el mensaje de fase fallida al terminar el lote.

Primer enrolamiento PC nuevo intenta abrir QR automáticamente mediante helper empaquetado en la distribución; si QRCoder no está disponible admite TXT. Identidad PC existente no abre QR repetidamente: usar Conectar PC guardada; el helper manual sigue disponible para reimportar tras Olvidar PC. No requiere elevación ni inicia servicio del puente.


Validación rc8: 116 pruebas pasan sin fallos (23 link, 16 desktop, 10 core, 67 app). Nuevas pruebas: retirada en reposo visible y adquisición pendiente que espera nuevo tag, pérdida de respuesta después de firmar sin repetir operación, timeout sin DNI seguido por otro trabajo dentro del mismo TLS. Distribución JVM actualizada y helper QR incluido. APK 56 compilado e instalado por ADB, SHA256 `75F78B5F87E4CF515D5397A3CC450369AC8545B309AA4D9D3964E6D479462897`; artefacto `D:/DNIeToolchain/AndroidPackages/dnie-evolution-2.0.0-rc8-suite-nfc-debug.apk`, log `D:/DNIeToolchain/Temp/suite-nfc-validation.log`. Retirada/reaproximación física con estados rc8 aún pendiente. Para probar: mismo comando CLI, Conectar PC guardada, acercar/retirar DNI antes de enviar trabajo y confirmar que PC permanece conectada mientras cambia estado DNI.


## Reconexión física con PC recordada y lote rc8 — 2026-10-07

Nueva ejecución CLI muestra la misma huella TLS que la anterior y «PC ya recordada. No necesitas un QR nuevo». El usuario confirma éxito y conexión usando confianza persistente. Trabajo nuevo `129a7109-55b5-4e5f-94be-2c103a0ef342`: PPTX, PNG y PDF, una entrada PIN para tres archivos, `PRIVATE_ANDROID`, `complete=true`, 3/3 CMS separados. La sesión terminó por Ctrl+C del usuario después de completar el lote; no atribuirlo a una desconexión espontánea.

Verificación independiente .NET de las tres firmas: integridad válida, certificado coincide con el trabajo y con el DNI previamente verificado, rechazo al alterar cada original. Revocación no comprobada. Artefactos privados: `D:/DNIeToolchain/SuiteContinuousJobs/129a7109-55b5-4e5f-94be-2c103a0ef342/`, incluyendo `independent-verification.json` y `signed-documents.zip`. El PDF usa CMS separado: esta evidencia no demuestra PDF integrado.

El texto del usuario incluye las dos pruebas anteriores; sus UUID no constituyen nuevos intentos. Queda comprobado el lote físico de tres archivos y persistencia de identidad/reconexión reportada sin QR. La confirmación «éxito» responde a la prueba propuesta de presencia, pero no detalla estados durante retirada/reaproximación: conservar pendiente esa secuencia documentada y dos trabajos exitosos sucesivos dentro del mismo TLS.
