# Recuperación ante renovación o pérdida de claves del DNI

Revisión conceptual: 2026-10-07. Diseño de recuperación pendiente; no cambia el formato ni las autorizaciones actuales.

## Regla corregida

No asumir que renovar certificados, reemitirlos o renovar el DNI conserva las claves privadas anteriores. Tratar cualquier operación externa que las sustituya como potencialmente destructiva para la recuperación. El mismo documento físico, número de DNI, CAN, PIN o identidad civil no demuestra continuidad criptográfica.

RENIEC indica cuatro años desde la emisión del certificado, no cuatro años desde cada lectura ni necesariamente desde la expedición del documento: [preguntas frecuentes](https://identidad.reniec.gob.pe/preguntas-frecuentes-identidad-y-certificados-digitales). Usar notBefore/notAfter reales; no calcular vigencia fija para todos los certificados. RENIEC describe incorporación de nuevos certificados para reemplazar los anteriores en [trámites de certificación](https://identidad.reniec.gob.pe/servicios) y contempla re-emisión de llaves en la [DPR 8.1, 2025](https://identidad.reniec.gob.pe/documents/d/guest/declaracion-practicas-2025).

El usuario informa que Ciudadano Digital reemplaza los certificados y genera nuevas claves en el chip. No hemos capturado esa operación destructiva ni confirmado sus APDU exactas para este DNI v3. La arquitectura debe soportar ese escenario sin depender de que sobreviva una clave antigua. No ejecutar la renovación real como prueba.

## Auditoría de la implementación actual

| Área | Implementación / consecuencia |
| --- | --- |
| Contenedor DNIEVLT1 | VaultContainer genera un secreto por contenedor; lo envuelve con RSA del destinatario y protege un keyset Streaming AEAD. recipient es SHA256 de la pública codificada, no del documento ni del certificado completo. |
| Renovación con misma pública | Cambiar certificado/DER sin cambiar la pública no produce por sí solo la pérdida criptográfica; comparar pública, además de vigencia/política. |
| Reemplazo de pública/privada | La nueva privada no abre el sobre anterior. Copiar el certificado público antiguo no conserva la privada. |
| Bóveda: respaldo ZIP | VaultBackup copia contenedores cifrados, no añade otro destinatario. Sirve contra pérdida del archivo; no recupera una clave de DNI eliminada. |
| Passkeys: catálogo | Las claves ES256 por sitio son propias de las passkeys, no son las claves RSA del DNI. EncryptedPasskeyRepository protege el catálogo con VaultContainer; reemplazar la clave RSA pierde acceso al catálogo, no cambia las públicas registradas en los sitios. |
| Passkeys: exportar/restaurar | PasskeyMaintenance.export vuelve a cifrar para la misma pública. restore y restorePrevious exigen la privada correspondiente. El rollback cifrado también puede quedar inaccesible tras la renovación. |
| Passkeys: migrar | Abre primero el catálogo con el DNI anterior, conserva datos en RAM y después autoriza el nuevo. PasskeyToolsViewModel vence esa transición en dos minutos. Puede servir con dos claves disponibles; no es un asistente de renovación destructiva en el mismo chip. |
| Delegación biométrica | PasskeyDeviceVault tiene una copia del catálogo bajo Keystore/biometría y vinculada a revisión. Puede permitir autenticaciones mientras teléfono/clave/revisión sobrevivan; mantenimiento sigue exigiendo DNI. No es respaldo portable ni recuperación garantizada. |
| Vencimiento | No confundir expiración/revocación del certificado con destrucción de privada. PasskeyCardAccess comprueba vigencia al crear, no impide automáticamente abrir un catálogo antiguo solo por fecha. Acceso depende de disponibilidad y funcionamiento de la clave. |

Las comprobaciones actuales de destinatario son correctas y deben mantenerse. El fallo es considerar suficiente el respaldo cifrado para continuidad tras eliminación de claves, o presentar migración como solución general a renovación.

## Alternativas de arquitectura para decidir

1. Modo exclusivo DNI: conservarlo, pero explicitar que perder/reemplazar la privada sin una preparación previa puede hacer irrecuperables los datos. No existe recuperación automática solo por acreditar la misma identidad.
2. Recuperación opcional independiente: secreto aleatorio de alta entropía guardado fuera del DNI y del teléfono, que proteja una vía adicional de acceso a los secretos de los contenedores o a un respaldo. El DNI permanece como vía cotidiana. Este modo cambia la exclusividad criptográfica del DNI y debe elegirse expresamente; no activar por defecto ni esconderlo tras una autenticación de identidad.
3. Preparación antes de renovar: con la clave antigua aún operativa, crear y verificar un paquete cifrado de transición independiente. Después autorizar el nuevo DNI y volver a proteger los datos con su nueva pública. Debe sobrevivir al cierre del proceso, no depender de dos minutos de RAM. Requiere definir qué secreto/segundo dispositivo protege el paquete; un ZIP cifrado con la clave antigua no sirve.

Preferencia propuesta, aún sin implementar: modo 2 opcional más asistente de modo 3. Vault sin biometría, conforme a la instrucción vigente. Mantener la copia biométrica de passkeys como comodidad, no como única estrategia de recuperación.

No usar como clave de recuperación DNI, CAN, PIN, datos ICAO, nombre, fecha de nacimiento ni una firma determinista sin un diseño revisado. Autenticar al mismo ciudadano con un certificado nuevo no permite calcular la privada ni el secreto anterior.

## Implementación pendiente

- Mensajes precisos: «clave anterior de este DNI» y «respaldo de archivos, no recuperación de clave»; no sugerir que basta volver a presentar el mismo plástico.
- Inventario de destinatarios/públicas y vigencia leída del certificado; alerta de sustitución antes de intentar PIN. Registrar riesgos sin claves ni datos personales.
- Política de recuperación en módulo independiente, separación de identidad/autorización y descifrado; elegir formato versionado, no reinterpretar DNIEVLT1 silenciosamente.
- Migrar contenedores existentes mientras la privada anterior siga disponible. Un contenedor antiguo sin vía alternativa no adquiere recuperación después de perder la clave.
- Persistencia transaccional y verificación del paquete de transición antes de marcar «preparado»; no borrar origen ni secretos de recuperación antes de comprobar destino.
- Passkeys antiguas BE=0 mantienen su política: no convertirlas retroactivamente en exportables. Conservar sus claves/credentialId al cambiar protección, y registrar de nuevo en el sitio cuando sea necesario según su elegibilidad.
- Pruebas sintéticas: misma identidad/nueva pareja RSA; mismo certificado renovado con misma pública; clave vieja ausente; respaldo antiguo inaccesible; recuperación preparada; secreto incorrecto; corte antes/después del reenlace; invariantes BE/BS.

P0: diseñar y preparar continuidad antes de recomendar renovar o perder el DNI. La firma PDF/CMS existente no se modifica: verificar firmas antiguas usa pública/evidencias; no exige conservar la privada para validarlas.
