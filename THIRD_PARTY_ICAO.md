# Dependencias incorporadas para ICAO

El procesamiento BER/MRZ/LDS se implementa en el módulo JVM local y utiliza BouncyCastle, que ya formaba parte del proyecto. La visualización JPEG utiliza Android. JPEG2000 incorpora:

- Artefacto: `com.github.Tgo1014:JP2ForAndroid:1.0.4`.
- Procedencia: https://github.com/Tgo1014/JP2ForAndroid, fork de https://github.com/ThalesGroup/JP2ForAndroid.
- Licencia: BSD de dos cláusulas, copyright 2018 Gemalto s.r.o.
- Motor nativo: OpenJPEG 2.4.0, https://github.com/uclouvain/openjpeg/tree/v2.4.0, licencia BSD de dos cláusulas con los titulares enumerados en su LICENSE.

Los textos completos de ambas licencias se conservan y distribuyen dentro del APK en `app/src/main/assets/third-party/JP2ForAndroid-LICENSE.txt` y `OpenJPEG-LICENSE.txt`. No se copió código ni binarios de Certezia o ReadID a la app.
