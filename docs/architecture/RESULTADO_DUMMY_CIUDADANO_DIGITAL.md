# Prueba manual con lector Bix y tarjeta vacía

El usuario informó «La tarjeta no es un DNIe válido», después de que el flujo solicitara su número de DNI. A su solicitud se cerró únicamente Edge de diagnóstico, se detuvieron capturadores y se retiró la tarjeta simulada cerrando su cliente local. No se continuó la operación ni se repitieron solicitudes autenticadas.

## Qué demuestran los registros

En la captura `20261005-173308-49b8e13c`, el navegador recibió HTTP 200 para autorización, parámetros de validación y manifiesto ClickOnce. El muestreo de host observó rundll32 y dfsvc durante el lanzamiento, pero no captó un proceso cddnie-app persistente. Eso puede deberse al intervalo de dos segundos, al proceso/nombre usado o a una salida breve; no se diagnostica un crash a partir de esa ausencia.

Durante el intervalo de esta captura la tarjeta recibió dos APDU, ambos con cabecera `00 A4 04 0C` (SELECT por nombre de aplicación, sin FCI): tamaños totales 12 y 21 bytes, separados por nueve milisegundos. Ambos respondieron 6A82, exactamente la política de la tarjeta vacía. Las cabeceras no contienen el AID seleccionado; no se atribuye un AID, una versión de DNI ni el proceso emisor por la longitud solamente. También hubo 94 solicitudes de ATR y dos controles; Windows puede originar sondeos automáticos.

Existe intercambio real de APDU a través del lector virtual. El error comunicado es coherente con aplicaciones ausentes en la simulación; no implica que el lector haya dejado de existir. La captura no registra el texto del error ni atribuye individualmente las llamadas WinSCard a un proceso, por lo que no prueba el criterio exacto de validación ni que el middleware Perú participara en esos SELECT.

No se probó PACE, PIN, certificados, firma, identidad personal ni el puente Android. La tarjeta era genérica, sin identidad RENIEC y sin respuesta de autenticación exitosa.

## Por qué pide el número de DNI

Se revisó nuevamente el JavaScript público, mediante GET estático sin credenciales:

- [Módulo de operaciones](https://pki.reniec.gob.pe/ciudadanodigital/chunk-NWAYOVMI.js): formulario con etiqueta de número de DNI, control de entrada y reCAPTCHA.
- [Servicio frontend](https://pki.reniec.gob.pe/ciudadanodigital/chunk-WCXMVV2V.js): `obtenerSesion` llama a `/autorizar` con parámetros `numeroDni`, `recaptchaToken` y `tipoOperacion`; después se obtiene la URL de validación para abrir el componente.

Por tanto, solicitar el número pertenece al flujo oficial de RENIEC y permite preparar la sesión/operación. No es el CAN de PACE ni el PIN del chip. La app Android del fork no intervino en esta prueba. El número introducido, captcha, token, URL autenticada y cuerpos no se conservaron ni se consultaron al analizar.

## Registro conservado y siguiente investigación

ZIP privado, ignorado por Git: `build/host-diagnostics/20261005-173308-49b8e13c/registro-dummy-conservado.zip`, con edge.jsonl, host.jsonl, copia del log de la tarjeta, errores de capturadores y manifiesto SHA-256. Verificado por CRC; sin perfil de navegador ni cookies.

El siguiente trabajo es inspeccionar el componente local ya descargado y sus referencias/importaciones para identificar aplicaciones y APIs esperadas, antes de diseñar un perfil virtual con terminación PACE. No devolver 9000 indiscriminadamente ni fingir un DNI válido: ello no demostraría interoperabilidad con una tarjeta real. Una prueba futura necesita trazas atribuibles o análisis estático, y luego operaciones reales autorizadas usando el adaptador Android existente.
