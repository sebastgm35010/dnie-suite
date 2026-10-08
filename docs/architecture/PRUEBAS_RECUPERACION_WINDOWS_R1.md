# Recuperación portable: prototipo JVM probado en Windows

2026-10-07. Implementación autorizada por el usuario. No está integrada ni habilitada en Android o Suite. El DNI real no se modificó ni se usó en estas pruebas.

Checkpoint previo: `checkpoint/pre-vault-recovery-windows-r1`, fuente comprimida en `D:/DNIeToolchain/Checkpoints/pre-vault-recovery-windows-r1/source.zip`. Conserva el estado anterior a la implementación.

## Capas y formato

Nuevo módulo `dnie-vault-recovery`, dependiente del formato JVM `dnie-vault-core`, sin NFC, PC/SC, Compose, Keystore ni biometría. `VaultRecoveryService` autentica archivos completos, prepara entradas y recupera en staging privado antes de entregar contenido al adaptador. `RecoveryPackageCodec` serializa y autentica el paquete separado. `RecoveryKdf` deriva la clave. Los adaptadores futuros serán responsables de permisos de staging, publicación atómica y adquisición real del secreto con CIF hard.

El archivo DNIEVLT1 existente no cambia. El paquete experimental DNIERCP1 guarda UUID, huella del destinatario, SHA256 exacto del archivo cifrado y secreto de 32 bytes por archivo, dentro de AES-256-GCM. La cabecera completa es AAD. Incluye sal aleatoria de 16 bytes, nonce de 12 bytes y contexto histórico de identidad de 32 bytes. No guarda PIN/CAN, privada del DNI, nombre del titular ni contenido original. El prototipo recibe el contexto; no implementa todavía extracción o validación de identidad entre certificados.

Argon2id v1.3: 64 MiB, tres pasadas, cuatro lanes; perfil fijo y rechazado antes de KDF si la cabecera pide otros costes. Límite de 1000 entradas; longitud del paquete y cuerpo acotadas. Contraseña de 16–1024 caracteres, sin NUL y sin recorte/normalización silenciosa. El mínimo de longitud no garantiza entropía. El perfil y el vector de referencia corresponden a [RFC 9106](https://www.rfc-editor.org/rfc/rfc9106.html), apartados 5.3 y 7.4. La medición aislada de una derivación fue 211 ms en esta PC; no representa Android ni un benchmark estadístico.

## Resultado

Comando ejecutado: `gradlew.bat :dnie-vault-recovery:test :dnie-vault-core:test :dnie-passkeys-core:test :dnie-vault-recovery:jar --console=plain`.

| Módulo | Pruebas | Fallos / errores | Ejecución |
| --- | ---: | --- | --- |
| Recuperación nueva | 12 | 0 / 0 | Ejecutadas |
| Bóveda existente | 26 | 0 / 0 | Resultado previo reutilizado por Gradle; fuentes intactas |
| Passkeys | 47 | 0 / 0 | Ejecutadas |

Los escenarios nuevos verifican:

- Recuperación desde paquete guardado/reabierto con contraseña, sin usar la privada antigua durante recuperación; otra RSA no abre el archivo original. Contenido multisegmento y metadatos coinciden byte por byte.
- Dos archivos con destinatarios distintos, cobertura explícita y rechazo de entradas duplicadas/ausentes o intercambiadas.
- Rechazo de contraseña incorrecta, corrupción de cabecera/cuerpo, truncado, bytes sobrantes y costes KDF hostiles antes de derivar.
- Corrupción del último segmento, digest incorrecto y cancelación: ningún callback de publicación; staging retirado.
- Reencriptado del contenido recuperado para nueva RSA y rechazo del paquete anterior para el nuevo archivo. Es composición de motores, todavía no un caso de uso de migración integrado.
- Contraseña Unicode, sal y contexto afectan la clave; coincidencia exacta con el vector Argon2id de RFC 9106.
- Entradas poseen copias de buffers, no muestran secretos y quedan inutilizables tras cerrar.
- Cambio de contraseña genera paquete nuevo pero el paquete antiguo sigue abriendo con la contraseña antigua. Este límite está probado, no oculto por la interfaz.

## Pendientes y límites

No se añadió recuperación a APK64 ni a GUI H15; no hay nuevo APK por esta fase. No se cambió la firma PDF/CMS, el protocolo PACE ni los respaldos de passkeys. Este prototipo demuestra recuperación de archivos; no demuestra recuperación de passkeys ni renovación real en RENIEC.

Siguiente fase: adapter Android fuera de UI; medir RAM/latencia/cancelación del KDF y staging privado, pruebas con artefactos Windows→Android y Android→Windows, respaldo/versionado con cobertura visible, y solo después prueba CIF hard física. La Suite de firma aún no es adaptador de descifrado remoto. La ABI de Tink usada en pruebas JVM es `tink-android:1.23.0`, compatible con el núcleo actual; no se supone intercambiable con `tink` estándar.

Preparar un paquete para archivos previos exige que su clave antigua siga disponible. Copiar un certificado público antiguo no la reemplaza. Sin paquete preparado y contraseña, o sin otra privada capaz de abrir el archivo, no hay recuperación tras destruir la clave. Con paquete y contraseña, existe acceso independiente del DNI, solo por esta vía opcional autorizada. Mantener archivo y paquete separados.

La limpieza de buffers es de mejor esfuerzo en JVM; no garantiza borrado de todas las copias internas. El staging temporal se retira al salir normalmente, fallar o cancelar; falta la gestión de muerte del proceso y limpieza al reiniciar en los adaptadores. El callback final debe publicar de forma segura; esta fase no implementa publicación SAF ni exportación atómica en Windows.
