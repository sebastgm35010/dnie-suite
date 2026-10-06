# Resultado android14: transporte estable y selección divergente RENIEC

## Separación

APDU acumula cuatro sesiones. Excluir4413ca43(20:28),f1177b3c(21:31:21),7b139cfa(21:31:46), todas anteriores a la retirada del minidriver. El control nuevo es d6ab8936-adfb-4837-a2cf-b05bec51438d,2026-10-06 21:35:08.637–21:38:10.876UTC:182,239s. Diagnóstico general648 eventos; nueva ventana puente6927–7007.

## Transporte y PACE

22 comandos y22 respuestas; cero transport_failed.20 probes iniciales responden6A86; después reset y dos SELECT de detección. Power-off completado dos veces y reset una vez. PACE físico completado al iniciar(evento6944) y tras reset(6999). No PACE del PC ni error SSC119→1 en este control. Cierre manual: stop_requested7005 antes de SocketException7006; no interpretar ese SocketException como caída espontánea. Monitor session_completed,attributionReady=true,collectorsStopped=true; ETW iniciado/parado código0.

Minidriver ausente en muestras de módulos y asociación contactless sigue ausente. CertPropSvc/SCardSvr Running. Esto respalda que la secuencia automática del minidriver contribuía al fallo anterior, sin demostrar que se haya corregido el cliente que reinicia SSC ni validar tráfico protegido continuado: en el control no hubo comandos PC protegidos.

## Dos etapas RENIEC

En el puente,SELECT21 detecta v2,A0000000003F00,6A82;SELECT22 detecta IASv3,A000000077030C60000000FE00000500,9000. Es detección funcional de v3 en el componente .NET, no lectura completa de certificados.

WMI: cddnie-app PID50644 arranca21:36:08.652UTC; Java PID7088 hijo del anterior arranca21:36:11.666 y termina21:36:13.683,exit0. Componente termina21:36:14.685,exit0. Java sí se ejecutó; no inferir crash. Logs oficiales locales de la última ejecución indican SELECTv2=6A82 y SELECTv3=6A82. Ninguna consulta Java adicional aparece en el APDU del móvil después de SELECT22; el componente .NET ya hizo UnpowerCard antes de lanzar Java. No publicar argumentos, CAN ni URLs de activación de esos logs.

Inspección estática previa y revisada: .NET SmartCardService.Open selecciona el primer lector. Java DnieComponent.connect enumera CARD_PRESENT y su bucle conserva únicamente el último terminal, sin escoger por AID ni probar todos. El mensaje de mala conexión corresponde a no reconocer v2/v3 en ese tramo Java.

Captura host durante la sesión: índice0 DNIe Bridge Android NFC Private Reader0,presente; índice1 Virtual PCD Contactless0,ausente; índice2 Windows Hello for Business1,presente. Esta evidencia, el bucle Java y las respuestas que no llegan al puente apuntan fuertemente a Java conectando al lector Windows Hello. Sigue siendo una atribución inferida: no capturamos el nombre del terminal efectivamente abierto por Java ni su handle PC/SC, y la enumeración exacta Java puede diferir. No afirmar que se demostró por llamada.

## Próximo paso

Instrumentar exclusivamente enumeración/selección del proceso Java y confirmar terminal/handle sin registrar argumentos ni claves. Probar lectura controlada seleccionando explícitamente nuestro lector, sin alterar Windows Hello, otros servicios ni los binarios oficiales. Después evaluar un aislamiento de lectores por proceso o integración compatible; no cambiar simplemente el orden global porque .NET elige primero y Java último. Mantener minidriver retirado durante el control hasta decidir restauración; no declara compatibilidad final IDplug/CSP/KSP.

Registros privados originales móvil en D:\Winodws files\Descargas\dnie-apdu-pace-android14.jsonl.json y dnie-diagnostico14.jsonl. Captura Windows tailnet-without-minidriver-70314f9ef4274ba2830b35fa445ada8d/a51f5190206b49c9ba51586da5839c82; ETW b5fc2af5586f4e3ca82073174b9ac76f. No se modificó APK, driver ni Windows Hello en este análisis.
