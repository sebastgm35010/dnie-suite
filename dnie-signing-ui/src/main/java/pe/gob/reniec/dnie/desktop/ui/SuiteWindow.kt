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
class SuiteWindow(initialFiles: List<Path> = emptyList(), settings: Preferences=Preferences.userRoot().node("pe/gob/reniec/dnie/suite-ui"), private val desktopActions: SuiteDesktopActions=SystemSuiteDesktopActions) : JFrame("DNIe Suite 3.1 · firma privada") {
    private val product=DesktopIntegration.coordinator()
    private val controller=SuiteController(product)
    private val bridgePanel=if(DesktopIntegration.helper()!=null) BridgePanel(product) else null
    private val prefs=settings
    private val designStore=SuiteDesignPreferences(settings)
    private val savedDesign=designStore.load()
    private var preferredPage: Int?=savedDesign.options.pageNumber
    private var firstSelection=true
    private var restoringDesign=false
    private var resourcesClosed=false
    private var previewDni="00000000"
    private val signedOutputs=mutableMapOf<Path,Path>()
    private var JComboBox<String>.text: String
        get()=editor.item?.toString().orEmpty()
        set(value){editor.item=value}
    private val phone=AetherComboBox((listOf("")+prefs.get("phone.history",prefs.get("phone","")).split('\n')).distinct().take(5).toTypedArray()).apply {
        name="suite-phone";isEditable=true;text=prefs.get("phone","");toolTipText="Vacío: buscar Android automáticamente. También admite una IP o nombre Tailscale."
    }
    private val pc=AetherTextField(pe.gob.reniec.dnie.desktop.remote.SuiteNetworkAddress.choose(prefs.get("pc",""),phone.text),12).apply { name="suite-pc" }
    private val workspace=AetherTextField(prefs.get("workspace",defaultPath("SuiteContinuousJobs")),38).apply { name="suite-workspace" }
    private val identity=AetherTextField(prefs.get("identity",defaultPath("SuiteContinuousState/pc.dpapi")),38).apply { name="suite-identity" }
    private val connect=AetherButton("Conectar Android",ButtonVariant.PRIMARY)
    private val disconnect=AetherButton("Desconectar",ButtonVariant.OUTLINE)
    private val qr=AetherButton("Abrir QR")
    private val progress=JProgressBar().apply { name="suite-progress";preferredSize=Dimension(180,6);isVisible=false }
    private val templateButtons=linkedMapOf<StampTemplate,AetherToggleChip>()
    private val status=JLabel("Conecta Android desde Suite → Conectar PC guardada.")
    private val fingerprint=AetherTextField().apply { isEditable=false }
    private val files=DefaultListModel<Path>()
    private val list=JList(files)
    private val canvas=PreviewCanvas()
    private val page=AetherSpinner(SpinnerNumberModel(1,1,10000,1))
    private val pageInfo=JLabel("Muestra A4")
    private val mode=AetherComboBox(arrayOf("Sello visible","PDF invisible","Firma separada (.p7s)"))
    private val tsa=AetherToggleChip("Sello de tiempo RENIEC")
    private val style=AetherComboBox(arrayOf(StampStyle.INSTITUTIONAL,StampStyle.CARD,StampStyle.MINIMAL,StampStyle.INK,StampStyle.HANDWRITTEN_ONLY))
    private val badge=AetherComboBox(arrayOf(StampBadgeType.SEAL_VECTOR,StampBadgeType.CHECK_SEAL,StampBadgeType.LOCK_SEAL,StampBadgeType.NONE))
    private val backgroundMode=AetherComboBox(StampBackgroundMode.entries.toTypedArray())
    private val dateFormat=AetherComboBox(StampDateFormat.entries.toTypedArray())
    private val density=AetherComboBox(StampDensity.entries.toTypedArray())
    private val showBorder=AetherToggleChip("Mostrar borde",true)
    private val imageButton=AetherButton("Imagen de rúbrica…")
    private val clearWhite=AetherToggleChip("Limpiar fondo blanco",true)
    private var handwrittenImage: ByteArray?=null
    private var imageLoading=false
    private val themeButton=AetherButton("Modo oscuro",ButtonVariant.GHOST)
    private val guideButton=AetherButton("?",ButtonVariant.GHOST).apply { name="suite-guide";toolTipText="Guía rápida";getAccessibleContext().accessibleName="Guía rápida" }
    private val quickGuide=SuiteQuickGuideDialog(this)
    private val target=AetherComboBox(StampPageTarget.entries.toTypedArray())
    private val colors=ColorSwatchSelector()
    private val title=AetherTextField("FIRMADO DIGITALMENTE")
    private val previewName=AetherTextField("",18)
    private val tsaBadge=AetherToggleChip("Mostrar distintivo TSA",true)
    private val pill=AetherStatusPill()
    private val network=AetherButton("Red y rutas",ButtonVariant.GHOST)
    private val advanced=JDialog(this,"Red y rutas",true)
    private val sizes=mutableMapOf<Path,Long?>()
    private val documentStates=mutableMapOf<Path,String>()
    private var pageCount=0
    private var pageWidth=595f
    private var pageHeight=842f
    private val previewTime=System.currentTimeMillis()
    private val stampTimer=Timer(160) { renderStamp() }.apply { isRepeats=false }
    private val reason=AetherTextField("Doy conformidad")
    private val location=AetherTextField()
    private val fields=linkedMapOf("DNI" to AetherToggleChip("DNI",true),"Fecha" to AetherToggleChip("Fecha",true),
        "Hora" to AetherToggleChip("Hora",true),"Motivo" to AetherToggleChip("Motivo",true),"Lugar" to AetherToggleChip("Lugar",true))
    private val sign=AetherButton("Firmar lote",ButtonVariant.PRIMARY)
    private val open=AetherButton("Abrir carpeta de salida",ButtonVariant.SECONDARY)
    private val openPdf=AetherButton("Abrir PDF firmado",ButtonVariant.PRIMARY).apply { name="open-signed-pdf";isEnabled=false }
    private var pairing: pe.gob.reniec.dnie.desktop.application.DesktopSuiteSession.PairingInfo?=null
    private val pairingQr=PairingQrDialog(this)
    private var resultDirectory: Path?=null
    private var connected=false
    private var connecting=false
    private var busy=false
    private val mutableControls=mutableListOf<JComponent>()
    init {
        defaultCloseOperation=DO_NOTHING_ON_CLOSE
        val available=GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        minimumSize=Dimension(minOf(1080,available.width),minOf(740,available.height))
        setSize(minOf(1350,available.width),minOf(900,available.height));setLocationRelativeTo(null)
        contentPane.background=AetherSwingTheme.background;layout=BorderLayout(12,12)
        style.name="stamp-style";badge.name="stamp-badge";target.name="stamp-target";previewName.name="stamp-preview-name";page.name="stamp-page"
        val header=JPanel(BorderLayout(12,0)).apply { background=AetherSwingTheme.surface;border=javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createMatteBorder(0,0,1,0,AetherSwingTheme.border),EmptyBorder(12,20,12,20)) }
        val brand=JPanel().apply { isOpaque=false;layout=BoxLayout(this,BoxLayout.Y_AXIS);preferredSize=Dimension(if(available.width<1160) 190 else 260,46) }
        brand.add(JLabel("DNIe Suite 3.1",AetherVectorIcon(),SwingConstants.LEFT).apply { font=Font("Segoe UI",Font.BOLD,if(available.width<1160) 18 else 22);foreground=AetherSwingTheme.text })
        brand.add(JLabel("Firma digital PAdES y lotes privados").apply { foreground=AetherSwingTheme.muted;font=font.deriveFont(11f) })
        header.add(brand,BorderLayout.WEST)
        header.add(JPanel(FlowLayout(FlowLayout.RIGHT,7,0)).apply { isOpaque=false
            add(pill);add(JLabel("Teléfono").apply { border=EmptyBorder(0,0,0,5) });phone.preferredSize=Dimension(155,36);add(phone);add(connect);add(qr);add(disconnect);add(network);add(themeButton);add(guideButton)
        },BorderLayout.EAST);add(header,BorderLayout.NORTH)
        buildAdvancedDialog()
        val add=AetherButton("+ Añadir archivos");val remove=AetherButton("Quitar",ButtonVariant.GHOST)
        val filePanel=AetherCardPanel(BorderLayout(8,12)).apply { preferredSize=Dimension(280,500) }
        filePanel.add(JLabel("Lote de documentos").apply { font=font.deriveFont(Font.BOLD,16f) },BorderLayout.NORTH)
        list.cellRenderer=DocumentCardCellRenderer({sizes[it]},{documentStates[it]?:"Pendiente"});list.fixedCellHeight=78
        val listArea=object: JScrollPane(list) {
            override fun paintChildren(g: Graphics) { super.paintChildren(g)
                if(files.isEmpty) { g.color=AetherSwingTheme.outline;val copy=g.create() as Graphics2D
                    try { copy.stroke=BasicStroke(1f,BasicStroke.CAP_BUTT,BasicStroke.JOIN_ROUND,10f,floatArrayOf(4f,4f),0f)
                        copy.drawRoundRect(10,18,(width-20).coerceAtLeast(1),130,14,14)
                        copy.drawString("Arrastra tus PDFs aquí",25,65);copy.drawString("o pulsa + Añadir archivos",25,88)
                    } finally { copy.dispose() } }
            }
        }
        listArea.aetherScroll();filePanel.add(listArea,BorderLayout.CENTER)
        filePanel.add(JPanel().apply { isOpaque=false;layout=BoxLayout(this,BoxLayout.Y_AXIS)
            add(JPanel(FlowLayout(FlowLayout.LEFT,0,0)).apply { isOpaque=false;add(add);add(remove) })
            add(Box.createVerticalStrut(18));add(JLabel("Nombre para previsualizar el sello"));add(Box.createVerticalStrut(6));add(previewName)
            add(Box.createVerticalStrut(6));add(JLabel("<html>Vista previa con datos de ejemplo.<br>Al firmar se usa el certificado real.</html>").apply { font=font.deriveFont(11f);foreground=AetherSwingTheme.muted })
        },BorderLayout.SOUTH)
        val preview=JPanel(BorderLayout(6,6)).apply { isOpaque=false }
        val previous=AetherButton("",ButtonVariant.GHOST).apply { icon=AetherVectorIcon(4);toolTipText="Página anterior" };val next=AetherButton("",ButtonVariant.GHOST).apply { icon=AetherVectorIcon(5);toolTipText="Página siguiente" }
        preview.add(JPanel().apply { isOpaque=false;layout=BoxLayout(this,BoxLayout.Y_AXIS)
            add(JPanel(FlowLayout(FlowLayout.LEFT,6,0)).apply { isOpaque=false;add(previous);add(JLabel("Página"));page.preferredSize=Dimension(65,32);add(page);add(pageInfo);add(next) })
            add(JPanel(FlowLayout(FlowLayout.LEFT,6,5)).apply { isOpaque=false;add(JLabel("Aplicar en"));add(target)
                for((preset,label) in listOf(SignaturePreset.TOP_LEFT to "↖",SignaturePreset.TOP_RIGHT to "↗",SignaturePreset.BOTTOM_LEFT to "↙",SignaturePreset.BOTTOM_RIGHT to "↘")) {
                    add(AetherButton("",ButtonVariant.GHOST).apply { border=EmptyBorder(6,6,6,6);icon=AetherVectorIcon(when(preset) { SignaturePreset.TOP_LEFT->0;SignaturePreset.TOP_RIGHT->1;SignaturePreset.BOTTOM_LEFT->2;else->3 });toolTipText=preset.displayName;addActionListener { if(!busy) { canvas.placement=StampPlacementGeometry.preset(canvas.placement,preset);scheduleStamp() } };mutableControls.add(this) })
                }
            })
        },BorderLayout.NORTH)
        val zoomInfo=JLabel("100%")
        val less=AetherButton("−",ButtonVariant.GHOST);val fit=AetherButton("Ajustar",ButtonVariant.GHOST);val more=AetherButton("+",ButtonVariant.GHOST)
        less.name="preview-zoom-out";more.name="preview-zoom-in";fit.name="preview-fit"
        fun zoom(value: Double) { canvas.zoom=value;zoomInfo.text="${(canvas.zoom*100).toInt()}%" }
        less.addActionListener { zoom(canvas.zoom-.25) };more.addActionListener { zoom(canvas.zoom+.25) };fit.addActionListener { zoom(1.0) }
        (preview.getComponent(0) as JPanel).add(JPanel(FlowLayout(FlowLayout.RIGHT,5,0)).apply { isOpaque=false;add(less);add(zoomInfo);add(more);add(fit) })
        preview.add(JScrollPane(canvas).aetherScroll(),BorderLayout.CENTER)
        preview.add(JLabel("Arrastra para mover · Esquinas y bordes para ajustar · Guías de alineación").apply { font=font.deriveFont(11f);foreground=AetherSwingTheme.muted },BorderLayout.SOUTH)
        previous.addActionListener { if((page.value as Int)>1) page.value=(page.value as Int)-1 }
        next.addActionListener { if((page.value as Int)<pageCount) page.value=(page.value as Int)+1 }
        val inspector=JPanel().apply { isOpaque=false;layout=BoxLayout(this,BoxLayout.Y_AXIS) }
        fun card(caption: String,build: (JPanel)->Unit) {
            val card=AetherCardPanel().apply { layout=BoxLayout(this,BoxLayout.Y_AXIS);alignmentX=LEFT_ALIGNMENT }
            val contents=JPanel().apply {isOpaque=false;layout=BoxLayout(this,BoxLayout.Y_AXIS)}
            val toggle=AetherDisclosureButton(caption).apply {alignmentX=LEFT_ALIGNMENT}
            toggle.addActionListener {contents.isVisible=!contents.isVisible;toggle.expanded=contents.isVisible;inspector.revalidate()}
            card.add(toggle);card.add(Box.createVerticalStrut(12));build(contents);card.add(contents)
            inspector.add(card);inspector.add(Box.createVerticalStrut(12))
        }
        fun option(panel: JPanel,label: String,component: JComponent) {
            panel.add(JLabel(label).apply { alignmentX=LEFT_ALIGNMENT });panel.add(Box.createVerticalStrut(5))
            component.alignmentX=LEFT_ALIGNMENT;component.maximumSize=Dimension(Int.MAX_VALUE,36);panel.add(component);panel.add(Box.createVerticalStrut(10))
        }
        card("Modalidad de firma") { option(it,"Formato",mode);it.add(tsa)
            it.add(JLabel("<html>Requiere internet. Se publica solo<br>si el sello de tiempo se verifica.</html>").apply { font=font.deriveFont(11f);foreground=AetherSwingTheme.muted }) }
        card("Estilo y plantilla") { panel ->
            panel.add(JPanel(GridLayout(2,2,6,6)).apply {
                isOpaque=false;name="stamp-templates";maximumSize=Dimension(Int.MAX_VALUE,80)
                for(template in StampTemplate.entries) {
                    val caption=when(template) {StampTemplate.INSTITUTIONAL->"Institucional";StampTemplate.EXECUTIVE->"Ejecutivo";StampTemplate.APPROVAL->"Visto bueno";StampTemplate.PRINT->"Impresión B/N"}
                    add(AetherToggleChip(caption).apply {
                        name="stamp-template-"+template.name.lowercase();toolTipText=template.label
                        addActionListener {if(!busy) {restoreDesign(currentDesign().copy(options=template.apply(options())));scheduleStamp()}}
                        templateButtons[template]=this;mutableControls.add(this)
                    })
                }
            })
            panel.add(Box.createVerticalStrut(10))
            option(panel,"Estilo",style);panel.add(imageButton);panel.add(clearWhite)
            panel.add(JLabel("Color del sello"));panel.add(colors);option(panel,"Fondo",backgroundMode);option(panel,"Espaciado",density);panel.add(showBorder)
        }
        card("Insignia y contenido") { panel ->
            option(panel,"Insignia",badge);option(panel,"Formato de fecha",dateFormat)
            panel.add(JPanel(java.awt.GridLayout(0,2,4,2)).apply { isOpaque=false;fields.values.forEach(::add) });panel.add(Box.createVerticalStrut(10))
            option(panel,"Título del sello",title);option(panel,"Motivo",reason);option(panel,"Lugar",location);panel.add(tsaBadge)
        }
        inspector.add(AetherButton("Restaurar diseño por defecto",ButtonVariant.GHOST).apply { name="restore-stamp-design";addActionListener { if(!busy) { restoreDesign(SuiteDesign());previewDni="00000000";scheduleStamp();status.text="Diseño restablecido. La conexión se conserva." } };mutableControls.add(this) })
        inspector.add(Box.createVerticalGlue())
        val options=JScrollPane(inspector).aetherScroll().apply { preferredSize=Dimension(310,500);horizontalScrollBarPolicy=JScrollPane.HORIZONTAL_SCROLLBAR_NEVER;verticalScrollBar.unitIncrement=16 }
        val body=JPanel(BorderLayout(14,0)).apply { isOpaque=false;border=EmptyBorder(0,20,0,20) }
        val split=JSplitPane(JSplitPane.HORIZONTAL_SPLIT,filePanel,preview).apply { resizeWeight=0.0;dividerLocation=280;dividerSize=10;border=null;isOpaque=false }
        body.add(split,BorderLayout.CENTER);body.add(options,BorderLayout.EAST);add(body,BorderLayout.CENTER)
        val footer=JPanel(BorderLayout(10,10)).apply { isOpaque=false;border=EmptyBorder(8,22,16,22) }
        status.font=status.font.deriveFont(12f)
        footer.add(JPanel(BorderLayout(0,6)).apply {isOpaque=false;add(status,BorderLayout.CENTER);add(progress,BorderLayout.SOUTH)},BorderLayout.CENTER)
        footer.add(JPanel(FlowLayout(FlowLayout.RIGHT)).apply { isOpaque=false;add(openPdf);add(open);sign.preferredSize=Dimension(210,44);add(sign) },BorderLayout.EAST);add(footer,BorderLayout.SOUTH)
        mutableControls.addAll(listOf(pc,phone,workspace,identity,add,remove,list,page,mode,style,reason,location,tsa,badge,target,colors,title,previewName,tsaBadge,network,previous,next,backgroundMode,dateFormat,density,showBorder,imageButton,clearWhite)+fields.values)
        fun enumRenderer(box: JComboBox<*>) { box.renderer=DefaultListCellRenderer().let { delegate -> ListCellRenderer { l,value,index,selected,focus ->
            val label=when(value) { is StampStyle->value.label;is StampBadgeType->value.label;is StampPageTarget->value.label;is StampBackgroundMode->value.label;is StampDateFormat->value.label;is StampDensity->value.label;else->value.toString() }
            delegate.getListCellRendererComponent(l,label,index,selected,focus).also { (it as JComponent).border=EmptyBorder(6,10,6,10) } } } }
        listOf(style,badge,target,backgroundMode,dateFormat,density).forEach(::enumRenderer)
        list.transferHandler=object: TransferHandler() {
            override fun canImport(support: TransferSupport): Boolean {
                val accepted=!busy && support.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor)
                if(support.isDrop)canvas.dropHighlight=accepted
                return accepted
            }
            override fun importData(support: TransferSupport): Boolean {
                canvas.dropHighlight=false
                if(!canImport(support)) return false
                val dropped=runCatching { (support.transferable.getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor) as List<*>).filterIsInstance<java.io.File>() }.getOrNull()?:return false
                canvas.dropHighlight=false
                addFiles(dropped.filter {it.extension.equals("pdf",true)},javax.swing.filechooser.FileSystemView.getFileSystemView());return true
            }
        }
        canvas.transferHandler=list.transferHandler
        imageButton.name="stamp-image";backgroundMode.name="stamp-background";dateFormat.name="stamp-date-format";density.name="stamp-density";themeButton.name="suite-theme"
        imageButton.addActionListener {if(!busy && !imageLoading)chooseImage()}
        guideButton.addActionListener { quickGuide.open() }
        themeButton.addActionListener {
            AetherSwingTheme.apply(this,!AetherSwingTheme.dark);prefs.putBoolean("theme.dark",AetherSwingTheme.dark)
            updateThemeButton(themeButton)
        }
        listOf(canvas,list).forEach {component->PdfDropSupport.install(component,{!busy},{canvas.dropHighlight=it}) {dropped->addFiles(dropped,javax.swing.filechooser.FileSystemView.getFileSystemView())}}

        canvas.resized={scheduleStamp()};canvas.changed={scheduleStamp()};colors.changed={scheduleStamp()}
        list.addMouseListener(object: MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) { if(e.clickCount==2 && SwingUtilities.isLeftMouseButton(e)) openSigned(list.selectedValue,false) }
            override fun mousePressed(e: MouseEvent) { menu(e) }
            override fun mouseReleased(e: MouseEvent) { menu(e) }
            private fun menu(e: MouseEvent) {
                if(!e.isPopupTrigger)return
                val index=list.locationToIndex(e.point);if(index<0 || list.getCellBounds(index,index)?.contains(e.point)!=true)return
                list.selectedIndex=index;val input=list.selectedValue
                JPopupMenu().apply {
                    add(JMenuItem("Abrir archivo firmado").apply { isEnabled=signedOutputs.containsKey(input);addActionListener { openSigned(input,false) } })
                    add(JMenuItem("Ver ubicación del resultado").apply { isEnabled=signedOutputs.containsKey(input);addActionListener { openSigned(input,true) } })
                    show(list,e.x,e.y)
                }
            }
        })
        listOf(style,badge,tsa,tsaBadge,backgroundMode,dateFormat,density,showBorder).forEach { control -> when(control) { is JComboBox<*>->control.addActionListener { scheduleStamp() };is JCheckBox->control.addActionListener { scheduleStamp() } } }
        fields.values.forEach { it.addActionListener { scheduleStamp() } }
        listOf(title,reason,location,previewName).forEach { input -> input.document.addDocumentListener(object: javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent)=scheduleStamp()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent)=scheduleStamp()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent)=scheduleStamp()
        }) }
        target.addActionListener { val choice=target.selectedItem as StampPageTarget
            if(pageCount>0 && canvas.image!=null && choice in listOf(StampPageTarget.FIRST_PAGE,StampPageTarget.LAST_PAGE)) { preferredPage=null;page.value=if(choice==StampPageTarget.FIRST_PAGE) 1 else pageCount }
            else if(canvas.image==null) preferredPage=when(choice) { StampPageTarget.LAST_PAGE->Int.MAX_VALUE;StampPageTarget.FIRST_PAGE->1;else->(page.value as Number).toInt() }
            scheduleStamp()
        }
        network.addActionListener { advanced.setLocationRelativeTo(this);advanced.isVisible=true }
        files.addListDataListener(object: javax.swing.event.ListDataListener {
            private fun update() { sign.isEnabled=connected && !busy && !files.isEmpty;sign.text="Firmar lote (${files.size()})" }
            override fun intervalAdded(e: javax.swing.event.ListDataEvent) = update()
            override fun intervalRemoved(e: javax.swing.event.ListDataEvent) = update()
            override fun contentsChanged(e: javax.swing.event.ListDataEvent) = update()
        })
        connect.addActionListener { connect() };disconnect.addActionListener { if(busy && connected && !AetherConfirmDialog.ask(this,"Desconectar","Interrumpir un lote puede dejar resultados parciales.","Desconectar")) return@addActionListener; controller.disconnect();connecting=false;pairing=null;pairingQr.close();connected=false;setBusy(false);status.text="Desconectado. No se repite ninguna operación." }
        qr.addActionListener { pairing?.let(pairingQr::open) ?: connect(showQr=true) }
        add.addActionListener {
            val chooser=JFileChooser().apply { isMultiSelectionEnabled=true }
            if(chooser.showOpenDialog(this)==JFileChooser.APPROVE_OPTION) {
                addFiles(chooser.selectedFiles.toList(),chooser.fileSystemView)
            }
        }
        remove.addActionListener { list.selectedIndices.sortedDescending().forEach(files::remove) }
        list.addListSelectionListener { if(!it.valueIsAdjusting) {updateResultActions(); if(!firstSelection || preferredPage==null)preferredPage=1;firstSelection=false;if((page.value as Int)!=1) page.value=1 else loadPreview() } }
        page.addChangeListener { loadPreview() }
        mode.addActionListener { canvas.visibleStamp=mode.selectedIndex==0;tsa.isEnabled=!busy && mode.selectedIndex!=2;scheduleStamp() }
        sign.addActionListener { sign() }
        openPdf.addActionListener { openSigned(list.selectedValue,false) }
        open.addActionListener { resultDirectory?.let { runCatching { Desktop.getDesktop().open(it.toFile()) }.onFailure { status.text="No se pudo abrir la carpeta de salida." } } }
        addWindowListener(object: WindowAdapter() { override fun windowClosing(e: WindowEvent) {
            if(!busy || AetherConfirmDialog.ask(this@SuiteWindow,"Cerrar Suite","Hay una operación activa. Cerrar interrumpe la conexión; no se repetirá.","Cerrar Suite")) {
                dispose()
            }
        } })
        fun align(container: Container) {
            if(container.layout is BoxLayout) container.components.filterIsInstance<JComponent>().forEach {
                it.alignmentX=Component.LEFT_ALIGNMENT
                if(it is JLabel) it.maximumSize=Dimension(Int.MAX_VALUE,it.preferredSize.height+3)
            }
            container.components.filterIsInstance<Container>().forEach(::align)
        }
        align(contentPane);align(advanced.contentPane)
        status.addPropertyChangeListener("text") { status.toolTipText=status.text }
        AetherSwingTheme.apply(this,prefs.getBoolean("theme.dark",false));updateThemeButton(themeButton)
        restoreDesign(savedDesign);setBusy(false);scheduleStamp()
        if(initialFiles.isNotEmpty()) addFiles(initialFiles.map(Path::toFile),javax.swing.filechooser.FileSystemView.getFileSystemView())
    }
    private fun connect(showQr: Boolean=false) {
        try {
            val output=Path.of(workspace.text).toAbsolutePath();val own=Path.of(identity.text).toAbsolutePath()
            val remote=phone.text.trim()
            if(remote.isNotEmpty() && !pe.gob.reniec.dnie.signing.link.SigningProvision.privateAddress(remote) && !pe.gob.reniec.dnie.desktop.remote.NetworkTargets.tailnetHost(remote)) {
                status.text="Deja el teléfono vacío para buscarlo o indica una IP privada/nombre Tailscale válido.";return
            }
            val previous=pc.text.trim()
            val local=pe.gob.reniec.dnie.desktop.remote.SuiteNetworkAddress.choose(previous,remote)
            pc.text=local
            status.text="Ruta automática de PC: $local"
            connecting=true;pairing=null;pairingQr.close();setBusy(true);qr.isEnabled=false;status.text="Esperando Android. Abre Suite continua en el teléfono…"
            controller.connect(local,remote,output,own,{ info ->
                pairing=info;fingerprint.text="Huella PC: ${info.fingerprint}";qr.isEnabled=true
                status.text=if(info.firstEnrollment) "Vincula esta PC mediante TXT/QR en Android." else "PC recordada: pulsa Conectar PC guardada en Android. La IP puede cambiar."
                if(showQr) pairingQr.open(info)
            },{ ok,message -> connecting=false;pairing=null;pairingQr.close();connected=ok;setBusy(false);status.text=message
                if(ok) {
                    val found=controller.connectedPhone().ifEmpty {remote}
                    phone.text=found
                    val history=(listOf(found)+prefs.get("phone.history","").split('\n')).filter {it.isNotBlank()}.distinct().take(4)
                    prefs.put("phone.history",history.joinToString("\n"))
                    phone.removeAllItems();phone.addItem("");history.forEach(phone::addItem);phone.text=found
                    pc.text=controller.connectedPc().ifEmpty {local}
                    prefs.put("pc",pc.text);prefs.put("phone",found);prefs.put("workspace",output.toString());prefs.put("identity",own.toString()) }
            })
        } catch(_: Exception) { connecting=false;setBusy(false);status.text="Configuración de conexión inválida." }
    }
    private fun loadPreview() {
        val file=list.selectedValue
        if(file==null || !file.fileName.toString().endsWith(".pdf",true)) { controller.invalidatePreview();canvas.image=null;pageCount=0;pageWidth=595f;pageHeight=842f;pageInfo.text="Muestra A4";scheduleStamp();return }
        canvas.image=null;pageInfo.text="Cargando…"
        controller.preview(file,(page.value as Number).toInt()) { result,message ->
            if(result==null) { canvas.image=null;pageInfo.text=message;pageWidth=595f;pageHeight=842f;scheduleStamp() } else {
                pageCount=result.count;(page.model as SpinnerNumberModel).maximum=result.count
                val desired=if(preferredPage==null)result.number else when(target.selectedItem) { StampPageTarget.FIRST_PAGE->1;StampPageTarget.LAST_PAGE->result.count;else->preferredPage!!.coerceIn(1,result.count) }
                preferredPage=null
                if((page.value as Number).toInt()!=desired) { result.image.flush();page.value=desired } else {
                    canvas.image=result.image;pageInfo.text="${result.number} / ${result.count}";pageWidth=result.widthPoints;pageHeight=result.heightPoints;scheduleStamp()
                }
            }
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
        if(imageLoading) {status.text="Espera a que termine de cargar la rúbrica.";return}
        val pin=AetherPinDialog(this,inputs.size).requestPin() ?: return
        if(pin.size !in 4..8 || pin.any { it !in '0'..'9' }) { pin.fill('\u0000');status.text="PIN inválido; no enviado al DNI.";return }
        try {
            val pdfMode=when(mode.selectedIndex) { 0->DesktopPdfMode.VISIBLE;1->DesktopPdfMode.INVISIBLE;else->DesktopPdfMode.DETACHED }
            val options=options().copy(visible=pdfMode==DesktopPdfMode.VISIBLE,reniecTimestamp=tsa.isSelected && pdfMode!=DesktopPdfMode.DETACHED)
            val request=DesktopSigningRequest(inputs,Path.of(workspace.text),pdfMode,options)
            setBusy(true);status.text="Lote en curso. Mantén el DNI cerca del teléfono…"
            controller.sign(request,pin) { result,message,sessionUsable ->
                if(result!=null)showCompleted(inputs,result)
                connected=sessionUsable
                setBusy(false);status.text=message;open.isEnabled=resultDirectory!=null
            }
        } catch(_: Exception) { pin.fill('\u0000');setBusy(false);status.text="No se pudo preparar el lote." }
    }
    internal fun showCompleted(inputs: List<Path>,result: DesktopSigningResult) {
        resultDirectory=result.directory
        inputs.forEach(signedOutputs::remove);signedOutputs.putAll(CompletedBatchPresentation.outputs(inputs,result))
        inputs.zip(result.documents).forEach { (path,document) -> documentStates[path]=if(document.signature!=null) "Firmado" else "No firmado" };list.repaint();updateResultActions()
        controller.completedCitizen(result) { citizen -> if(citizen!=null) { previewName.text=citizen.fullName;previewDni=citizen.dniNumber;scheduleStamp() } }
    }
    private fun setBusy(value: Boolean) {
        bridgePanel?.setSuiteActive(connected || value)
        busy=value;progress.isVisible=value;progress.isIndeterminate=value;pill.state=if(connected) PillState.CONNECTED else if(connecting) PillState.WAITING else PillState.IDLE
        pill.text=if(connected) "Conectado" else if(connecting) "Esperando móvil" else "Desconectado"
        mutableControls.forEach { it.isEnabled=!value };canvas.interactive=!value
        connect.isEnabled=!connected && !value;disconnect.isEnabled=connected || connecting;sign.isEnabled=connected && !value && !files.isEmpty
        tsa.isEnabled=!value && mode.selectedIndex!=2
        qr.isEnabled=!connected && (pairing!=null || !value)
        updateResultActions()
    }
    private fun buildAdvancedDialog() {
        advanced.defaultCloseOperation=JDialog.HIDE_ON_CLOSE
        val content=AetherCardPanel().apply { layout=BoxLayout(this,BoxLayout.Y_AXIS) }
        fun input(label: String,component: JComponent) { content.add(JLabel(label));content.add(Box.createVerticalStrut(6));component.maximumSize=Dimension(Int.MAX_VALUE,36);content.add(component);content.add(Box.createVerticalStrut(14)) }
        input("IP de esta PC (se actualiza según la red)",pc);input("Carpeta de resultados",workspace);input("Archivo de identidad DPAPI",identity)
        input("Huella SHA-256 de la PC",fingerprint)
        content.add(AetherButton("Copiar huella").apply { addActionListener { Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(fingerprint.text),null) } })
        bridgePanel?.let { content.add(Box.createVerticalStrut(18));content.add(it) }
        content.add(Box.createVerticalStrut(16));content.add(AetherButton("Listo",ButtonVariant.PRIMARY).apply { addActionListener { advanced.isVisible=false } })
        advanced.contentPane=JScrollPane(content).aetherScroll().apply { verticalScrollBar.unitIncrement=16;horizontalScrollBarPolicy=JScrollPane.HORIZONTAL_SCROLLBAR_NEVER };advanced.setSize(800,if(bridgePanel!=null) 640 else 440)
    }
    private fun addFiles(selected: List<java.io.File>,view: javax.swing.filechooser.FileSystemView) {
        setBusy(true);status.text="Preparando archivos…"
        controller.prepareFiles(selected,view) { paths,rejected ->
            val room=50-files.size();val accepted=paths.filterNot(files::contains).take(room)
            accepted.forEach { files.addElement(it);documentStates[it]="Pendiente" }
            controller.fileSizes(accepted) { sizes.putAll(it);list.repaint() }
            if(list.selectedIndex<0 && files.size()>0) list.selectedIndex=0
            setBusy(false)
            status.text=when { rejected>0->"$rejected archivo(s) no disponibles. Selecciona el original desde su carpeta.";paths.size>room->"Límite de 50 archivos. Se añadieron ${accepted.size}.";else->"Archivos preparados." }
        }
    }
    private fun chooseImage() {
        val chooser=JFileChooser().apply {fileFilter=javax.swing.filechooser.FileNameExtensionFilter("Imagen PNG","png")}
        if(chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return
        val path=chooser.selectedFile.toPath();val clean=clearWhite.isSelected
        imageLoading=true;imageButton.isEnabled=false;status.text="Preparando imagen…"
        object: SwingWorker<ByteArray,Unit>() {
            override fun doInBackground()=StampImageLoader.load(path,clean)
            override fun done() {
                imageLoading=false
                if(resourcesClosed)return
                imageButton.isEnabled=!busy
                runCatching {get()}.onSuccess {bytes->handwrittenImage=bytes;style.selectedItem=if(style.selectedItem==StampStyle.HANDWRITTEN_ONLY) StampStyle.HANDWRITTEN_ONLY else StampStyle.INK;scheduleStamp();status.text="Rúbrica cargada para esta sesión."}
                    .onFailure {status.text="No se pudo cargar PNG: máximo 512 KiB y 4 megapíxeles."}
            }
        }.execute()
    }
    private fun options()=PdfSigningOptions(pageNumber=(page.value as Number).toInt(),pageTarget=target.selectedItem as StampPageTarget,
        placement=canvas.placement,visible=mode.selectedIndex==0,reniecTimestamp=tsa.isSelected,reason=reason.text,location=location.text,
        appearance=SignatureAppearance(style=style.selectedItem as StampStyle,colorTheme=colors.selected,badge=badge.selectedItem as StampBadgeType,
            customHeader=title.text.take(160),showTsaBadge=tsaBadge.isSelected,
            handwrittenPng=handwrittenImage?.copyOf(),showHandwritten=style.selectedItem in listOf(StampStyle.INK,StampStyle.HANDWRITTEN_ONLY),
            backgroundMode=backgroundMode.selectedItem as StampBackgroundMode,dateFormat=dateFormat.selectedItem as StampDateFormat,density=density.selectedItem as StampDensity,showBorder=showBorder.isSelected,
            showDni=fields.getValue("DNI").isSelected,showDate=fields.getValue("Fecha").isSelected,showTime=fields.getValue("Hora").isSelected,
            showReason=fields.getValue("Motivo").isSelected,showLocation=fields.getValue("Lugar").isSelected))
    private fun restoreDesign(design: SuiteDesign) {
        restoringDesign=true
        try {
            handwrittenImage=design.options.appearance.handwrittenPng?.copyOf()
            val o=design.options;val a=o.appearance;preferredPage=o.pageNumber
            mode.selectedIndex=when(design.mode) { DesktopPdfMode.VISIBLE->0;DesktopPdfMode.INVISIBLE->1;DesktopPdfMode.DETACHED->2 }
            backgroundMode.selectedItem=a.backgroundMode;dateFormat.selectedItem=a.dateFormat;density.selectedItem=a.density;showBorder.isSelected=a.showBorder
            style.selectedItem=a.style;badge.selectedItem=a.badge;colors.selected=a.colorTheme;title.text=a.customHeader
            reason.text=o.reason;location.text=o.location;tsa.isSelected=o.reniecTimestamp;tsaBadge.isSelected=a.showTsaBadge
            previewName.text=design.previewName;canvas.placement=o.placement;target.selectedItem=o.pageTarget
            fields.getValue("DNI").isSelected=a.showDni;fields.getValue("Fecha").isSelected=a.showDate;fields.getValue("Hora").isSelected=a.showTime
            fields.getValue("Motivo").isSelected=a.showReason;fields.getValue("Lugar").isSelected=a.showLocation
            canvas.visibleStamp=design.mode==DesktopPdfMode.VISIBLE
            if(pageCount>0) { val wanted=o.pageNumber.coerceIn(1,pageCount);if((page.value as Number).toInt()!=wanted)page.value=wanted else loadPreview() }
        } finally { restoringDesign=false }
    }
    private fun currentDesign()=SuiteDesign(options(),when(mode.selectedIndex) { 0->DesktopPdfMode.VISIBLE;1->DesktopPdfMode.INVISIBLE;else->DesktopPdfMode.DETACHED },previewName.text)
    private fun scheduleStamp() { if(!restoringDesign) {
        val design=currentDesign()
        templateButtons.forEach { (template,button)->button.isSelected=SuiteTemplateSelection.matches(template,design.options) && design.mode==DesktopPdfMode.VISIBLE }
        designStore.save(design);stampTimer.restart()
    } }
    private fun updateResultActions() {
        open.isEnabled=!busy && resultDirectory!=null
        openPdf.isEnabled=!busy && signedOutputs[list.selectedValue]?.fileName?.toString()?.endsWith(".pdf",true)==true
    }
    private fun openSigned(input: Path?,reveal: Boolean) {
        val output=signedOutputs[input]?:return
        controller.openOutput(output,reveal,desktopActions) { ok -> if(!ok)status.text="No se pudo abrir el resultado; comprueba su carpeta." }
    }
    override fun dispose() {
        if(!resourcesClosed) { resourcesClosed=true;stampTimer.stop();canvas.stampImage=null;canvas.image=null;designStore.close();pairingQr.close();quickGuide.close();advanced.dispose();controller.close();bridgePanel?.close() }
        super.dispose()
    }
    private fun renderStamp() {
        if(!canvas.visibleStamp) return
        val configuration=options()
        controller.stamp(pageWidth*configuration.placement.width,pageHeight*configuration.placement.height,
            previewName.text.ifBlank { "NOMBRE DEL TITULAR" },configuration,previewTime,previewDni) { image,error -> canvas.stampImage=image;canvas.stampError=error }
    }
    private fun defaultPath(suffix: String): String = if(Files.isDirectory(Path.of("D:/DNIeToolchain"))) "D:/DNIeToolchain/$suffix"
        else Path.of(System.getProperty("user.home"),"DNIeSuite",suffix).toString()
}
