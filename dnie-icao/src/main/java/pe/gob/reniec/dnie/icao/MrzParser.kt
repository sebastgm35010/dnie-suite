package pe.gob.reniec.dnie.icao

object MrzParser {
    fun parse(bytes: ByteArray): IcaoContent.Identity {
        val mrz = bytes.toString(Charsets.US_ASCII).replace("\r", "").replace("\n", "")
        require(mrz.all { it in 'A'..'Z' || it in '0'..'9' || it == '<' }) { "MRZ contiene caracteres inválidos" }
        val lines = when (mrz.length) {
            90 -> mrz.chunked(30)
            72 -> mrz.chunked(36)
            88 -> mrz.chunked(44)
            else -> error("Formato MRZ no soportado (${mrz.length} caracteres)")
        }
        val a = lines[0]
        val b = lines[1]
        val checks = linkedMapOf<String, Boolean>()
        val number: String
        val birth: String
        val sex: String
        val expiry: String
        val nationality: String
        val optional: String
        val names: String
        if (lines.size == 3) {
            val baseNumber = a.substring(5, 14)
            val continuation = a.substring(15, 30).trimEnd('<')
            if (a[14] == '<' && continuation.isNotEmpty()) {
                number = baseNumber + continuation.dropLast(1)
                checks["Número de documento"] = check(number, continuation.last())
            } else {
                number = baseNumber
                checks["Número de documento"] = check(number, a[14])
            }
            birth = b.substring(0, 6)
            sex = b.substring(7, 8)
            expiry = b.substring(8, 14)
            nationality = b.substring(15, 18)
            optional = a.substring(15, 30) + b.substring(18, 29)
            names = lines[2]
            checks["Fecha de nacimiento"] = check(birth, b[6])
            checks["Vencimiento"] = check(expiry, b[14])
            checks["Compuesto"] = check(a.substring(5, 30) + b.substring(0, 7) + b.substring(8, 15) + b.substring(18, 29), b[29])
        } else {
            number = b.substring(0, 9)
            nationality = b.substring(10, 13)
            birth = b.substring(13, 19)
            sex = b.substring(20, 21)
            expiry = b.substring(21, 27)
            names = a.substring(5)
            optional = if (b.length == 44) b.substring(28, 42) else b.substring(28, 35)
            checks["Número de documento"] = check(number, b[9])
            checks["Fecha de nacimiento"] = check(birth, b[19])
            checks["Vencimiento"] = check(expiry, b[27])
            if (b.length == 44) {
                checks["Datos opcionales"] = check(optional, b[42])
                checks["Compuesto"] = check(b.substring(0, 10) + b.substring(13, 20) + b.substring(21, 43), b[43])
            } else checks["Compuesto"] = check(b.substring(0, 10) + b.substring(13, 20) + b.substring(21, 35), b[35])
        }
        val split = names.split("<<", limit = 2)
        return IcaoContent.Identity(clean(a.substring(0, 2)), clean(a.substring(2, 5)), clean(number),
            clean(split[0]), clean(split.getOrElse(1) { "" }), clean(nationality), birth, clean(sex), expiry,
            clean(optional), checks, lines.joinToString("\n"))
    }

    private fun clean(s: String) = s.replace('<', ' ').trim().replace(Regex(" +"), " ")
    private fun check(s: String, digit: Char): Boolean {
        val weights = intArrayOf(7, 3, 1)
        val sum = s.mapIndexed { i, c ->
            val value = when (c) { '<' -> 0; in '0'..'9' -> c - '0'; else -> c - 'A' + 10 }
            value * weights[i % 3]
        }.sum()
        return (digit in '0'..'9' && sum % 10 == digit - '0') || (digit == '<' && sum % 10 == 0)
    }
}
