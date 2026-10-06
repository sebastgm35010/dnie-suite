# Seleccion de lector RENIEC: dos decisiones diferentes

Inspeccion estatica local6 octubre2026, sin cambiar software propietario ni prioridad global del sistema.

Windows SCardListReaders devuelve lectores disponibles de los grupos solicitados; su contrato publico no garantiza prioridad por compatibilidadDNI ni documenta un lector favorito universal. CardTerminals.CARD_PRESENT filtra presencia, no identifica DNI. Fuentes: https://learn.microsoft.com/en-us/windows/win32/api/winscard/nf-winscard-scardlistreadersw y https://docs.oracle.com/en/java/javase/21/docs/api/java.smartcardio/javax/smartcardio/CardTerminals.html.

Codigo local inspeccionado (decompilado, privado en build/research):

- SmartCardService.cs:62 crea CardChannel usando context.Readers.First(). Open conecta a ese primer lector; RemovedCard genera exactamente «No se ha encontrado un DNIe conectado al lector de tarjetas inteligentes».
- CddnieApplication.Run llama ObtenerVersionDnie antes de GetDependencies/JavaService.Run y abandona si esa etapa falla.
- DnieComponent.java:66 enumera CARD_PRESENT;72 rechaza lista vacia;79 asigna terminal repetidamente en el bucle, sin break ni validacion por candidato. Solo despues del bucle abre servicio y verificav2/v3. Por tanto usa ultimo terminal presente.
- JavaService recibe parametros decodificados de activacion y ejecuta el runtime customjre; no transmite explicitamente el lector previamente elegido por .NET a DnieComponent.

Metadatos PC/SC medidos ahora, sin conectar ni enviar APDU: index0 puente sin tarjeta, index1virtualPCD sin tarjeta,index2WindowsHello presente. En anteriores capturas con DNI el puente paso a presente mientras Hello permanecia presente. Orden observado no es contrato de Windows.

Esto explica sin contradiccion el mensaje «no hay DNI» cuando el puente no tiene sesion: .NET conecta primero al puente vacio y corta el flujo antes de lanzar Java. Cuando el DNI esta presente, .NET puede detectarv3 correctamente y luego Java puede seleccionarHello al ser ultimo presente. Es inferencia para los ensayos reales; la decision estatica de cada componente esta comprobada, pero falta traza terminal/handle del Java de ClickOnce para atribucion definitiva por intento.

No es necesario atribuir toda seleccion al fallback de IDplug: ese administrador permite seleccion manual y es otro cliente PKCS11. Observacion del usuario identificaHello como slot alternativo; log exportado truncaba descripcion y por si solo no identificaba el slot.

Solucion a investigar: selector explicito por proceso, mantener lector durante esa sesion y comprobar terminal del Java real. El lanzador propio ya filtra exactamente el puente y completo Android18, pero falta integrarlo con ClickOnce. Cambiar nombre/orden global de lectores no resuelve robustamente dos componentes que eligen extremos opuestos. No desactivarHello ni serviciosWindows para resolver seleccion.
