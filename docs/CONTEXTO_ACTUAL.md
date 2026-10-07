# Estado vigente · corrección USB tras reinicio

2026-10-06: el usuario confirma que funciona y firma, en el contexto de la prueba USB. Evidencia reportada por el usuario; no se recibió artefacto nuevo para verificación independiente. La estabilidad de reconexión sigue pendiente. Evaluación de escalabilidad y alternativas sin lector propio en [viabilidad de distribución](architecture/VIABILIDAD_DISTRIBUCION_SIN_CONTROLADOR.md): conservar PC/SC compatible y estudiar edición portable por API/PKCS#11. No se cambiaron componentes operativos en esa evaluación.

Actualización posterior: Windows aísla el motor USB en un auxiliar con Job Object, cancelación y límite exclusivo para llamadas nativas de preparación. Se identificó bloqueo físico en `UsbDk_StartRedirect` antes de AOA/PACE; reconectar liberó el auxiliar. Registro y límites en [aislamiento USB](architecture/AISLAMIENTO_USB_USBDK.md). Android sigue en 52. USB todavía necesita validación TLS y firma; no declarar transporte estable por tener aislamiento.

Actualización 2026-10-06: Android `2.0.0-rc4-usb` (52) instalado y gestor/servicio Windows Release actualizado. Tailscale y LAN parecen funcionar según el usuario; no se documenta una nueva firma de regresión. Se corrigió el registro Android de accesorio AOA, el permiso USB y la espera/estado Windows. Prueba física sin DNI: host llegó a `usb_waiting_android` y Android ofreció abrir DNIe Evolución, confirmado por el usuario. Falta la conexión TLS y firma por USB. Detalles y pruebas en [corrección USB AOA](architecture/CORRECCION_USB_AOA_RC4.md). El host se detuvo tras la prueba, sin tarjeta publicada; servicio permanece activo. Distribuciones/evidencia en `D:/DNIeToolchain/Checkpoints/usb-aoa-rc4`.

## Registro previo conservado: antes del reinicio
Actualizado: 2026-10-06; comprobación operativa 2026-10-07 02:42 UTC. Este archivo describe el estado actual. Los informes con números de versión anteriores son historia, no instrucciones para iniciar hosts adicionales.

## Por dónde retomar

1. [Estado y procedimiento tras reiniciar](operations/RETOMAR_TRAS_REINICIO_RC4.md).
2. [Arquitectura actual y límites de las capas](architecture/ARQUITECTURA_ACTUAL_2_0.md).
3. [Pendientes priorizados y criterios de cierre](PENDIENTES_ACTUALES.md).
4. [Implementación del gestor nativo](architecture/GESTOR_NATIVO_WINDOWS_RC4.md).

## Versión y checkpoint

- Fork autorizado: `modest-bose-evolution`, rama `refactor/modularizacion`. Base `modest-bose` y Antigravity clausuradas.
- Código funcional Windows: `77afc1f622454221b2ed84b2e95c9cd543708aad`, tag `checkpoint/windows-native-2.0.0-rc4`.
- Android instalado: `2.0.0-rc3`, versionCode 51, paquete debug `pe.gob.reniec.dnie.evolution.debug`. No se cambió ni reinstaló Android al sustituir el gestor Windows.
- Referencia previa: `checkpoint/puente-2.0.0-rc3`; firma Windows real preservada en `checkpoint/firma-windows-1.8.14`, además de evidencia Android21/22.
- Distribuciones, hashes y pruebas: `D:/DNIeToolchain/Checkpoints/windows-native-rc4`. APK rc3: `D:/DNIeToolchain/Checkpoints/2.0.0-rc3`.

## Estado comprobado

`DNIeBridgeAgent`: Running, Automatic; binarios Release instalados en D:. GUI nativa abierta, una instancia. Consulta autenticada de solo lectura: host Stopped, fase idle, sin tarjeta publicada y sin error; último perfil solicitado LAN, sin conexión activa. No había conexiones al puerto 39443. No se inició ni detuvo un host para guardar esta documentación.

El lector propio aparece con estado PnP OK. Existe también un listener anterior en `0.0.0.0:35963`, asociado a WUDFHost. Su dispositivo exacto no se identificó en esta comprobación; queda pendiente aislarlo del flujo nuevo. No detener WUDFHost genéricamente.

El minidriver DNI quedó aislado en los experimentos anteriores; no se restauró en rc3/rc4. No se tocaron Hello, SCardSvr, CertPropSvc, firewall o perfiles de red. El reinicio pendiente es manual, por solicitud del usuario.

## Evidencia y límites

Windows: 123 comprobaciones del motor, 31 del control en Debug y 31 en Release; 10 comprobaciones sobre el servicio Release instalado desde cuenta no administrativa. Inicio/parada/reinicio conservaron PID del servicio. Release rechazó un comando debug directo; paquete sin BAT/PowerShell/PDB y sin DebugForm. Certificado y metadatos de identidad preservados exactamente, estado con ACL protegida.

Android: APK Debug/Release recompilados satisfactoriamente; 251 pruebas y dos omisiones opcionales corresponden a la validación rc3 anterior, no a una nueva ejecución en esta documentación.

Tailscale y las firmas/lecturas del puente tienen evidencia física histórica. El gestor nativo aún necesita una firma real de regresión. LAN TLS y USB AOA tienen cobertura sintética y siguen pendientes de prueba física. UsbDk 1.0.22 está instalado; PnP devolvió 3010 al reiniciar el dispositivo del teléfono. Reiniciar no demuestra por sí solo que USB funcione.

## Historia

El contexto anterior, que mezclaba rc1/rc2/rc3/rc4 y PIDs antiguos, se preserva en [archivo histórico](archive/CONTEXTO_HASTA_WINDOWS_NATIVE_RC4.md). Para operar, usar esta entrada y el procedimiento actual; no los launchers ni sesiones temporales de esas notas.
