package pe.gob.reniec.dnie.bridge.android

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import pe.gob.reniec.dnie.core.nfc.DnieCardChannel
import java.io.File

/** Real mobile engine, synthetic NFC channel: opt-in native PCSC / Edge integration. */
class CrossLayerInteropTest {
    @Test fun browserToNativePcscToMobileEngine() {
        val directory = System.getenv("DNIE_CROSS_LAYER_FIXTURE")
        assumeTrue("Cross-layer fixture required", directory != null)
        val root=File(checkNotNull(directory))
        val trace=File(root,"mobile-engine.jsonl")
        fun event(name:String) { trace.appendText("{\"time\":${System.currentTimeMillis()},\"category\":\"mobile_engine\",\"event\":\"$name\",\"fields\":{}}\n") }
        val channels=mutableListOf<DnieCardChannel>()
        val atr=byteArrayOf(0x3B,0x80.toByte(),1,0x81.toByte())
        var handshakes=0
        File(root,"android-provision.json").inputStream().use { BridgeProvision.read(it) }.use { pairing ->
            PinnedBridgeClient(pairing).run(::event) {
                RestartablePaceCard.open("000000",atr,{
                    object:DnieCardChannel {
                        var closed=false
                        override fun transceive(command:ByteArray):ByteArray {
                            check(!closed); event("synthetic_nfc_transmit")
                            return byteArrayOf(0x90.toByte(),0)
                        }
                        override fun close(){closed=true}
                        override fun isConnected()=!closed
                        override fun setTimeout(timeoutMs:Int){}
                    }.also { channels.add(it) }
                },::event,establish={channel,_->handshakes++;event("synthetic_pace_established");channel})
            }
        }
        assertTrue(handshakes>=2)
        assertTrue(channels.all { !it.isConnected() })
        event("test_completed")
    }
}
