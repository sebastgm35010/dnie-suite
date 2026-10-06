# Bóveda: despliegue inicial del motor NFC

Fecha: 2026-10-04. Versión: **1.3.0-vault-lab**, versionCode **14**.
Paquete: `pe.gob.reniec.dnie.evolution.debug`; nombre visible: **DNIe Evolucion**.

## Alcance entregado

Primera etapa del [plan de bóveda y passkeys](PLAN_BOVEDA_Y_PASSKEYS.md): prueba de capacidad privada real, con UI independiente creada por el subagente encargado de diseño. El APK permite ejecutar una prueba por NFC con el DNI del usuario. La ejecución física permanece pendiente; las pruebas automatizadas no acreditan compatibilidad de la tarjeta real.

Todavía no hay almacenamiento de documentos, catálogo persistente ni proveedor de passkeys. El resultado positivo habilita continuar el desarrollo; no habilita automáticamente esos productos ni sustituye la revisión del esquema criptográfico de producción.

## Motor y separación

- `dnie-vault-core`: módulo JVM independiente de Android, transporte, PDF e ICAO. `VaultCapabilityProbe` valida la pública de cifrado RSA-2048, crea datos sintéticos, envuelve un secreto aleatorio de 32 bytes y verifica su recuperación exacta. Con el secreto recuperado abre un pequeño contenido AES-256-GCM y comprueba su integridad. No es un formato de archivos ni un motor de streaming.
- `VaultProbeCard` y `DnieTransceiver`: contrato y operación experimental en protocolo. El perfil v3 y el transporte extendido se comprueban antes del PIN. La operación privada consume la autorización de esta prueba y los errores cierran la sesión.
- `CommandApdu`: representación, serialización y parseo de APDU extendidas; los comandos cortos conservan su codificación. El parser ahora rechaza bytes sobrantes y longitudes inconsistentes.
- `PaceSecureChannel`: DO97 extendido, carga segura larga y APDU física extendida. Mantiene MAC, SSC y GET RESPONSE físico previo a autenticar la respuesta. No añade command chaining ni reintentos de descifrado.
- `IsoDepCardChannel`: declara capacidad extendida y límite del lector; rechaza comandos que el dispositivo no puede transportar.
- `VaultViewModel`: coordinación, credenciales efímeras, cancelación y resultados. La criptografía de software se ejecuta fuera del hilo principal; el dispatcher puede sustituirse en pruebas.

