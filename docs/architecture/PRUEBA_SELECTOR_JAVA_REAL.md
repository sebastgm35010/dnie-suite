# Prueba temporal de seleccion del puente en Java real

6 de octubre de 2026. La prueba anterior confirmo que .NET detecta DNI v3 en el puente, pero su hijo Java elige Windows Hello. Para comparar el mismo flujo web se habilita `watch-reniec-reader.py --select-bridge`.

## Alcance

`pcsc-bridge-selector.js` filtra SCardListReadersA/W solo dentro del Java de RENIEC observado. Devuelve exclusivamente DNIe Bridge Android NFC Private Reader 0 cuando existe; si falta, devuelve una lista vacia sin elegir otro lector. Conserva fallos originales de PCSC y propiedad de los buffers, incluidos buffers autoasignados. No modifica el orden global, los servicios Windows, el JAR/runtime de RENIEC, argumentos de activacion, stdout, operaciones de tarjeta ni respuestas APDU. La seleccion se retira al desprender el observador; no requiere nuevo APK.

El observador registra la lista original antes del filtro y un evento separado `bridge_reader_list_filtered`. Por eso la lista original en el registro puede seguir mostrando Hello. La evidencia decisiva es SCardConnectA/W del Java real al puente y el resultado posterior del flujo oficial.

## Validacion previa

Sintaxis JavaScript comprobada. Prueba sintetica padre Java a hijo Java ejecutada con el runtime reducido RENIEC: filtro aplicado y SCardGetStatusChange del hijo consulta un unico lector, el puente. Sin conectar ni transmitir a la tarjeta. Captura privada de validacion: D:/DNIeToolchain/Instrumentation/self-test-bridge-selector-presence.jsonl, passed=true.

## Operacion

Captura privada preparada: D:/DNIeToolchain/LiveCaptures/tailnet-java-bridge-filter-81fba7208f274845be896d2ed20c2af6. Observador limitado a 900 s; siguiente solicitud Tailscale activa captura host, navegador y ETW limitada a 300 s. `result.json` al terminar el monitor o `stop.txt` retiran la instrumentacion. El limite no desconecta por si solo el DNI.

Usar Android instalado con Traducir PACE y Tailscale. Mantener otros consumidores de tarjeta cerrados. Iniciar sesion nueva, esperar tarjeta activa y ejecutar un unico intento en el Edge auditable existente. Detener en Android tras el resultado y exportar registros.

El comando combinado que incluia abrir nueva ventana Edge fue rechazado por revision automatica sin detalle de motivo. Se armo la captura sin esa apertura; el usuario navega manualmente en Edge existente. No declarar exito hardware antes del resultado real.
