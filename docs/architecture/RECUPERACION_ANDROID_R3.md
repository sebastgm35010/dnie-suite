# Recuperación de bóveda en Android R3

2026-10-07. Checkpoint previo `checkpoint/pre-vault-recovery-android-r3`, sobre `190e36d`. APK65 / `2.0.0-rc17-vault-recovery` instalado por ADB en el teléfono del usuario, preservando datos. Prueba física de recuperación pendiente.

## Capas

Motor binario/KDF: `dnie-vault-recovery`, mismo formato R1 probado en Windows. Almacenamiento/SAF: `AndroidVaultRecoveryAdapter`, coordinado bajo el mutex IO de `AndroidVaultRepository`. NFC y cancelación: `VaultViewModel` coordina autorización ya existente y entrega secreto al adaptador; Compose usa `VaultRecoveryControls` y callbacks explícitos en NavGraph. No clases UI usadas como servicio, ninguna dependencia del motor hacia Android.

## Uso

1. Bóveda → recuperación: selecciona un archivo de la lista filtrada. Para archivos de otro equipo, importa antes el `.dnievault`.
2. Para preparar: introduce CAN/PIN arriba, pulsa «Preparar paquete con DNI y PIN», elige destino `.dnierecovery`, escribe y confirma contraseña de al menos 16 caracteres. Acerca el DNI original cuando la app lo indique.
3. La app comprueba destinatario antes del PIN, recupera secreto con CIF hard una vez, cierra NFC, autentica todo el archivo y cifra/verifica el paquete antes de publicarlo. Un paquete cubre exactamente ese archivo. Guarda también una copia cifrada exportada y mantén el paquete separado.
4. Para comprobar o recuperar: selecciona el archivo, pulsa «Recuperar con paquete y contraseña», elige paquete y destino, introduce contraseña. No se usa DNI, PIN, CAN ni biometría en esta vía alternativa. Se valida paquete, UUID interno, destinatario, contenido completo y digest exacto antes de abrir el destino.

La recuperación entrega una copia descifrada y trata de aplicar nombre/extensión originales. No sustituye automáticamente la pública del contenedor ni migra passkeys. El reencriptado con un nuevo DNI se realiza con las funciones habituales después de comprobar la copia.

## Seguridad y límites

No se persiste contraseña, secreto ni paquete abierto en preferencias/estado restaurable. Campos de contraseña usan estado Compose no guardado; no se registran. CharArray se retira al cancelar espera NFC y al finalizar/cancelar coroutine, incluyendo cancelación antes de iniciarse. JVM y campos String no ofrecen borrado garantizado de todas las copias internas.

Contexto KDF de preparación Android: huella pública histórica del destinatario, guardada en el paquete; no es identidad civil verificada ni se calcula con el certificado renovado. Contraseña y paquete habilitan recuperación independiente del chip; copias antiguas siguen útiles aunque se cree otro paquete con distinta contraseña.

KDF/lectura/validación se ejecutan en IO fuera del hilo UI. Argon2id conserva 64 MiB/3 pasadas/4 lanes: RAM/latencia física aún sin medir. Cancelación cooperativa entre etapas; una derivación en curso termina antes de observar cancelación. Staging privado comparte la limpieza al inicializar VaultFileStore. Restauración SAF fallida puede dejar destino vacío o parcialmente escrito; los originales cifrados se conservan y no se reintenta automáticamente. No se promete publicación atómica de proveedores SAF ni verificación de persistencia remota después de flush.

Prueba instrumentada añadida: `VaultRecoveryInstrumentedTest` usa directorios aislados y RSA sintética, prepara/exporta/importa/recupera y verifica que contraseña incorrecta conserve destino previo. Ejecutada en el teléfono del usuario: OK (1 test), 4.708 s para el escenario completo. Recuperación exacta con identificadores locales distintos y destino intacto ante contraseña incorrecta. Usa URIs file internas, no el selector/proveedor SAF externo. No hay fallback sintético en la app.

Regresión JVM: 76 pruebas app, 23 recuperación y 26 bóveda, sin fallos/errores/omisiones (Gradle puede reutilizar resultados de módulos intactos). APK debug y APK de pruebas compilados. APK principal y auxiliar instalados tras reintentar con aceptación del usuario. Instrumentación real en Android superada, 1 prueba. Flujo DNI/SAF real y cruces Windows↔Android pendientes. Suite Windows sin cambios.

Artefacto: `D:/DNIeToolchain/AndroidPackages/dnie-evolution-2.0.0-rc17-vault-recovery-debug.apk`.
