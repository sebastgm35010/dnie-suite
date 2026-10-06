# Limpieza del motor: etapa 1

Referencia congelada: commit10089b4 / tag checkpoint/firma-windows-1.8.14. APK probado y hash respaldadosD; no reemplazarlo por una refactorizacion sin nueva prueba fisica.

## Cambios

PcResponseFragments ahora posee los buffers de fragmentos para el PC: divide respuestas SM codificadas, atiende GET RESPONSE local y borra buffers al consumirlos/retirar sesion. PaceTranslationEndpoint coordina el motor PACE/encaminamiento y delega esa responsabilidad. La division ocurre despues de codificar la respuesta; drenar fragmentos no altera SSC ni envia comandos al DNI. El GET RESPONSE fisico sigue en su canal separado.

Se conserva el mismo comportamiento ante autenticacion incompleta, MAC alterado, replay, nuevas negociaciones y ruta publica de certificados. Esta etapa no modifica el rechazo de PIN ni el error conocido del PC tras reset.

Version de desarrollo1.8.15-core-cleanup(48) diferenciada de la referencia1.8.14. No instalada ni enviada como parte de esta limpieza.

CONTEXTO_ACTUAL.md contiene solo referencia vigente, pendientes y operacion. Cronologia conservada en docs/archive/CONTEXTO_HISTORICO_HASTA_1_8_14.md, explicitamente marcada como historica. MOTOR_FUNCIONAL.md identifica propietarios y puntos de entrada. Exclusiones Git ampliadas para APK, llaves privadas empaquetadas y capturas. Se retiraron blancos finales de cuatro fuentes propias sin tocar licencias de terceros.

## Validacion

Control de arquitectura aprobado. Pruebas del protocolo, contrato puente, adaptador Android y app mas compilacion APK ejecutadas offline. Resultado:187 casos,185 aprobados,2 interop omitidos,0 errores/fallos. BUILD SUCCESSFUL en1m59s; APK1.8.15 generado. Las pruebas existentes ejercitan traduccion entre dos sesiones cifradas, respuestas fragmentadas, MAC/replay, perfiles PACE y certificados. No se ha repetido aun firma con tarjeta fisica sobre este APK. Log privado: D:/DNIeToolchain/Temp/build-1-8-15-core-cleanup.txt.

## Siguiente limite de refactorizacion

Extraer negociacion PC-PACE como estado independiente de la traduccion de comandos. Luego introducir el rechazo protocolario controlado del SM tras reset en un commit separado, con prueba reproducible y confirmacion de no reutilizacion de claves/SSC. Selector ClickOnce y convivencia de minidriver permanecen independientes.
