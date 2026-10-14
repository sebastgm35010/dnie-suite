package pe.gob.reniec.dnie.desktop.remote

import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.*
import pe.gob.reniec.dnie.network.DiscoveryWire

/** OS address hints are untrusted until discovery MAC and pinned TLS succeed. */
object NetworkTargets {
    private val dns=ThreadPoolExecutor(0,2,30,TimeUnit.SECONDS,SynchronousQueue(),{ task ->
        Thread(task,"dnie-route-dns").apply {isDaemon=true}
    },ThreadPoolExecutor.AbortPolicy())
    fun resolveHint(value: String): List<InetAddress> {
        if(DiscoveryWire.allowed(value))return listOf(InetAddress.getByName(value))
        if(!tailnetHost(value))return emptyList()
        val task=runCatching {dns.submit<List<InetAddress>> {InetAddress.getAllByName(value).filter {address ->
            val b=address.address; b.size==4 && b[0].toInt()==100 && (b[1].toInt() and 255) in 64..127
        }} }.getOrNull()?:return emptyList()
        return try {task.get(2,TimeUnit.SECONDS)}catch(_: Exception){task.cancel(true);emptyList()}
    }
    fun tailnetHost(value: String)=value.length<=253 && value.matches(Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.[a-z0-9-]+\\.ts\\.net"))
    @Volatile private var cached=emptyList<InetAddress>()
    private var fetchedAt=0L
    @Synchronized fun tailnetPeers(): List<InetAddress> {
        if(fetchedAt!=0L && System.nanoTime()-fetchedAt<30_000_000_000L)return cached
        fetchedAt=System.nanoTime();cached=emptyList()
        val executable=Path.of(System.getenv("ProgramFiles")?:"C:/Program Files","Tailscale","tailscale.exe")
        if(!Files.isRegularFile(executable))return cached
        val process=runCatching {ProcessBuilder(executable.toString(),"status","--json").redirectError(ProcessBuilder.Redirect.DISCARD).start()}.getOrNull()?:return cached
        val watchdog=Executors.newSingleThreadScheduledExecutor {r->Thread(r,"dnie-tailnet-status-deadline").apply {isDaemon=true}}
        val deadline=watchdog.schedule({process.destroyForcibly()},2500,TimeUnit.MILLISECONDS)
        try {
            val bytes=process.inputStream.use {it.readNBytes(1024*1024+1)}
            require(bytes.size<=1024*1024)
            if(process.waitFor(500,TimeUnit.MILLISECONDS) && process.exitValue()==0)cached=extractTailnetIps(String(bytes,Charsets.UTF_8))
        } catch(_: Exception) { } finally {deadline.cancel(false);watchdog.shutdownNow();process.destroyForcibly()}
        return cached
    }
    /** Extract only literal IP hints from bounded local status; no JSON value can authorize a peer. */
    internal fun extractTailnetIps(text: String): List<InetAddress> {
        require(text.length<=1024*1024)
        return Regex("\"TailscaleIPs\"\\s*:\\s*\\[([^]]{0,512})]").findAll(text)
            .flatMap {match->Regex("\"(100\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3})\"").findAll(match.groupValues[1]).map {it.groupValues[1]}}
            .filter {DiscoveryWire.allowed(it) && it.split('.')[1].toInt() in 64..127}.distinct().take(32).map(InetAddress::getByName).toList()
    }
}
