# Plan de bóveda personal y passkeys con DNIe por NFC

Fecha: 2026-10-04. Estado: propuesta, sin implementación criptográfica nueva.
Actualización posterior: el despliegue de laboratorio de la etapa 0 está descrito en [IMPLEMENTACION_BOVEDA_INICIAL.md](IMPLEMENTACION_BOVEDA_INICIAL.md). Su validación física sigue pendiente; las restantes etapas continúan como propuesta.
Actualización tras prueba del usuario: el log recibido confirma PSO DECIPHER extendido sobre PACE/NFC con SW 9000 y el usuario informa resultado positivo del ciclo sintético. Véase [VALIDACION_BOVEDA_Y_FLUIDEZ.md](VALIDACION_BOVEDA_Y_FLUIDEZ.md). El almacenamiento de producción y la evaluación del perfil RSA siguen pendientes.
Investigación: revisión de la repo y fuentes primarias, con subagente Luna solicitado por el usuario.
«nc» confirmado como NFC; sincronización Nextcloud fuera de este alcance.

## 1. Resultado propuesto

Una bóveda local permite guardar archivos cifrados sin acercar el DNI, después de vincular su certificado público de cifrado. Abrir, revelar o exportar requiere DNI + CAN para PACE + PIN para la operación privada. Posteriormente, la misma infraestructura protege passkeys independientes por sitio.

Son capacidades separadas: cifrado de archivos, acceso a la clave privada del DNI, almacén de credenciales y proveedor de passkeys del sistema. Que una falle no debe impedir firma PDF, lotes o lectura ICAO.

Primero se debe demostrar un ciclo de cifrado/descifrado de un secreto aleatorio por NFC. Hasta aprobarlo, no ofrecer almacenamiento de datos reales que pudiera resultar irrecuperable.

## 2. Evidencia y primera barrera técnica

