package pe.gob.reniec.dnie.icao

/** Bounded definite-length BER, shared by LDS and odd READ BINARY responses. */
object BerTlv {
    const val MAX_FILE_BYTES = 1024 * 1024
    data class Header(val tag: Int, val size: Int, val length: Int) {
        val total: Int get() = size + length
    }
    data class Node(val tag: Int, val value: ByteArray)

    fun header(bytes: ByteArray, start: Int = 0): Header? {
        require(start in 0..bytes.size)
        if (start == bytes.size) return null
        var p = start
        val first = bytes[p++].toInt() and 255
        var tag = first
        if (first and 31 == 31) {
            var count = 0
            do {
                if (p == bytes.size) return null
                val b = bytes[p++].toInt() and 255
                require(++count <= 2) { "Tag BER demasiado largo" }
                tag = (tag shl 8) or b
            } while (b and 128 != 0)
        }
        if (p == bytes.size) return null
        val firstLength = bytes[p++].toInt() and 255
        val length = if (firstLength < 128) firstLength else {
            val count = firstLength and 127
            require(count in 1..3) { "Longitud BER indefinida o fuera de límites" }
            if (bytes.size - p < count) return null
            var n = 0
            repeat(count) { n = (n shl 8) or (bytes[p++].toInt() and 255) }
            n
        }
        require(length <= MAX_FILE_BYTES - (p - start)) { "Archivo ICAO demasiado grande" }
        return Header(tag, p - start, length)
    }

    fun nodes(bytes: ByteArray): List<Node> {
        val out = mutableListOf<Node>()
        var p = 0
        while (p < bytes.size) {
            require(out.size < 512) { "Demasiados objetos BER" }
            val h = requireNotNull(header(bytes, p)) { "Cabecera BER truncada" }
            require(h.total <= bytes.size - p) { "Contenido BER truncado" }
            out += Node(h.tag, bytes.copyOfRange(p + h.size, p + h.total))
            p += h.total
        }
        return out
    }

    fun value(bytes: ByteArray, expectedTag: Int): ByteArray {
        val parsed = nodes(bytes)
        require(parsed.size == 1) { "Se esperaba un único objeto BER" }
        val node = parsed.first()
        require(node.tag == expectedTag) { "Tag ICAO inesperado: %X".format(node.tag) }
        return node.value
    }

    fun tags(bytes: ByteArray): Set<Int> {
        val out = mutableSetOf<Int>()
        var p = 0
        while (p < bytes.size) {
            var tag = bytes[p++].toInt() and 255
            if (tag and 31 == 31) {
                var count = 0
                do {
                    require(p < bytes.size && ++count <= 2) { "Lista de tags truncada" }
                    val b = bytes[p++].toInt() and 255
                    tag = (tag shl 8) or b
                } while (b and 128 != 0)
            }
            out += tag
        }
        return out
    }
}
