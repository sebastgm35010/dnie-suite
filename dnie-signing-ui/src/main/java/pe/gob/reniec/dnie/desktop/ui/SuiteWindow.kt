package pe.gob.reniec.dnie.desktop.ui

import java.awt.*
import java.awt.event.*
import java.nio.file.*
import java.util.prefs.Preferences
import javax.swing.*
import javax.swing.border.EmptyBorder
import pe.gob.reniec.dnie.desktop.model.*
import pe.gob.reniec.dnie.pdf.*

/** Isolated user-mode frontend. No bridge service, driver or reader registration. */
class SuiteWindow(initialFiles: List<Path> = emptyList()) : JFrame("DNIe Suite 3.1 · firma privada") {
    private val product=DesktopIntegration.coordinator()
    private val controller=SuiteController(product)
    private val bridgePanel=if(DesktopIntegration.helper()!=null) BridgePanel(product) else null
    private val prefs=Preferences.userRoot().node("pe/gob/reniec/dnie/suite-ui")
    private val phone=JTextField(prefs.get("phone",""),12).apply { name="suite-phone" }
    private val pc=JTextField(pe.gob.reniec.dnie.desktop.remote.SuiteNetworkAddress.choose(prefs.get("pc",""),phone.text),12).apply { name="suite-pc" }
    private val workspace=JTextField(prefs.get("workspace",defaultPath("SuiteContinuousJobs")),38).apply { name="suite-workspace" }
    private val identity=JTextField(prefs.get("identity",defaultPath("SuiteContinuousState/pc.dpapi")),38).apply { name="suite-identity" }
    private val connect=JButton("Conectar Android")
    private val disconnect=JButton("Desconectar")
    private val qr=JButton("Abrir QR")
    private val status=JLabel("Conecta Android desde Suite → Conectar PC guardada.")
    private val fingerprint=JTextField().apply { isEditable=false }
    private val files=DefaultListModel<Path>()
    private val list=JList(files)
    private val canvas=PreviewCanvas()
    private val page=JSpinner(SpinnerNumberModel(1,1,10000,1))
    private val pageInfo=JLabel("Sin PDF seleccionado")
    private val mode=JComboBox(arrayOf("Sello visible","PDF invisible","Firma separada (.p7s)"))
    private val tsa=JCheckBox("Sello RENIEC · PAdES-T")
    private val style=JComboBox(arrayOf("Tarjeta","Minimalista"))
    private val reason=JTextField("Doy conformidad")
    private val location=JTextField()
    private val fields=linkedMapOf("DNI" to JCheckBox("DNI",true),"Fecha" to JCheckBox("Fecha",true),
        "Hora" to JCheckBox("Hora",true),"Motivo" to JCheckBox("Motivo",true),"Lugar" to JCheckBox("Lugar",true))
    private val sign=JButton("Firmar lote")
    private val open=JButton("Abrir resultados")
    private var pairing: pe.gob.reniec.dnie.desktop.application.DesktopSuiteSession.PairingInfo?=null
    private val pairingQr=PairingQrDialog(this)
    private var resultDirectory: Path?=null
    private var connected=false
    private var busy=false
    private val mutableControls=mutableListOf<JComponent>()
    init {
        defaultCloseOperation=DO_NOTHING_ON_CLOSE;minimumSize=Dimension(1080,740);setSize(1350,900);setLocationRelativeTo(null)
        layout=BorderLayout(12,12)
        val header=JPanel(BorderLayout(8,8)).apply { border=EmptyBorder(18,22,0,22) }
        header.add(JLabel("DNIe Suite").apply { font=Font("Segoe UI",Font.BOLD,27) },BorderLayout.WEST)
        header.add(JLabel("Documentos en tu PC · firma con tu DNI en Android"),BorderLayout.EAST)
        val connection=JPanel().apply { layout=BoxLayout(this,BoxLayout.Y_AXIS);border=EmptyBorder(8,0,8,0) }
        connection.add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(JLabel("PC"));add(pc);add(JLabel("Android"));add(phone);add(connect);add(disconnect);add(qr)
        })
        connection.add(JPanel(FlowLayout(FlowLayout.LEFT)).apply { add(JLabel("Resultados"));add(workspace);add(JLabel("Identidad PC"));add(identity) })
        connection.add(fingerprint)
        bridgePanel?.let(connection::add)
        header.add(connection,BorderLayout.SOUTH);add(header,BorderLayout.NORTH)
        val add=JButton("Añadir archivos"); val remove=JButton("Quitar")
        val filePanel=JPanel(BorderLayout(8,8)).apply { border=EmptyBorder(0,18,0,0);preferredSize=Dimension(260,500) }
        filePanel.add(JLabel("Lote de documentos"),BorderLayout.NORTH);filePanel.add(JScrollPane(list),BorderLayout.CENTER)
        filePanel.add(JPanel(FlowLayout()).apply { add(add);add(remove) },BorderLayout.SOUTH)
        list.cellRenderer=DefaultListCellRenderer().let { delegate -> javax.swing.ListCellRenderer<Path> { l,value,index,selected,focus ->
            delegate.getListCellRendererComponent(l,value.fileName.toString(),index,selected,focus) } }
        val preview=JPanel(BorderLayout(6,6))
        preview.add(JPanel(FlowLayout(FlowLayout.LEFT)).apply { add(JLabel("Página"));add(page);add(pageInfo) },BorderLayout.NORTH)
        preview.add(canvas,BorderLayout.CENTER)
        preview.add(JLabel("Arrastra el área azul; redimensiona desde su esquina. Es una guía de posición."),BorderLayout.SOUTH)
        val options=JPanel().apply { layout=BoxLayout(this,BoxLayout.Y_AXIS);border=EmptyBorder(8,12,8,18);preferredSize=Dimension(255,500) }
        fun option(label: String,component: JComponent) { options.add(JLabel(label));component.maximumSize=Dimension(230,32);options.add(component);options.add(Box.createVerticalStrut(12)) }
        option("Formato PDF",mode);option("Apariencia",style);option("Motivo",reason);option("Lugar",location)
        options.add(tsa)
        options.add(JLabel("<html>Requiere internet. Si no se verifica<br>el sello, no se publica el PDF.</html>"))
        options.add(Box.createVerticalStrut(12))
        options.add(JLabel("Datos incluidos"));fields.values.forEach { options.add(it) }
        options.add(Box.createVerticalStrut(22));options.add(JLabel("Nombre siempre incluido."))
        options.add(Box.createVerticalStrut(10));options.add(JLabel("<html>Misma configuración para<br>los PDF del lote.</html>"))
        val split=JSplitPane(JSplitPane.HORIZONTAL_SPLIT,filePanel,preview).apply { resizeWeight=0.22;dividerLocation=270 }
        val body=JPanel(BorderLayout(10,10));body.add(split,BorderLayout.CENTER);body.add(options,BorderLayout.EAST);add(body,BorderLayout.CENTER)
        val footer=JPanel(BorderLayout(10,10)).apply { border=EmptyBorder(8,22,18,22) }
        footer.add(status,BorderLayout.CENTER);footer.add(JPanel(FlowLayout()).apply { add(open);add(sign) },BorderLayout.EAST);add(footer,BorderLayout.SOUTH)
        mutableControls.addAll(listOf(pc,phone,workspace,identity,add,remove,list,page,mode,style,reason,location,tsa)+fields.values)
        files.addListDataListener(object: javax.swing.event.ListDataListener {
            private fun update() { sign.isEnabled=connected && !busy && !files.isEmpty }
            override fun intervalAdded(e: javax.swing.event.ListDataEvent) = update()
            override fun intervalRemoved(e: javax.swing.event.ListDataEvent) = update()
            override fun contentsChanged(e: javax.swing.event.ListDataEvent) = update()
        })
        connect.addActionListener { connect() };disconnect.addActionListener { if(busy && connected && JOptionPane.showConfirmDialog(this,"Interrumpir un lote puede dejar resultados parciales. ¿Desconectar?","Desconectar",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION) return@addActionListener; controller.disconnect();pairing=null;pairingQr.close();connected=false;setBusy(false);status.text="Desconectado. No se repite ninguna operación." }
        qr.addActionListener { pairing?.let(pairingQr::open) ?: connect(showQr=true) }
        add.addActionListener {
            val chooser=JFileChooser().apply { isMultiSelectionEnabled=true }
            if(chooser.showOpenDialog(this)==JFileChooser.APPROVE_OPTION) {
                setBusy(true);status.text="Preparando archivos…"
                controller.prepareFiles(chooser.selectedFiles.toList(),chooser.fileSystemView) { paths,rejected ->
                    paths.forEach { path -> if(files.size()<50 && !files.contains(path)) files.addElement(path) }
                    if(list.selectedIndex<0 && files.size()>0) list.selectedIndex=0
                    setBusy(false);status.text=if(rejected==0) "Archivos preparados." else "$rejected archivo(s) no disponibles. Selecciona el archivo original desde su carpeta."
                }
            }
        }
        remove.addActionListener { list.selectedIndices.sortedDescending().forEach(files::remove) }
        list.addListSelectionListener { if(!it.valueIsAdjusting) loadPreview() }
        page.addChangeListener { loadPreview() }
        mode.addActionListener { canvas.visibleStamp=mode.selectedIndex==0;tsa.isEnabled=!busy && mode.selectedIndex!=2 }
        sign.addActionListener { sign() }
        open.addActionListener { resultDirectory?.let { Desktop.getDesktop().open(it.toFile()) } }
        addWindowListener(object: WindowAdapter() { override fun windowClosing(e: WindowEvent) {
            if(!busy || JOptionPane.showConfirmDialog(this@SuiteWindow,"Hay una operación activa. Cerrar interrumpe la conexión; no se repetirá.","Cerrar Suite",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION) {
                pairingQr.close();controller.close();bridgePanel?.close();dispose()
            }
        } })
        initialFiles.filter(Files::isRegularFile).forEach(files::addElement)
        if(files.size()>0) list.selectedIndex=0
        setBusy(false)
    }
    private fun connect(showQr: Boolean=false) {
        try {
            val output=Path.of(workspace.text).toAbsolutePath();val own=Path.of(identity.text).toAbsolutePath()
            val remote=phone.text.trim()
            if(!pe.gob.reniec.dnie.signing.link.SigningProvision.privateAddress(remote)) {
                status.text="Indica la dirección IP local o Tailscale de Android para preparar la conexión y el QR.";return
            }
            val previous=pc.text.trim()
            val local=pe.gob.reniec.dnie.desktop.remote.SuiteNetworkAddress.choose(previous,remote)
            pc.text=local
            if(local!=previous) JOptionPane.showMessageDialog(this,"Se usará la dirección actual $local. La PC conserva su identidad; en Android actualizado no necesitas importar otro TXT/QR.")
            pairing=null;pairingQr.close();setBusy(true);qr.isEnabled=false;status.text="Esperando Android. Abre Suite continua en el teléfono…"
            controller.connect(local,remote,output,own,{ info ->
                pairing=info;fingerprint.text="Huella PC: ${info.fingerprint}";qr.isEnabled=true
                status.text=if(info.firstEnrollment) "Vincula esta PC mediante TXT/QR en Android." else "PC recordada: pulsa Conectar PC guardada en Android. La IP puede cambiar."
                if(showQr) pairingQr.open(info)
            },{ ok,message -> pairing=null;pairingQr.close();connected=ok;setBusy(false);status.text=message
                if(ok) { prefs.put("pc",local);prefs.put("phone",remote);prefs.put("workspace",output.toString());prefs.put("identity",own.toString()) }
            })
        } catch(_: Exception) { setBusy(false);status.text="Configuración de conexión inválida." }
    }
    private fun loadPreview() {
        val file=list.selectedValue
        if(file==null || !file.fileName.toString().endsWith(".pdf",true)) { controller.invalidatePreview();canvas.image=null;pageInfo.text="Sin vista previa para este formato";return }
        canvas.image=null;pageInfo.text="Cargando…"
        controller.preview(file,(page.value as Number).toInt()) { result,message ->
            if(result==null) { canvas.image=null;pageInfo.text=message } else { canvas.image=result.image;pageInfo.text="${result.number} / ${result.count}" }
        }
    }
    private fun sign() {
        if(files.isEmpty || !connected) return
        val inputs=(0 until files.size()).map(files::get)
        setBusy(true);status.text="Comprobando archivos antes de pedir el PIN…"
        controller.checkInputs(inputs) { valid ->
            setBusy(false)
            if(!valid) { status.text="Hay archivos no disponibles o mayores de 256 MiB. Vuelve a seleccionarlos; la conexión se conserva." }
            else signValidated(inputs)
        }
    }
    private fun signValidated(inputs: List<Path>) {
        val password=SigningPinPanel(inputs.size);password.focusPin()
        if(JOptionPane.showConfirmDialog(this,password,"Autorizar firma",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION) { password.clear();return }
        val pin=password.takePin()
        if(pin.size !in 4..8 || pin.any { it !in '0'..'9' }) { pin.fill('\u0000');status.text="PIN inválido; no enviado al DNI.";return }
        try {
            val pdfMode=when(mode.selectedIndex) { 0->DesktopPdfMode.VISIBLE;1->DesktopPdfMode.INVISIBLE;else->DesktopPdfMode.DETACHED }
            val options=PdfSigningOptions(reniecTimestamp=tsa.isSelected && pdfMode!=DesktopPdfMode.DETACHED,pageNumber=(page.value as Number).toInt(),visible=pdfMode==DesktopPdfMode.VISIBLE,
                placement=canvas.placement,reason=reason.text,location=location.text,appearance=SignatureAppearance(
                    style=if(style.selectedIndex==0) StampStyle.CARD else StampStyle.MINIMAL,
                    showDni=fields.getValue("DNI").isSelected,showDate=fields.getValue("Fecha").isSelected,showTime=fields.getValue("Hora").isSelected,
                    showReason=fields.getValue("Motivo").isSelected,showLocation=fields.getValue("Lugar").isSelected))
            val request=DesktopSigningRequest(inputs,Path.of(workspace.text),pdfMode,options)
            setBusy(true);status.text="Lote en curso. Mantén el DNI cerca del teléfono…"
            controller.sign(request,pin) { result,message,sessionUsable ->
                if(result!=null) resultDirectory=result.directory
                connected=sessionUsable
                setBusy(false);status.text=message;open.isEnabled=resultDirectory!=null
            }
        } catch(_: Exception) { pin.fill('\u0000');setBusy(false);status.text="No se pudo preparar el lote." }
    }
    private fun setBusy(value: Boolean) {
        bridgePanel?.setSuiteActive(connected || value)
        busy=value;mutableControls.forEach { it.isEnabled=!value };canvas.interactive=!value
        connect.isEnabled=!connected && !value;disconnect.isEnabled=connected || value;sign.isEnabled=connected && !value && !files.isEmpty
        tsa.isEnabled=!value && mode.selectedIndex!=2
        qr.isEnabled=!connected && (pairing!=null || !value)
        open.isEnabled=resultDirectory!=null
    }
    private fun defaultPath(suffix: String): String = if(Files.isDirectory(Path.of("D:/DNIeToolchain"))) "D:/DNIeToolchain/$suffix"
        else Path.of(System.getProperty("user.home"),"DNIeSuite",suffix).toString()
}
