# Pendientes actuales · Windows rc4 USB / Android rc4-usb

Actualizado 2026-10-07 tras primera firma remota física de suite H1b (APK 54), además de la validación del motor compartido de firma. El diagnóstico del teléfono confirma `2.0.0-rc5-signing`, correspondiente al candidato compilado 53. Este listado sustituye los encabezados de pendientes de versiones anteriores. Diferenciar implementación, simulación y validación con dispositivo real.

## Siguiente secuencia

| Prioridad | Trabajo | Estado / criterio de cierre |
| --- | --- | --- |
| P1 | Causa de lector ausente de PC/SC pese a backend conectado | Usuario confirma recuperación tras reiniciar. Falta determinar causa y reproducibilidad: capturar enumeración/arranque PnP, UMDF y SCardSvr si se repite, distinguiendo estado privado del backend y visibilidad real. No reinstalar minidriver sin evidencia. [Diagnóstico](operations/LECTOR_AUSENTE_PCSC_2026_10_07.md). |
| P1 | Firma remota privada de suite, sin PC/SC | H1b implementa comando real/TLS inverso y aprobación Android/PIN local. Tests de red TLS con tarjeta simulada pasan, APK 54 compilado. Primera firma física LAN completada (1/1 CMS) y verificada .NET, con certificado DNI coincidente y rechazo de contenido alterado. Lote físico adicional 3/3 verificado .NET (TXT/CSV/binario). Faltan cancelación/pérdidas físicas y emparejamiento permanente; USB/PDF pendientes. No compartir SSC ni ampliar SYSTEM. [Procedimiento](suite/FIRMA_REMOTA_H1B.md). |
| P1 | Ampliar regresión física de lotes Android rc5 | Lote físico de cuatro archivos y ZIP verificados: dos PDF, dos CMS y una firma PDF anterior conservada; mismo certificado instalado Windows. Una entrada de PIN, una apertura PACE, 3.721 s, sin fallas. Pendientes: lotes mayores, interrupción/cancelación física y perfilado de frames lentos. Ver MOTOR_FIRMA_LOTES_RC5.md. |
| P0 | Arranque real después del reinicio | Comprobar servicio automático y lector PnP, sin host/red hasta pulsar Iniciar. Abrir dos veces el gestor y comprobar una ventana; iniciar/parar sin UAC. No asumir que la prueba previa de SCM acredita un reinicio real. |
| P0 | Identificar y retirar el listener legado | Antes del reinicio hay `0.0.0.0:35963` bajo WUDFHost. Identificar su dispositivo/controlador; retirar únicamente el componente legado si se confirma. No matar WUDFHost genéricamente, ni tocar SCardSvr/Hello. El lector propio no usa red/VPCD. Cierre: sin listener legado y lector propio operativo. |
| P0 | Regresión Tailscale con gestor nativo | Sesión física, lectura y una firma ReFirma/Firma Perú con verificación independiente; manualmente retirar/volver a presentar el DNI y reconectar. Evidencia histórica funciona, pero no valida la GUI/servicio recién instalados. |
| P1 | USB AOA real tras reinicio | Usuario confirma funcionamiento y firma después del aislamiento. Falta adjuntar/validar artefacto independiente y cerrar estabilidad, reconexión y parada repetidas. APK 52 y gestor Release actualizado. Ver AISLAMIENTO_USB_USBDK.md. |
| P1 | LAN TLS real | Usar IP Wi-Fi real del teléfono, ruta PC→Android y huella estable. Localizar fallo por etapa: TCP, DNIREADY1, TLS, token, PC/SC, NFC. Validar firma y reconexión sin abrir reglas globales ni desactivar cifrado. |
| P1 | Ciudadano Digital sobre candidato actual | Activar selector opcional, comprobar lector exacto y repetir reconocimiento. El hito previo es «DNIe ya tiene certificados instalados» y paso a autenticación facial. No continuar a descargar certificados/cambio de PIN como prueba automática. |

## Seguridad y distribución

- USB P0: identificado `UsbDk_StartRedirect` en `usb_redirect_initial`. Implementado aislamiento en auxiliar, Job Object, cancelación acotada y cuarentena si no termina. Reconectar el cable liberó el auxiliar físico. Usuario confirma funcionamiento/firma; continúa pendiente eliminar el bloqueo nativo y validar ciclos/artefactos. Ver AISLAMIENTO_USB_USBDK.md; una firma no acredita estabilidad en reconexión.

