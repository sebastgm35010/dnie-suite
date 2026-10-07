# H3 · PDF integrado invisible de escritorio

Fecha: 2026-10-07. Checkpoint previo: `checkpoint/pre-suite-pdf-invisible`.

## Comportamiento

La suite continua conserva emparejamiento DPAPI/Keystore, TLS privado y un PIN Windows por lote. Los documentos permanecen en PC; Android recibe las operaciones de firma del mismo protocolo. No publica tarjeta PC/SC ni cambia el puente compatible.

Por defecto, un archivo con extensión `.pdf` se procesa como PDF y produce `signed/..._firmado.pdf`; los demás archivos producen CMS separado `.p7s`. `--pdf-mode detached` mantiene CMS separado incluso para PDF. Disponible en `demo`, `remote` y `suite`; en `suite` colocar la opción después de `--identity`. Un PDF inválido falla individualmente sin fallback automático a CMS. El lote continúa si el fallo es documental, conserva la política existente de detener operaciones privadas tras error de tarjeta y no repite firmas.

## Capas

- `dnie-pdf-desktop`: adaptador JVM PDFBox 3.0.8 de `PadesSignerEngineInterface`, política de documentos y workspace temporal privado. Sin UI ni dependencias Android.
- `dnie-signing-core`: clasificación explícita opcional `pdfInput` sin inventar páginas para firma invisible. Conserva contratos, lotes y publicación de resultados.
- `dnie-signing-desktop`: selecciona formato, importa originales y compone el adaptador. Se elimina `UnavailablePdfEngine`.
- `dnie-core`: conserva CMS, atributos firmados y verificación de respuesta RSA. No cambia PACE, SSC, PIN por operación ni transporte.

PDFBox utiliza guardado incremental y firma externa `/ETSI.CAdES.detached`. Se reserva espacio de 32768 bytes para CMS; solo se publica el PDF terminado después de verificar la respuesta RSA contra el certificado. Entrada limitada a 256 MiB, cache de escritura mixta de 32 MiB y temporales dentro del trabajo en D:, con ACL del propietario y limpieza al finalizar. Estos límites no certifican endurecimiento total contra PDF hostil ni garantizan uso de memoria total de 32 MiB.

Se rechazan PDF cifrados, sin páginas, DocMDP que prohíba modificaciones y FieldMDP (soporte pendiente). Se conservan bytes/revisiones anteriores; esto no convierte una firma anterior inválida en válida. No hay visor, sello visible, TSA, LTV, validación de confianza completa o revocación. La fecha es del reloj local.

## Evidencia

Pruebas nuevas: cuatro del adaptador, dos del caso de uso y una de opciones CLI. Regresión: 123 pruebas sin fallos, errores ni omisiones en PDF desktop, signing desktop/core/link y app; APK Debug compilado y distribución Windows actualizada. Logs en `D:/DNIeToolchain/Temp/suite-pdf-final-checks.log` y `suite-pdf-packaging.log`. Cubren firma invisible, widgets sin área visible, verificación CMS, alteración rechazada, dos firmas sucesivas, preservación del prefijo, PDF inválido/cifrado/certificado restrictivo y respuesta RSA inválida sin salida parcial; lote mixto y continuidad tras fallo documental.

El ejecutable empaquetado produjo un PDF sintético en `D:/DNIeToolchain/PdfDesktopEvidence/jobs/0fecba76-d340-493a-9ef1-468d70b65d1c`. .NET SignedCms verificó integridad, certificado sintético coincidente y rechazo de alteración. **Es simulación: falta firma integrada física con DNI mediante la suite.** El verificador lee el ByteRange/Contents de la revisión final de los PDF propios y conserva verificación CMS separado. Es un extractor acotado, no un validador general de semántica PDF. No verifica criptográficamente todas las revisiones previas.

También se ejecutó un segundo lote sintético (segunda firma incremental sobre el PDF anterior y un binario), 2/2 verificados con .NET; evidencia `D:/DNIeToolchain/PdfDesktopEvidence/jobs/f47ec544-b449-48e1-810e-b7b0cf776c1b`. Las pruebas JVM verifican ambas firmas previas; el script independiente verifica la firma final y preservación de su original.

## Prueba física siguiente

Usar el ejecutable habitual actualizado:

```powershell
$env:JAVA_HOME='C:/Program Files/Microsoft/jdk-17.0.20.101-hotspot'
& 'D:/DNIeToolchain/Builds/signing-desktop-h0/install/dnie-signing-desktop/bin/dnie-signing-desktop.bat' suite --pc '192.168.1.5' --phone '192.168.1.11' --workspace 'D:/DNIeToolchain/SuiteContinuousJobs' --identity 'D:/DNIeToolchain/SuiteContinuousState/pc.dpapi'
```

En Android 56, Conectar PC guardada; enviar un PDF técnico y opcionalmente otro archivo, PIN único desde terminal, mantener DNI. Comprobar `_firmado.pdf` y ejecutar `tools/verify-job.ps1 -JobDirectory <UUID>` o un verificador externo. No hace falta nuevo enrolamiento ni APK para este incremento. El APK se recompila como regresión de los contratos compartidos.

Siguiente orden: prueba física del PDF invisible, corpus documental y después apariencia visible; visor/editor queda detrás del contrato del motor.

## Validación física H3 completada

2026-10-07: nuevo trabajo `ed878567-300a-4a38-aeba-e56cd47eb640`, backend PRIVATE_ANDROID, 3/3 con PIN único desde Windows: dos PDF integrados invisibles y un DOCX con CMS separado. El trabajo anterior `129a7109-55b5-4e5f-94be-2c103a0ef342` del transcript es histórico y no evidencia adicional de PDF integrado.

`verify-job.ps1` verificó independientemente con .NET las tres firmas, coincidencia del certificado del trabajo y rechazo del contenido alterado. Los PDF conservan el original como prefijo y su ByteRange final cubre la revisión completa salvo Contents. Certificado coincidente con el DNI previamente validado y presente en CurrentUser/My. No se comprobó revocación ni confianza completa. Evidencia privada fuera de Git: `D:/DNIeToolchain/SuiteContinuousJobs/ed878567-300a-4a38-aeba-e56cd47eb640/independent-verification.json`.

Después de informar 3/3 aparece el mensaje genérico «Suite continua finalizada o solicitud invalida» y el prompt de cierre BAT. `result.json` declara `complete:true`, `sessionError:null`. Las firmas están completas, pero el motivo del cierre posterior no consta: el catch exterior de CLI omite tipo/fase. No atribuirlo sin evidencia a NFC, TLS, PIN o Ctrl+C. Pendiente diagnóstico de cierre por fase sin secretos y prueba de dos lotes consecutivos exitosos en la misma sesión.

Próximo hito documental: apariencia visible mediante contrato del motor PDF; visor/editor después.
