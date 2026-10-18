package pe.gob.reniec.dnie.data.bridge

import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.net.TestUri
import org.junit.Assert.*
import org.junit.Test

class BridgeAndroidAdapterTest {
    @Test fun importThrowsBridgeProvisionFileExceptionWhenInputStreamIsNull() {
        val resolver = object : ContentResolver(null) {}
        val context = object : ContextWrapper(null) {
            override fun getContentResolver(): ContentResolver = resolver
        }
        val adapter = BridgeAndroidAdapter(context)
        val uri = TestUri("content://test/missing")
        val error = assertThrows(BridgeProvisionFileException::class.java) {
            adapter.import(uri)
        }
        assertEquals("No se pudo abrir el archivo de emparejamiento seleccionado.", error.message)
    }
}
