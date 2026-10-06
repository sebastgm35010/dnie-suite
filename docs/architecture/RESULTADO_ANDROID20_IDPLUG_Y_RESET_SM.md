# Android20: IDplug operativo, rechazo local de PIN y SM tras reset

## Resultado y alcance

Sesión df0cee09-d210-4a70-a1da-d638bf2eefe1, 6 octubre 2026, 22:24:43.849–22:28:52.173 UTC: aproximadamente 248,324 s. Un único ensayo, 202 comandos / 201 respuestas, cuatro resets completados y dos power-off completados. Estados externos: 171 × 9000, 24 × 6A86, 1 × 6A82, 1 × 6102, 3 × 6282, 1 × 6985. El diagnóstico general contiene 670 eventos.

Las capturas del usuario muestran IDplug Manager seleccionando explícitamente el lector puente, token Operational / PKI Application y las tres categorías de claves AUT/FIR/CIF. La última muestra Login success y referencias a objetos privados. Mostrar esos objetos no significa extraer claves privadas ni prueba una firma desde Windows. La captura de información muestra cinco intentos PIN restantes en ese momento.

## Ciudadano Digital: fallo separado

Browser registra autorizar y url-validacion HTTP200 a las 22:24:55, seguidos del manifiesto ClickOnce200. La carga del manifiesto se registra cancelada, compatible con entrega a un manejador externo; no demuestra por sí sola instalación fallida.

APDU21–22, a las 22:24:59, detectan v2 con 6A82 y v3 con 9000; siguen power-off y pausa hasta 22:25:45. No hay lectura de identidad/certificados del motor Java por el puente en esa ventana. El usuario informa rechazo de la web. Sigue siendo compatible con la hipótesis de selección Java de otro terminal, pero falta atribución/terminal del proceso para confirmar este intento. El selector por proceso que completó Android18 aún no está integrado con ClickOnce. No confundir el lector correcto elegido por IDplug con el elegido por Java.

## IDplug y PIN

A las 22:25:47–48 se completa PACE del PC. Se observan muchas consultas VERIFY protegidas sin datos (longitud19), todas con estado externo9000; ese estado de envoltura SM no prueba por sí solo el resultado interno de PIN. VERIFY protegido con datos en comando156 también recibe estado externo9000; la captura Login success aporta evidencia de autenticación IDplug.

Comando186 a las 22:28:31.639: VERIFY plano 00200003, longitud5, responde6985. El endpoint conserva PACE PC y la ruta PlainCertificateReadRoute solo admite selecciones/lecturas de certificados, por lo que rechaza esta consulta localmente. No se transmite al DNI ni contiene un PIN candidato. No demuestra PIN bloqueado ni consumió intentos por esta consulta. El aviso reportado por Firma Perú puede estar mapeando este estado como bloqueo, sin atribución concluyente del cliente emisor.

## Causa inmediata del cierre

Reset solicitado después de comando201 a las 22:28:44.909 y completado a las 22:28:45.448. Android reconstruye correctamente su PACE físico (pace_ready) pero TranslatedPaceCard.reset retira el endpoint PACE PC y crea uno sin claves terminales.

Comando202 a las 22:28:52.123: VERIFY protegido 0C200003, longitud19, llega sin un nuevo MSE/GENERAL AUTHENTICATE del PC después del reset. Falla en PaceTranslationEndpoint.transmit, rama secure ?: error("Terminal SM before authentication"). El diagnóstico confirma IllegalStateException > IllegalStateException y cierre transport_or_card_failed; no TagLostException ni fallo físico PACE en este cierre. No hubo respuesta ni APDU física para el comando202.

El cierre manual pedido después aparece con estado idle. No se debe conservar ciegamente claves/SSC antiguos tras un reset ni reenviar criptogramas sin autenticar para evitar este error.

## Próximos cambios/pruebas

1. Integrar selector Java exacto en el lanzamiento real de Ciudadano Digital y comprobar terminal elegido / resultado original; conservar Hello y servicios Windows.
2. Hacer explícito el estado PC-SM-no-autenticado tras reset: emitir diagnóstico de generación/contexto y rechazo protocolario controlado, sin derribar innecesariamente el transporte y sin autenticar/fabricar éxito. Validar con prueba sintética reset → comando SM antiguo → nuevo PACE → operación correcta.
3. Diseñar compatibilidad de consultas PIN sin datos que siguen a lectura plana de certificados. Separar consulta de estado de VERIFY con PIN real; no ampliar a firma/escritura arbitrarias ni devolver9000 ficticio. Comprobar cuál cliente usa este flujo y conservar semántica de contadores.
4. Probar Ciudadano Digital e IDplug/Firma Perú por separado para atribuir cada cambio de contexto. La firma móvil posterior confirmada por el usuario respalda que el aviso Windows no acredita bloqueo permanente.

No se modificó el motor ni se generó APK en este análisis. No se incluyen identidad, números de serie, PIN/CAN ni criptogramas privados.
