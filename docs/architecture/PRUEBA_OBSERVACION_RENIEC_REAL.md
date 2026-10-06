# Prueba: observar seleccion del Java real de RENIEC

Captura privada preparada: D:\DNIeToolchain\LiveCaptures\tailnet-reader-observation-d43a71a585d74b6386acebc1155fa2bb. No filtro de lectores, no cambio de orden global, no reemplazo del JAR ni del runtime RENIEC. Android sigue en version instalada1.8.14;1.8.15 no instalada.

## Metodo

Observador propio usa Frida17.22.2 instalado solo en D:/DNIeToolchain/Instrumentation/python. Se adjunta a lanzadores ClickOnce dfsvc de Microsoft y a cddnie-app.exe; los hijos RENIEC/Java se instrumentan antes de reanudarlos mediante child gating limitado al proceso padre. No spawn gating global. Los hijos ajenos se reanudan sin instrumentar. Esto introduce una pausa breve de arranque, no cambia la seleccion de lector ni las respuestas PCSC.

pcsc-observer.js observa SCardListReadersA/W, SCardGetStatusChangeA/W y SCardConnectA/W. Captura PID, rol, lista en orden, presencia y lector solicitado/estado del retorno. APIs originales se ejecutan; no se sustituyen argumentos ni retornos. No lee APDU, PIN, CAN, ATR, certificados ni argumentos del proceso. Solo nombres de tres lectores conocidos; otros nombres redactados.

El runtime reducido no incluye java.instrument/JDWP. Se observa la frontera PCSC real en vez de alterar configuracion de proveedores Java. Las pruebas iniciales detectaron un enganche tardio; corregido enganchando al retorno de carga de biblioteca. Prueba sintetica final en runtime RENIEC: lista/presencia capturadas. Prueba adicional padreJava→hijoJava pasa con captura del hijo desde su primer listado. Se corrigio el bloqueo del callback usando hilo separado; pruebas previas fallidas permanecen privadasD y no son evidencia de resultado real.

Fuentes de API: https://frida.re/docs/javascript-api/ y https://frida.re/docs/frida-core/method.Session.enable_child_gating.html.

## Estado preparado

ObserverPID55624 y monitorPID3068, limite900s desde armado; ETW/PCSC hasta300s desde siguiente solicitud Tailscale. ServidorPID53000 escucha39443; EdgePID54504 confirmado con CDP local19371. Lanzadores ClickOnce observados con child_observation_ready. IDplug no detectado al preparar.

Un comando posterior que iniciaba captura inmediata del navegador y navegaba al sitio fue rechazado por revision automatica sin motivo adicional. Edge se abrio correctamente antes; usuario debe navegar manualmente. Captura Edge de watch-tailnet-session arranca con solicitud del movil y puerto disponible confirmado. No afirmar captura inmediata de navegacion previa.

## Usuario

Mantener Firma Peru/ReFirma/IDplug cerrados. Crear nueva deteccion del DNI en Android con Traducir PACE y Tailscale; esperar activo. En Edge entrar manualmente a https://pki.reniec.gob.pe/ciudadanodigital/ y ejecutar un solo flujo. Al recibir resultado detener sesion y exportar diagnostico/APDU. Guardar aviso exacto.

## Criterio

Buscar reader_list y reader_presence del PID Java real, seguidos de connect_requested/returned. Comparar con PID.NET. Hello como lector conectado por Java demostraria la hipotesis; puente correctamente seleccionado obliga a investigar respuestas/protocolo. Si no se captura connect_requested, no inferir lector a partir de un muestreo separado; registrar cobertura incompleta y repetir.

Los observadores se desprenden al agotar limite o al crear stop.txt en raiz. ETW tiene stop propio al finalizar sesion. La captura limitada no detiene por si sola el DNI. No hay prueba hardware concluida aun ni nuevo APK por estas herramientas.
