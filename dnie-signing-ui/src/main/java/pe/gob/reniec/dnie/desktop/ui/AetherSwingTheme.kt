package pe.gob.reniec.dnie.desktop.ui

import java.awt.Color
import java.awt.Font
import javax.swing.UIManager
import javax.swing.plaf.ColorUIResource
import javax.swing.plaf.FontUIResource

object AetherSwingTheme {
    var primary=Color(0x004AC6);var primaryContainer=Color(0xE5EEFF);var onPrimaryContainer=Color(0x00174B)
    var secondary=Color(0x006A63);var secondaryContainer=Color(0xD0F7F0)
    var background=Color(0xF8F9FF);var surface=Color.WHITE;var container=Color(0xEFF4FF)
    var border=Color(0xDCE9FF);var text=Color(0x0B1C30);var muted=Color(0x434655);var outline=Color(0x737686);var error=Color(0xBA1A1A)
    var dark=false
        private set
    private fun palette()=listOf(primary,primaryContainer,onPrimaryContainer,secondary,secondaryContainer,background,surface,container,border,text,muted,outline,error)
    fun apply(window: java.awt.Window,enabled: Boolean) {
        val old=palette();dark=enabled
        primary=Color(if(enabled) 0x4C8EFF else 0x004AC6);primaryContainer=Color(if(enabled) 0x1E293B else 0xE5EEFF);onPrimaryContainer=Color(if(enabled) 0xE2E8F0 else 0x00174B)
        secondary=Color(if(enabled) 0x67D9C8 else 0x006A63);secondaryContainer=Color(if(enabled) 0x163A38 else 0xD0F7F0)
        background=Color(if(enabled) 0x0B1326 else 0xF8F9FF);surface=Color(if(enabled) 0x131B2E else 0xFFFFFF);container=Color(if(enabled) 0x1E293B else 0xEFF4FF)
        border=Color(if(enabled) 0x2E3C56 else 0xDCE9FF);text=Color(if(enabled) 0xE2E8F0 else 0x0B1C30);muted=Color(if(enabled) 0xA9B5CB else 0x434655);outline=Color(if(enabled) 0x94A3B8 else 0x737686);error=Color(if(enabled) 0xFFB4AB else 0xBA1A1A)
        install();val replacements=old.zip(palette()).toMap()
        fun refresh(c: java.awt.Component) {
            replacements[c.background]?.let {c.background=it};replacements[c.foreground]?.let {c.foreground=it}
            if(c is javax.swing.text.JTextComponent) c.caretColor=primary
            if(c is java.awt.Container)c.components.forEach(::refresh)
            c.repaint()
        }
        refresh(window);window.ownedWindows.forEach(::refresh)
    }
    fun install() {
        var font=FontUIResource("Segoe UI",Font.PLAIN,13)
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
