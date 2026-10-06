# Android21: firma desde Firma Perú validada

6 octubre 2026. APK1.8.14-reniec-pki, traducción PACE Android, lector PC/SC puente y Tailscale. Prueba preparada para Firma Perú como cliente único; no se ejecutó lector Java paralelo ni se reinstaló el minidriver aislado.

## Evidencia

Sesión 1b2248a4-2903-4431-81bf-d28170225631, 22:42:05.264–22:44:05.059 UTC:119,795 segundos, 56 comandos y 56 respuestas, un reset y un power-off completados. Estados:33×9000,20×6A86 sondeos,2×6282 EOF y1×63C5. Ningún transport_failed en el APDU exportado.

Comando50: VERIFY sin datos 00200003 responde63C5 (consulta de estado con cinco intentos restantes, no un PIN incorrecto enviado).
Comandos51 y54: VERIFY con datos responden9000; no se incluyen PIN/criptogramas en este informe.
Comando55: MSE para firma 002241B6 responde9000.
Comando56: PSO COMPUTE DIGITAL SIGNATURE 002A9E9A responde9000.

No hubo pc_pace_requested ni pc_pace_ready en este ensayo: Android abrió/reabrió PACE físico dos veces; Firma Perú usó comandos lógicos planos traducidos al canal físico protegido. Esto contrasta con la sesión previa IDplug que sí había creado PACE del PC y luego conservado un contexto obsoleto tras reset.

PDF original sintético1546 bytes; resultado PRUEBA-FIRMA-PERU[FP].pdf158851 bytes, generado en la carpeta de captura. Verificación independiente de solo lectura con pyHanko, sin permitir fetch de certificados/red: una firma embebida, intact=true, valid=true, coverage=ENTIRE_FILE, modificationLevel=NONE, digest SHA256, signature SHA256_RSA. trusted=true según el contexto local predeterminado del validador. No equivale a haber comprobado revocación actual mediante consulta online ni a validación oficial externa.

El JSON privado de verificación conserva tamaño y SHA256 del PDF firmado para reproducibilidad. No se reexportó ni modificó el documento.

Diagnóstico general249 eventos: stop_requested active precede la SocketException final; cierre manual. Monitor Windows reason=session_completed, attributionReady=true, collectorsStopped=true.

## Hito demostrado

Firma Perú completó una firma real usando el DNI a través de Android y el lector PC/SC; el resultado firmado se verificó criptográficamente. El PIN no estaba bloqueado en esta secuencia. Ver certificados y listar referencias privadas ya no es la única evidencia: la operación de firma y su PDF válido están confirmados.

## Pendientes y conservación

- Preservar APK1.8.14 y esta configuración como referencia funcional antes de cambiar el motor. No se compiló ni modificó código en este análisis.
- Resolver por separado el estado PC-SM tras reset y las consultas planas cuando otro cliente ha establecido PACE PC; no reusar claves/SSC viejos.
- Integrar selector real de Ciudadano Digital y confirmar su proceso/terminal; esta firma no prueba su flujo.
- Evaluar coexistencia de minidriver aislado y clientes Windows, sin detener CertPropSvc/SCardSvr.
- Probar ReFirma en una sesión independiente y verificar su documento resultante. No extrapolar compatibilidad de todas las aplicaciones de Windows.

Captura privada: D:/DNIeToolchain/LiveCaptures/tailnet-firma-peru-aa0d8a82c89d45668703e7083fe6ebeb. Exportaciones APDU21/diagnóstico21 en Descargas. PDF y datos personales permanecen fuera de Git.
