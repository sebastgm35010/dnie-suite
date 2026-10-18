package pe.gob.reniec.dnie.data.suite

import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.net.TestUri
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class RemoteSigningAndroidAdapterTest {
    @Test fun readThrowsIOExceptionWhenInputStreamIsNull() {
        val resolver = object : ContentResolver(null) {}
        val context = object : ContextWrapper(null) {
            override fun getContentResolver(): ContentResolver = resolver
        }
        val adapter = RemoteSigningAndroidAdapter(context)
        val uri = TestUri("content://test/missing")
        val error = assertThrows(IOException::class.java) {
            adapter.read(uri)
        }
        assertEquals("No se pudo abrir el archivo de solicitud", error.message)
    }
}
