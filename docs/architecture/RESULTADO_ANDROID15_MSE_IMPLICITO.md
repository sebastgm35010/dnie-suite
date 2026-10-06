# Android15: selección Java lograda, MSE PACE implícito rechazado

## Resultado real

Sesión e82709f4-8f30-4de9-88b9-b40224000fa4,2026-10-06 21:48:35.970–21:51:47.506UTC,191,536s;27 comandos/27 respuestas,sin transport_failed. Cierre manual: stop_requested7122 antes de SocketException7123. No confundir esa salida con pérdida NFC.

Lanzador sin ventana confirmado PID59960: proveedor filtrado listo, motor original iniciado21:48:37.209UTC y terminado21:48:41.274UTC,exit0,officialReportedFailure=true. No hubo UI gráfica porque se ejecutó lectura diagnóstica, no el wrapper ClickOnce.

Java efectivamente opera sobre nuestro lector: SELECTv2(17)6A82,SELECTv3(22)9000; SELECT IAS P2=04(23)9000 con38 bytes de contenido; EF.CardAccess(24)9000; READ25/26 con9000. EF.CardAccess reconstruido en memoria anuncia OID0.4.0.127.0.7.2.2.4.4.4,versión2,parameterId13 (DER02010202010d). No se leyó identidad ni certificados completos.

Comando27,MSE:SetAT,0022C1A40F800A04007F00070202040404830102. Contiene DO80 perfil PACE-IM/ECDH/AES256 y DO83 referenciaCAN2,omiteDO84. Traductor1.8.12 exigía exactamente esos objetos más DO84=13 y devuelve6A80. pc_pace_requested7120 aparece; no pc_pace_ready,no GeneralAuthenticate. Rechazo ocurre antes de comprobar CAN/token,por forma del comando. Mensaje oficial de número DNI incorrecto es genérico y no demuestra CAN erróneo.

Monitor session_completed,attributionReady=true,collectorsStopped=true. Captura privada tailnet-reader-selector-f8ea777fc3fe419eba379e04044ce3d5/e3e8d3a202fb4313a5ca7d30942db0a9; ETW7612052c2cc64ebe819c951d92fe1483. Resultado oficial privado sin publicar argumentos/personales.

## Corrección1.8.13

Endpoint acepta dos formas exactas: OID+CAN+dominio13 o el mismoOID+CAN sinDO84. Omitir dominio selecciona el único perfil13 implementado/anunciado. Otros OID,referenciassecretas,dominios,objetosdesconocidos/duplicados continúan rechazados; token/MAC/SSC/antireplay no cambian. Forma implícita termina PACE localPC sin tocar PACE físico. Añadidas pruebas de autenticación mutua/lectura protegida, CAN incorrecto y perfiles/objetos inválidos. Hardware completo todavía pendiente del APK nuevo.

## Fuentes aportadas

Guía oficial DNIe v3: https://identidad.reniec.gob.pe/documents/d/guest/guia_dnie_version3 . Describe instalación de middleware32/64bits, solicitudCAN, certificadosAUT/FIR en almacén Windows y lectorISO7816; no especifica APDU ni selectorJava. La ventanaCAN observada en pruebas es coherente con ese flujo.

DNIeTools público: https://serviciosportal.reniec.gob.pe/mwwd2/samples/dnietools . Acceso directo403,contenido recuperado del buscador oficial: ejemplosABI/MRTD,validaciónoffline y cambioPIN,requiereJRE8 y driverOberthur antiguo. No trasladar esas exigencias a v3 ni ejecutar cambiosPIN en pruebas. La página no aporta una licencia abierta para incorporar sus componentes.

GuíaCiudadanoDigital https://pki.reniec.gob.pe/ciudadanodigital/guia-usuario devolvió401. No se obtuvo su contenido ni se fabricó autorización. Página pública deCiudadanoDigital confirmaDNIev2/v3,lectorISO7816 y Edge: https://pki.reniec.gob.pe/ciudadanodigital/ . No se aplicaron ajustesdeWindows/firewall citados en documentación antigua.


Validación final: 182 pruebas aprobadas,2 interop omitidas,cero fallos/errores. Arquitectura aprobada. APK1.8.13-reniec-pace(46) compilado offline. Captura/lectura siguiente armada: D:\DNIeToolchain\LiveCaptures\tailnet-reader-selector-d94aec2e81f64914813aa21be2ca6780, espera2h; lectura motor Java original sin ventana y sin llamada web. CertPropSvc/SCardSvr activos y asociacióncontactless ausente. Mensajero encargado de enviar APK, pendiente confirmar recepción.

Telegram confirmó la entrega del APK 1.8.13: 30.171.446 bytes, únicamente APK e instrucciones. Captura y lectura siguen armadas para conectar después de instalar.
