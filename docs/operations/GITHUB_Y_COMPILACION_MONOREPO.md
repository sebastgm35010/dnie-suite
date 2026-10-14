# GitHub privado y compilación del monorepo

Repositorio: [sebastgm35010/dnie-suite](https://github.com/sebastgm35010/dnie-suite), privado, rama principal `main`. El código Android/Windows/driver comparte un repositorio; sus módulos y distribuciones permanecen independientes. La base clausurada 1.0.8 y los archivos de ejecución de esta PC no se convierten en otra raíz de desarrollo.

La carpeta local conserva su nombre `modest-bose-evolution` para mantener rutas y worktrees existentes. El remoto GitHub se denomina `origin`; la referencia local a la base congelada se conserva como `base-local` y no recibe publicaciones. Se conserva la rama histórica `baseline/1.0.8` y los checkpoints Git, incluido `checkpoint/pre-github-private-monorepo`. No se reescribe el historial.

## Autenticación

GitHub CLI se autentica mediante el navegador (`gh auth login --hostname github.com --git-protocol https --web`). No guardar PAT, tokens OAuth ni contraseñas en archivos del proyecto o URL de remotos. Las credenciales pertenecen al almacén local de GitHub CLI/sistema; la cuenta que clone el repositorio necesitará acceso privado.

## Contenido

- Fuentes, tests, documentación vigente e histórica, avisos de terceros y registro SQLite/SQL.
- Historial Git y checkpoints de código, tras revisar los archivos y patrones de secretos antes de subirlos.
- Fuera de Git: keystores, PFX/P12, identidades de PC, perfiles/token de emparejamiento, registros APDU/diagnóstico personales, documentos de usuario, SDK/WDK, builds, APK y bases temporales del registro.
- Distribuciones compiladas se publican como artefactos/Releases privados cuando se preparen; no almacenar copias binarias en cada carpeta de fuentes.

## Preparar un clon

1. Clonar el repositorio privado con la cuenta autorizada.
2. Instalar JDK 17 y configurar `JAVA_HOME`; no hay ruta fija de Java en `gradle.properties`.
3. Para Android, instalar SDK/API 34 y configurar `ANDROID_HOME` o un `local.properties` local con `sdk.dir`. No versionar ese archivo.
4. Para Bridge, usar Windows y .NET SDK 8 con Windows Desktop. Para el driver, añadir MSVC/SDK/WDK y consultar su README.
5. Elegir un directorio de compilación fuera del repositorio; `tools/build_suite.py --plan` permite revisar los comandos sin ejecutarlos.

```powershell
python tools/build_suite.py --component all --output D:/DNIeToolchain/Builds/clon --test
```

`--offline` limita Gradle a dependencias ya cacheadas. El empaquetado .NET usa el cache indicado por `NUGET_PACKAGES` o `<output>/nuget-packages`; puede requerir red al restaurar paquetes por primera vez. El componente `suite` puede compilar sin el adaptador opcional de Bridge y sin instalar un lector PC/SC. `all` construye ese adaptador antes de generar la distribución de la Suite.

## Firma Android y continuidad de las instalaciones

La copia local existente mantiene `signing/evolution-development.jks`, excluido de Git. Si existe, se usa para las actualizaciones debug/release como hasta ahora. Un clon sin ese archivo utiliza la firma debug del SDK y produce release sin firmar.

Para configurar otra identidad privada de compilación, proporcionar variables de entorno, nunca argumentos con contraseñas:

- `DNIE_SIGNING_KEYSTORE`: ruta al keystore existente.
- `DNIE_SIGNING_STORE_PASSWORD`: contraseña de almacén.
- `DNIE_SIGNING_KEY_ALIAS`: alias.
- `DNIE_SIGNING_KEY_PASSWORD`: contraseña de clave.

Los valores de compatibilidad `android` / `evolution` son únicamente los de la identidad de desarrollo actual. Una ruta explícita inexistente produce error; no se degrada silenciosamente a otra identidad. Las contraseñas pueden introducirse de forma local o mediante secretos del entorno de compilación.

Para actualizar un teléfono que ya tenga nuestra app, conservar el keystore que firmó esa instalación. El repositorio contiene el código, no sustituye el respaldo privado del keystore, de la identidad TLS ni de la bóveda del usuario.

## Límites de portabilidad actuales

El nuevo punto de compilación permite elegir rutas y no instala componentes. El instalador/servicio Bridge actual conserva `D:/DNIeBridgeApp`, y varias herramientas históricas documentan rutas de esta PC. Generalizar ese almacenamiento y el despliegue del driver a otro equipo es un trabajo independiente; no se modifican esos componentes al publicar el código.

Los informes de G:/D: son evidencias históricas. Usar el README raíz y los documentos vigentes como entrada, sin repetir comandos de diagnóstico o instalación antiguos.
