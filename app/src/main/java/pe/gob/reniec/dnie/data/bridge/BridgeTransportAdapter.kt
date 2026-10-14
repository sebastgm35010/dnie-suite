package pe.gob.reniec.dnie.data.bridge

import android.content.Context
import android.hardware.usb.UsbManager
import pe.gob.reniec.dnie.bridge.android.BridgePairing
import java.io.Closeable
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/** Platform transport only. TLS and NFC/PACE remain in their existing adapters. */
class BridgeTransportAdapter(private val context: Context) : Closeable {
    @Volatile private var listener: ServerSocket? = null
    @Volatile private var socket: Socket? = null
    @Volatile private var accessory: android.os.ParcelFileDescriptor? = null
    private var workers: java.util.concurrent.ExecutorService? = null
    @Synchronized fun arm(pairing: BridgePairing): String {
        close()
        if (pairing.transport == "tls_reverse") {
            val address = localAddresses().firstOrNull() ?: error("Conecta el teléfono a Wi-Fi")
            listener = ServerSocket(39444, 1, InetAddress.getByName(address))
            return "$address:39444"
        }
        if (pairing.transport == "usb_accessory") {
            val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val device = manager.accessoryList?.singleOrNull() ?: error("Inicia USB directo en la PC y conecta el cable")
            check(BridgeUsbIdentity.matches(device)) { "Accesorio USB desconocido" }
            check(manager.hasPermission(device)) { "Autoriza el accesorio USB en Android" }
            return "USB directo listo"
        }
        return ""
    }
    fun socketFactory(pairing: BridgePairing): (() -> Socket)? = when (pairing.transport) {
        "tls_reverse" -> { {
            val server = checkNotNull(listener) { "Autoriza el receptor Wi-Fi primero" }
            val waitBudget = (pairing.expiresAtEpochMillis - System.currentTimeMillis()).coerceIn(1L, 300_000L).toInt()
            server.soTimeout = waitBudget
            val connected = server.accept()
            socket = connected
            try {
                val attemptBudget = (pairing.expiresAtEpochMillis - System.currentTimeMillis()).coerceIn(1L, 5_000L).toInt()
                pe.gob.reniec.dnie.signing.link.ReverseTlsPeer.requirePrivate(connected.inetAddress)
                connected.soTimeout = attemptBudget
                // DHCP does not change identity. PinnedBridgeClient authenticates the PC by TLS pin.
                connected.outputStream.write("DNIREADY1".toByteArray(Charsets.US_ASCII)); connected.outputStream.flush()
                connected
            } catch (failure: Exception) {
                runCatching { connected.close() }
                if (socket === connected) socket = null
                throw failure
            }
        } }
        "usb_accessory" -> { { openAccessorySocket() } }
        else -> null
    }
    private fun openAccessorySocket(): Socket {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val device = manager.accessoryList?.singleOrNull() ?: error("Cable USB desconectado")
        check(BridgeUsbIdentity.matches(device) && manager.hasPermission(device))
        val descriptor = checkNotNull(manager.openAccessory(device)) { "No se pudo abrir el accesorio USB" }
        accessory = descriptor
        val input = FileInputStream(descriptor.fileDescriptor)
        val output = FileOutputStream(descriptor.fileDescriptor)
        output.write("DNIREADY1".toByteArray(Charsets.US_ASCII)); output.flush()
        // Local-only shim forwards encrypted TLS bytes. The physical PC link is USB bulk,
        // without IP, tethering, ADB or plaintext APDU on the loopback socket.
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        listener = server
        val client = Socket(InetAddress.getLoopbackAddress(), server.localPort)
        val local = server.accept()
        socket = local
        server.close(); listener = null
        workers = Executors.newFixedThreadPool(2)
        workers!!.execute {
            try { val buffer=ByteArray(65536); while(true) { val count=input.read(buffer); if(count<0)break; local.getOutputStream().write(buffer,0,count);local.getOutputStream().flush() } }
            catch (_: Exception) { } finally { runCatching {local.close()};runCatching {descriptor.close()} }
        }
        workers!!.execute {
            try { val buffer=ByteArray(16384); while(true) { val count=local.getInputStream().read(buffer);if(count<0)break;output.write(buffer,0,count);output.flush() } }
            catch (_: Exception) { } finally {runCatching {local.close()};runCatching {descriptor.close()} }
        }
        return client
    }
    @Synchronized override fun close() {
        runCatching { listener?.close() }; listener = null
        runCatching { socket?.close() }; socket = null
        runCatching { accessory?.close() }; accessory = null
        workers?.shutdownNow(); workers = null
    }
    companion object {
        fun localAddresses(): List<String> = NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && it.name.startsWith("wlan") }
            .flatMap { it.inetAddresses.toList() }.filterIsInstance<java.net.Inet4Address>()
            .filter { it.isSiteLocalAddress }.mapNotNull { it.hostAddress }
    }
}
