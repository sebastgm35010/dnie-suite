package pe.gob.reniec.dnie

import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import pe.gob.reniec.dnie.ui.passkeys.PasskeyToolsActivity

/** Public platform automation avoids Espresso 3.6's hidden InputManager API removed in API 37. */
class PasskeyToolsRuntimeTest {
    @Test fun independentMaintenanceScreenOpensAndScrollsWithoutNfc() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, PasskeyToolsActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        fun containsText(node: AccessibilityNodeInfo, text: String): Boolean {
            if (node.text?.toString()?.contains(text) == true) return true
            repeat(node.childCount) { i -> node.getChild(i)?.let { if (containsText(it, text)) return true } }
            return false
        }
        fun waitForText(text: String): Boolean {
            repeat(100) {
                if (instrumentation.uiAutomation.rootInActiveWindow?.let { containsText(it, text) } == true) return true
                Thread.sleep(100)
            }
            return false
        }
        fun scrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.isScrollable) return node
            repeat(node.childCount) { i -> node.getChild(i)?.let { child -> scrollable(child)?.let { return it } } }
            return null
        }
        try {
            assertTrue(waitForText("Configuración de passkeys"))
            assertFalse(activity.isFinishing)
            val scroll = scrollable(requireNotNull(instrumentation.uiAutomation.rootInActiveWindow))
            // Large displays can show the complete form without a scrollable range.
            if (scroll != null) repeat(3) { scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD); Thread.sleep(200) }
            assertTrue(waitForText("Volver"))
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
