# Claves estables de la app, DNI y recuperación: referencias de comunidad

Investigación 2026-10-07. Propuesta para decidir, no implementación ni cambio de permisos. Complementa RECUPERACION_Y_RENOVACION_DNI.md.

## Casos encontrados

1. [age-plugin-yubikey, issue 75](https://github.com/str4d/age-plugin-yubikey/issues/75): un usuario solicita duplicar su clave en otra YubiKey para sobrevivir a pérdida o avería. La solicitud demuestra el mismo problema de continuidad; no demuestra una función implementada ni compatibilidad con DNIe.
2. [Recuperación oficial KeePassXC](https://github.com/keepassxreboot/keepassxc/blob/develop/utils/keepassxc-cr-recovery/README.md): su herramienta necesita la base y el secreto challenge-response conservado desde configuración. No obtiene dicho secreto del dispositivo perdido. Enseñanza: el respaldo de datos no sustituye respaldo del material de acceso.
3. [Yubico PivSession.GenerateKeyPair](https://docs.yubico.com/yesdk/yubikey-api/Yubico.YubiKey.Piv.PivSession.html): generar en un slot ocupado reemplaza la privada anterior y no permite recuperarla. Es evidencia para YubiKey, no verificación del comando de renovación de RENIEC.
4. [Formato age](https://age-encryption.org/v1): separa clave de archivo del mecanismo de acceso y permite envolverla para varios destinatarios. Referencia de arquitectura para recuperación, sin proponer sustituir Tink ni importar el plugin.
5. [Bitwarden, passkeys para descifrado](https://contributing.bitwarden.com/architecture/deep-dives/passkeys/implementations/relying-party/prf/): distingue autenticación WebAuthn de descifrado; usa PRF y protección de claves intermedias. La presencia de una firma/autenticación válida no entrega por sí sola un secreto de descifrado estable. El DNI actual no implementa WebAuthn PRF mediante nuestras operaciones RSA.
6. [Android Keystore](https://developer.android.com/privacy-and-security/keystore): protección y restricciones de uso de claves locales, con autenticación del usuario soportada por Android. No ofrece una política nativa que valide un certificado externo de DNI para autorizar una clave. Una condición impuesta solo por la app no equivale a dependencia criptográfica de la privada del DNI.

No se encontró en las fuentes revisadas una solución demostrada específica a renovación destructiva de DNIe Perú v3. Los patrones de diseño anteriores sí aplican al problema criptográfico general.

## Evaluación de la propuesta del usuario

| Modelo | Renovación de DNI | Protección / límite |
| --- | --- | --- |
| Datos → clave propia → privada propia cifrada solo con DNI | Sigue dependiendo de la privada antigua | Añade indirección; no resuelve recuperación por sí solo. |
| Clave propia accesible localmente; DNI comprueba autorización | Puede sobrevivir si conserva almacén local | DNI es una política de aplicación. Modificar/comprometer la app puede eludirla; no llamarlo «solo descifrable con DNI». |
| Clave propia estable con protección DNI y una vía de recuperación independiente | Puede reenlazarse a nueva pública con recuperación preparada | DNI protege criptográficamente el uso cotidiano. El secreto alternativo también permite acceso: modo recuperable opcional y explícito. |
| Clave propia solo no exportable en Keystore | No depende del certificado | Pérdida de teléfono/app/clave local continúa siendo un riesgo; requiere respaldo independiente si se desea portabilidad. |

Una firma del DNI sobre la pública de la app puede documentar su vinculación al titular. No permite reconstruir la privada de la app ni descifrar después de perder los secretos. No derivar claves de signatures/challenges como sustituto improvisado de PRF.

## Recomendación propuesta para passkeys

- Conservar ES256 por sitio, credentialId, contadores y flags BE/BS existentes. Las claves WebAuthn ya son propias de la app; hoy su almacenamiento depende de la clave RSA del DNI.
- Considerar un modo independiente del DNI para protección cotidiana del catálogo mediante Keystore/autenticación local. La biometría de passkeys sigue siendo opcional. Definir la alternativa local para quien no la active.
- DNI opcional para acreditar identidad o autorizar operaciones sensibles, si se elige esa política. Su firma no convierte una passkey ordinaria en credencial de identidad aceptada por cualquier sitio ni garantiza que la comprobación sea una barrera criptográfica del hardware.
- Recuperación cifrada y portable para credenciales elegibles con secreto independiente. Mantener BE=0 en las antiguas, sin exportarlas retroactivamente. El modo nuevo necesita migración del catálogo mientras sea accesible.
- No implementar sincronización en nube implícita ni usar una copia biométrica local como único respaldo.

## Recomendación propuesta para bóveda

Par asimétrico propio por bóveda tiene sentido si se conserva «cifrar sin presentar DNI»: la pública estable permite envolver claves aleatorias de cada archivo y la privada estable se desbloquea al descifrar. No es necesario cifrar los archivos completos con RSA. Si no se requiere ese flujo, una clave maestra simétrica con claves por archivo puede reducir complejidad.

Diseño candidato: archivo cifrado con clave aleatoria → clave de archivo protegida por pública de bóveda → privada de bóveda cifrada con una clave de envoltura → envolturas alternativas de esta última para DNI y secreto de recuperación. Algoritmos/formatos y autenticación de metadatos se decidirán y probarán antes de implementar; no se define aquí un nuevo protocolo criptográfico completo.

Renovar DNI cambiaría la protección del acceso a la privada estable, sin tener que volver a cifrar todos los archivos, siempre que una vía anterior o de recuperación permita abrirla. Recuperación de identidad por nombre/número de documento no reemplaza esa vía.

No añadir biometría a la bóveda. No almacenar la privada propia en claro ni mezclar claves maestras entre bóveda y passkeys. Una privada común de bóveda aumenta el alcance de compromiso; rotar por compromiso no es lo mismo que reenlazar un DNI y puede requerir actualizar claves de archivos.

La recuperación debe prepararse antes de perder la antigua privada. Los DNIEVLT1 existentes no adquieren una segunda vía simplemente por instalar la versión nueva. Mantener respaldo fuente y migración transaccional verificable.

## Decisiones aún abiertas

Modo independiente de passkeys, alternativa local sin biometría, modalidad opcional de recuperación de bóveda, secreto/segundo dispositivo de recuperación y formato versionado. Investigación no equivale a autorización para quitar el requisito actual de DNI. No se ha alterado código de cifrado, passkeys, firmas o autenticación ni incorporado dependencias/código de los proyectos investigados.

Revisión posterior: [riesgos y soluciones de proyectos comparables](RIESGOS_RECUPERACION_PASSWORD_Y_DNI.md). Precisión sobre age: su destinatario de contraseña no se mezcla con otros destinatarios en el mismo archivo. Se evalúa paquete de recuperación separado para preservar la ruta portable CIF hard sin distribuir una vía débil en cada copia.
