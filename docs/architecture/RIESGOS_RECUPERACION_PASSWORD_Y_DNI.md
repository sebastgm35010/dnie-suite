# Riesgos del acceso CIF hard con recuperación por contraseña

Investigación: 2026-10-07. Propuesta arquitectónica, no implementación. Amplía IDENTIDAD_ESTABLE_ENTRE_CERTIFICADOS.md; no cambia la app ni archivos existentes.

## Lecciones de proyectos existentes

**Cryptomator:** cifra claves maestras con una clave derivada de contraseña. Cambiar contraseña vuelve a proteger dichas claves, sin recifrar todos los datos. Su documentación advierte que una copia antigua de la clave maestra protegida puede mantener útil una contraseña anterior. Su recovery key es material de acceso altamente sensible, independiente de la contraseña actual. [Contraseña y recuperación](https://docs.cryptomator.org/desktop/password-and-recovery-key/), [arquitectura](https://docs.cryptomator.org/security/architecture/).

**Bitwarden:** distingue exportación ligada a la cuenta y exportación protegida por contraseña. Rotar la clave de cuenta puede volver inaccesible la primera; recomienda preparar un respaldo independiente antes. Advierte también de corrupción por clientes que escriben con claves antiguas. [Exportaciones](https://bitwarden.com/help/encrypted-export/), [rotación](https://bitwarden.com/help/account-encryption-key/).

**age:** dispone de claves aleatorias por archivo y sobres por destinatario. Hay una restricción relevante que la investigación previa no detalló: su destinatario scrypt de contraseña debe ser el único del archivo; no se mezcla con destinatarios de clave pública. No afirmar que age v1 implementa directamente DNI OR contraseña. Su formato requiere asimismo validación y límites del coste KDF al leer archivos ajenos. [Especificación](https://age-encryption.org/v1).

**Argon2id:** RFC 9106 proporciona parámetros para entornos de memoria limitada; el perfil de 64 MiB/3 pasadas/4 lanes es referencia de medición, no una configuración del APK ya validada. [RFC 9106](https://www.rfc-editor.org/rfc/rfc9106.html).

No hay prueba en estas fuentes de un flujo específico RENIEC v3 con renovación destructiva resuelto automáticamente. Las conclusiones siguientes son aplicación de esos patrones a nuestro sistema.

## Riesgos y controles propuestos

| Conflicto | Consecuencia | Control propuesto |
| --- | --- | --- |
| DNI OR contraseña | La ruta de contraseña no necesita la privada física: no es doble factor. Un secreto débil reduce la protección global de los datos cubiertos. | Modo recuperable explícito, secreto largo preferiblemente generado, no PIN/CAN. Mantener opción exclusiva DNI. |
| Ataque offline | Una copia del archivo/paquete permite probar contraseñas sin la app, CRL ni contador de intentos. | KDF de memoria y tiempo calibrados, sal aleatoria, sin hash rápido/verificador alternativo. La sal o el DNI no añaden secreto. No prometer bloqueo efectivo offline. |
| Recuperación dentro de todos los archivos | Cada copia distribuida incorpora una vía adicional; contraseñas viejas permanecen útiles en copias anteriores. | Evaluar paquete separado protegido por contraseña, conservando acceso habitual de cada archivo solo por DNI. Si se permite recuperación embebida, identificar expresamente ese modo. |
| Paquete separado perdido o desactualizado | La contraseña sola no basta: faltan secretos, sal o entradas de archivos añadidos después. | Manifest de UUID y versión/destinatario; respaldo redundante y exportación verificada; indicar cobertura y fecha. No marcar un archivo recuperable hasta comprobar su entrada. |
| Cambio de contraseña frente a compromiso | Reenvolver el mismo secreto no revoca un sobre viejo en manos de terceros. | Separar «cambiar contraseña» de «rotar secretos por compromiso». Rotar puede exigir recifrar contenido y mantener copias originales inaccesibles al atacante es imposible retrospectivamente. |
| Renovación de DNI ya ejecutada | El sobre RSA antiguo no abre; no se puede crear recuperación desde una privada ausente. | Preparar el paquete con DNI antiguo operativo antes. Tras renovar, usar paquete para reenlazar. Si no existe clave antigua/segunda vía, declarar no recuperable. |
| Identidad cambiante o corrección registral | Recalcular contexto KDF desde un certificado diferente puede cambiar la clave aunque la contraseña sea correcta. | Guardar identidad contextual y versión de normalización originales; no depender del certificado actual para reconstruirlas. Comprobar identidad para reenlace en una etapa distinta. |
| Unicode, teclado y espacios | Una contraseña visualmente parecida puede tener bytes distintos en Android/Windows. | Definir UTF-8 y política explícita, no trim silencioso; doble entrada y prueba de recuperación desde otro entorno. Preferir frase generada con representación portable. |
| Cabecera maliciosa/KDF desmesurado | Un archivo puede solicitar memoria/tiempo excesivos antes de autenticarse. | Límites de tamaño, versiones/perfiles permitidos y límites de memoria, pases y lanes antes de derivar. Nunca ejecutar valores arbitrarios de la cabecera. |
| Manipulación o mezcla de sobres | Sustituir UUID/identidad/sal/versión puede causar corrupción, downgrade o asociar un secreto a otro archivo. | AEAD con contexto versionado autenticado, identificadores únicos y verificación de relación con el contenedor. No publicar plaintext antes de autenticación completa. |
| Corte o escrituras concurrentes | Un cambio parcial puede dejar archivo y sobre de recuperación incoherentes. | Staging, revisión esperada, exclusión de escritores, commit atómico y comprobación del resultado antes de borrar origen. |
| Rollback de copias | Una cabecera antigua auténtica sigue siendo auténtica; un equipo nuevo offline no sabe si existe una posterior. | Generaciones/estado local y avisos permiten detectar algunos casos; no prometer antirollback global sin referencia externa confiable. |
| Lentitud Android | KDF fuerte ejecutada por archivo o en UI produce pausas/OOM. | Trabajo fuera del hilo UI, concurrencia limitada, medir memoria/tiempo y usar derivación por apertura de paquete; no cachear secretos persistentemente para acelerar. |
| Passkeys | Restaurar un catálogo antiguo puede perder credenciales/estado; BE=0 no se vuelve exportable porque se agregue contraseña. | Respaldar solo elegibles, conservar credentialId/claves/BE/BS; restauración explícita y pruebas de compatibilidad con sitios. No compartir raíz con la bóveda. |

## Recomendación para este proyecto

Conservar CIF hard y el contenedor portable como función principal. La identidad pública puede ayudar a seleccionar/autorizar el reenlace, pero no es un factor secreto.

Antes de optar por dos sobres dentro de cada archivo, evaluar como primera alternativa un **paquete de recuperación separado**, cifrado por contraseña, que contenga los secretos mínimos de los archivos seleccionados, no sus contenidos en claro. El paquete se prepara con la clave antigua y se guarda aparte. Abrir normalmente sigue requiriendo solo archivo, app compatible y DNI, sin almacenes locales previos. Recuperar requiere archivo, paquete y contraseña.

La alternativa embebida conserva la ventaja de un solo archivo recuperable, a cambio de distribuir la vía de contraseña en cada copia. No es intrínsecamente inválida, pero requiere un formato propio revisado y un modo claramente anunciado; la regla de age no prohíbe todos los esquemas OR, solo impide tratarla como implementación compatible de age v1.

No introducir todavía privada maestra de app como requisito para abrir cada archivo; no es necesaria para el objetivo portable CIF hard. No biometría para bóveda. No cambiar formato DNIEVLT1 silenciosamente. Establecer perfiles y migración opcional de archivos antiguos mientras sus claves físicas estén disponibles.

## Pruebas necesarias antes de implementar interfaz

1. Vectores KDF y cifrado interoperables Android/JVM; contraseña incorrecta, caracteres internacionales, normalización/identidad histórica.
2. Misma identidad con RSA nuevo: archivo viejo falla con DNI nuevo; paquete válido abre y permite reenlazar; clave vieja eliminada sin paquete falla de forma explícita.
3. Manipulación de cabecera/paquete, entrada repetida, UUID incorrecto, parámetros desmesurados, paquete truncado e importación cancelada sin publicaciones parciales.
4. Cambio de contraseña y prueba que demuestre que copia antigua permanece accesible con contraseña antigua; distinguir rotación completa.
5. Pérdida de proceso/SAF, carreras con otro escritor, rollback y cobertura parcial de inventario.
6. Rendimiento por apertura/lote fuera de UI, límites de memoria y uso de passkeys con elegibilidad antigua preservada.

Sigue pendiente elegir embebido o paquete separado y parámetros medidos. Se documentan riesgos y controles; no se afirma recuperación implementada ni auditada externamente.
