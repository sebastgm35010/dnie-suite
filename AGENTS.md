# Fork de evolución

Esta carpeta es el fork autorizado para nuevas funcionalidades y refactorización.
La base 1.0.8 permanece clausurada en `G:\Mi unidad\BOT TRADER\DNI_CODEX\modest-bose`: no modificarla.
No modificar la copia de Antigravity.

- Mantener las invariantes de IAS → PKI → PACE → PKI protegido.
- Mantener token PACE, MAC, contador SSC y GET RESPONSE físico previo al desenvolvimiento.
- Separar núcleo JVM, protocolo, transporte Android, PDF e interfaz sin dependencias circulares.
- Ejecutar las pruebas de todos los módulos afectados y compilar el APK después de una refactorización.
- La identidad de la app evolucionada debe permitir coexistir con DNIe Modest Bose 1.0.8.
- No incluir keystores, PIN/CAN, logs personales ni APK de terceros en Git.
