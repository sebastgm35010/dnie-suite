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
