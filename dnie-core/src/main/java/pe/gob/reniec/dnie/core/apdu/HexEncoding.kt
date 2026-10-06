package pe.gob.reniec.dnie.core.apdu

/** Avoids constructing a Formatter for every byte in diagnostic traffic. */
fun encodeHex(bytes: ByteArray, separator: String = " "): String {
    val digits = "0123456789ABCDEF"
    val output = StringBuilder(bytes.size * 2 + maxOf(0, bytes.size - 1) * separator.length)
    bytes.forEachIndexed { index, byte ->
        if (index != 0) output.append(separator)
        val value = byte.toInt() and 255
        output.append(digits[value ushr 4]).append(digits[value and 15])
    }
    return output.toString()
}