El perfil privado sigue la sección 3 del [manual oficial v3 de RENIEC](https://identidad.reniec.gob.pe/documents/d/guest/manual_comandos_dnie_v3): clave 83, MSE `002241B8098401838004FF300100`, PSO `002A8086`, indicador `81` y bloque RSA de 256 bytes. Se prueba RSA PKCS#1 v1.5; no se asume OAEP ni se ensayan alternativas automáticamente. El comando protegido de esta implementación tiene 300 bytes y debe probarse en el canal PACE/NFC real.

## UI y manejo de secretos

Entrada **Bóveda** en la cabecera de Certificados. Pantalla dedicada con fondo oscuro, acento verde, campos CAN/PIN ocultos, progreso, cancelación y huella SHA-256 del SPKI. Sin barra inferior durante la prueba; volver superior, volver del sistema, salir de la ruta y segundo plano cancelan el trabajo.

No se usan preferencias, SavedState ni archivos para guardar credenciales, claves o datos sintéticos. El PIN no se reintenta automáticamente. Las credenciales desaparecen de los campos al comenzar la operación y se eliminan del estado al cancelar/finalizar. Se cierran conexiones descartadas y se ignoran resultados de trabajos cancelados.

La respuesta privada PSO se registra únicamente como datos ocultos y estado SW; el tráfico SM físico contiene ciphertext. El buffer de respuesta plana del transceptor se limpia tras parsearlo, y el motor limpia sus buffers de secreto y contenido. Esto es limpieza de mejor esfuerzo: JVM/JCA pueden conservar copias internas en memoria. Durante la ruta de bóveda se activa FLAG_SECURE para proteger capturas y vista de recientes según las garantías del sistema Android.

La prueba verifica relación entre la pública leída y la operación privada, no confianza de la cadena de certificados ni validación de identidad con una autoridad. El resultado no significa que una passkey resida dentro del chip.

## Validación

Compilación y exportación APK completadas. **203 pruebas JVM aprobadas**, cero errores/fallos:

| Módulo | Pruebas |
|---|---:|
| dnie-core | 48 |
| dnie-protocol | 61 |
| dnie-vault-core | 5 |
| dnie-icao | 27 |
| dnie-pdf | 20 |
| app | 42 |

Cobertura nueva: codificación APDU contrastada con la implementación independiente del JDK en límites de longitud; comandos oficiales; cifrado y MAC del comando extendido comprobados independientemente; rechazo de MAC alterado; secretos ausentes del historial/replay; negativa a ejecutar una operación sin sesión autorizada; ciclo completo con RSA privado de prueba; rechazo de secreto distinto; cancelación, repetición de tags, transporte/perfil no declarado, PIN incorrecto sin retry y limpieza de buffers/estado.

También compiló `:app:compileDebugAndroidTestKotlin`. No se ejecutaron pruebas instrumentadas ni NFC en el teléfono durante esta entrega.

```powershell
.\gradlew.bat :dnie-core:test :dnie-protocol:test :dnie-vault-core:test :dnie-icao:test :dnie-pdf:testDebugUnitTest :app:testDebugUnitTest :app:exportBranchApk :app:compileDebugAndroidTestKotlin --console=plain
```

## Artefactos y preservación

APK: `app/build/outputs/apk/evolution/dnie-evolution-debug.apk`.

SHA-256: `96FFA9B9645D8D74F021A8D900BA5110D10D191C6C9F9F963A24B39408AD9E6C`.
Firma APK verificada mediante apksigner, esquema v2; conserva la identidad de firma del fork.

Antes de compilar se conservó el APK anterior en `app/build/outputs/apk/checkpoints/dnie-evolution-1.2.1-signing-dev.apk`, SHA-256 `6590B216C02AA1291F1C6005FF571BAB144B615E3D627C38840674E46225CD8B`. Es respaldo del binario, no un nuevo commit/checkpoint de todos los cambios pendientes del código. No se hizo reset, stash ni commit de cambios previos.

La base clausurada sigue con APK SHA-256 `B5E3754E409A5BED57E01A4810C7FC5F1D0E1597E44C8B1E3E0D4E21A571BA94`. Antigravity permanece intacta. No se instaló el APK ni se controló el teléfono.

## Prueba física siguiente

1. Instalar este APK sobre DNIe Evolucion.
2. Entrar en **Certificados → Bóveda**.
3. Introducir CAN y PIN, pulsar **Iniciar prueba NFC** y acercar el DNI v3. Mantenerlo inmóvil hasta el resultado.
4. Si aparece **Descifrado comprobado**, el ciclo sintético se verificó en esa ejecución. Si falla, conservar el resultado y el diagnóstico APDU para revisar la fase exacta. No reintentar PIN incorrecto sin corregirlo.
5. Repetir una firma PDF y comprobarla externamente como regresión física del fork.

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r "G:\Mi unidad\BOT TRADER\DNI_CODEX\modest-bose-evolution\app\build\outputs\apk\evolution\dnie-evolution-debug.apk"
```

Si el lector no soporta el comando extendido o el chip rechaza el perfil protegido, investigar transporte/encadenamiento con evidencia antes de modificarlo. Después de aprobar capacidad NFC y perfil criptográfico, seguir con streaming autenticado, contenedores versionados y recuperación/migración explícitas; passkeys continúan como etapa separada.
