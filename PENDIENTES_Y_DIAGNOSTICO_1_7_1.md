# Pendientes y diagnóstico de 1.7.1

Actualizado: 2026-10-05. Fuente: `dnie-diagnostico5.jsonl`, analizado en su ubicación de adjunto sin copiar el registro personal al repositorio.

Avance posterior: [mantenimiento 1.7.2](MANTENIMIENTO_1_7_2.md) crea checkpoint y reduce trabajo repetido de búsqueda, separa importación de firma y añade comprobación de capas. La mejora física de fluidez sigue pendiente. [Plan puente 2.0](docs/architecture/PLAN_PUENTE_ANDROID_PC_2_0.md) distingue las vías PCSC/PKCS#11/Windows y sus pruebas; el puente no está implementado.

## Resultado de esta ejecución

244 eventos: dos anteriores al arranque y 242 de `1.7.1-passkeys-diagnostics`, IDs 1777–2018, SDK 36. Se excluyeron los dos eventos anteriores de las conclusiones.

- QR: `scan_requested` → `scanner_result` → `scan_failed`, código 13 de `MlKitException`, después de unos 4,2 segundos. Google define 13 como `INTERNAL` ([referencia oficial](https://developers.google.com/android/reference/com/google/mlkit/common/MlKitException)). No es el código explícito de cancelación ni el de permiso denegado. El registro no identifica qué componente interno falló. No aparecen entrega al autenticador ni apertura de cámara alternativa registradas por nuestra app.
- Passkeys: la primera apertura de administración salió al fondo sin operación de tarjeta. La segunda completó selección PKI, certificado, PIN y apertura del catálogo: unos 1.913 ms entre selección PKI y catálogo abierto, seguida de `operation_completed`. Las cancelaciones de fondo posteriores no revierten ese éxito; falta contexto para atribuirlas a una acción específica. No hay una ceremonia de registro/autenticación de sitio ni confirmación biométrica en este segmento.
- Bóveda: seis elementos cargados correctamente en 263 ms.
- Rendimiento: 49 eventos `slow_frame`: bóveda 24 (máximo 195 ms), passkeys 9 (93 ms), consola 6 (208 ms), certificados 5 (455 ms), lotes 3 (237 ms), firma 1 (128 ms), arranque 1 (615 ms). No hay denominador de frames ni prueba controlada equivalente para afirmar mejora/regresión frente al registro anterior. Sigue habiendo trabajo de fluidez.
- No aparece un crash de la app en este segmento. Esto no certifica los flujos que no se ejecutaron.

## Pendientes en orden recomendado

| Prioridad | Trabajo | Estado y criterio de cierre |
|---|---|---|
| 1 | Diagnosticar el error interno del lector QR | Abierto. Reproducir en dispositivo con un QR FIDO real; comprobar lector con/sin autozoom y disponibilidad del módulo, sin registrar contenido QR. Si persiste, evaluar lector propio aislado con CameraX/ML Kit para eliminar la dependencia de la pantalla de escaneo de GMS. No atribuir el código 13 a una causa concreta sin evidencia. |
| 1 | Optimizar fluidez de navegación y bóveda | Abierto. Medir escritura/desplazamiento y cambios de pestaña con un recorrido repetible y trazas de hilo principal/Compose; distinguir carga de disco de composición/layout y arranque. Corregir el coste demostrado, manteniendo motores fuera de UI. |
| 1 | Validar biometría opcional de passkeys en teléfono | Implementada; pendiente de evidencia física del ciclo completo: habilitar con DNI, autenticar con huella, cancelar, cambiar inscripción y comprobar invalidación/retorno al DNI. La bóveda de archivos sigue sin biometría. |
| 2 | Autenticación QR completa PC → teléfono → sitio | **Pendiente de prueba real: el usuario no puede probarla ahora.** No marcar como validada por abrir la cámara. Verificar qué proveedores ofrece Android y que el navegador acepta una assertion de una credencial de esta app. |
| 2 | Decidir transporte híbrido propio | No implementado. El flujo actual entrega a Google Play Services; si este no ofrece nuestras credenciales, un lector propio tampoco resuelve ese límite. Evaluar caBLE/CTAP híbrido como módulo independiente solo después de la prueba anterior. |
| 2 | Ampliar pruebas de almacenamiento y recuperación | Las seis pruebas sintéticas del mantenimiento ya pasaron en emulador. Añadir proveedores SAF con escritura fallida/cancelación, muerte de proceso y recuperación al reiniciar; confirmar que nunca se publica una restauración incompleta. La migración con dos DNI físicos sigue pendiente y no bloquea ampliar simulaciones. |
| 2 | Mejorar información de certificados y recuperación | Propuesta por implementar: mostrar por separado vigencia del certificado, documento y destinatario del catálogo; avisos de renovación y guía de migración antes de perder acceso al DNI anterior. Un respaldo no recupera por sí solo una clave inaccesible. |
| 3 | Facilitar continuidad de delegación biométrica | Mejora por diseñar. Actualmente cualquier cambio del catálogo invalida la copia biométrica y exige habilitarla de nuevo. Evaluar actualización segura sin conservar autorizaciones reutilizables ni habilitar administración sin DNI. |
| 3 | Rediseño general y extracción de responsabilidades restantes | Posterior a estabilizar motores. Mantener separados componentes, coordinación, adaptadores y criptografía. Priorizar límites de responsabilidad sobre dividir archivos únicamente por tamaño. |
| 3 | Integración Windows y formatos entre proveedores | Futuro; no implementado. Requiere transporte autorizado a las operaciones privadas y evaluación de compatibilidad. El respaldo actual es de esta app, sin sincronización automática ni interoperabilidad general. |

## Qué ya existe y no debe confundirse con trabajo pendiente

- Registro y autenticación Android: el usuario ya confirmó uso en Facebook/Google en entregas anteriores; este log no vuelve a probarlos.
- Respaldo cifrado, restauración, migración a nuevo DNI y recuperación del catálogo anterior: implementados y con pruebas sintéticas reales del motor. Pendiente la matriz física y la ampliación de fallos Android.
- Las passkeys antiguas conservan BE=0 y no son exportables; necesitan registro nuevo en cada sitio para respaldo portable. No cambiar ese indicador retroactivamente.
- La migración cambia la protección del catálogo de passkeys; no implica migración masiva de los documentos de la bóveda.
- Checkpoint privado 1.6.5 y base de firma clausurada: preservados. Conviene crear otro checkpoint cuando la nueva entrega quede confirmada en el teléfono.

Esta actualización solo documenta hallazgos y pendientes. No modifica código, no genera otro APK y no instala ni controla el teléfono. Las pruebas físicas aplazadas permanecen abiertas, sin tratarlas como fallos ni éxitos.
