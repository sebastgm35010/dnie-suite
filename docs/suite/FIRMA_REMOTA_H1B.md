# Firma remota privada — primer recorrido H1b

Versión Android candidata: 2.0.0-rc6-remote-signing / 54. Este recorrido es independiente del lector PC/SC y del servicio SYSTEM; no instala ni publica tarjeta para Windows. El puente compatible con ReFirma permanece separado.

## Recorrido implementado

La CLI `remote` valida/importa originales en la PC y reutiliza BatchSigner/CMS. Genera identidad TLS efímera para este trabajo y solicitud con UUID, IP de PC, caducidad, cantidad, huella y token aleatorio. La solicitud no contiene PIN/CAN ni datos del documento. Se guarda como TXT con permisos de usuario; no se imprime el token en consola.

Android tiene acceso **Certificados → Suite**, escaneo QR o importación TXT, huella visible, CAN heredado de pantalla principal y entrada PIN local. Autorizar prepara el receptor TCP (39446), autentica TLS con huella fijada, entrega el token dentro de TLS y habilita la aproximación NFC. El certificado TLS de la PC no es el certificado del DNI.

La PC inicia TCP al teléfono y actúa como servidor TLS dentro de esa conexión. Esto evita requerir un listener o una regla de firewall nueva en Windows. El teléfono acepta únicamente la IP indicada y verifica el certificado; PC comprueba token/servicio `DNISIGN1`. Tras autenticar, UUID/secuencias y presupuesto del contrato privado controlan las solicitudes. Solo se repite una conexión TCP fallida antes de TLS/autorización; nunca una APDU, PIN ni firma.

El endpoint Android abre el transceptor comprobado (IAS → PKI → PACE → PKI protegido), verifica PIN antes de cada operación privada y devuelve certificado/firma. No hay PACE emulado para PC, APDU libre, reset ni PCSC en esta ruta. Archivos, atributos CMS, ZIP y resultados permanecen en PC. El motor comprueba RSA contra el certificado antes de publicar.

Salir/bloquear/cancelar cierra transporte y tarjeta, borra copia mutable PIN e invalida solicitud. El PIN API heredado todavía crea Strings; no se promete su borrado absoluto. Un rechazo de PIN se informa sin repetir. La UI no afirma verificar visualmente documentos que nunca recibe.

## Prueba física

1. Instalar APK 54; abrir Certificados → Suite. Mantener esa pantalla y comprobar IP Wi-Fi.
2. Ejecutar en PC, con Java 17, el launcher empaquetado:

```powershell
$env:JAVA_HOME='C:/Program Files/Microsoft/jdk-17.0.20.101-hotspot'
& 'D:/DNIeToolchain/Builds/signing-desktop-h0/install/dnie-signing-desktop/bin/dnie-signing-desktop.bat' remote --pc '192.168.1.5' --phone '192.168.1.11' --workspace 'D:/DNIeToolchain/SuiteJobs' --input 'D:/ruta/documento.txt'
```

3. La CLI muestra ruta TXT y huella. Importar TXT al teléfono por canal de confianza o generar QR con `dnie-signing-desktop/tools/make-pairing-qr.ps1 -PairingFile <ruta mostrada>`. Reutiliza QRCoder instalado, no inicia puente. El QR contiene token efímero: solo mostrar localmente.
4. Comparar huella, CAN correcto, PIN correcto y Autorizar este lote. Aproximar DNI cuando indique PC autenticada; mantenerlo hasta terminar. No operar un puente NFC simultáneo.
5. PC imprime carpeta del trabajo y resultado; verificar con `dnie-signing-desktop/tools/verify-job.ps1 -JobDirectory <carpeta>`. No prueba revocación en línea.

## Límites de esta entrega

Primera entrega CMS separado, incluso para PDF. No firma PDF integrado, no GUI PC final, no identidad permanente ni emparejamiento automático por tailnet. La solicitud/identidad es nueva por trabajo y dura cinco minutos; los originales se copian antes de abrir la tarjeta. Hay límites acotados de IO para este prototipo. TLS inverso acepta IPv4 privada LAN o tailnet; la primera prueba física LAN se completó correctamente. USB privado todavía pendiente: no confundir con USB del puente compatible.

Pruebas: codec/endpoint/sesión existentes, parsing estricto y sockets TLS reales con tarjeta simulada, dos CMS verificados con certificado sintético, rechazo de huella y token, argumentos inválidos. No confundir estas pruebas con una firma física DNI. La primera firma real y su verificación independiente se completaron el 2026-10-07; evidencia a continuación.

Validación del incremento: 17 tests de link, 12 de escritorio, 28 del puente Android (dos omisiones opcionales), 10 signing-core y 67 app; sin fallos/errores. Build APK Debug e instalación ADB confirmados, versionCode 54. SHA256 APK: D3EE73E7B81E558EBC41B3734916B8764A1CCCA9898C11FB06A31186366BCE85. Logs/artefactos privados en D:, no Git.


## Evidencia física confirmada — 2026-10-07

APK 54 instalado. El usuario importó la solicitud TXT, autorizó en Android y comunicó «Terminó la sesión». El proceso PC terminó con 1/1 archivos firmados, trabajo `93d563c9-b2d5-4f11-a34b-ef7d714144e9`, backend `PRIVATE_ANDROID`, `complete=true`. Los originales permanecieron en PC y el DNI real ejecutó la operación por NFC. La ruta nueva no utiliza PC/SC ni servicio SYSTEM; no se desinstalaron los controladores existentes para esta prueba.

Verificación independiente .NET SignedCms: integridad válida, certificado embebido coincidente con el certificado del trabajo y del DNI previamente verificado/instalado en Windows, y rechazo al modificar el original en memoria. No se evaluó revocación en línea ni confianza completa de cadena.

Evidencia privada fuera de Git:

- Original: `D:/DNIeToolchain/SuiteRemoteH1b/prueba-firma-remota.txt`.
- Trabajo y `independent-verification.json`: `D:/DNIeToolchain/SuiteRemoteH1b/jobs/93d563c9-b2d5-4f11-a34b-ef7d714144e9`.
- Firma (3101 bytes): `signed/b4a0074a-abc3-4b80-bd34-e8b6ed8d0897/prueba-firma-remota.txt.p7s` dentro del trabajo.
- ZIP: `signed-documents.zip` dentro del trabajo.
- Salida CLI: `D:/DNIeToolchain/SuiteRemoteH1b/desktop.stdout.log`; stderr vacío.
- APK: `D:/DNIeToolchain/AndroidPackages/dnie-evolution-2.0.0-rc6-remote-signing-debug.apk`.

Solicitud de emparejamiento PC eliminada al finalizar. No reutilizar token/UUID ni PID histórico. Próximos ensayos físicos: varios originales en un lote, cancelación/pérdida y reconexión con autorización nueva. Después, identidad permanente de PC y PDF integrado; USB privado permanece pendiente.
