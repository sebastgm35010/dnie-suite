# Vista previa compatible y CAN compartido — 1.5.1

Versión `1.5.1-vault-preview-can`, código 23. Cambios en el fork de evolución; base y Antigravity sin modificaciones.

## Compatibilidad del visor

`app/domain/vault/VaultPreviewPolicy` define las capacidades y el límite de tamaño sin depender de Android ni Compose. La biblioteca obtiene un booleano `canPreview` en cada elemento: la tarjeta no interpreta extensiones ni abre archivos.

Se ofrece vista previa de PDF, PNG/JPEG/WebP y texto. AAC, MP3, vídeos, hojas de cálculo, ejecutables y archivos comprimidos no tienen botón de vista previa. Los archivos grandes tampoco lo tienen. La opción de descifrar una copia se conserva.

Los objetos nuevos guardan un MIME orientativo en el catálogo privado, junto a sus etiquetas visibles. Los anteriores usan la extensión del nombre como indicio; si no hay información suficiente no se ofrece vista previa. Después de restaurar un objeto se aprende el MIME autenticado. El esquema local 3 mantiene lectura de versiones 1 y 2; el contenedor criptográfico no cambia.

El MIME del catálogo o un nombre importado no certifican el contenido: `VaultPreviewFiles` vuelve a consultar la misma política usando los metadatos autenticados, antes de abrir el visor. Un MIME explícitamente incompatible prevalece sobre una extensión aparentemente compatible. Texto se presenta literalmente; no se ejecuta HTML.

## CAN compartido

`CanStorage` se traslada desde `ui/components` a `data/preferences`. Se mantienen exactamente el archivo de preferencias y la clave de la pantalla principal; no hay migración, segundo CAN ni almacenamiento de credenciales dentro del contenedor de bóveda.

`RememberedCanStore` define el contrato suspendido que consume el ViewModel. `AndroidRememberedCanStore` accede a la preferencia compartida en IO. El ViewModel recibe el contrato y puede probarse con un almacén en memoria, sin Context ni pantalla principal.

Al entrar o regresar a la bóveda se carga el CAN guardado. «Recordar CAN» se activa cuando ya hay un CAN válido almacenado. Al iniciar una operación NFC con esa opción se guarda el valor actual. Tras la operación o cancelación reaparece el CAN recordado; el PIN se borra siempre y una nueva operación continúa necesitando acción explícita y PIN.

Desmarcar «Recordar CAN» borra la preferencia compartida, por lo que también deja de recordarse en la pantalla principal. Se rechazan valores guardados que no sean seis dígitos ASCII. El CAN no entra en logs, exports, backups ni saved-state de Compose; permanece en la preferencia privada existente cuando el usuario elige recordarlo. Esa preferencia no se describe como almacenamiento cifrado. El estado sigue ocultando las credenciales en `toString`.

## Validación

Regresión y compilación debug/exportación finalizadas correctamente: 233 pruebas sin fallos, errores ni omisiones (núcleo 49, protocolo 61, bóveda 24, ICAO 27, PDF 20 y app 52). Nuevas pruebas: formatos compatibles/incompatibles y límites; CAN heredado, refresco al cambiar desde la pantalla principal, cancelación sin conservar PIN, desactivación y valores inválidos; compatibilidad del esquema del catálogo y MIME orientativo. Se ejecutaron los módulos afectados y se reutilizaron resultados válidos de los módulos sin cambios.

Los fuentes instrumentados Android compilaron; no se ejecutaron pruebas instrumentadas ni una nueva lectura NFC física en esta entrega. `git diff --check` pasó sin errores. El APK está en `app/build/outputs/apk/evolution/dnie-evolution-debug.apk` y no se instaló automáticamente.

Firma APK v2 verificada. Paquete `pe.gob.reniec.dnie.evolution.debug`, código 23. SHA-256: `D3CB59B9E82D2795AFF42D5BE1C6A0A44841E13DD933EEB62D2F6FDB641B17A6`.

Las reglas de formato, almacenamiento y sesión no residen en componentes visuales. Un rediseño de la UI puede conservar la política, los casos de uso y adaptadores.
