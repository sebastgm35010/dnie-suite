# Alcance antes de aislar el minidriver DNIe

Restricción del usuario: no detener CertPropSvc si es requerido por el sistema. Se retira la propuesta de control con servicio detenido; no se ejecutó. Revisión únicamente de lectura.

CertPropSvc está Running/Automatic, depende de RpcSs y no muestra servicios dependientes directos en esta PC. Eso no demuestra ausencia de consumidores funcionales. Microsoft documenta que es necesario para smart-card Plug and Play y copia certificados al almacén Personal: https://learn.microsoft.com/en-us/windows/security/identity-protection/smart-cards/smart-card-certificate-propagation-service . No es el servicio SCardSvr, y no se considera una dependencia global del arranque normal por esta evidencia; sí proporciona funcionalidad del sistema que interesa preservar.

Minidriver DNI identificado: oem237.inf, original peruminidriver.inf, IN Groupe1.8.0.459. Driver del lector puente separado: dnibridgereader.inf, paquetes oem234/235/236; no retirarlos. Registros Calais de Peru DNIe Device y Peru DNIe Contactless Device apuntan PeruMinidriver64.dll. INF instala DLL Perú/IDplug y registros Calais32/64bits; contempla secciones de desinstalación. Su existencia no garantiza que pnputil ejecute todas esas secciones ni elimine registros de instalaciones previas.

Retirar el paquete específico del DNI es un control dirigido: puede impedir su integración CSP/KSP, propagación de certificados y operaciones de firma/autenticación Windows dependientes del minidriver. El lector puente puede seguir enumerado porque su driver es otro. No equivale a demostrar compatibilidad final con IDplug; si el cliente automático desaparece, hay que enviar APDU con un cliente controlado y revalidar posteriormente con el minidriver restaurado. IDplug podría usar su propia ruta PKCS#11, por lo que no asumir que toda comunicación desaparece.

Plan revisado, aún no ejecutado: respaldar/exportar exclusivamente el paquete oem237 y los registros relevantes; comprobar método de restauración; retirar el paquete DNI durante una ventana delimitada sin borrar DLL manualmente ni modificar servicios/políticas; verificar después las asociaciones y módulos, evitando asumir que retirar INF evita una reinstalación automática; realizar control de presencia y APDU con productor conocido; restaurar y comparar. No bloquear Windows Update ni cambiar políticas para forzar el aislamiento. Si ese aislamiento no es fiable, replantear una prueba sintética aislada.

No se desinstaló ningún paquete, se borró registro, se detuvo servicio ni se armó un nuevo monitor en esta revisión.
