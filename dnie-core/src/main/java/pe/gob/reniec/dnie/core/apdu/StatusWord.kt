package pe.gob.reniec.dnie.core.apdu

/**
 * Representa los códigos de estado (Status Words SW1-SW2) estándar ISO 7816-4 y propietarios
 * para el chip del DNIe Peruano v2/v3.
 */
sealed class StatusWord(
    val code: Int,
    val description: String
) {
    val sw1: Int get() = (code ushr 8) and 0xFF
    val sw2: Int get() = code and 0xFF

    open val isSuccess: Boolean get() = code == SW_SUCCESS
    open val isWarning: Boolean get() = sw1 == 0x62 || sw1 == 0x63
    open val isError: Boolean get() = !isSuccess && !isWarning

    fun toHexString(): String = "%04X".format(code)

    override fun toString(): String = "SW: %s - %s".format(toHexString(), description)

    // --- Instancias canónicas ---

    /** 0x9000: Operación exitosa */
    object Ok : StatusWord(SW_SUCCESS, "Operación completada exitosamente (OK)")

    /** 0x6282: Fin de archivo alcanzado antes de agotar Le */
    object EofReached : StatusWord(SW_EOF_REACHED, "Fin de archivo alcanzado (End of file reached)")

    /** 0x63C0 .. 0x63CF: Contador de reintentos de PIN */
    data class PinRetryCounter(val attemptsRemaining: Int) : StatusWord(
        SW_PIN_RETRY_BASE or (attemptsRemaining and 0x0F),
        "PIN incorrecto. Intentos restantes en tarjeta: $attemptsRemaining"
    )

    /** 0x6700: Longitud incorrecta Lc o Le */
    object WrongLength : StatusWord(SW_WRONG_LENGTH, "Longitud de datos incorrecta (Wrong length Lc/Le)")

    /** 0x6982: Estado de seguridad no satisfecho (PIN no validado o MSE no ejecutado) */
    object SecurityStatusNotSatisfied : StatusWord(
        SW_SECURITY_STATUS_NOT_SATISFIED,
        "Estado de seguridad no satisfecho (Security status not satisfied)"
    )

    /** 0x6983: Autenticación bloqueada permanentemente (tarjeta bloqueada) */
    object AuthenticationBlocked : StatusWord(
        SW_AUTHENTICATION_BLOCKED,
        "Método de autenticación bloqueado permanentemente (Tarjeta bloqueada, requiere PUK en RENIEC)"
    )

    /** 0x6985: Condiciones de uso no satisfechas */
    object ConditionsOfUseNotSatisfied : StatusWord(
        SW_CONDITIONS_NOT_SATISFIED,
        "Condiciones de uso no satisfechas (Conditions of use not satisfied)"
    )

    /** 0x6A80: Parámetros incorrectos en el campo de datos / Datos no válidos */
    object IncorrectParametersInDataField : StatusWord(
        SW_INCORRECT_PARAMETERS_IN_DATA_FIELD,
        "Parámetros incorrectos en el campo de datos / Offset excede EF (Wrong parameters in data field)"
    )

    /** 0x6A82: Archivo o Applet no encontrado */
    object FileNotFound : StatusWord(
        SW_FILE_NOT_FOUND,
        "Archivo o Applet no encontrado (File or application not found)"
    )

    /** 0x6A86: Parámetros P1-P2 incorrectos */
    object IncorrectParametersP1P2 : StatusWord(
        SW_INCORRECT_P1_P2,
        "Parámetros P1 o P2 incorrectos (Incorrect parameters P1-P2)"
    )

    /** 0x6A88: Datos referenciados no encontrados */
    object ReferencedDataNotFound : StatusWord(
        SW_REFERENCED_DATA_NOT_FOUND,
        "Datos de referencia no encontrados (Referenced data not found)"
    )

    /** 0x6B00: Offset fuera del rango del archivo */
    object WrongOffset : StatusWord(
        SW_WRONG_OFFSET,
        "Offset fuera del límite del archivo elemental (Wrong parameter / Offset outside EF)"
    )

    /** 0x6D00: Instrucción INS no soportada */
    object InstructionNotSupported : StatusWord(
        SW_INS_NOT_SUPPORTED,
        "Instrucción INS no soportada o inválida (Instruction code not supported)"
    )

    /** 0x6E00: Clase CLA no soportada */
    object ClassNotSupported : StatusWord(
        SW_CLASS_NOT_SUPPORTED,
        "Clase CLA no soportada o inválida (Class not supported)"
    )

    /** Estado desconocido o no categorizado específicamente */
    class Unknown(code: Int, customDesc: String? = null) : StatusWord(
        code,
        customDesc ?: "Código de estado no reconocido: %04X".format(code)
    )

    companion object {
        const val SW_SUCCESS = 0x9000
        const val SW_EOF_REACHED = 0x6282
        const val SW_PIN_RETRY_BASE = 0x63C0
        const val SW_WRONG_LENGTH = 0x6700
        const val SW_SECURITY_STATUS_NOT_SATISFIED = 0x6982
        const val SW_AUTHENTICATION_BLOCKED = 0x6983
        const val SW_CONDITIONS_NOT_SATISFIED = 0x6985
        const val SW_INCORRECT_PARAMETERS_IN_DATA_FIELD = 0x6A80
        const val SW_FILE_NOT_FOUND = 0x6A82
        const val SW_INCORRECT_P1_P2 = 0x6A86
        const val SW_REFERENCED_DATA_NOT_FOUND = 0x6A88
        const val SW_WRONG_OFFSET = 0x6B00
        const val SW_INS_NOT_SUPPORTED = 0x6D00
        const val SW_CLASS_NOT_SUPPORTED = 0x6E00

        /**
         * Mapea un entero de 16 bits (SW1:SW2) al objeto [StatusWord] correspondiente.
         */
        fun fromSw(sw: Int): StatusWord {
            val normalizedSw = sw and 0xFFFF
            return when {
                normalizedSw == SW_SUCCESS -> Ok
                normalizedSw == SW_EOF_REACHED -> EofReached
                (normalizedSw and 0xFFF0) == SW_PIN_RETRY_BASE -> {
                    val remaining = normalizedSw and 0x0F
                    PinRetryCounter(remaining)
                }
                normalizedSw == SW_WRONG_LENGTH -> WrongLength
                normalizedSw == SW_SECURITY_STATUS_NOT_SATISFIED -> SecurityStatusNotSatisfied
                normalizedSw == SW_AUTHENTICATION_BLOCKED -> AuthenticationBlocked
                normalizedSw == SW_CONDITIONS_NOT_SATISFIED -> ConditionsOfUseNotSatisfied
                normalizedSw == SW_INCORRECT_PARAMETERS_IN_DATA_FIELD -> IncorrectParametersInDataField
                normalizedSw == SW_FILE_NOT_FOUND -> FileNotFound
                normalizedSw == SW_INCORRECT_P1_P2 -> IncorrectParametersP1P2
                normalizedSw == SW_REFERENCED_DATA_NOT_FOUND -> ReferencedDataNotFound
                normalizedSw == SW_WRONG_OFFSET -> WrongOffset
                normalizedSw == SW_INS_NOT_SUPPORTED -> InstructionNotSupported
                normalizedSw == SW_CLASS_NOT_SUPPORTED -> ClassNotSupported
                else -> Unknown(normalizedSw)
            }
        }

        fun fromBytes(sw1: Byte, sw2: Byte): StatusWord {
            val sw = ((sw1.toInt() and 0xFF) shl 8) or (sw2.toInt() and 0xFF)
            return fromSw(sw)
        }
    }
}
