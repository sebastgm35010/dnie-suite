package pe.gob.reniec.dnie.desktop.ui

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.nio.file.Files
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pe.gob.reniec.dnie.signing.link.SuitePairing

class PairingQrImageTest {
    @get:Rule val folder=TemporaryFolder()
    private fun pairing()=SuitePairing(UUID.randomUUID(),"192.168.1.5",ByteArray(32){0x44},ByteArray(32){0x55})
    @Test fun qrDecodesToExactAndroidPairingAndCanBeOpenedRepeatedlyWithoutPng() {
        pairing().use { pairing ->
            val file=folder.newFile("suite-emparejamiento.txt").toPath()
            Files.writeString(file,pairing.encode())
            repeat(2) {
                PairingQrImage.read(file,pairing.fingerprintText()).use { preview ->
                    val image=preview.image
                    val pixels=image.getRGB(0,0,image.width,image.height,null,0,image.width)
                    val decoded=QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(image.width,image.height,pixels))))
                    assertEquals(pairing.encode(),decoded.text)
                    assertEquals(pairing.fingerprintText(),preview.fingerprint)
                }
            }
            assertEquals(listOf(file.fileName.toString()),folder.root.listFiles()!!.map { it.name })
        }
    }
    @Test fun closingErasesRenderedPixels() {
        pairing().use { pairing ->
            val file=folder.newFile().toPath();Files.writeString(file,pairing.encode())
            val preview=PairingQrImage.read(file,pairing.fingerprintText());preview.close()
            assertTrue(preview.image.getRGB(0,0,preview.image.width,preview.image.height,null,0,preview.image.width).all { it==-1 })
        }
    }
    @Test fun rejectsWrongFingerprint() {
        pairing().use { pairing ->
            val file=folder.newFile().toPath();Files.writeString(file,pairing.encode())
            assertThrows(IllegalArgumentException::class.java) { PairingQrImage.read(file,"00") }
        }
    }
    @Test fun rejectsOversizedOrMalformedPairing() {
        val file=folder.newFile().toPath();Files.writeString(file,"x".repeat(1025))
        assertThrows(IllegalArgumentException::class.java) { PairingQrImage.read(file,"") }
        Files.writeString(file,"DNISUITE2|invalid")
        assertThrows(IllegalArgumentException::class.java) { PairingQrImage.read(file,"") }
    }
}
