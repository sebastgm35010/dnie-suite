# IDplug instalado y pruebas pendientes con firmadores

Fecha: 2026-10-05. Android vigente: 1.8.3-transparent-apdu. El usuario propuso comprobar el middleware oficial descargado para investigar acceso desde otras aplicaciones. La instalación oficial ya estaba autorizada en el trabajo previo; la ejecución anterior no elevada había fallado con 1603/1925.

## Paquete y resultado comprobado

- MSI recibido: `D:\Winodws files\Descargas\idplug-classic-4.6.1-Peru-es-64bits.msi`, 105041920 bytes.
- SHA-256: `05DF8E88D17A424C0B7C435E30CD13F28EC1379A7DCFD25C2428E0F754EAEDCE`, coincide con la copia oficial inspeccionada previamente.
- Authenticode Valid; IDEMIA IDENTITY & SECURITY FRANCE SAS.
- [Página oficial RENIEC](https://identidad.reniec.gob.pe/dni-electronico), sección Driver DNI electrónico V3.0 para Windows de 64 bits, enlaza este mismo paquete.
- Instalación desde la terminal administrativa autorizada mediante msiexec /qn /norestart REBOOT=ReallySuppress: **exit 0**, sin reinicio.
- Producto registrado: IN Groupe IDplug Classic 4.6.1 (64-bit), versión 4.6.1.475.
- Directorios principales configurados mediante las propiedades INSTALLDIR e INSTALL64DIR en `D:\DNIeToolchain\OfficialMiddleware\IDPlugClassic-x86` y `IDPlugClassic-x64`. Archivos principales: aproximadamente 182 MiB. El MSI declara otros componentes de Windows; no todos se reubican en D:.
- Minidrivers instalados en System32/SysWOW64: PeruMinidriver64.dll y PeruMinidriver.dll, 1.8.0.459, firmas Valid.
- Registro Calais confirmado para Peru DNIe Device y Peru DNIe Contactless Device, vinculados al minidriver, Microsoft Base Smart Card Crypto Provider y Microsoft Smart Card Key Storage Provider.
- SCardSvr y CertPropSvc Running. Nuestro lector PC/SC sigue enumerándose.

Log privado y resultado reproducible: `D:\DNIeToolchain\MiddlewareInstall\874c953b505c4ffb9083fef2bcb84f69`. No incorporar el MSI, log detallado ni datos personales al repositorio.

## Prueba de disponibilidad PKCS#11

Se verificó la firma del DLL x64 y se cargó el módulo oficial en un proceso Python aislado. C_Initialize, C_GetSlotList y C_Finalize devolvieron CKR_OK. Enumeró tres slots y uno con token presente, coherente con Windows Hello presente y nuestro DNI ausente en ese instante. Esta prueba no identifica el token ni demuestra lectura de certificados del DNI. No se llamó a C_Login, C_Sign ni se ingresó PIN.

ReFirma PCX 1.6.0.10 y Plataforma Firma Perú 1.1.0.2 están registradas. Se localizaron sus ejecutables ClickOnce. Una búsqueda de nombres API en esos ejecutables y sus DLL del mismo directorio no produjo coincidencias; no permite afirmar qué proveedor usan en ejecución.

## Qué habilita y qué no demuestra

Hay dos capas distintas: el controlador propio presenta el lector PC/SC; IDplug aporta la interpretación de la tarjeta y proveedores criptográficos. La instalación puede permitir que aplicaciones que buscan certificados en Windows los encuentren. [CertPropSvc](https://learn.microsoft.com/en-us/windows/security/identity-protection/smart-cards/smart-card-certificate-propagation-service) propaga certificados de tarjetas al almacén Personal del usuario según las políticas existentes. No se modificaron esas políticas ni se importó manualmente el certificado personal como raíz.

La instalación no prueba que el ATR real del lector coincida con el perfil oficial ni que su PACE/lectura por NFC funcione. Tampoco demuestra que la falta de IDplug causara el error anterior de Ciudadano Digital: el código inspeccionado de identificación .NET y Java utiliza PC/SC directamente, y las consultas Java no llegaron al puente.

## Secuencia de prueba siguiente

1. Sesión nueva de la app en modo transparente y captura APDU; PACE pertenece a la PC en este modo.
2. IDplug Manager detecta el DNI a través de nuestro lector. Registrar su selección de lector, correspondencia ATR/perfil y comandos reales; el usuario responde al diálogo de CAN si aparece. No ejecutar cambio de PIN, desbloqueo ni renovación.
3. Comprobar enumeración de certificados mediante IDplug/PKCS#11 y, por separado, su asociación a CSP/KSP y aparición en Personal. No basta importar un .cer sin asociación a la clave de tarjeta para firmar.
4. Abrir una aplicación concreta, ReFirma o Firma Perú, comprobar selector de certificados y capturar los módulos cargados. La firma requiere una prueba controlada autorizada por el usuario, su PIN y validación del documento resultante.
5. Mantener el experimento Java explícito para Ciudadano Digital: no mezclar una mejora de certificados Windows con una conclusión no probada sobre su selección de lector.

El DNI no estaba conectado durante la verificación posterior: lectura real, asociación de certificados y firma desde estas aplicaciones siguen pendientes.
