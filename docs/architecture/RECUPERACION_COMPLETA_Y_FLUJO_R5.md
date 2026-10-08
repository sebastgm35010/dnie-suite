# Bóveda completa y navegación por tareas · R5

2026-10-08. Android67 / `2.0.0-rc19-whole-vault`. Base preservada: commit `7220e6b`, etiqueta `checkpoint/pre-vault-whole-recovery-r5`.

## Recorrido del usuario

Tres pestañas principales: **Mis archivos**, **Protección** y **Copias**. La pantalla de CAN/PIN es contextual para abrir archivos o vincular otro DNI; no constituye una pestaña principal de comprobaciones.

Antes de renovar: guardar respaldo ZIP desde Copias; en Protección elegir **Proteger toda mi bóveda**, introducir CAN/PIN del DNI original, elegir destino y contraseña, mantener DNI durante la autorización. La protección incluye todos los objetos actuales, también papelera y objetos ocultos por filtros.

Dos artefactos distintos: el respaldo ZIP contiene documentos cifrados; `proteccion-boveda.dnierecovery` contiene claves de recuperación cifradas por contraseña, sin los documentos. Para recuperar en otro dispositivo hay que importar primero el respaldo y luego elegir protección y contraseña. No hace falta conservar la privada antigua para esta recuperación.

**Recuperar toda mi bóveda** entrega un ZIP de documentos originales sin cifrar, con nombres y extensiones conservados y nombres únicos ante duplicados. No borra ni sobrescribe los objetos cifrados de la bóveda. Para pasar al nuevo DNI se requiere extraer ese ZIP, vincular el nuevo certificado y cifrar nuevamente los documentos: no se implementó una migración automática entre certificados.

## Capas y garantías

- Compose: `VaultScreen`, `VaultRecoveryControls`, `VaultAccessPanel` y `VaultToolsPanel`, navegación y asistentes.
- Coordinación: `VaultViewModel`, captura de autorización, sesión NFC, progreso, cancelación y borrado de secretos.
- Caso de uso: `domain/vault/UnlockRecoveryBatch.kt`, autorización de envelopes en una sesión. El usuario introduce PIN una vez; el protocolo consume autorización en DECIPHER, por lo que se realiza VERIFY para cada envelope posterior. Ante rechazo no se reintenta.
- Persistencia: `AndroidVaultRepository` serializa en IO; `AndroidVaultRecoveryAdapter` valida y publica paquetes/ZIP mediante ContentResolver.
- Motor independiente: `dnie-vault-recovery`, mismo formato DNIERCP1 y Argon2id/AES-GCM de R3. El formato cotidiano DNIEVLT1 y la secuencia PACE no cambian.

Se rechaza una bóveda con destinatarios distintos antes de verificar PIN: no se informa protección completa cuando falta acceso a alguna clave. Hasta 1000 objetos por operación. Los buffers de claves permanecen transitoriamente en memoria y se borran al cerrar/fallar; no se persisten en claro. Importaciones duplicadas del mismo UUID solo comparten entrada si el destinatario y SHA256 de todo el ciphertext coinciden; UUID con contenido distinto falla.

Preparación valida todos los documentos antes de escribir la protección. Recuperación exige cobertura de todos los objetos actuales y autentica todos antes de publicar: archivos añadidos después de preparar obligan a actualizar la protección. Un archivo dañado o sin cobertura aborta sin abrir el destino externo. ZIP sin compresión reduce carga de CPU; staging privado `.plain` se elimina en finally y participa en limpieza de inicio existente.

La publicación SAF no es atómica: un error del proveedor o cancelación durante la copia final puede dejar un destino incompleto. Los archivos originales permanecen intactos. Cambiar contraseña no invalida paquetes antiguos; conservar sus copias bajo control. Se mantiene recuperación alternativa autorizada solo para archivos, no para passkeys ni biometría.

## Validación

Build final: `:app:testDebugUnitTest :dnie-vault-recovery:test :app:assembleDebug :app:assembleDebugAndroidTest`, BUILD SUCCESSFUL. XML: app 79 pruebas y motor recuperación 23, sin fallos/errores; Gradle reutilizó tareas del motor sin cambios.

Instrumentación en teléfono: dos pruebas de recuperación superadas, incluida recuperación de tres objetos (duplicado incluido), conservación de nombres únicos, rechazo de cobertura incompleta y ciphertext alterado sin sobrescribir destino. Se usan RSA sintética y URIs file privadas; no prueba DNI físico ni proveedor SAF externo. Prueba visual de navegación intentada: no encontró jerarquía Compose; dumpsys confirmó keyguard activo y pantalla apagada. Repetición con teléfono desbloqueado superada: OK (1 test), 2.096 s; selección preservada entre pantallas y una acción de lote.

APK principal y auxiliar instaladas por ADB, ambas Success. Artefacto: `D:/DNIeToolchain/AndroidPackages/dnie-evolution-2.0.0-rc19-whole-vault-debug.apk`.

Pendientes: lote real con CIF hard y PIN único introducido por usuario, flujo SAF externo, cruces Windows↔Android, interrupción NFC y publicación; automatización futura del recifrado con nuevo DNI. No renovar certificados reales para probar.
