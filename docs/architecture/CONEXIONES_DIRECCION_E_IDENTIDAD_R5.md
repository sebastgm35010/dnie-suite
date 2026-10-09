# Conexiones R5: dirección variable, identidad estable

2026-10-09. Checkpoint previo: `checkpoint/pre-address-mobility-apk76`.

## Comportamiento

Suite y el puente Wi-Fi inverso aceptan la conexión de la PC recordada aunque cambie su IPv4 privada por DHCP, otra interfaz o Tailscale. La dirección del TXT/QR antiguo queda como referencia; no autentica al equipo. No requiere otro QR ni regenerar claves al cambiar de dirección. Windows todavía necesita conocer la IP vigente del teléfono para dirigir la conexión.

En el transporte TLS directo antiguo, Android ofrece editar la IP de destino conservando huella, token, puerto, vencimiento y modo de transporte. Tailscale mantiene su nombre/IP editable y su política de resolver solo direcciones tailnet; no se convierte tráfico LAN en texto claro.

## Separación de capas

- `dnie-signing-link/ReverseTlsPeer`: política compartida para limitar receptores inversos a IPv4 privadas/tailnet; no acredita confianza.
- `PhoneSigningTransport`: usa la dirección del socket aceptado, verifica certificado vigente/huella SHA-256, después token. Publica la IP actual solamente tras autenticar.
- `BridgeTransportAdapter`: recibe el socket local sin comparar contra la IP antigua. `PinnedBridgeClient` verifica la huella y no resuelve un destino histórico cuando el socket ya existe. USB conserva TLS sobre el accesorio.
- `SuitePcIdentity`: restaura la misma identidad DPAPI, privada TLS y token. Devuelve un emparejamiento con la dirección vigente sin modificar el archivo de identidad ni renovar el certificado.
- Compose presenta estados y emite acciones; `BridgeViewModel` aplica una nueva dirección al TLS directo sin sustituir credenciales. `RemoteSigningViewModel` presenta la IP autenticada en la sesión.
- `RemoteSigningScreen` sustituye el formulario por `SuiteSessionScreen` desde la solicitud de conexión. PC y DNI tienen estados distintos y hay una única acción para detener; terminar un lote único también limpia el indicador de conexión.

## Seguridad y compatibilidad

Formatos DNISUITE1/DNISUITE2, almacenamiento cifrado Android, token, TLS 1.2/1.3 y validación de huella continúan vigentes. No hay descubrimiento que pueda cambiar la huella ni fallback sin TLS. No se modifican PACE, PIN, firma, controlador, bóveda o certificados personales. Un peer privado con huella/token incorrectos no abre el DNI. La autenticación fallida termina el intento, sin reenviar firmas.

## Artefactos y validación

APK77 `3.0.0-alpha05-mobility` compilada e instalada por ADB conservando datos. Paquete/versionCode comprobados en el móvil.

- JVM: signing-link 24 pasan; bridge Android 29 pasan / 2 omitidas externas; desktop 25 pasan; UI Windows 2 pasan; app 79 pasan / 1 omitida. Total 159 pasan / 3 omitidas, cero fallos/errores. La mayoría de tareas ajenas al cambio reutilizó resultados; las tareas afectadas se ejecutaron.
- Debug APK, auxiliar instrumental y Kotlin release: BUILD SUCCESSFUL.
- TLS real/certificado sintético: dos lotes con IP guardada distinta de IP real; lote único con dirección antigua; huella/token incorrectos sin abrir tarjeta. Puente inverso funciona sin resolver un hostname histórico inexistente y rechaza pin incorrecto. Dirección pública fuera de alcance del receptor.
- DPAPI real: nueva IP conserva UUID, fingerprint, token y bytes del almacén; manipulación del payload rechazada.
- APK: `D:/DNIeToolchain/AndroidPackages/dnie-evolution-3.0.0-alpha05-mobility-debug.apk`, SHA256 `FAE1D49BB91B558CDC50740E1A3917DC21B8D02492BFB061754536AB543BA99A`.
- Windows GUI: `D:/DNIeToolchain/Builds/mobility77/signing-ui/install/dnie-signing-ui/DNIe Suite.lnk`. Distribución con adaptador Bridge actual, separado y recompilado; el servicio y controlador instalados no se sustituyen. La GUI ya no indica renovar emparejamiento por IP.
- CLI: `D:/DNIeToolchain/Builds/mobility77/signing-desktop/install/dnie-signing-desktop/bin/dnie-signing-desktop.bat`.
- Logs de compilación fuera de Git: `D:/DNIeToolchain/address-mobility-build-d.log`, `address-mobility-ui-build.log`, `address-mobility-integration-build.log`.
- Prueba instrumentada de pantallas preparada; pendiente de desbloqueo del móvil.

La primera batería ejecutada sobre G: pasó los casos TLS de movilidad, pero falló en pruebas que requieren ACL Windows sobre el sistema de archivos de Google Drive. Se repitió en D: manteniendo las ACL y DPAPI de producción.

Prueba física pendiente: cambiar la IP de la PC real y conectar con el emparejamiento ya guardado, luego firmar. Los certificados/tarjetas de las pruebas de transporte son sintéticos; no se afirma una firma física nueva.
