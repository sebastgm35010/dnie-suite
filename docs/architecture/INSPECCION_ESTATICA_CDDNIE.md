# Inspección estática del componente Ciudadano Digital

Se inspeccionaron las copias locales de ClickOnce de `cddnie-app.exe` y `cddnie-core.dll` con ILSpy CLI 9.1.0.7988. No se ejecutó el componente, no se reabrió el navegador y no se enviaron operaciones al backend. El análisis no depende de que el proceso siga activo. Las salidas de descompilación permanecen privadas en `build/research/reniec-host/cddnie-static`, ignoradas por Git; no se incorporó código de terceros a la app.

## Identificación reproducible

| Archivo | SHA-256 |
|---|---|
| cddnie-app.exe | AB900EEAE6F239EB89FE94769DE5E23C7AB610E458F8CF46242289C1A460DC25 |
| cddnie-core.dll | BF0FFE343F7FC9A3DFF930860A4C3220A32E3C193D54BC755AAEF877FBCE78C2 |
| dcdeliverydnie3.jar, encontrado posteriormente en la caché del usuario | B46DD7FA9ABCC26E047F75E598FE7B5A34BE4AC44E7111E45F34698894CC67FD |

Se identificaron los ensamblados por su contenido y hashes. No se afirma que su firma Authenticode esté validada: la comprobación anterior del ejecutable devolvió UnknownError y la biblioteca NotSigned.

## Criterio exacto de rechazo inicial

`CddnieApplication.ObtenerVersionDnie` abre PC/SC y llama primero a `SmartcardDnieService.IsVersion2`, después a `IsVersion3` si la primera prueba no acepta la tarjeta. Cada método considera aceptado únicamente el status word 9000.

| Prueba | SELECT construido por el componente | Interpretación |
|---|---|---|
| v2 | `00 A4 04 0C 07 A0000000003F00` | aplicación usada para identificar v2 |
| v3 | `00 A4 04 0C 10 A000000077030C60000000FE00000500` | IAS v3 |

Si ambas pruebas fallan, devuelve exactamente «La tarjeta no es un DNIe válido» y termina después de intentar notificar el error. Los tamaños de estos comandos, 12 y 21 bytes, su orden y sus cabeceras coinciden con los dos SELECT registrados en la prueba de tarjeta vacía, que respondió 6A82 a ambos. Es una correlación fuerte entre análisis estático y captura; la captura no guardó el AID ni atribuyó el proceso por llamada y por sí sola no confirma los bytes completos.

El IAS v3 es el mismo AID que utiliza nuestro `V3PkiProbe` en `dnie-protocol`. El rechazo inicial observado tiene una explicación concreta: la simulación no implementa esas aplicaciones. No se ha observado un fallo de PACE en esa etapa.

## Transporte y ciclo de vida

`SmartCardService.Open` establece contexto, enumera lectores y elige `context.Readers.First()`. No ofrece selección de lector en este código. Conecta en modo compartido con cualquier protocolo y transmite mediante WSCT. La distribución incluye `WSCT.Wrapper.WinSCard.dll`.

Al terminar la detección, `Dispose` desconecta usando `Disposition.UnpowerCard` y libera contexto. El puente deberá respetar desactivación, reinicio y nueva conexión, sin asumir que una sesión PACE anterior sobrevive a esa secuencia. La elección del primer lector también puede causar problemas si hay varios dispositivos, por ejemplo Windows Hello; no se modificó su configuración.

El ejecutable obtiene los argumentos de la URL de activación ClickOnce. Sin activación de red o sin argumentos, retorna enseguida. En el rechazo inicial también retorna por una ruta prevista; una duración breve no prueba un crash. El muestreo del host cada dos segundos puede omitir procesos breves.

## Qué viene después de reconocer la tarjeta

La secuencia de `Run` es: notificar inicio, identificar v2/v3, resolver dependencias específicas de esa versión y ejecutar Java. La detección anterior usa PC/SC directamente; no aparece una llamada a PKCS#11 o al minidriver Perú en ese tramo.

`DependenciesManagerService` obtiene `descriptor.json` desde la URL de bibliotecas recibida por activación, selecciona las dependencias v2/v3 y compara sus hashes SHA-1. `JavaService` lanza un runtime propio `customjre/bin/java.exe`, recoge stdout y notifica resultados. La caché prevista es `.dcdelivery-2.0/files` dentro del perfil del usuario.

Ya existe allí `dcdeliverydnie3.jar`. Solo se inventariaron sus clases, sin ejecutarlo. Incluye clases RENIEC de lectura de tarjetas y postpersonalización, entre ellas `SmartCardReaderUtils`, `DnieCard`, `PostPersoDnieV3` y modelos de lectura, certificados, PIN y firma. La presencia de esos nombres no demuestra que todas las funciones se usen en la operación solicitada. Falta trazar sus llamadas para conocer APDU posteriores y quién gestiona autenticación/canales seguros.

El componente tiene instrucciones de logging que pueden incluir argumentos de activación, argumentos Java y resultados. No se leyeron esos logs ni se imprimieron URLs autenticadas; futuras capturas deben seguir filtrando valores personales y tokens.

## Siguiente paso para el puente

1. Inspeccionar estáticamente el JAR v3 y trazar la ruta de validación, los resets y las APDU posteriores; distinguir lectura de funciones de administración.
2. Diseñar un adaptador PC/SC hacia el motor Android que conserve estados y traduzca solo las operaciones compatibles, con un único propietario de PACE/SSC.
3. Probar primero el SELECT IAS contra un DNI real por NFC y devolver su respuesta real, después avanzar por operaciones de lectura autorizadas.
4. Verificar transporte autenticado y cifrado, aislamiento del puerto del lector virtual y manejo de desconexión antes de usar datos reales.

No se cambió la tarjeta vacía para devolver 9000 artificialmente, ni se modificaron los binarios oficiales. Superar únicamente la identificación no demostraría compatibilidad del motor Java ni de otras apps de firma.
