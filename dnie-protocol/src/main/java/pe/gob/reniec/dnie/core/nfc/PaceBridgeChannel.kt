package pe.gob.reniec.dnie.core.nfc

import pe.gob.reniec.dnie.core.apdu.CommandApdu
import pe.gob.reniec.dnie.core.apdu.ResponseApdu
import pe.gob.reniec.dnie.core.logging.ApduLogger

/** Fresh PKI-only session for contact adaptation; never shares an ICAO/signing session. */
object PaceBridgeChannel {
    fun open(physical: DnieCardChannel, can: String): DnieCardChannel {
        val quiet = ApduLogger(enabled = false)
        var secure: PaceSecureChannel? = null
        try {
            require(can.length == 6 && can.all { it in '0'..'9' })
            val success = V3PkiSession.open(can, { command: CommandApdu, _: String ->
                ResponseApdu.fromBytes((secure ?: physical).transceive(command.toByteArray()))
            }, { profile ->
                secure = PaceHandler(quiet).performPace(physical, can, profileSecurityInfo = profile)
            })
            check(success) { "PKI bridge requires DNIe v3" }
            return checkNotNull(secure)
        } catch (failure: Exception) {
            physical.close()
            throw IllegalStateException("PKI bridge could not open", failure)
        }
    }
}
