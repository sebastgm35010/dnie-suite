package pe.gob.reniec.dnie.core.nfc

/** Failure while establishing an authenticated PACE session. */
class PaceException(message: String, statusWord: Int? = null) : CardProtocolException(message, statusWord)
