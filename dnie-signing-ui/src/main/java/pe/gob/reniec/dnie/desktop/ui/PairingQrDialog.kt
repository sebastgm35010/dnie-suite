package pe.gob.reniec.dnie.desktop.ui

import java.awt.*
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.*
import javax.swing.border.EmptyBorder
import pe.gob.reniec.dnie.desktop.application.DesktopSuiteSession

/** A modeless local QR, retired with its connection; no scripts or Bridge DLL. */
internal class PairingQrDialog(private val owner: Window) : AutoCloseable {
    private var dialog: JDialog?=null
    private var preview: PairingQrImage?=null
    fun open(info: DesktopSuiteSession.PairingInfo) {
        check(SwingUtilities.isEventDispatchThread())
        dialog?.let { it.isVisible=true;it.toFront();return }
        val window=JDialog(owner,"Emparejar Android",Dialog.ModalityType.MODELESS)
        val qr=JLabel("Preparando QR…",SwingConstants.CENTER).apply { preferredSize=Dimension(450,450);isOpaque=true;background=Color.WHITE }
        val content=AetherCardPanel(BorderLayout(10,12)).apply { border=EmptyBorder(20,20,20,20) }
        content.add(JLabel("<html><b>Vincular teléfono con DNIe Suite</b><br><br>1 · Escanea este QR en Android.<br>2 · Compara la huella antes de recordar esta PC.</html>",AetherShieldIcon(),SwingConstants.LEFT),BorderLayout.NORTH)
        content.add(qr,BorderLayout.CENTER)
        content.add(JPanel(BorderLayout(8,8)).apply {
            val formatted=info.fingerprint.replace(":","").chunked(4).joinToString(" ")
            add(JTextArea("Huella PC:\n$formatted").apply { isEditable=false;lineWrap=true;wrapStyleWord=true;rows=3;isOpaque=false;font=Font(Font.MONOSPACED,Font.PLAIN,12) },BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.RIGHT,8,0)).apply { isOpaque=false
                add(AetherButton("Copiar huella").apply { addActionListener { Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(info.fingerprint),null);text="✓ Copiada" } })
                add(AetherButton("Cerrar",ButtonVariant.PRIMARY).apply { addActionListener { close() } })
            },BorderLayout.SOUTH)
        },BorderLayout.SOUTH)
        window.contentPane=content;window.defaultCloseOperation=WindowConstants.DO_NOTHING_ON_CLOSE
        window.addWindowListener(object: WindowAdapter() { override fun windowClosing(e: WindowEvent) { close() } })
        dialog=window;window.pack();window.setLocationRelativeTo(owner);window.isVisible=true
        object: SwingWorker<PairingQrImage,Unit>() {
            override fun doInBackground()=PairingQrImage.read(info.file,info.fingerprint)
            override fun done() {
                val result=runCatching { get() }.getOrNull()
                if(dialog!==window) { result?.close();return }
                if(result==null) qr.text="No se pudo preparar el QR. Reconecta e inténtalo otra vez."
                else { preview=result;qr.text="";qr.icon=ImageIcon(result.image) }
            }
        }.execute()
    }
    override fun close() {
        check(SwingUtilities.isEventDispatchThread())
        dialog?.dispose();dialog=null;preview?.close();preview=null
    }
}
