package pe.gob.reniec.dnie.desktop.ui

import java.awt.Color
import java.awt.Font
import javax.swing.UIManager
import javax.swing.plaf.ColorUIResource
import javax.swing.plaf.FontUIResource

object AetherSwingTheme {
    val primary=Color(0x004AC6);val primaryContainer=Color(0xE5EEFF);val onPrimaryContainer=Color(0x00174B)
    val secondary=Color(0x006A63);val secondaryContainer=Color(0xD0F7F0)
    val background=Color(0xF8F9FF);val surface=Color.WHITE;val container=Color(0xEFF4FF)
    val border=Color(0xDCE9FF);val text=Color(0x0B1C30);val muted=Color(0x434655);val outline=Color(0x737686);val error=Color(0xBA1A1A)
    fun install() {
        val font=FontUIResource("Segoe UI",Font.PLAIN,13)
        UIManager.getDefaults().keys().toList().filter { it.toString().endsWith(".font") }.forEach { UIManager.put(it,font) }
        for(key in listOf("Panel.background","Viewport.background","OptionPane.background")) UIManager.put(key,ColorUIResource(background))
        for(key in listOf("Label.foreground","TextField.foreground","ComboBox.foreground","CheckBox.foreground","List.foreground")) UIManager.put(key,ColorUIResource(text))
        for(key in listOf("TextField.background","PasswordField.background","List.background","ComboBox.background")) UIManager.put(key,ColorUIResource(surface))
        UIManager.put("List.selectionBackground",ColorUIResource(primaryContainer));UIManager.put("List.selectionForeground",ColorUIResource(text))
        UIManager.put("CheckBox.background",ColorUIResource(surface))
        UIManager.put("ScrollPane.border",javax.swing.BorderFactory.createEmptyBorder())
        UIManager.put("TextField.caretForeground",primary)
        UIManager.put("TextField.border",javax.swing.BorderFactory.createCompoundBorder(
            javax.swing.BorderFactory.createLineBorder(border),javax.swing.BorderFactory.createEmptyBorder(7,8,7,8)))
    }
}
