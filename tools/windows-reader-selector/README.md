# Selector de lector por proceso

La seleccion de Ciudadano Digital ya fue validada con el puente en Android22. Este componente convierte el filtro nativo en una operacion separada de host-diagnostics. No captura navegador, certificados, CAN/PIN, argumentos de proceso ni APDU. No cambia configuracion Java/Windows ni afecta otros Java.

`selector_engine.py` posee los enganches y sus hijos suspendidos. `selector_service.py` posee duracion (hasta8h), estado/IPC por archivos y registro limitado a dos archivos de aproximadamente1MB. `pcsc-bridge-selector.js` posee el filtro de lista PCSC. El panel PowerShell Windows Forms solo inicia, consulta estado y solicita parada.

## Uso actual

Abrir `DNIeBridge.exe` desde el acceso **DNIe Bridge** del escritorio y activar el selector opcional antes de abrir Ciudadano Digital. La GUI y este adaptador funcionan sin elevación; el servicio instalado conserva los permisos del host. Detener el selector o cerrar la GUI solicita retirar sus enganches. Ver [gestor nativo](../windows-manager/README.md).

## Uso histórico del panel anterior

Abrir `ABRIR-PUENTE-PC.bat`. Iniciar host requiere elevacion de Windows; el selector y el panel funcionan en la cuenta del usuario que ejecuta RENIEC. Si el host ya escucha no se reinicia. Activar selector antes de lanzar Ciudadano Digital, conectar Android por Tailscale y mantener el DNI apoyado. Retirar selector al acabar. Cerrar el panel solicita retirar los enganches; el host permanece hasta su boton Detener. No hay nuevo QR/JSON para Tailscale.

Requisitos actuales: Python con psutil, Frida17.22.2 en directorio privado, runtime oficial RENIEC instalado y controlador PC/SC propio. En este equipo Frida reside en D:/DNIeToolchain/Instrumentation/python. Para otra ubicacion usar DNIE_FRIDA_DIR antes de abrir el panel. El componente no instala dependencias ni modifica firewall.

Estado/logs del panel en D:/DNIeToolchain/Desktop cuando esta disponible; alternativamente LOCALAPPDATA/DNIeBridge/Desktop. Ningun servicio se registra para arrancar con Windows.

## Alcance y limites

Java elegible: ruta exacta del runtime RENIEC del usuario. .NET elegible: cddnie-app.exe en la cache ClickOnce del usuario; lanzador dfsvc solo bajo Microsoft.NET de Windows. Child gating limitado a esos procesos padres; hijos ajenos se reanudan sin filtrarse. Hay pausa de inicio durante el enganche. El lector debe existir; si no, Java no cambia a Hello.

Este filtro no controla la seleccion inicial .NET ni autentica una PC por si mismo. El host/driver siguen verificando acceso y el transporte mantiene su politica independiente. Sesion suspendida no equivale a lector desconectado. No enviar PIN ni cambios de certificado en pruebas automaticas con un DNI real.

La validacion hardware del panel nuevo queda pendiente para cerrar el candidato2.0. Distribucion automatica/instalador y firma de controlador para otras PC son pendientes de distribucion; este equipo conserva su instalacion de pruebas funcional. La prueba Android22 valida el filtro anterior identico, no todos los estados de este supervisor nuevo.

## Pruebas

`python -m unittest discover -s tools/windows-reader-selector -p test_*.py` comprueba alcance y cleanup con dobles. `selector_service.py --self-test --directory <directorio nuevo> --dependency-dir <frida privado> --probe-classes <clases de prueba>` lanza un padre/hijo Java reducido con comprobacion interna de lista exacta, sin conectar a tarjeta.

Dependencias: [frida-core usa wxWindows Library Licence3.1](https://github.com/frida/frida-core/blob/main/COPYING), con excepcion expresa para codigo objeto; no se presenta como MIT. [psutil usa BSD3-Clause](https://github.com/giampaolo/psutil/blob/master/LICENSE). Conservar sus avisos cuando se distribuya. No se ha incorporado el APK GPLv3 de Virtual Smart Card ni codigo propietario de RENIEC.
