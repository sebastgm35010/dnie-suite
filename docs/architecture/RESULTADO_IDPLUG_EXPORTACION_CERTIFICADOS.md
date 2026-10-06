# IDplug Manager: registro PKCS#11 y exportacion de certificados

Fuente privada proporcionada por usuario: D:/Winodws files/Descargas/log dnie id plg/. Ventana del log:6 octubre2026,17:49:30.011–17:51:02.390 hora local. No confundir con Ciudadano Digital, Firma Peru ni atribuir uso de APK1.8.15: esta version refactorizada no se instalo durante el trabajo.

## Hallazgos

Log5414 lineas, IDplug-pkcs11 64bits producto4.6.1 / archivo2.8.3.475. Biblioteca cargada desde D:/DNIeToolchain/OfficialMiddleware/IDPlugClassic-x64/DLLs/idplug-pkcs11.dll. Confirma que el administrador trabaja mediante su biblioteca PKCS#11, aunque el minidriver Windows este aislado.

C_OpenSession, busqueda de objetos y C_GetAttributeValue presentes. No hay BEGIN C_Login ni BEGIN C_Sign en este archivo: acredita lectura/exportacion/gestion, no nueva firma ni nueva autenticacion con PIN. Consultas VERIFY protegidas sin datos aparecen como parte de consulta de estado. Los mecanismos enumerados no deben interpretarse automaticamente como todos soportados por esta tarjeta.

Slot1:18 resultados CKR_OK en llamadas token/mecanismos/credenciales; incluye APDU protegidas. Slot3:12 CKR_TOKEN_NOT_RECOGNIZED para esas mismas familias. Enumeracion inicial contiene puente, virtualPCD y WindowsHello, pero la descripcion de slots del logger esta truncada a un caracter: no se asigna definitivamente slot3 a Hello sin mas evidencia. Los errores no demuestran token DNI desconocido en slot1.

6 CKR_ATTRIBUTE_TYPE_INVALID en consultas de atributos a las17:50:04: atributos no disponibles para esos objetos; no afirmar bloqueo de tarjeta. Los7 CKR_CRYPTOKI_NOT_INITIALIZED se concentran en Finalize/CloseSession al salir17:51:01; no fueron un fallo de firma.

## Certificados exportados

| Archivo | Bytes | Funcion segun extensiones X.509 |
| --- | ---: | --- |
| 1.cer |1584| Autoridad CA, RSA4096 |
| 2.cer |1656| Autoridad CA, RSA4096 |
| 3.cer |1738| Autoridad CA, RSA4096 |
| 4.cer |2321| Personal cifrado, keyEncipherment/dataEncipherment, RSA2048 |
| 5.cer |2403| Personal firma, contentCommitment, RSA2048 |
| 6.cer |2346| Personal autenticacion, digitalSignature, RSA2048 |

Todos parseados como DER X.509. Se verificaron firmas de la cadena de autoridades1(autoemitida)→2→3. Ninguna autoridad de estos seis archivos coincide como emisor directo de los tres certificados personales; no constituye por si sola una cadena completa de confianza para ellos. No se instalo ninguna autoridad ni se cambiaron almacenes de confianza.

La evidencia mas fuerte:5.cer coincide byte a byte con el certificado firmante embebido en el PDF de Firma Peru de Android21, cuya firma ya se verifico intacta y valida. Esto enlaza la exportacion IDplug con el DNI que realizo la firma real.

## Consecuencia para el motor

Preservar la via funcional de lectura/gestion y firma viaPKCS11. Separar errores de otros slots de la sesion activa. Log PKCS11 es una fuente adicional para atribuir cliente/slot/operacion en futuros ensayos; no requiere copiar biblioteca propietaria ni registros personales al repositorio. Rechazo SM tras reset y selector Java de Ciudadano Digital siguen pendientes distintos.

Solo informe sanitizado versionado. Certificados y log permanecen en carpeta original, sin modificaciones; resumen privado en D:/DNIeToolchain/HistoricalResults/idplug-export-2026-10-06-summary.json.
