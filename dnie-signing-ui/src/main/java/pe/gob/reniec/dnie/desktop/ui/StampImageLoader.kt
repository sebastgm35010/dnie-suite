package pe.gob.reniec.dnie.desktop.ui

import java.nio.file.*
import java.awt.image.BufferedImage
import java.io.*
import javax.imageio.ImageIO

/** Validates PNG metadata before allocating a raster; invoked outside the EDT. */
object StampImageLoader {
    fun load(path: Path,clearWhite: Boolean): ByteArray {
        require(Files.size(path) in 1..512L*1024) {"PNG máximo 512 KiB"}
        val bytes=Files.newInputStream(path).use {it.readNBytes(512*1024+1)}
        require(bytes.size<=512*1024 && bytes.take(8).toByteArray().contentEquals(byteArrayOf(-119,80,78,71,13,10,26,10))) {"Elige un PNG válido"}
        val image=javax.imageio.stream.MemoryCacheImageInputStream(ByteArrayInputStream(bytes)).use {stream->
            val reader=ImageIO.getImageReaders(stream).asSequence().firstOrNull()?:error("PNG no compatible")
            try {
                reader.input=stream
                val w=reader.getWidth(0);val h=reader.getHeight(0)
                require(w in 1..4096 && h in 1..4096 && w.toLong()*h<=4_194_304) {"Imagen máxima 4 megapíxeles"}
                reader.read(0)
            } finally {reader.dispose()}
        }
        try {
            val rgba=BufferedImage(image.width,image.height,BufferedImage.TYPE_INT_ARGB)
            try {
                for(y in 0 until image.height) for(x in 0 until image.width) {
                    val pixel=image.getRGB(x,y)
                    rgba.setRGB(x,y,if(clearWhite && (pixel shr 16 and 255)>235 && (pixel shr 8 and 255)>235 && (pixel and 255)>235) pixel and 0xFFFFFF else pixel)
                }
                val out=ByteArrayOutputStream();check(ImageIO.write(rgba,"png",out));require(out.size()<=512*1024) {"PNG procesado mayor de 512 KiB"};return out.toByteArray()
            } finally {rgba.flush()}
        } finally {image.flush()}
    }
}
