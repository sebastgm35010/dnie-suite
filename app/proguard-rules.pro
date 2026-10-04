# ==============================================================================
# Reglas R8 / ProGuard para DNIe Signer Perú (pe.gob.reniec.dnie)
# ==============================================================================

# --- Kotlin Coroutines ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# --- BouncyCastle Provider & PKIX ---
# Conservar proveedores JCA/JCE y registradores dinámicos
-keep class org.bouncycastle.jce.provider.BouncyCastleProvider { *; }
-keep class org.bouncycastle.jcajce.provider.** { *; }
-keep class org.bouncycastle.asn1.** { *; }
-keep class org.bouncycastle.cert.** { *; }
-keep class org.bouncycastle.cms.** { *; }
-keep class org.bouncycastle.operator.** { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Suprimir advertencias de dependencias opcionales de BouncyCastle
-dontwarn javax.naming.**
-dontwarn javax.crypto.**

# --- PDFBox Android (com.tom-roush:pdfbox-android) ---
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn com.tom_roush.fontbox.**

# Conservar recursos de fuentes y mapas de caracteres de PDFBox
-keepclassmembers class * extends java.lang.Enum {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep attributes for annotations and reflection
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# --- Android NFC & Hardware ---
-keepclassmembers class android.nfc.Tag { *; }
-keepclassmembers class android.nfc.tech.IsoDep { *; }

# --- Modelos de Dominio, APDU y Estado UI ---
-keep class pe.gob.reniec.dnie.domain.model.** { *; }
-keep class pe.gob.reniec.dnie.core.apdu.** { *; }
-keep class pe.gob.reniec.dnie.ui.**$UiState { *; }
