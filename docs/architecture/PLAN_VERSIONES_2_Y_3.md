# Consolidacion 2.0 y redisenio 3.0

## Alcance confirmado

La referencia hardware es checkpoint/ciudadano-digital-android22, commit aadb115, sumada a la firma PDF Windows de Android21 y la base Android1.8.14 preservada. Ciudadano Digital supero validacion del DNI y detecto certificados instalados con seleccion del puente en Java real. El siguiente tramo de autenticacion es facial con ID Peru; la descarga posterior del programa de cambio de PIN queda fuera del alcance.

## Entrega 2.0

- Motor: negociacion PACE-PC, traduccion SM, fragmentacion y ruta de certificados con responsabilidades separadas. Ningun motor depende de Compose o ViewModels.
- Ciclo de vida: reset retira claves y contadores del PC; una APDU protegida sin autenticacion recibe rechazo de protocolo sin tirar la conexion ni reutilizar SSC. Error MAC/replay y perdida fisica siguen invalidando la sesion.
- Compatibilidad: consulta vacia de estado PIN observada se distingue de VERIFY con datos. Los cambios y firmas protegidos siguen siendo operaciones reales del DNI.
- Android: modo recomendado con traduccion por defecto, estado visible y opciones de comparacion apartadas. Transporte, archivos y diagnostico permanecen en adaptadores.
- Windows: selector por proceso como componente operativo con inicio/parada/estado. No requiere captura de navegador ni ETW. No altera Hello, servicios ni la seleccion de otros programas. Dependencias y limites se documentan.
- Validacion: pruebas de protocolo cifrado y reinicio, pruebas host/driver sinteticas, chequeo de arquitectura y APK compilado. Repeticion fisica de Ciudadano Digital y Firma Peru sobre el candidato antes de declarar 2.0 final.

La deuda tecnica debe quedar enumerada y acotada; no se afirmara ausencia absoluta de defectos a partir de pruebas sinteticas. XML y firma por lotes en ReFirma son candidatos de compatibilidad, no funciones nuevas aprobadas por este ensayo ni requisitos para repetir reconocimiento RENIEC. Mantener pendiente la distribucion de controlador con firma de produccion: el controlador actual es de pruebas.

## Entrega 3.0

Redisenio completo de navegacion, disposicion y presentacion de identidad, firma, boveda, passkeys y puente. Consume los mismos casos de uso y estados publicos. Compose emite acciones; ViewModels coordinan; adaptadores contienen APIs Android/Windows; bibliotecas del motor mantienen protocolos y criptografia. La UI no administra claves PACE, SSC, sockets ni APDU.

Preparar contratos de sesion y errores antes de redibujar pantallas. Medir escritura, scroll y cambios de pestanas con las regresiones de rendimiento existentes. Preservar referencia instalada y exportaciones, sin mezclar redisenio con cambios de protocolo.
