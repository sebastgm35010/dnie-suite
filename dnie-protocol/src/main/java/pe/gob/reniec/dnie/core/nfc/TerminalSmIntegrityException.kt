package pe.gob.reniec.dnie.core.nfc

/** Public counters and random context ID only. Never contains keys, tokens or APDU data. */
class TerminalSmIntegrityException(
    val contextId: String,
    val expectedSsc: Long?,
    val matchingDiagnosticSsc: List<Long>
) : IllegalArgumentException("Terminal SM integrity failure")
