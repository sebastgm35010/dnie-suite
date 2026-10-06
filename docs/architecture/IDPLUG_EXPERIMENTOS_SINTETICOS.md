# IDplug: experimentos sintéticos sobre el lector propio

Fecha de cliente: 2026-10-06. Android permanece en 1.8.6-bridge-diagnostics (39); no se cambia el motor NFC ni se instala un APK.

El usuario confirmó que la ventana CAN apareció una sola vez y autorizó responder como tarjeta sintética para investigar IDplug. No se considera replicada esa ventana ni confirmado el CAN introducido en los intentos posteriores.

## Método

El backend de pruebas adjunta una tarjeta sintética al lector PC/SC propio durante un máximo de cuatro minutos. Reutiliza únicamente ATR de clasificación, FCI y EF.CardAccess públicos; UID artificial, desafío artificial y valor de demostración 000000. No utiliza el teléfono, PIN real, certificados personales ni claves privadas. Nunca responde autenticación final exitosa ni firma.

Se abrió IDplug Manager y se capturaron metadatos de ventanas. Un proceso separado cargó el PKCS#11 oficial y seleccionó explícitamente el slot del lector propio para C_GetTokenInfo y C_OpenSession. La traza del controlador no identifica el PID del cliente: puede incluir consultas automáticas del minidriver y de Windows además de IDplug. No atribuir cada APDU a la UI.

El lector estaba instalado pero inicialmente ausente de la enumeración PC/SC. Reiniciar únicamente ROOT\\SMARTCARDREADER\\0001 permitió enumerarlo; no se cambiaron firewall, Windows Hello ni el servicio de tarjetas.

## Resultados medidos

| Perfil | Respuesta del simulador | Observación |
| --- | --- | --- |
| uid-on | SELECT/EF.CardAccess válidos; MSE rechazado 6985 | Consultas a CardAccess e intentos MSE repetidos; no ventana CAN confirmada. |
| nonce-gate | MSE 9000 y desafío cifrado artificial; mapeo rechazado 6985 | El cliente avanzó hasta GA tag81. C_GetTokenInfo y C_OpenSession devolvieron 0xe1; no se ejecutó C_Login. |
| auth-failure-gate | MSE, desafío, mapeo IM y punto público sintéticos 9000; autenticación final 6300 | El cliente llegó a GA tag85 y repitió el flujo. Captura de 659 solicitudes: 122 respuestas finales 6300 y ningún reset. C_GetTokenInfo y C_OpenSession devolvieron 0xe1. No ventana CAN observada en la captura. |

El punto público de prueba es el generador brainpoolP256r1 de [RFC 5639, sección 3.4](https://www.rfc-editor.org/rfc/rfc5639.txt). Esto solo emula la estructura del intercambio: no implementa el secreto compartido ni demuestra interoperabilidad criptográfica PACE.

El monitor consulta etiquetas estáticas y botones nativos, porque wxWidgets presenta algunos controles como Pane en UI Automation. No lee valores de campos, teclado ni CAN/PIN. Detectó el panel principal con etiqueta PIN y sin etiqueta CAN; esta observación no excluye un diálogo muy breve fuera de la ventana temporal.

## Código y validación

- `tools/windows-pcsc-driver/synthetic-tests/IdplugFixture.cs`: modelo por etapas y backend finito, trazas sintéticas marcadas; reset limpia selección y estado del intercambio.
- `tools/host-diagnostics/Start-IdplugSynthetic.ps1`: arranque del backend SYSTEM mediante tarea temporal, artefactos privados en D:.
- `tools/host-diagnostics/Probe-IdplugSynthetic.py`: llamadas PKCS#11 limitadas al slot propio.
- `tools/host-diagnostics/Watch-IdplugWindows.ps1`: clasificación de ventanas sin valores de entrada.

Compilación Windows: cero errores y advertencias. Autoprueba: nueve comprobaciones de detección y siete de etapas/fallo de autenticación aprobadas. Las pruebas verifican que fuera de secuencia se rechaza GA y que el último paso siempre falla, sin habilitar firma.

Capturas privadas: D:\DNIeToolchain\IdplugSynthetic. Ensayo final: `1ab5ac825f4e4e24aff84be19a4b4371`, archivos synthetic-apdu.jsonl, pkcs11-calls.jsonl e idplug-windows.jsonl. No incorporar estos archivos al repositorio.

## Pendientes

1. Reproducir la apertura CAN en un flujo interactivo exacto y correlacionarla con PID, llamada PKCS#11 y APDU. Los ensayos actuales no alcanzaron C_Login porque C_OpenSession falló; no equivalen al flujo completo de login de IDplug Manager.
2. Identificar qué consultas de reconocimiento adicionales necesita el token: SELECT de archivos/aplicaciones aún no modelados reciben 6A82. No inventar certificados ni aceptar autenticación para superar esa detección.
3. Comparar proceso recién iniciado y estado persistente para investigar caché de CAN, sin asumir que existe o que causó el fallo. Las repeticiones sin reset medidas muestran que repetir PACE no exige un reinicio físico.
4. Mantener independiente el fallo real posterior a PACE: posible doble envoltura SM entre PC y Android, pendiente de diagnóstico. La simulación no lo resuelve ni reproduce el canal físico.
