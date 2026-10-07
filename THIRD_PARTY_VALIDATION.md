# Dependencias de validación H9

- Apache Santuario XML Security 2.3.5: Apache-2.0. Se usa únicamente Canonicalizer C14N 1.0. No se usa resolución XMLDSIG de red, JSR105, XPath/XSLT ni motor StAX. Fuente https://santuario.apache.org/ y Maven org.apache.santuario:xmlsec:2.3.5.
- SLF4J API + NOP 1.7.36: MIT. Fuente https://www.slf4j.org/license.html. Backend NOP sin salida para Santuario; no se registran certificados, documentos o credenciales.
- Commons Codec 1.17.1: Apache-2.0, dependencia de Santuario. Fuente https://commons.apache.org/proper/commons-codec/.
- Woodstox/StAX externo y JAXB no incorporados. Canonicalización opera sobre DOM del runtime Android/JVM.
- Bouncy Castle bcpkix/bcprov/bcutil 1.78.1 y JCA ya existentes, no se cambia su versión.

Licencias y avisos de Santuario, Commons Codec y SLF4J se conservan en app/src/main/assets/third-party. Certificado público AAC publicado por INDECOPI se incluye como dato de confianza; procedencia, hash y política de rotación en docs/suite/CONFIANZA_TSL_CADENA_H9.md. No incluye claves privadas ni certificados del titular.
