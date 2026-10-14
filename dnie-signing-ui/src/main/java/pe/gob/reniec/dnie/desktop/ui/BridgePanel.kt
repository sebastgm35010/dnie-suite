package pe.gob.reniec.dnie.desktop.ui

import java.awt.FlowLayout
import java.util.concurrent.Executors
import javax.swing.*
import pe.gob.reniec.dnie.desktop.orchestration.*

/** Optional bridge frontend. All native IPC and mode decisions stay outside Swing. */
internal class BridgePanel(private val product: DesktopProductCoordinator) : JPanel(java.awt.BorderLayout(8,8)), AutoCloseable {
    private val tasks=Executors.newSingleThreadExecutor { Thread(it,"suite-bridge-control") }
    private val state=JLabel("Consultando Bridge…")
    private val refresh=AetherButton("Actualizar estado")
    private val manager=AetherButton("Abrir gestor Bridge")
    private val stop=AetherButton("Detener puente",ButtonVariant.OUTLINE)
    private var suiteActive=false
    private var running=false
    @Volatile private var closed=false
    init {
        isOpaque=false
        add(JPanel().apply { isOpaque=false;layout=BoxLayout(this,BoxLayout.Y_AXIS)
            add(JLabel("Compatibilidad con otras aplicaciones").apply { font=font.deriveFont(java.awt.Font.BOLD) });add(state)
        },java.awt.BorderLayout.NORTH)
        add(JPanel(FlowLayout(FlowLayout.LEFT,6,0)).apply { isOpaque=false;add(refresh);add(manager);add(stop) },java.awt.BorderLayout.CENTER)
        refresh.addActionListener { run { render(product.status()) } }
        manager.addActionListener { run { product.openBridgeManager();"Gestor abierto. Configura o inicia el puente allí." } }
        stop.addActionListener {
            if(JOptionPane.showConfirmDialog(this,"Se desconectará el lector de las aplicaciones de Windows. ¿Detener el puente?",
                "Detener puente",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION) run { render(product.stopBridge()) }
        }
        run { render(product.status()) }
    }
    private fun render(snapshot: BridgeSnapshot): String = when(snapshot.availability) {
        BridgeAvailability.NOT_INSTALLED -> "Servicio no instalado."
        BridgeAvailability.SERVICE_STOPPED -> "Servicio detenido."
        BridgeAvailability.UNAVAILABLE -> "Estado no disponible; revisa el gestor."
        BridgeAvailability.OPTIONAL_MODULE_ABSENT -> "Módulo opcional ausente."
        BridgeAvailability.AVAILABLE -> when(snapshot.host) {
            BridgeHostState.STOPPED -> "Puente detenido; suite disponible."
            BridgeHostState.RUNNING -> if(snapshot.cardPresent) "Lector activo · DNI presente." else "Puente activo · esperando DNI."
            BridgeHostState.STARTING -> "Iniciando puente…"
            BridgeHostState.STOPPING -> "Deteniendo puente…"
            BridgeHostState.FAILED -> "Puente con error; detén o revisa el gestor."
            BridgeHostState.UNKNOWN -> "Estado del puente desconocido."
        }
    }
    private fun run(action: () -> String) {
        if(running || closed)return
        running=true;buttons();state.text="Consultando…"
        tasks.submit {
            val message=try { action() } catch(e: ProductModeConflict) { e.message ?: "Modo ocupado." }
                catch(_: Exception) { "No se pudo operar Bridge. Revisa el gestor." }
            SwingUtilities.invokeLater { if(!closed) { running=false;buttons();state.text=message } }
        }
    }
    fun setSuiteActive(active: Boolean) {suiteActive=active;buttons()}
    private fun buttons() {refresh.isEnabled=!running;manager.isEnabled=!running && !suiteActive;stop.isEnabled=!running && !suiteActive}
    override fun close() {closed=true;tasks.shutdownNow()}
}
