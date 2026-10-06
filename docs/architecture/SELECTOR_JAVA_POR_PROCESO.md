# Selector Java por proceso para el motor RENIEC

## Implementación y alcance

BridgeReaderLauncher.java instala un proveedor TerminalFactory propio solo dentro de su JVM, antes de inicializar TerminalFactory.getDefault. El tipo DNIeBridgeOnly devuelve una lista filtrada por nombre exacto DNIe Bridge Android NFC Private Reader0. Delega operaciones PC/SC al proveedor original SunPCSC y no cambia respuestas, ATR, seguridad ni estado físico. Si el proveedor propio no quedó seleccionado o falta lector, aborta sin elegir otra tarjeta. Filtro aplicado a ALL/CARD_PRESENT/CARD_ABSENT y demás estados del delegado. Windows Hello queda disponible para otros procesos.

El runtime reducido RENIEC Java11 no incluye java.instrument. Se usa un lanzador normal en classpath, sin agentes ni modificación de ejecutables/JAR oficiales, y se llama a reniec.pkiep.App.main del JAR original de lectura. SHA256 verificado B46DD7FA9ABCC26E047F75E598FE7B5A34BE4AC44E7111E45F34698894CC67FD. No se ejecuta la aplicación de postpersonalización.

Es un control del motor Java, no una integración ya completada con ClickOnce/navegador. El wrapper .NET oficial fija su comando Java sin selector y toma argumentos de activación ClickOnce. No se parcheó ese programa ni se reenviaron parámetros/tokens al backend. El resultado local aún no completa validación web.

## Privacidad y prueba

Run-FilteredReniecReader.ps1 espera el siguiente pcsc_card_present del host existente, hasta2h. Usa el último CAN introducido en el middleware oficial solo en memoria y lo entrega por stdin, sin argumento de proceso ni archivo nuevo. Puede estar errado si la última entrada del usuario lo estaba; no hay reintentos automáticos con otros CAN.

Se inicializa App antes de quitar los handlers de java.util.logging, luego se invoca main: evita que el logging oficial escriba sus argumentos/CAN durante esta prueba. El stdout de lectura puede contener identidad y certificados, por lo que se guarda únicamente en official-result-private.txt privado enD:, sin publicarlo en chat niGit. Metadatos selector-events.jsonl y selector-result.json contienen estado/PID/resultado booleano, sin payload.

Seis comprobaciones sintéticas pasaron: aislar lector correcto de Hello/otros, estados presente/ausente y ausencia sin fallback; no conectan tarjeta. Prueba metadata con el mismo runtime oficial confirma proveedor propio seleccionado y único lector puente visible, tarjeta ausente antes del ensayo. Se compiló fuente8/target8 con JDK17 y se ejecutó con customjre11 -client. Parser PowerShell aprobado. No cambia Android, no requiere APK nuevo.

## Ensayo preparado

Captura D:\DNIeToolchain\LiveCaptures\tailnet-reader-selector-f8ea777fc3fe419eba379e04044ce3d5. Monitor PID64936, controlador del selector PID55012, ambos confirmados en espera. Monitor captura hasta300s; proceso Java tiene límite360s y espera tarjeta hasta300s tras disparo. Cliente apunta exclusivamente al puente; no abrir Ciudadano Digital/IDplug durante este control para evitar consumidores simultáneos.

Android1.8.12: Traducir PACE, conexión Tailscale sinJSON, acercar DNI y mantener pantalla. El control inicia automáticamente al publicarse tarjeta Windows. El proceso oficial intenta identificación, PACE, lectura de datos/certificados; no firma ni verifica PIN ni llama backend web. Al terminar se puede Detener y exportar APDU/diagnóstico general. Tras evaluar lectura, diseñar integración del selector con flujo oficial sin cambio global de orden ni desactivar Hello. Minidriver Perú sigue retirado; CertPropSvc y SCardSvr activos.
