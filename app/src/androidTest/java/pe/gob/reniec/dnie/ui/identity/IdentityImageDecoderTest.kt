package pe.gob.reniec.dnie.ui.identity

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gemalto.jp2.JP2Encoder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import pe.gob.reniec.dnie.icao.IcaoImage
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class IdentityImageDecoderTest {
    private fun bitmap() = Bitmap.createBitmap(64, 80, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }

    @Test fun rendersJpegOnAndroid() = runBlocking {
        val original = bitmap()
        val bytes = ByteArrayOutputStream().apply { original.compress(Bitmap.CompressFormat.JPEG, 90, this) }.toByteArray()
        val decoded = IdentityImageDecoder.decode(IcaoImage(bytes, "image/jpeg"))
        assertEquals(64, decoded.width)
        assertEquals(80, decoded.height)
        original.recycle()
        decoded.recycle()
    }

    @Test fun loadsNativeCodecAndRendersJpeg2000OnAndroid() = runBlocking {
        val original = bitmap()
        val bytes = requireNotNull(JP2Encoder(original).setNumResolutions(3).encode())
        val decoded = IdentityImageDecoder.decode(IcaoImage(bytes, "image/jp2"))
        assertEquals(64, decoded.width)
        assertEquals(80, decoded.height)
        assertEquals(Color.BLUE, decoded.getPixel(0, 0))
        original.recycle()
        decoded.recycle()
    }
}
