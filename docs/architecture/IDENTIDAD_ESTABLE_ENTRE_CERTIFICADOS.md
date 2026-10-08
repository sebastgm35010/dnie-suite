# Identidad estable entre certificados frente a clave de descifrado

Investigación: 2026-10-07. Diseño propuesto, no altera contenedores ni autorizaciones existentes.

## Identificador encontrado

RENIEC documenta en sus perfiles el atributo Subject.serialNumber (OID 2.5.4.5) con formato PNOPE-<número de DNI>. Fuente: [DPC ECEP-RENIEC V5](https://pki.reniec.gob.pe/docs/docsrepo/CPS_ECEP-RENIEC-V5.0_ROOT3ROOT5.pdf) y [perfil Class 2 CIF de la política ECERNEP](https://pki.reniec.gob.pe/docs/docsrepo/CP-ECERNEP-PERU-CA-ROOT3_v2.0.pdf). Hay perfiles donde es opcional: no asumir presencia universal ni sustituirlo silenciosamente por un nombre CN.

Inspección local de dos certificados RENIEC instalados en CurrentUser/My: ambos incluyen atributo de identificador del titular con formato PNOPE de ocho dígitos. Solo se consultó metadato público; no se exportaron claves ni se copiaron datos personales al registro. No disponemos de un certificado posterior a una renovación real para acreditar continuidad con este DNI.

El candidato a continuidad es el identificador de persona que RENIEC certifica, no una referencia de la CRL. No confundir Subject.serialNumber con el serialNumber X.509 de la emisión individual.

## Uso como clave de registro

Propuesta ilustrativa, no especificación implementada:

```
identity_id = SHA256("dnie:identity:v1|PE|RENIEC|PNOPE|" + DNI_normalizado)
```

Preservar ceros iniciales, tipo/país y autoridad lógica de identidad. Extraer atributos ASN.1, rechazar duplicados/conflictos o formatos no admitidos. No hacer parseo informal de CN. La continuidad depende de que el identificador certificado permanezca igual; no prometer invariabilidad absoluta frente a correcciones registrales ni cualquier perfil futuro.

Nombres pueden cambiar, repetirse o normalizarse de otra manera. CN incluye función AUT/FIR/CIF. Serie de emisión, fechas, pública/SPKI y huella del certificado cambian. CA intermedia/ancla/política pueden renovarse: verificar su autorización, no incorporar sus huellas exactas a un identity_id que se desea estable.

CRL/OCSP informan estado de certificados, no enlazan automáticamente una emisión nueva con otra antigua ni contienen la privada anterior. Datos de CRL son públicos y variables. Referencia: [RFC 5280](https://www.rfc-editor.org/rfc/rfc5280.html).

Para autorizar con este identificador: validar cadena y política de emisor admitido, vigencia/uso y revocación aplicables; exigir prueba de posesión mediante reto fresco y dominio de operación con la clave adecuada. Comparar IDs solo permite enlazar registros, no autorizar presentando un certificado público copiado. No usar el certificado CIF como firmante fuera de los usos admitidos; utilizar la clave de autenticación correspondiente según la operación.

Un hash sin secreto de un DNI no anonimiza de manera fuerte: su espacio es enumerable. Evitar exponerlo como identificador universal; considerar IDs por contexto y, en un servicio, HMAC bajo secreto del servicio si se requiere impedir enumeración. Eso introduce estado/secreto del servicio y no es descifrado offline portable.

## Por qué no recupera la bóveda

Todos los ingredientes anteriores son públicos o predecibles. Un hash largo no añade entropía secreta. Si se usa como AES key, quien conozca el DNI y el formato puede calcularla sin conectar una tarjeta. Verificar CRL antes de permitir usarla es una condición de aplicación, no una dependencia criptográfica de CIF hard: un tercero puede omitir esa comprobación.

La nueva privada prueba posesión/identidad pero no reconstruye la antigua. Firmas públicas de CA sobre certificados tampoco son secretos. Añadir una clave propia protegida solo por el DNI antiguo vuelve a dejar la misma dependencia.

El identity_id sí puede seleccionar un secreto aleatorio en un servicio o registro protegido. La liberación tras autenticar con certificado nuevo sería recuperación mediante ese servicio/almacén; no derivación del secreto a partir de identidad y ya no sería un flujo estrictamente offline sin estado previo.

Para conservar el acceso portable CIF hard: mantener sobres criptográficos para la clave física y una vía independiente de recuperación preparada antes de sustituirla, si el usuario opta por ella. Sin vía adicional o clave antigua disponible, identity_id no permite abrir contenedores antiguos.

## Actualización: contraseña de recuperación solicitada por el usuario

El usuario pide incorporar una contraseña de recuperación al cálculo. Se admite como secreto independiente en el diseño; aún no hay implementación, pantalla ni formato nuevo desplegados.

Mantener dos valores distintos: identity_id público para asociar al titular y K_recovery secreto para proteger la recuperación. No publicar K_recovery como key/value de identidad ni guardarlo en SQL, logs o metadatos.

Composición candidata, expresada mediante las entradas de Argon2id:

```
identity_id = SHA256(identidad_canonica_con_dominio)
K_recovery = Argon2id(
    P = UTF8(contraseña_de_recuperación),
    S = sal_aleatoria_del_paquete,
    X = codificación_versionada(dominio_de_recuperación, identity_id),
    salida = 32 bytes,
    parámetros = perfil_de_memoria_tiempo_paralelismo_versionado
)
```

Referencia: [RFC 9106](https://www.rfc-editor.org/rfc/rfc9106.html). Argon2id permite datos asociados X y sal S; usar sal aleatoria de al menos 16 bytes. Definir parámetros concretos tras medir Android/Windows, persistirlos y limitar valores admitidos antes de reservar memoria. Identidad es contexto público, no una fuente de entropía secreta. No utilizar SHA256(nombre + contraseña) como protección de contraseñas.

La sal y los parámetros viajan en el contenedor/paquete portable. Mantenerlos iguales permite obtener la misma clave con la misma contraseña e identidad después de renovar certificados, aunque sus claves RSA sean distintas. Sales diferentes producen claves distintas: no es una clave global invariable del ciudadano. Una contraseña nueva requiere actualizar el sobre de recuperación, no cambiar identity_id.

K_recovery protegerá mediante AEAD una copia del secreto aleatorio necesario para abrir el contenedor; no reemplazará el cifrado aleatorio del contenido. Mantener otro sobre para CIF hard como acceso habitual. Verificar autenticidad antes de publicar contenido; definir y autenticar versión, identidad contextual y parámetros. No registrar la contraseña ni un hash rápido adicional para comprobarla; validar el sobre autenticado. No recortar ni transformar silenciosamente la contraseña.

Quien posea archivo, identidad requerida y contraseña puede recuperar sin la privada anterior del DNI. Comprobar después un DNI nuevo vincula el reenlace al titular, pero esa comprobación por sí sola no añade una barrera criptográfica hardware a la vía de contraseña. La robustez frente a intentos offline depende de contraseña fuerte y KDF, no del PIN/CAN ni de CRL.

El modo de recuperación debe habilitarse/prepararse mientras la clave antigua abre los datos. No rescata retroactivamente un archivo cuya única privada ya fue eliminada. Mantener explícita esta segunda vía y la bóveda sin biometría.
