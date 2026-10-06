# Resultado del flujo manual: componente Ciudadano Digital

El usuario llegó a `cddnie-app.exe` y comunicó que no podía avanzar más. Se detuvo la captura y se conservó el registro; no se repitió ni se intentó continuar el flujo.

## Evidencia capturada

- El navegador obtuvo HTTP 200 en `/ciudadanodigital/autorizar`, `/ciudadanodigital/parametros/url-validacion` y el manifiesto ClickOnce, en dos intentos. No se conservaron tokens ni cuerpos.
- Las transferencias del manifiesto aparecen canceladas en el navegador. No basta para diagnosticar un fallo: el host sí lanzó ClickOnce (`dfsvc`) y después `cddnie-app`.
- Primer proceso `cddnie-app`, PID 18736: el muestreo observó carga de **winscard.dll**, además de bibliotecas criptográficas Windows, y conexiones TCP salientes a puerto 443. Después dejó de aparecer.
- Segundo proceso `cddnie-app`, PID 31040: apareció brevemente y desapareció; el intervalo de muestreo no captó DLL relevantes en ese intento.
- SCardSvr y CertPropSvc permanecieron Running en las muestras. No se registró un listener local de `cddnie-app` en las muestras, sin excluir que existiera uno muy breve.

**Hallazgo para el puente:** la carga de WinSCard en ejecución aporta evidencia concreta de integración con la capa de tarjetas Windows. No demuestra por sí sola llamadas PCSC específicas, enumeración exitosa de un lector, uso del minidriver Perú ni compatibilidad con NFC Android. La causa de no poder continuar sigue sin identificarse; desaparecer de la lista de procesos no prueba un crash.

## Conservación

Directorio privado ignorado por Git: `build/host-diagnostics/20261005-172216-fc8ac9b9`.

- `edge.jsonl` y `host.jsonl`: registros sanitizados.
- `capture-manifest.json`: tamaño y SHA-256 de cada registro conservado.
- `registro-conservado.zip`: copia verificada por CRC con los registros y manifiesto.

El ZIP no incluye el perfil Edge ni cookies/sesiones. Los capturadores terminaron; no se cerró la ventana de Edge ni se relanzó el componente. Cerrar esa ventana desactiva su puerto CDP.

Pendiente, para una sesión posterior autorizada: inspección del componente descargado y sus referencias WinSCard/eID/PKCS#11, identificación del mensaje exacto que impide continuar y comprobación de instalación del middleware. Este registro no demuestra una operación real sobre el DNI.
