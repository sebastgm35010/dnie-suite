# Pendiente: PACE del middleware y petición del CAN

Corrección del usuario, 2026-10-06: la ventana CAN solo apareció en el primer intento; no volvió a replicarse. Los 9000 registrados no prueban que hubiera una nueva entrada de CAN ni que el usuario hubiera confirmado un CAN correcto en ese ensayo. Ver [experimentos sintéticos](IDPLUG_EXPERIMENTOS_SINTETICOS.md).

Fecha de cliente: 2026-10-05. Última versión instalada: 1.8.6-bridge-diagnostics (39).

Último ensayo: emparejamiento a4e0c7ba826942a18959d993770e0ec4. El middleware completó MSE y los cuatro GENERAL AUTHENTICATE con 9000, incluida la autenticación final. Se superó el 6300 del ensayo donde el usuario había introducido un CAN incorrecto.

El primer comando posterior con SM, cabecera 0CA40204 y secuencia 17, produjo transport_failed con CardProtocolException tras 29 ms. Android cerró y el host recibió EOF. No hay evidencia de microdesconexión física en este fallo; la clase registrada apunta al protocolo. Un opening_failed posterior con IllegalStateException es otro evento de apertura y no sustituye la excepción del comando 17.

Hipótesis principal: PACE del PC ha establecido nuevas claves mientras Android conserva el canal PACE previo. El comando ya protegido del middleware se vuelve a envolver por Android. Falta distinguir exactamente respuesta sin protección, MAC o SSC mediante diagnóstico acotado; CardProtocolException sola no identifica el subtipo.

Pendientes prioritarios:

1. Definir propietario único de PACE para IDplug. Como control técnico, considerar PC dueño de PACE con relay NFC y GET UID adaptado. Si se mantiene PACE físico en Android como requisito, diseñar terminación/traducción explícita del PACE/SM del PC; no basta con permitir INS22/86.
2. Registrar causa de protocolo y SW cuando estén disponibles, sin mensajes arbitrarios, CAN/PIN ni claves. No relajar la verificación de MAC ni fabricar 9000.
3. Repetir el comando posterior a PACE y después lectura de certificados. Firma y compatibilidad completa aún no validadas.
4. Mantener separado el problema de selección del último lector presente en Java oficial: Windows Hello puede quedar elegido por el código inspeccionado.

Evidencia privada: D:\DNIeToolchain\LiveCaptures\ead200a74e2b4d40b87e5b1fb2906b08\opening-diagnostics-1-8-6. El usuario pidió análisis rápido y dejar pendiente por cuota del modelo. No se inicia otra compilación ni otra sesión.
