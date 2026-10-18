package pe.gob.reniec.dnie.data.bridge

import android.content.Context
import android.hardware.usb.UsbManager
import pe.gob.reniec.dnie.bridge.android.BridgePairing
import java.io.Closeable
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Platform transport only. TLS and NFC/PACE remain in their existing adapters. */
class BridgeTransportAdapter(private val context: Context) : Closeable {
    @Volatile private var closed = false
    @Volatile private var listener: ServerSocket? = null
    @Volatile private var socket: Socket? = null
    @Volatile private var accessory: android.os.ParcelFileDescriptor? = null
    @Volatile private var workers: ExecutorService? = null
    private var discovery: AutoCloseable? = null

    @Synchronized fun arm(pairing: BridgePairing): String {
        close()
        closed = false
        if (pairing.transport == "tls_reverse") {
            listener = ServerSocket(39444, 1)
            try { discovery = pe.gob.reniec.dnie.data.network.AndroidNetworkPresence(context).bridge(pairing) }
            catch (_: Exception) { discovery = null }
            return if (discovery != null) "Red segura · descubrimiento activo" else "Red segura · indica la dirección del teléfono en la PC"
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
            val server = synchronized(this) {
                check(!closed) { "Transporte cerrado" }
                checkNotNull(listener) { "Autoriza el receptor Wi-Fi primero" }
            }
            val waitBudget = (pairing.expiresAtEpochMillis - System.currentTimeMillis()).coerceIn(1L, 300_000L).toInt()
            server.soTimeout = waitBudget
            val connected = server.accept()
            synchronized(this) {
                if (closed) {
                    runCatching { connected.close() }
                    error("Transporte cerrado")
                }
                socket = connected
            }
            try {
                val attemptBudget = (pairing.expiresAtEpochMillis - System.currentTimeMillis()).coerceIn(1L, 5_000L).toInt()
                pe.gob.reniec.dnie.signing.link.ReverseTlsPeer.requirePrivate(connected.inetAddress)
                connected.soTimeout = attemptBudget
                // DHCP does not change identity. PinnedBridgeClient authenticates the PC by TLS pin.
                connected.outputStream.write("DNIREADY1".toByteArray(Charsets.US_ASCII)); connected.outputStream.flush()
                connected
            } catch (failure: Exception) {
                runCatching { connected.close() }
                synchronized(this) { if (socket === connected) socket = null }
                throw failure
            }
        } }
        "usb_accessory" -> { { openAccessorySocket() } }
        else -> null
    }

    private fun openAccessorySocket(): Socket {
        synchronized(this) { check(!closed) { "Transporte cerrado" } }
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val device = manager.accessoryList?.singleOrNull() ?: error("Cable USB desconectado")
        check(BridgeUsbIdentity.matches(device) && manager.hasPermission(device))
        val descriptor = checkNotNull(manager.openAccessory(device)) { "No se pudo abrir el accesorio USB" }
        var server: ServerSocket? = null
        var client: Socket? = null
        var local: Socket? = null
        var pool: ExecutorService? = null
        try {
            synchronized(this) {
                if (closed) error("Transporte cerrado")
                accessory = descriptor
            }
            val input = FileInputStream(descriptor.fileDescriptor)
            val output = FileOutputStream(descriptor.fileDescriptor)
            output.write("DNIREADY1".toByteArray(Charsets.US_ASCII)); output.flush()
            // Local-only shim forwards encrypted TLS bytes. The physical PC link is USB bulk,
            // without IP, tethering, ADB or plaintext APDU on the loopback socket.
            server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
            synchronized(this) {
                if (closed) error("Transporte cerrado")
                listener = server
            }
            client = Socket(InetAddress.getLoopbackAddress(), server.localPort)
            local = server.accept()
            server.close()
            val acceptedLocal = local
            pool = Executors.newFixedThreadPool(2)
            synchronized(this) {
                if (closed) error("Transporte cerrado")
                listener = null
                socket = acceptedLocal
                workers?.shutdownNow()
                workers = pool
            }
            pool.execute {
                try {
                    val buffer = ByteArray(65536)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        acceptedLocal.getOutputStream().write(buffer, 0, count)
                        acceptedLocal.getOutputStream().flush()
                    }
                } catch (_: Exception) {
                } finally {
                    runCatching { acceptedLocal.close() }
                    runCatching { descriptor.close() }
                }
            }
            pool.execute {
                try {
                    val buffer = ByteArray(16384)
                    while (true) {
                        val count = acceptedLocal.getInputStream().read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        output.flush()
                    }
                } catch (_: Exception) {
                } finally {
                    runCatching { acceptedLocal.close() }
                    runCatching { descriptor.close() }
                }
            }
            return client
        } catch (failure: Throwable) {
            pool?.shutdownNow()
            runCatching { client?.close() }
            runCatching { local?.close() }
            runCatching { server?.close() }
            runCatching { descriptor.close() }
            synchronized(this) {
                if (listener === server) listener = null
                if (socket === local) socket = null
                if (accessory === descriptor) accessory = null
                if (workers === pool) workers = null
            }
            throw failure
        }
    }

    @Synchronized override fun close() {
        closed = true
        runCatching { discovery?.close() }; discovery = null
        runCatching { listener?.close() }; listener = null
        runCatching { socket?.close() }; socket = null
        runCatching { accessory?.close() }; accessory = null
        workers?.shutdownNow(); workers = null
    }

    companion object {
        fun localAddresses(): List<String> = pe.gob.reniec.dnie.network.PhoneDiscovery.addresses().mapNotNull { it.hostAddress }
    }
}
