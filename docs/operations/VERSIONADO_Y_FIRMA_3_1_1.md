# Versionado 3.1.1 y firma estable de los APK Android

2026-10-14. El usuario confirma instalación manual de APK84, habilita nuevamente ADB y solicita incrementar 3.1.0→3.1.1 y las entregas siguientes. APK85 / 3.1.1 instalada por ADB con reemplazo, conservando applicationId y firma. No se desinstala ni borra el almacén de la app.

## Versión única y próximas entregas

`gradle.properties` define `dnie.versionName=3.1.1` y `dnie.androidVersionCode=85`. La raíz Gradle aplica esa versión a sus proyectos; Android consume ambos valores. Configuración muestra `DNIe Evolución 3.1.1 (85)` usando BuildConfig, sin texto de versión fijo.

Antes de una entrega nueva ejecutar `python tools/bump-version.py --next-patch`: la siguiente será 3.1.2 / 86, luego 3.1.3 / 87. Sin argumento solo consulta. No incrementar por cada compilación, test o reintento de la misma entrega. No alterar versiones históricas de informes ni atribuir la versión nueva al controlador/servicio Windows que no se recompiló o instaló.

## Firma de la app y del auxiliar

La app debug y el auxiliar androidTest ya compartían la firma en APK84: se comprobó antes de modificar. Se mantiene el keystore local de evolución y la herencia de signingConfig del tipo debug en androidTest. No se genera otra identidad ni se añade el keystore a Git.

`tools/android/verify-debug-pair.ps1` verifica criptográficamente ambos APK, exige un único certificado y comprueba paquete de app/auxiliar, igualdad de certificados y compatibilidad con APK anterior, incluido incremento de versionCode. Detiene la instalación si cambia la identidad. Ejemplo:

```powershell
./tools/android/verify-debug-pair.ps1 -AppApk '<app-debug.apk>' -TestApk '<app-debug-androidTest.apk>' -PreviousApk '<entrega-anterior.apk>'
```

El auxiliar conserva su paquete estable `pe.gob.reniec.dnie.evolution.debug.test` y está instalado para actualizaciones posteriores con `adb install -r -t`. AGP no escribe versionName/versionCode en su manifiesto de prueba; la comprobación de firma/paquete sigue siendo estricta. No se distribuye dentro de producción. Misma firma no elimina necesariamente un aviso del instalador del fabricante; en esta ejecución ambos se instalaron con éxito y sin modificar políticas del teléfono.

## Verificación realizada

- App y auxiliar con mismo SHA-256 de certificado; compatible con APK84. Instalaciones ADB: Success / Success. Estado instalado comprobado: 3.1.1, código85. Arranque de MainActivity hacia Inicio.
- Debug y release compilados offline. Reutilización de caché mutable en `Builds/flows-r8/modules`; entregables85 copiados a carpeta de versión y AndroidPackages, con hash propio. La APK84 entregada y la evidencia histórica R8 permanecen conservadas.
- 90 casos unitarios Android:89 pasan,0 fallos/errores,1 omitido por privilegios Windows de un enlace de directorio.
- Cuatro pruebas instrumentadas R8 pasan en9,22s: continuidad de hoja NFC hasta resultado, CAN compacto/PIN transitorio, reintento explícito y barra fija/tabs sobre PDF sintético. Se corrigió únicamente la ruta del PDF de fixture para usar un directorio que ya permite FileProvider; no se amplían permisos ni rutas del proveedor.
- No se utilizó DNI, PIN real ni operación de firma/cifrado. NFC/TSA físicos, háptica percibida, cambio de red y suspensión siguen pendientes. Las pruebas de interfaz no acreditan esos flujos.

## Rutas y retención

```text
D:/DNIeToolchain/AndroidPackages/
  dnie-evolution-3.1.0-v84-flows-r8-debug.apk
  dnie-evolution-3.1.1-v85-debug.apk
D:/DNIeToolchain/Builds/version3.1.1/
  delivery/dnie-evolution-3.1.1-v85-debug.apk
  delivery/dnie-evolution-3.1.1-v85-androidTest.apk
  signing-pair.json
  android-r8-tests.log
  installed-version.txt
  build-final.log
G:/Mi unidad/BOT TRADER/DNI_CODEX/revision-versionado-3.1.1-20261014/
  INFORME_VERSIONADO_3_1_1.md
  resultados.json
  apk/
  evidencias/
  GIT_CHECKPOINTS.json
  SHA256SUMS.json
```

APK83 se conserva en `D:/DNIeToolchain/Checkpoints/pre-version3.1.1`. Fuente previa etiquetada `checkpoint/pre-version3.1.1`. No se actualizan la Suite/Bridge Windows instaladas ni servicio/driver.
