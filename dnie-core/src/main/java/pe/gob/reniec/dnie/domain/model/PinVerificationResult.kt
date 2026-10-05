package pe.gob.reniec.dnie.domain.model

/**
 * Resultado de la operación de verificación de PIN en tarjeta del DNIe.
 */
sealed class PinVerificationResult {

    abstract val isSuccess: Boolean

    /**
     * El PIN fue verificado correctamente por la tarjeta (SW: 90 00).
     * El estado de seguridad queda habilitado para operaciones criptográficas.
     */
    object Success : PinVerificationResult() {
        override val isSuccess: Boolean = true
        override fun toString(): String = "Success"
    }

    /**
     * El PIN ingresado no coincide con el registrado en el chip (SW: 63 CX).
     * @property attemptsRemaining Número exacto de intentos restantes en la memoria segura.
     */
    data class InvalidPin(val attemptsRemaining: Int) : PinVerificationResult() {
        override val isSuccess: Boolean = false
        val isCritical: Boolean get() = attemptsRemaining == 1
    }

    /**
     * El método de autenticación está bloqueado (SW: 69 83 o intentos restantes = 0).
     * El ciudadano debe acudir a RENIEC para desbloqueo con PUK.
     */
    object CardBlocked : PinVerificationResult() {
        override val isSuccess: Boolean = false
        override fun toString(): String = "CardBlocked"
    }
}
