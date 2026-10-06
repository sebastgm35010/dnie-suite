# Passkeys: una autorización DNI · 1.6.4

El proveedor abría el catálogo cifrado con DNI/PIN para mostrar cuentas, destruía las claves al devolver la lista y exigía otra apertura al seleccionar una cuenta. Ahora el desbloqueo entrega una autorización limitada para completar esa selección con una sola lectura NFC y una sola verificación del PIN.

## Diseño modular

`dnie-passkeys-core/PasskeyContinuation` implementa `PasskeyContinuationAuthorizer`. Conserva en memoria copias únicamente de las claves correspondientes a las entradas mostradas (máximo 50). Cada entrada tiene un token opaco propio vinculado a esa cuenta; un token no puede seleccionar otra cuenta de la lista. Se consume toda la autorización al usar una entrada, antes de firmar. Las demás copias se borran; la seleccionada se borra al finalizar o fallar la firma.

El contrato está separado de Android y permite implementar posteriormente otro origen de autorización. `dnie-passkeys-android/PasskeyAuthorizations` adapta identidad del solicitante y expiración Android. El parser y verificador preparan nuevamente la solicitud final; `PasskeyOperations` intenta continuarla y, si no dispone de autorización válida, ofrece el flujo habitual con DNI. Compose solo presenta el estado.

## Límites

- Duración máxima de 60 segundos desde que se publica la selección. Reloj monotónico y limpieza programada en Android; una autorización vencida siempre se rechaza incluso si el temporizador se retrasa.
- Un solo grupo pendiente por proceso. Publicar una nueva selección destruye el anterior; el temporizador antiguo no borra el nuevo.
- Coincidencia de RP, paquete, firmantes actuales de la app, origen verificado, hash delegado cuando existe y cuenta seleccionada. La lista permitida de la solicitud final se comprueba nuevamente.
- Si el descubrimiento incluye un desafío, debe coincidir con el desafío final. Si Android no lo incluye, se valida el desafío final y se mantiene la vinculación al solicitante, sitio y selección opaca.
- No se guarda PIN, CAN, sesión NFC ni autorización en disco, preferencias o estado restaurable. El canal NFC se cierra tras desbloquear. El proceso puede conservar brevemente claves de las cuentas mostradas; ese es el cambio frente a exigir dos lecturas.
- Fallar la validación de una continuación o pausar su Activity antes de terminar revoca su token. Los errores/cancelaciones durante la publicación limpian la autorización cuando el motor puede observarlos. Si el usuario cierra el selector del sistema después de publicar las cuentas, Android puede no avisar al proveedor: en ese caso se destruye al vencer el plazo, reemplazarse o cerrar el proceso.
- Cerrar sesión mediante Credential Manager borra cualquier autorización pendiente. Ninguna continuación permite crear, administrar o borrar passkeys; esas operaciones siguen requiriendo DNI/PIN.

El diagnóstico registra `continuation_consumed` o `continuation_unavailable`, sin tokens, cuentas, claves ni desafíos. En la primera ruta el inicio de sesión debería completarse sin otro `awaiting_card`; en la segunda puede solicitar DNI nuevamente.

## Posibilidad del modo de dispositivo

El modo «DNI para habilitar el teléfono; biometría para usar passkeys» queda como ampliación separada y optativa. No hay una opción biométrica activa ni una copia persistente utilizable sin DNI en esta versión. Se podrá añadir un adaptador de autorización con claves protegidas por Android Keystore y autenticación biométrica por operación, manteniendo la política DNI independiente y los controles RP/solicitante del motor.

Ese modo requiere diseñar enrolamiento con DNI/PIN, almacenamiento local alternativo, revocación, cambio de huellas, pérdida del teléfono y recuperación. La bóveda de documentos no cambia de política. La app tampoco afirma identidad civil ante los sitios: el modo de autenticación local no cambia la atestación WebAuthn actual.

## Validación

Pruebas sintéticas JVM comprueban uso único, independencia respecto al catálogo original, caducidad, vinculación de solicitante/sitio/origen/hash/desafío, lista permitida, cancelación/reemplazo, descubrimiento sin desafío, concurrencia y token vinculado a la cuenta seleccionada. La comprobación final con el selector real de Google/Facebook requiere una prueba en el teléfono.

Compilación del APK y verificación de firma completadas. Pasaron 104 pruebas de los módulos afectados (39 passkeys JVM, 11 adaptador Android y 54 app), incluidas 8 nuevas del motor de continuación; también 12 comprobaciones WebAuthn independientes. Actualización 28 / `1.6.4-passkeys-single-authorization` instalada por ADB. SHA-256: `D009F7690CBEBAC5FCDBE028505CAA25656E4821955671391558766D6652AC1E`.
