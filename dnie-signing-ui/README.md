# Interfaz aislada de DNIe Suite

Frontend Swing/JVM Java 17 para el motor privado de firma. Vista previa PDF y selector de área, lote, campos, formato y conexión recordada. No utiliza PC/SC ni instala servicio/driver. Backend en `dnie-signing-desktop`, preview/render en `dnie-pdf-desktop`; llamadas fuera del EDT y PIN efímero por lote.

[Uso, pruebas y próximas integraciones](../docs/suite/INTERFAZ_AISLADA_Y_ESCANEO_UNIFICADO_H5.md).

```powershell
$env:JAVA_HOME='C:/Program Files/Microsoft/jdk-17.0.20.101-hotspot'
./gradlew.bat --offline '-Pkotlin.incremental=false' '-PdesktopBuildDir=D:/DNIeToolchain/Builds/signing-desktop-h0' :dnie-signing-ui:installDist
& 'D:/DNIeToolchain/Builds/signing-ui/install/dnie-signing-ui/tools/create-shortcut.ps1'
```

Abrir `DNIe Suite.lnk`, terminar primero otras sesiones sobre el mismo teléfono y usar Android → Suite → Conectar PC guardada. Reutiliza rutas e identidad de la CLI; modificar campos si se usa otro equipo. El área azul es guía, no preview de la identidad del certificado. Perfil común por lote; prueba física pendiente. Android 56 puede firmar; Android 57 añade lectura unificada local fuera de Suite. Puede abrirse con archivos como argumentos de inicio, nunca PIN/CAN.

Avisos PDF y helper QR acompañan la distribución. No se incluye Java ni middleware RENIEC; no hay backend simulado en la ventana. Pruebas de backend TLS utilizan simulación explícita de Android y tarjeta.