- Escalabilidad: conservar el lector funcional y simplificar su instalación. El usuario prioriza una suite propia de firma sobre la propuesta portable LAN. Revisión y secuencia de extracción en [núcleo de firma](architecture/NUCLEO_FIRMA_SUITE_PROPIA.md): contratos JVM neutrales, casos de uso compartidos, consolidación Android y adaptador PDF/PCSC de escritorio. No se cambió el driver ni el motor durante la revisión. Las alternativas anteriores siguen documentadas en VIABILIDAD_DISTRIBUCION_SIN_CONTROLADOR.md.

| Trabajo | Pendiente concreto |
| --- | --- |
| Consentimiento por operación del puente | La PC autorizada puede pedir operaciones mediante clientes PC/SC. Diseñar confirmación Android para operaciones privadas sin confundirla con selector o ACL local; mantener compatibilidad y pruebas de cancelación. No declararlo ya implementado. |
| Controlador de producción | El controlador instalado es de pruebas. Validar carga después de reiniciar y planificar firma/distribución admitida por Windows. Certificado personal DNI o confianza raíz no reemplazan los requisitos de firma del controlador. |
| Firma de editor / instalador | Paquete nativo sin Authenticode público. SHA256 verifica consistencia, no identidad del editor. Planificar instalación/desinstalación y actualización de producción. |
| Servicio y actualizaciones | Probar fallo de arranque, cierre de SCM con sesión real, recuperación ante crash, actualización interrumpida/rollback, disco D no disponible y limpieza de versiones antiguas. No afirmar cobertura por los 10 controles básicos existentes. |
| Convivencia de middleware | Minidriver DNI aislado en ensayos anteriores; preservar respaldo y estado. Investigar compatibilidad separada antes de restaurarlo, porque antes generaba PACE PC y cierres. |
| Sesiones prolongadas | Probar inactividad, pérdida NFC, desconexión de cable/red, fondo/bloqueo Android y varias presentaciones. El host persistente no significa mantener una sesión criptográfica válida sin el DNI. |

## Otras funciones y deuda restante

- QR FIDO: probar ceremonia completa navegador→teléfono→proveedor→sitio. Abrir la cámara no acredita WebAuthn ni transporte híbrido propio; caBLE/CTAP híbrido propio no está implementado. Reproducir el error GMS/ML Kit si vuelve a aparecer con la versión actual.
- Bóveda/passkeys: migración con dos DNI reales y certificados distintos, recuperación/cancelación SAF, muerte de proceso y restauraciones parciales. Las simulaciones y aperturas de catálogo no sustituyen esa prueba. Mantener separado vencimiento del DNI y vencimiento del certificado.
- Passkeys: comprobar delegación biométrica en teléfono, cancelación, cambio de inscripción y vuelta al DNI. La bóveda conserva requisito DNI y no usa biometría.
- Rendimiento Android: recorrido repetible de escritura, scroll y pestañas, con medición comparable; hay mejoras confirmadas por el usuario pero no evidencia de eliminación de todos los tirones.
- Compatibilidad Windows adicional: XML y lotes ReFirma son candidatos de ensayo; CMS/P7S y PDF tienen evidencia previa. No reenviar una firma por timeout ni hacer intentos PIN con datos simulados contra el DNI real.
- Mantenimiento: revisión de contratos/errores, estilo y legibilidad C#, matriz de pruebas, empaquetado portable de dependencias opcionales y versión de artefactos. Separación en archivos/módulos no equivale a deuda técnica cero.
- Versión 3.0: rediseño completo de navegación/presentación después de consolidar 2.0. Usar los mismos casos de uso; no mover PACE, SSC, sockets o APDU a UI.

## Ya resuelto: no reabrir sin una regresión nueva

Gestor nativo y servicio permanente; control desde cuenta normal; inicio idempotente; instancia única de ventana; limpieza al detener; identidad TLS estable; QR local en memoria; separación Debug/Release Windows. El pipe ya corrige consulta de identidad bajo SYSTEM y pérdida de respuesta antes del acuse. En rc3 se retiraron límites de inactividad/30 minutos del host autenticado y se introdujeron perfiles cifrados por transporte.

Resultados exactos y procedimiento de prueba: [estado/retomar](operations/RETOMAR_TRAS_REINICIO_RC4.md). No usar PIDs, monitores o launchers de documentos históricos como estado actual.
