package pe.gob.reniec.dnie.core.nfc

/** The observed empty VERIFY query is separate from certificate selection and PIN authentication. */
internal object PlainPinStatusQuery {
    private val query = byteArrayOf(0, 0x20, 0, 3)
    fun transmit(bytes: ByteArray, logical: (ByteArray) -> ByteArray): ByteArray? {
        if (!bytes.contentEquals(query)) return null
        return logical(bytes).also { require(it.size >= 2) }
    }
}
