package pe.gob.reniec.dnie.desktop.ui

import javax.swing.SwingUtilities
import javax.swing.UIManager
fun main(args: Array<String>) {
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    SwingUtilities.invokeLater { SuiteWindow(args.take(50).map { java.nio.file.Path.of(it) }).isVisible=true }
}
