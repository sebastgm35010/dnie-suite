package pe.gob.reniec.dnie.passkeys

/** Envelope check only. The platform hybrid authenticator validates the actual caBLE message. */
object FidoQrPayload {
    fun validate(value: String): String {
        require(value.startsWith("FIDO:/") && value.length in 7..4096)
        require(value.substring(6).all { it in '0'..'9' })
        return value
    }
}
