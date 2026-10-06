# Android22: Ciudadano Digital supera la seleccion equivocada

6 de octubre de 2026. Prueba real del flujo oficial con el puente NFC y filtro temporal de lectores dentro del Java de RENIEC. Android instalado mantiene la referencia funcional; no se instalo un APK nuevo para esta prueba.

## Resultado observado

El usuario obtuvo «El DNIe ya tiene certificados instalados» en descargar certificados, el resultado esperado para su documento. Tambien probo la opcion de cambio de PIN y observo el avance de validacion del DNIe a autenticacion. Aclaro que esta autenticacion es facial mediante camara e ID Peru, no una verificacion de PIN contra el DNI. Despues se descargaria un programa dedicado al cambio de PIN; ese tramo queda fuera del alcance y no se ejecutara. Se alcanzo el limite de estos flujos que requiere el puente. Los resultados de interfaz son reportados por el usuario; no equivalen a una instalacion nueva de certificados ni a un cambio efectivo de PIN.

La captura PCSC confirma que Java38900 y Java64760 conectaron a DNIe Bridge Android NFC Private Reader 0 mediante SCardConnectA, con retorno0. El segundo es hijo observado del proceso.NET53348, que tambien conecto correctamente al puente mediante SCardConnectW. No hay conexiones Java a Windows Hello en esta captura. El filtro `bridge_reader_list_filtered` se aplico antes de cada conexion Java.

Esto confirma que restringir la seleccion en el Java real resuelve el obstaculo de lector equivocado de la prueba anterior. No demuestra cobertura de todas las funciones de Ciudadano Digital. El filtro sigue siendo una herramienta experimental temporal, no una integracion instalada permanente.

## Cruce movil y host

Registro movil Android22: una sesion, perfil pace_android_translated, desde23:25:26.379 hasta23:28:06.820 UTC,160.441s. Contiene104comandos y104respuestas. Host confirma104solicitudes y104retornos en la misma ventana.

Estados APDU externos:76×9000,20×6A86,4×6A82,4×6282. No convertir estados de sondeo incompatibles o fin de archivo en fallo global del flujo. Las respuestas protegidas tienen estado externo; no inferir su estado interno a partir de esos9000.

Latencia comando-respuesta movil: mediana105ms, maxima159ms para104pares. Es tiempo de la operacion registrada en Android, no latencia completa de navegador ni de red ni benchmark general de la app.

Tres reset_requested/reset_completed y cuatro power_off_requested/power_off_completed. No hay comando VERIFY (INS20) ni CHANGE REFERENCE DATA (INS24) en el registro aportado: el avance a pantalla de autenticacion no valida ejecucion de cambio de PIN. El registro APDU tampoco prueba que hayan quedado instalados nuevos certificados.

session_closed del movil precede el EOF del host aproximadamente651ms; EOF no es evidencia por si solo de fallo NFC. Monitor termina con session_completed, attributionReady=true y collectorsStopped=true. Observador termina con stopped; archivos de error del observador, navegador y monitor vacios. Filtro retirado al desprender la instrumentacion.

## Evidencia privada

Movil: D:/Winodws files/Descargas/dnie-apdu-pace-android22.jsonl.json.

Raiz Windows: D:/DNIeToolchain/LiveCaptures/tailnet-java-bridge-filter-81fba7208f274845be896d2ed20c2af6. Subcaptura host5b90ef04d18346a0b735bb99d8520ddd. Instrumentacion propia preparada en commit21dbd31. Registros y datos personales permanecen fuera de Git.

## Siguiente trabajo

Convertir la seleccion por proceso en una opcion operativa reproducible con inicio, estado, parada y recuperacion definidos. Conservar contrato oficial de activacion, stdout y notificacion de resultados; no cambiar servicios ni orden global de lectores. Mantener esta herramienta como referencia de comparacion mientras se prepara una integracion estable. Repetir prueba oficial tras esa integracion; no afirmar que el flujo queda instalado permanentemente por el exito de esta captura.
