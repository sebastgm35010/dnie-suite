package pe.gob.reniec.dnie.icao

import java.nio.ByteBuffer

object IcaoParser {
    fun parse(file: IcaoFile, bytes: ByteArray): IcaoContent {
        val value = BerTlv.value(bytes, file.tag)
        return when (file) {
            IcaoFile.COM -> {
                val fields = BerTlv.nodes(value)
                val tags = fields.single { it.tag == 0x5C }.value
                IcaoContent.Inventory(BerTlv.tags(tags), fields.firstOrNull { it.tag == 0x5F01 }?.value?.toString(Charsets.US_ASCII))
            }
            IcaoFile.DG1 -> MrzParser.parse(BerTlv.nodes(value).single { it.tag == 0x5F1F }.value)
            IcaoFile.DG2 -> parseFace(value)
            IcaoFile.DG5, IcaoFile.DG7 -> {
                val imageTag = if (file == IcaoFile.DG7) 0x5F43 else 0x5F40
                val images = BerTlv.nodes(value).filter { it.tag == imageTag }.map { image(it.value) }
                require(images.isNotEmpty()) { "Bloque sin imágenes reconocibles" }
                IcaoContent.Images(images)
            }
            IcaoFile.DG11, IcaoFile.DG12 -> parseDetails(value)
            IcaoFile.SOD -> SodVerifier.parse(value)
            IcaoFile.DG13 -> IcaoContent.Opaque(bytes.size)
        }
    }

    private fun parseDetails(value: ByteArray): IcaoContent.Details {
        val labels = mapOf(
            0x5F0E to "Nombre completo", 0x5F10 to "Número personal", 0x5F2B to "Fecha de nacimiento completa",
            0x5F11 to "Lugar de nacimiento", 0x5F42 to "Domicilio", 0x5F12 to "Teléfono",
            0x5F13 to "Profesión", 0x5F14 to "Título", 0x5F15 to "Resumen personal",
            0x5F17 to "Otros números de documento", 0x5F18 to "Custodia",
            0x5F19 to "Autoridad emisora", 0x5F26 to "Fecha de expedición",
            0x5F1A to "Otras personas en el documento", 0x5F1B to "Observaciones",
            0x5F1C to "Restricciones de salida", 0x5F55 to "Fecha de personalización",
            0x5F56 to "Sistema de personalización"
        )
        val result = linkedMapOf<String, String>()
        fun add(nodes: List<BerTlv.Node>, depth: Int = 0) {
            require(depth < 8) { "Anidamiento excesivo en datos personales" }
            nodes.forEach { n ->
                when {
                    n.tag == 0x5C || n.tag == 0x02 -> Unit
                    n.tag == 0xA0 -> add(BerTlv.nodes(n.value), depth + 1)
                    n.tag in labels -> {
                        val text = if (n.tag == 0x5F2B || n.tag == 0x5F26 || n.tag == 0x5F55) {
                            if (n.value.all { (it.toInt() and 255) in 0x30..0x39 }) n.value.toString(Charsets.US_ASCII)
                            else n.value.joinToString("") { "%02X".format(it) }
                        } else n.value.toString(Charsets.UTF_8).replace('<', ' ').trim()
                        val label = labels.getValue(n.tag)
                        result[label] = listOfNotNull(result[label], text).joinToString(" · ")
                    }
                    else -> result["Campo %X".format(n.tag)] = "${n.value.size} bytes (contenido binario)"
                }
            }
        }
        add(BerTlv.nodes(value))
        return IcaoContent.Details(result)
    }

    private fun parseFace(value: ByteArray): IcaoContent.Images {
        val records = mutableListOf<ByteArray>()
        fun visit(data: ByteArray, depth: Int = 0) {
            require(depth < 8) { "Anidamiento biométrico excesivo" }
            BerTlv.nodes(data).forEach { n ->
                when (n.tag) {
                    0x5F2E, 0x7F2E -> records += n.value
                    0x7F61, 0x7F60 -> visit(n.value, depth + 1)
                }
            }
        }
        visit(value)
        val images = records.flatMap { record ->
            require(record.size >= 14 && record.copyOfRange(0, 4).contentEquals(byteArrayOf(0x46, 0x41, 0x43, 0))) {
                "Formato facial no reconocido (se esperaba ISO 19794-5 FAC)"
            }
            val b = ByteBuffer.wrap(record)
            b.position(8)
            val length = b.int
            val count = b.short.toInt() and 65535
            require(length == record.size && count in 1..9) { "Registro facial truncado o inválido" }
            val out = mutableListOf<IcaoImage>()
            repeat(count) {
                val start = b.position()
                require(b.remaining() >= 32) { "Cabecera facial truncada" }
                val blockLength = b.int
                val features = b.short.toInt() and 65535
                val imageOffset = start + 20 + features * 8
                require(blockLength >= 32 + features * 8 && blockLength <= record.size - start) { "Longitud facial inválida" }
                b.position(imageOffset)
                b.get() // Face image type
                val format = b.get().toInt() and 255
                val width = b.short.toInt() and 65535
                val height = b.short.toInt() and 65535
                require(format in 0..1 && width > 0 && height > 0) { "Formato o dimensiones faciales inválidas" }
                val payload = record.copyOfRange(imageOffset + 12, start + blockLength)
                val img = image(payload, width, height)
                require(img.mimeType == if (format == 0) "image/jpeg" else "image/jp2") { "Formato facial inconsistente" }
                out += img
                b.position(start + blockLength)
            }
            require(b.position() == record.size) { "Datos extra en registro facial" }
            out
        }
        require(images.isNotEmpty()) { "DG2 sin registros faciales reconocibles" }
        return IcaoContent.Images(images)
    }

    private fun image(bytes: ByteArray, width: Int? = null, height: Int? = null): IcaoImage {
        require(bytes.size >= 4) { "Imagen truncada" }
        val mime = when {
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
            bytes[0] == 0xFF.toByte() && bytes[1] == 0x4F.toByte() && bytes[2] == 0xFF.toByte() && bytes[3] == 0x51.toByte() -> "image/jp2"
            bytes.size >= 12 && bytes.copyOfRange(0, 12).contentEquals(byteArrayOf(0, 0, 0, 12, 0x6A, 0x50, 0x20, 0x20, 13, 10, -121, 10)) -> "image/jp2"
            else -> error("Codificación de imagen no reconocida")
        }
        return IcaoImage(bytes, mime, width, height)
    }
}
