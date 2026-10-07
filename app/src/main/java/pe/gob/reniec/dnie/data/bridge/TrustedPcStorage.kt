package pe.gob.reniec.dnie.data.bridge

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import pe.gob.reniec.dnie.bridge.android.BridgePairing
import pe.gob.reniec.dnie.bridge.android.BridgeProvision
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Pairing credential is encrypted with a non-exportable Android Keystore key. */
class TrustedPcStorage(context: Context) {
    private val directory=context.noBackupFilesDir
    private fun file(transport: String) = AtomicFile(File(directory, "trusted-bridge-pc-$transport.bin"))
    private fun transports(method: pe.gob.reniec.dnie.bridge.BridgeConnectionMethod) = when(method) {
        pe.gob.reniec.dnie.bridge.BridgeConnectionMethod.LOCAL_NETWORK -> listOf("tls_reverse", "tls")
        pe.gob.reniec.dnie.bridge.BridgeConnectionMethod.USB_NETWORK -> listOf("usb_accessory")
        else -> emptyList()
    }
    private val alias="dnie-bridge-trusted-pc-v1"
    private fun key(): SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias,null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun save(pairing: BridgePairing) {
        if (!pairing.persistent) return
        val file=file(pairing.transport)
        val raw=BridgeProvision.serialize(pairing).toByteArray(Charsets.UTF_8)
        try {
            val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,key());updateAAD(alias.toByteArray()) }
            val encrypted=cipher.doFinal(raw)
            val out=file.startWrite()
            try {out.write(cipher.iv);out.write(encrypted);file.finishWrite(out)} catch(failure: Exception){file.failWrite(out);throw failure}
        } finally {raw.fill(0)}
    }
    @Synchronized fun load(method: pe.gob.reniec.dnie.bridge.BridgeConnectionMethod): BridgePairing? {
        val file=transports(method).map(::file).firstOrNull {it.baseFile.exists()} ?: return null
        val bytes=file.openRead().use { input -> val buffer=ByteArray(8193);var total=0;while(total<buffer.size){val n=input.read(buffer,total,buffer.size-total);if(n<0)break;total+=n};require(total in 29..8192);buffer.copyOf(total) }
        var plain: ByteArray?=null
        return try {
            val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)));updateAAD(alias.toByteArray())}
            plain=cipher.doFinal(bytes,12,bytes.size-12)
            BridgeProvision.parse(plain.toString(Charsets.UTF_8)).also { require(it.persistent) }
        } finally {plain?.fill(0);bytes.fill(0)}
    }
    @Synchronized fun forget() {
        listOf("tls", "tls_reverse", "usb_accessory", "tailscale_tcp").forEach {file(it).delete()}
        AtomicFile(File(directory,"trusted-bridge-pc.bin")).delete()
    }
}
