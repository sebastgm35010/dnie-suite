package pe.gob.reniec.dnie.desktop.ui

import io.nayuki.qrcodegen.QrCode
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.nio.file.Files
import java.nio.file.Path
import pe.gob.reniec.dnie.signing.link.SuitePairing

/** Local presentation only. Never writes the pairing token to an image file. */
internal class PairingQrImage(val image: BufferedImage, val fingerprint: String) : AutoCloseable {
    override fun close() {
        (image.raster.dataBuffer as DataBufferInt).data.fill(0xFFFFFF)
        image.flush()
    }
    companion object {
        fun read(file: Path, expectedFingerprint: String): PairingQrImage {
            val bytes=Files.newInputStream(file).use { it.readNBytes(1025) }
            try {
                require(bytes.size in 1..1024)
                return SuitePairing.parse(String(bytes, Charsets.UTF_8)).use { pairing ->
                    require(pairing.fingerprintText().equals(expectedFingerprint, ignoreCase=true))
                    val code=QrCode.encodeText(pairing.encode(), QrCode.Ecc.MEDIUM)
                    val border=4; val scale=6; val side=(code.size+2*border)*scale
                    val image=BufferedImage(side,side,BufferedImage.TYPE_INT_RGB)
                    for(y in 0 until side) for(x in 0 until side)
                        image.setRGB(x,y,if(code.getModule(x/scale-border,y/scale-border)) 0 else 0xFFFFFF)
                    PairingQrImage(image,pairing.fingerprintText())
                }
            } finally { bytes.fill(0) }
        }
    }
}
