# Seleccion equivocada confirmada en Java real de Ciudadano Digital

6 octubre2026. Observacion PCSC del proceso real, sin filtro, orden global cambiado ni sustitucion de respuestas. Frida observa APIs originales; child gating introduce una pausa de arranque que debe separarse del tiempo de tarjeta.

## Evidencia decisiva

Captura privada tailnet-reader-observation-d43a71a585d74b6386acebc1155fa2bb.

Proceso.NET37840:
- 23:14:26.758 UTC: SCardListReadersW devuelve puente index0, virtualPCD index1, WindowsHello index2.
- 23:14:26.759: SCardConnectW solicita DNIe Bridge Android NFC Private Reader0; retorno0 (exito).
- Host muestra SELECT v2 con6A82 y SELECTv3 con9000 antes de power-off23:14:27.191; detecta correctamente el DNIv3.

HijoJava65936, parent37840:
- 23:14:28.608: SCardListReadersA devuelve los mismos3lectores y orden.
- 23:14:28.614: SCardGetStatusChangeA devuelve puente presente, virtualPCD ausente, WindowsHello presente.
- 23:14:28.670: SCardConnectA solicita Windows Hello for Business1, no el puente.
- 23:14:28.672: conexion aHello devuelve0 (exito).

El usuario observa luego «Al parecer no has conectado tu DNIe de la manera correcta…». El Java realmente seleccionoHello: ya no es una inferencia basada en un proceso auxiliar. Coincide con el bucle local inspeccionado que conserva ultimo CARD_PRESENT. No demuestra fallo de reconocimiento del DNI en la etapa.NET.

## Demora de instrumentacion

Hubo dos procesos.NET observados. El primero9288 fue visto23:13:19.766 y quedo instrumentado23:13:36.665:16,899s de arranque retenido. Intento conectar al puente y recibio0x80100069 (tarjeta retirada/ausente); no llego a observarse hijoJava en ese intento.

Segundo.NET37840:44ms de child_seen aattached. Java65936:272ms. Por tanto la demora adicional de la primera instrumentacion no debe atribuirse al motor NFC ni a lectura lenta. La captura modifica temporalmente tiempos de inicio, no el orden ni lector elegido.

## Puente y cierre

Una sesion NFC capturada,22solicitudes/22respuestas. Monitor reason=session_completed, attributionReady=true, collectorsStopped=true; host retorna a espera. EOF final por si solo no atribuye cierre manual sin diagnostico movil de esta sesion. No se necesita ese diagnostico para demostrar la seleccionJava, ya observada directamente.

Stop.txt solicitado en raiz para cerrar captura navegador y desprender Frida de los procesos restantes. No cambio permanente de configuracion Java/Windows ni nuevoAPK.

## Siguiente paso

Aplicar el selector propio por proceso al lanzamiento real Java de Ciudadano Digital, conservando argumentos de activacion, salida esperada y notificacion oficial. Lanzador aislado ya obtuvo identidad/certificados en Android18, pero falta integrar ese contrato ClickOnce. No reordenar lectores globalmente ni desactivarHello. En una nueva prueba registrar SCardConnect del Java al puente y resultado final de la web.
