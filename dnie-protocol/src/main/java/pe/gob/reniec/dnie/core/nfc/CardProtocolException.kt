package pe.gob.reniec.dnie.core.nfc

/**
 * Excepción base para errores a nivel de protocolo APDU y comunicación con el chip DNIe.
 */
open class CardProtocolException(
    message: String,
    val statusWord: Int? = null,
    cause: Throwable? = null
) : Exception(
    if (statusWord != null) "$message [SW: %04X]".format(statusWord) else message,
    cause
)

/**
 * Lanzada cuando el AID del Applet PKI de RENIEC no se encuentra o la tarjeta no es un DNIe válido (SW: 6A82).
 */
class AppletNotFoundException(
    message: String = "El Applet PKI de RENIEC no fue encontrado en la tarjeta (tarjeta no compatible o no es DNIe)",
    statusWord: Int = 0x6A82
) : CardProtocolException(message, statusWord)

/**
 * Lanzada ante un fallo en la verificación de PIN (SW: 63CX u otro error de autenticación).
 */
class PinVerificationException(
    val attemptsRemaining: Int? = null,
    message: String = if (attemptsRemaining != null) "PIN incorrecto. Intentos restantes: $attemptsRemaining" else "Error al verificar PIN",
    statusWord: Int? = null
) : CardProtocolException(message, statusWord)

/**
 * Lanzada cuando el método de verificación de PIN está permanentemente bloqueado (SW: 6983 o intentos agotados).
 */
class CardBlockedException(
    message: String = "El chip del DNIe se encuentra bloqueado por exceder el número máximo de reintentos de PIN. Acuda a una agencia de RENIEC.",
    statusWord: Int = 0x6983
) : CardProtocolException(message, statusWord)

/**
 * Lanzada cuando una operación criptográfica en hardware (MSE o PSO CDS) falla.
 */
class CryptoOperationException(
    message: String,
    statusWord: Int? = null,
    cause: Throwable? = null
) : CardProtocolException(message, statusWord, cause)

/**
 * Lanzada cuando la tarjeta se desconecta físicamente del campo RF durante una transacción (TagLostException).
 */
class CardDisconnectedException(
    message: String = "La tarjeta DNIe fue retirada del campo NFC antes de completar la operación",
    cause: Throwable? = null
) : CardProtocolException(message, null, cause)

/**
 * Lanzada ante errores de lectura o desensamblado de los certificados elementales (EF).
 */
class CertificateReadException(
    message: String,
    statusWord: Int? = null,
    cause: Throwable? = null
) : CardProtocolException(message, statusWord, cause)

/** Integrity loss invalidates the whole physical scan, including subsequent independent branches. */
class SecureMessagingIntegrityException(message: String) : CardProtocolException(message)