El [manual oficial v3 de RENIEC](https://identidad.reniec.gob.pe/documents/d/guest/manual_comandos_dnie_v3), documento 2025_0014, sección 3, documenta descifrado con la privada 83, PIN referencia 03 y RSA PKCS#1 v1.5. Su ejemplo MSE es `002241B8098401838004FF300100`; PSO usa `002A8086`, datos `81 || ciphertext` y longitud extendida `000101` para 257 bytes. No describe cómo ejecutar esta operación con PACE/NFC ni acredita OAEP.

La app ya lee CE83, pero su interfaz solo expone lectura, PIN y firma. `CommandApdu.kt` rechaza datos mayores de 255 bytes; `PaceSecureChannel.kt` también limita la carga segura a 255. No basta añadir PSO:DECIPHER: hay que soportar APDU extendida en representación, parseo, envoltura y transporte. Consultar límite real de IsoDep y compatibilidad de la tarjeta. GET RESPONSE recoge respuestas; no sustituye el transporte de un comando largo. Encadenar comandos sería otra alternativa que requiere evidencia de soporte de la tarjeta y pruebas propias.

Prueba aislada propuesta:

1. Leer CE83 y revisar algoritmo, tamaño RSA, keyUsage y huella SHA-256 del SPKI. Vincular el destinatario mostrando identidad y huella; no seleccionarlo solo por nombre o número de DNI.
2. Mantener la ruta IAS → PKI → PACE → PKI protegido y verificar el PIN introducido por el usuario. Sin tanteo de PIN ni reintentos automáticos de credenciales rechazadas.
3. Generar un secreto aleatorio de 32 bytes y cifrarlo con la pública correspondiente usando el perfil documentado.
4. Ejecutar MSE/PSO en sesión experimental, recuperar el secreto y comparar exactamente en memoria. No registrar secretos, CAN, PIN, bloques descifrados o imágenes.
5. Repetir con desconexión, cancelación y sesión nueva. Ejecutar regresión de firma y canales seguros antes de habilitar la capacidad.

RSA v1.5 tiene riesgos de oráculo de padding: evaluar su idoneidad y superficie de ataque antes de aprobar el formato de producción. No exponer un servicio genérico de descifrado a otras apps ni remotamente; exigir autorización local por operación y tratar fallos sin revelar su causa criptográfica. Esto reduce exposición, pero no reemplaza la revisión del protocolo. No afirmar soporte OAEP ni hacer downgrade silencioso. Si aparece soporte documentado y verificado para OAEP, preferirlo y fijar hash/MGF1 explícitamente. [Especificación RSA](https://www.rfc-editor.org/info/rfc8017/).

No derivar claves de firmas, CAN, PIN, DNI, MRZ o imágenes. No reutilizar la privada de firma como mecanismo de descifrado.

## 3. Diseño de cifrado

Cifrado híbrido: el teléfono cifra el archivo con cifrado simétrico autenticado; la pública del DNI protege únicamente un secreto pequeño que permite recuperar esas claves. El chip realiza una operación privada por envoltura, no procesa el archivo entero.

Para archivos grandes, evaluar [Tink Streaming AEAD](https://developers.google.com/tink/streaming-aead) y seleccionar una plantilla compatible con las plataformas objetivo. No construir un esquema propio de segmentos, nonces o padding.

Cada objeto tendrá un secreto de envoltura aleatorio de 32 bytes. RSA protege ese secreto; una AEAD estándar protege con él el keyset de Streaming AEAD y los metadatos privados, con separación de claves/usos definida mediante primitivas de biblioteca. El keyset serializado puede exceder la capacidad RSA: no cifrarlo directamente con RSA. Por ejemplo, RSA-2048/OAEP-SHA256 solo admite 190 bytes; esto es una consideración de formato, no una afirmación de soporte del DNI.

Contenedor versionado, extensión provisional `.dnievault`:

- Cabecera pública mínima: versión, suite, identificador aleatorio, destinatario por huella SPKI, longitudes acotadas y secreto envuelto.
- Metadatos cifrados: nombre, MIME, notas y propiedades privadas.
- Contenido cifrado mediante Streaming AEAD.
- Contexto inmutable del contenido como AAD (versión, suite de contenido e identificador); destinatario y envoltura autenticados en la AEAD del keyset, con su propio contexto. Codificación canónica y límites de parseo antes de asignar memoria. La pertenencia al catálogo necesita autenticación independiente cuando se incorpore.

Validar vectores comunes antes de declarar el formato compatible con Windows. No ocultar que tamaños y cantidad de archivos siguen siendo observables.

### Importar con la bóveda cerrada

No usar una sola clave maestra inaccesible para toda importación: impediría cifrar sin DNI. Los archivos nuevos se sellan individualmente con la pública almacenada y entran en una bandeja de entrada. Sus nombres permanecen cifrados. Un catálogo cifrado permite buscar y ordenar después de desbloquear e integrar esos objetos.

Cualquier persona con la pública puede crear un archivo cifrado para su titular: integridad AEAD no prueba quién lo creó. Tratar importaciones externas como entradas no confiables, aplicar límites y no incorporarlas automáticamente como documentos autenticados.

### Política de acceso

Modo inicial propuesto: DNI + PIN por apertura/exportación sensible y por operación de passkey, sin una copia persistente de claves que permita acceso mediante biometría solamente. La pública se guarda para permitir nuevas importaciones.

La privada del DNI no sale del chip; las claves simétricas y las passkeys sí se procesan en RAM del dispositivo mientras se usan. La protección no cubre un teléfono comprometido durante el desbloqueo. Cancelación, segundo plano y cierre liberan referencias y limpian buffers donde sea posible; el runtime JVM no permite prometer borrado perfecto.

NFC acredita presencia durante una operación; no garantiza presencia continua del DNI después. Un desbloqueo por sesión sería una política distinta y visible, no la opción predeterminada estricta.

## 4. Pérdida, renovación, copias y exportación

La política inicial es exclusivamente DNI. Perder, bloquear o destruir la tarjeta puede hacer irrecuperable la bóveda, incluso conservando todos los archivos cifrados. Un DNI nuevo puede tener otra clave y no abrir los objetos anteriores.

Una recuperación opcional necesita una segunda envoltura con otra clave de recuperación guardada fuera del teléfono. Es una ruta adicional de descifrado; no se debe presentar como «solo DNI». Debe decidirse expresamente antes de utilizar datos importantes; no añadir contraseña de emergencia o acceso biométrico oculto.

Migración: con la tarjeta antigua disponible, recuperar cada secreto y envolverlo para la pública nueva. Actualizar la envoltura y volver a proteger el keyset con su nuevo contexto; el AAD inmutable del contenido debe conservarse para evitar recifrar archivos grandes. Autenticar/versionar el manifiesto de migración según el formato definitivo y verificar restauración antes de retirar destinatarios antiguos. La expiración de un certificado no equivale a cambio de clave; identificar por SPKI y separar política de nuevas importaciones de lectura histórica.

Copias solo cifradas, escrituras atómicas y recuperación tras interrupción. AEAD detecta alteración del contenido, pero no evita por sí sola restaurar una copia antigua válida: la defensa contra rollback exige estado de confianza adicional.

Exportar descifrado requiere elección explícita de destino mediante SAF. La copia exportada y el original importado quedan fuera de la protección de la bóveda. No borrar automáticamente originales; evitar temporales públicos, miniaturas y logs con datos. No asumir que borrar un archivo elimina copias o backups.

## 5. Passkeys después de la bóveda

Actualización 2026-10-05: este diseño ya tiene implementación inicial en 1.6.0-preview, documentada en `IMPLEMENTACION_PASSKEYS.md`. El catálogo de passkeys es independiente del catálogo/backup de archivos, con descubrimiento bloqueado y claves solo durante una operación. El motor y el proveedor Android se separan en dos módulos; la validación física completa del proveedor sigue pendiente.

Cada RP/sitio recibe un par de claves propio; inicialmente P-256/ES256 donde lo acepte el RP. La privada se cifra como registro de la bóveda. El DNI desbloquea ese registro y la app firma la ceremonia WebAuthn con la clave de la passkey. La firma digital RENIEC no sustituye esa clave.

Para ofrecerlas a navegadores/apps se necesita un proveedor del sistema: [CredentialProviderService en Android 14+](https://developer.android.com/identity/sign-in/credential-provider), habilitado por el usuario. La API cliente CredentialManager no convierte por sí misma esta app en proveedor. La bóveda sigue siendo independiente de ese requisito de versión.

Contrato mínimo:

- Registro y autenticación con challenge fresco, credenciales/handles aleatorios y validación de algoritmos soportados.
- Validar RP ID, origen y app solicitante mediante las reglas oficiales, Digital Asset Links y autorizaciones de navegadores pertinentes. Rechazar solicitudes de otra identidad/origen.
- Generar y validar CBOR/COSE, authenticatorData y clientData conforme a [WebAuthn](https://www.w3.org/TR/webauthn-3/).
- Consentimiento y verificación real antes de marcar UP/UV; attestation `none` al inicio. Definir contadores y BE/BS conforme a comportamiento efectivo y exportabilidad, sin declarar capacidades de hardware inexistentes.
- Probar cancelación, llamadas repetidas, pantalla bloqueada, PIN erróneo y tiempos de NFC dentro de la ceremonia del sistema.
- Evitar exponer cuentas y RP IDs en índices en claro para descubrimiento; evaluar entrada de autenticación del proveedor antes de revelar selección de cuentas. Probar este flujo en hardware real.

Las passkeys son claves de software protegidas por la bóveda, no claves FIDO no exportables dentro del DNI. El DNI tampoco se transforma en autenticador CTAP2 por implementar este proveedor. No hace falta nuestro servidor para guardar la bóveda; los sitios siguen gestionando sus registros públicos WebAuthn.

## 6. Componentes investigados por Luna

Licencias del repositorio consultadas; antes de incorporar código fijar commit/versiones y revisar avisos por archivo y dependencias transitivas.

| Proyecto | Licencia principal | Uso propuesto / límite |
|---|---|---|
| [Tink Java](https://github.com/tink-crypto/tink-java) | Apache-2.0 | AEAD/Streaming AEAD. No implementa el protocolo privado del DNI. Preferir el repo Java actual frente al monorepo archivado. |
| [Android identity-samples, CredentialProvider/MyVault](https://github.com/android/identity-samples) | Apache-2.0 | Esqueleto de proveedor local; adaptar y probar, no asumir almacén seguro de producción. |
| [Kotpass](https://github.com/keemobile/kotpass) | MIT | Lectura/escritura KDBX en Kotlin si añadimos contraseñas/intercambio. No es bóveda de archivos ni integración DNI lista. |
| [SimpleWebAuthn](https://github.com/MasterKale/SimpleWebAuthn) | MIT | Sitio de prueba que registra/verifica ceremonias; no es proveedor ni almacén de privadas. |
| [fido2-net-lib](https://github.com/passwordless-lib/fido2-net-lib) | MIT | Alternativa .NET para RP de prueba, misma distinción. |
| [Microsoft Passkey Manager sample](https://learn.microsoft.com/en-us/samples/microsoft/windows-classic-samples/passkeymanager/) | MIT en raíz, excepciones por archivos | Referencia de proveedor Windows; no copiar su almacenamiento de demostración como diseño seguro. [Licencia](https://github.com/microsoft/Windows-classic-samples/blob/main/LICENSE). |
| [KeePassDX](https://github.com/Kunzisoft/KeePassDX), [KeePassXC](https://github.com/keepassxreboot/keepassxc) | GPL; revisar COPYING/archivos | Referencias de UX, KDBX y factores físicos. Integrar código exige cumplir sus licencias. Su challenge-response no prueba compatibilidad DNI. |
| [OpenSC](https://github.com/OpenSC/OpenSC) | LGPL-2.1-or-later | Referencia PC/SC/PKCS#11. Comprobar soporte concreto de tarjeta/interfaz. |
| [libfido2](https://github.com/Yubico/libfido2) | BSD-2-Clause | Cliente para autenticadores FIDO; NFC FIDO no es el protocolo PKI del DNI. |

[Keyguard](https://github.com/AChep/keyguard-app/blob/master/LICENSE) declara All Rights Reserved; [Strongbox](https://github.com/strongbox-password-safe/strongbox) publica código con restricciones. Excluirlos del conjunto de código reutilizable permisivo. No se encontró una solución completa MIT que ya resuelva Android + Windows + descifrado DNIe Perú por NFC.

KDBX queda como evaluación posterior para contraseñas: agregar envolturas DNI externas no garantiza que clientes KeePass normales puedan abrir el archivo sin adaptación.

## 7. Módulos y etapas con criterios de aceptación

Antes de implementar, registrar un checkpoint reproducible de la versión funcional del fork con sus cambios pendientes y APK. La base clausurada y Antigravity permanecen intactas.

| Etapa | Entrega | Criterio para avanzar |
|---|---|---|
| 0. Capacidad privada NFC | Adaptador aislado `CardKeyUnwrapper` en protocolo; APDU extendida y SM | Secreto sintético recuperado exactamente en tarjeta real; cancelación segura; regresiones de firma/SSC/MAC aprobadas. |
| 1. Núcleo de bóveda | `dnie-vault-core`, JVM: envelope, contenedor, streaming, parser y repositorio abstracto | Round-trip, tampering, truncación, sustitución de cabecera, límites de memoria y vectores portables. Revisión del perfil RSA antes de datos reales. |
| 2. Bóveda Android | Feature independiente: vincular pública, sellar archivos, bandeja, abrir/exportar, backup | Funciona sin DNI para sellar; sin privada disponible no abre; no persiste secretos; se recupera de NFC/cierre/escritura interrumpidos. |
| 3. Núcleo passkeys | `dnie-passkeys-core`, registros y ceremonias; sitio RP de prueba MIT | Registro y assertion verificados por servidor independiente; aislamiento de RP y origen; claves distintas por sitio. |
| 4. Proveedor Android | `dnie-passkeys-android`, API 34+, MyVault como referencia | Navegador y app de prueba en dispositivo real, selección del proveedor, cancelación y NFC sin regresiones. |
| 5. Windows | Cliente del formato, luego proveedor nativo | Vectores comunes; transporte privado disponible y verificado; API/plugin compatible con build concreto. |

La UI depende de servicios de bóveda/passkeys; estos dependen de contratos JVM. `dnie-protocol` implementa el unwrap y `dnie-nfc` aporta transporte. La bóveda no depende de UI, PDF o ICAO. Los módulos passkeys no modifican el flujo de firma de documentos.

Windows es una segunda etapa: actualmente el usuario solo tiene NFC del teléfono. Poder abrir un contenedor en Windows requiere acceso real a la operación privada, mediante lector compatible o un puente Android autenticado que se diseñaría por separado. Ese puente entregaría secretos al PC autorizado; debe explicitar esta confianza. La documentación distingue APIs WebAuthn cliente de la integración de gestores: [overview Windows](https://learn.microsoft.com/en-us/windows/security/identity-protection/hello-for-business/webauthn-apis), [modelo de plugin](https://learn.microsoft.com/en-us/windows/apps/develop/security/third-party). Verificar build/SDK y requisitos de Windows Hello antes de comprometer soporte.

## 8. Próximo trabajo concreto

Comenzar por etapa 0: secretos de prueba y APDU extendida segura, sin crear todavía una bóveda de producción. Si descifrado no funciona por NFC o el perfil no es aceptable, informar el resultado y replantear la dependencia del DNI; no sustituirla silenciosamente por otra clave local.

Este cambio añade documentación únicamente. No se modificó funcionalidad, no se compiló un nuevo APK y no se instaló ni controló el teléfono durante la planificación.
