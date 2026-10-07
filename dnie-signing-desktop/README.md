# Suite local · primer hito

Ejecutable Kotlin/JVM, sin GUI, que reutiliza `dnie-core` y `dnie-signing-core`. Esta entrega es **simulación explícita**, con una clave temporal y un certificado sintético por trabajo. No firma con el DNI, no conecta a PC/SC y no usa certificados personales Windows. La clave simulada no se exporta.

```powershell
$env:GRADLE_USER_HOME = 'C:/Users/LENOVO/.gradle'
.\gradlew.bat --offline '-Pkotlin.incremental=false' '-PdesktopBuildDir=D:/DNIeToolchain/Builds/signing-desktop-h0' :dnie-signing-desktop:test :dnie-signing-desktop:installDist

$env:JAVA_HOME = 'C:/Program Files/Microsoft/jdk-17.0.20.101-hotspot'
& 'D:/DNIeToolchain/Builds/signing-desktop-h0/install/dnie-signing-desktop/bin/dnie-signing-desktop.bat' demo --workspace 'D:/DNIeToolchain/SuiteJobs' --input 'D:/ruta/archivo.txt'
```

Se requiere Java 17. `desktopBuildDir` es opcional; sin él se usa el build del módulo. No se descarga otro SDK/JDK ni se instala un servicio. `installDist` genera los launchers estándar Gradle y dependencias JVM; el BAT solo lanza Java, sin elevación, host del puente o instalaciones.

Cada trabajo recibe un UUID y contiene `originals/`, `.p7s` bajo `signed/`, `certificate.cer`, `certificate.pem`, `signed-documents.zip` y `result.json`. El manifest declara `backend: SIMULATED`, estados y rutas relativas. Archivos PDF también se tratan como originales binarios con CMS separado; todavía no hay PDF integrado.

Para verificación independiente en Windows:

```powershell
& './dnie-signing-desktop/tools/verify-job.ps1' -JobDirectory 'D:/DNIeToolchain/SuiteJobs/UUID-del-trabajo'
```

El script usa .NET SignedCms, comprueba identidad del certificado del trabajo y rechazo al alterar contenido en memoria. No comprueba confianza completa ni revocación. Un certificado sintético válido criptográficamente no es una firma personal del DNI.

El motor inyecta tarjeta/PDF; los adaptadores de almacenamiento, CLI y resultados están en archivos separados. Límites: 50 entradas, 256 MiB por archivo, 512 MiB por trabajo. Se copian originales antes de abrir la sesión; nombres iguales no se pisan. El core mantiene autorización por operación dentro de un lote y detiene operaciones privadas tras pérdida/rechazo. La propiedad de la conexión asegura un solo cierre, incluso si falla la importación.

Códigos CLI: `0` ayuda o trabajo completo; `1` resultado incompleto/falla; `2` argumentos inválidos. No se aceptan PIN/CAN como argumentos. No hay fallback automático desde un modo real a simulación. Workspace y resultados de laboratorio deben permanecer fuera de Git.

Siguiente entrega: adaptador cliente PC/SC, selección explícita del lector, credencial oculta y primera firma CMS con DNI aprobado. Contexto permanente: [CONTEXTO_SUITE_FIRMA.md](../docs/suite/CONTEXTO_SUITE_FIRMA.md).
