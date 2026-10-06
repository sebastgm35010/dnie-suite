# Prueba con IDplug Manager activo

Captura automática del 6 de octubre: 18:08:09.878–18:08:33.336 UTC, 23,457 segundos. Carpeta privada: `D:\DNIeToolchain\LiveCaptures\tailnet-idplug-a70587307b8741e180634039cad9c000\dad45638cc4d4bd5972b31bb51f55844`.

El monitor terminó con `session_completed` y confirmó todos los recolectores detenidos. IDplugManager, PID 59216, aparece en las cuatro muestras de procesos y sigue respondiendo después del cierre. RENIEC no estaba activo: el lanzador había terminado por argumentos vacíos y la apertura del sitio fue rechazada por política del entorno.

Se confirmaron sincronización, autorización, apertura del lector y tarjeta presente. Hubo 76 respuestas PC/SC: 71 estados externos 9000, cuatro sondas 6A86 y una continuación 6102. El orden completo de headers y estados es idéntico al ensayo anterior sin IDplug Manager activo. Por ello **tener la ventana de IDplug abierta no demuestra que ese proceso haya generado los comandos**, ni que haya reconocido el token o completado una operación PKCS#11. Sigue siendo necesario provocar/observar una operación concreta del middleware y atribuirla.

La última respuesta fue GET UID FFCA0000=9000. El host observó EOF aproximadamente 1,165 segundos después; en el cierre volvió a aparecer Win32Exception y el servidor regresó a espera. El origen del cierre inicial requiere el registro del móvil, y el código/fase de la excepción nativa requiere ampliar la metadata de errores del backend. No atribuir la excepción posterior como causa inicial sin evidencia.

No hubo comando CB/DO85 ni validación final de RENIEC. La captura de Edge volvió a fallar (no había CDP disponible); no hay registros de navegador. La lectura de campos conocidos por UI Automation después del ensayo no entregó indicadores de login, certificados ni mensajes de error; no inferir éxito de IDplug a partir de ese resultado vacío.

Pendiente recibir registro APDU móvil de esta ventana, confirmar cómo se cerró, capturar una operación explícita de IDplug con la tarjeta conectada y mejorar la atribución/código nativo sin afectar PACE o el transporte.
