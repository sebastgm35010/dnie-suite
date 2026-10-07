# Actualización H4 · sello visible

`--pdf-mode visible` añade sello blanco/negro al campo firmado; página, posición, campos y estilo card/minimal configurables. PDF invisible continúa por defecto. App Android instalada 56 sirve. [Uso y límites](../docs/suite/PDF_VISIBLE_H4.md). El motor se llama ahora `JvmPdfSigner`; la apariencia está separada del núcleo criptográfico. H3 físico validado; H4 físico pendiente.

# Suite de firma privada · estado vigente H3

La suite continua usa Android/DNI por TLS privado, PC recordada y PIN único por lote. PDF integrado invisible por defecto (`_firmado.pdf`); otros archivos CMS separado. Añadir `--pdf-mode detached` para conservar `.p7s` también en PDF. La firma remota CMS física está validada; PDF integrado de escritorio tiene pruebas sintéticas y verificación independiente, pendiente prueba física. [H3: arquitectura y uso](../docs/suite/PDF_INVISIBLE_H3.md).

El historial siguiente documenta los hitos previos; las limitaciones de H0/H1b sobre PDF no describen el estado H3.

# Suite local · registro histórico H0

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

La propuesta H0 de cliente PC/SC fue sustituida por el canal privado H1b descrito abajo. Contexto permanente: [CONTEXTO_SUITE_FIRMA.md](../docs/suite/CONTEXTO_SUITE_FIRMA.md).
# Actualización H1b: firma remota privada

El comando `remote` conecta con Android por TLS inverso y utiliza el DNI real exclusivamente
después de autorizar el lote/PIN en Certificados → Suite. No usa PC/SC ni fallback a demo.
Solicitud TXT/QR efímera por trabajo; documentos, CMS y resultados permanecen en PC.
Primera firma física LAN completada (1/1 CMS), con integridad/certificado DNI y rechazo de contenido alterado verificados mediante .NET; tests TLS con tarjeta simulada también pasan. [Procedimiento](../docs/suite/FIRMA_REMOTA_H1B.md).


## H2: Suite continua

Comando `suite --pc <IPv4 PC> --phone <IPv4 teléfono> --workspace <carpeta> --identity <archivo DPAPI>` desde terminal interactiva. Identidad PC persistente protegida por cuenta Windows; Android recuerda huella/token con Keystore. QR una vez, conexión foreground con varios trabajos, PIN sin eco en Windows por trabajo. No PC/SC, SYSTEM ni PIN por argv; sin PDF integrado. [Uso, arquitectura y límites](../docs/suite/SESION_CONTINUA_H2.md).
