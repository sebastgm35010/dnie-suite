package pe.gob.reniec.dnie.bridge

/** PC/SC Part 3, ISO 14443-A: map the real ATS historical bytes, with T=1 and TCK. */
object ContactlessAtr {
    fun fromTypeAHistory(history: ByteArray): ByteArray {
        require(history.size <= 15)
        val atr = ByteArray(history.size + 5)
        atr[0] = 0x3b
        atr[1] = (0x80 or history.size).toByte()
        atr[2] = 0x80.toByte()
        atr[3] = 0x01
        history.copyInto(atr, 4)
        var checksum = 0
        for (index in 1 until atr.lastIndex) checksum = checksum xor (atr[index].toInt() and 255)
        atr[atr.lastIndex] = checksum.toByte()
        return atr
    }
}
