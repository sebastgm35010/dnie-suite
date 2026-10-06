# Passkeys: desafío largo · 1.6.3

El diagnóstico compartido tras probar Facebook mostró `request_shape` con desafío codificado de 1948 caracteres, seguido de `FIELD_challenge_LENGTH` y `CREATE_OPTIONS`. La solicitud ofrecía ES256 (-7), autenticador platform, residentKey y userVerification required, attestation none y extensión opcional prf. El rechazo ocurría en el parser local, antes de validar al solicitante o abrir una sesión NFC. Este registro no permite confirmar que no exista otro rechazo posterior.

El parser limitaba el desafío a 256 caracteres y a 128 bytes decodificados. Se sustituye ese límite en registro e inicio de sesión por una política compartida que permite hasta 8192 bytes decodificados, manteniendo el mínimo local de 16 bytes, base64url canónico y el límite general del documento JSON. Los 8192 bytes son una cota local de recursos, no un máximo impuesto por WebAuthn. El desafío no se recorta, sustituye ni transforma antes de construir clientDataJSON o firmar su hash.

La especificación recomienda desafíos de al menos 16 bytes y exige que el valor retornado corresponda al emitido por el RP: [W3C WebAuthn, desafíos criptográficos](https://www.w3.org/TR/webauthn-3/#sctn-cryptographic-challenges).

Se añaden cinco pruebas con datos exclusivamente sintéticos: longitud codificada de 1948 en create/get; máximo permitido y exceso; mínimo; formato/codificación canónica; conservación en clientDataJSON y verificación independiente de la firma ECDSA. No se incorporan cuentas, identificadores, desafíos ni el historial personal del usuario al repositorio.

Se conservan las comprobaciones de identidad/DAL, ámbitos RP, exclusiones, extensiones obligatorias, PIN y canal seguro. El diagnóstico persistente continúa disponible para la siguiente prueba real del sitio.

Validación: 96 pruebas aprobadas en los módulos afectados (passkeys JVM 31, adaptador Android 11, app 54), más 12 comprobaciones WebAuthn independientes. Compilación y verificación de firma del APK completadas; instalada por ADB la versión 27 / `1.6.3-passkeys-long-challenge`. La aceptación final del registro por Facebook requiere repetir el flujo real.

SHA-256 del APK: `A3958D8809CF87728AD750AAA091EBE9BF04008BB10EE00F8AFE5D0E2626FFCE`.
