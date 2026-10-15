package pe.gob.reniec.dnie.desktop.ui

import java.awt.Color
import javax.swing.*
import java.awt.event.HierarchyEvent

/** Finite, EDT-only repaint animation. One timer per interacting control; idle controls have none running. */
internal class AetherSwingAnimator(private val owner: JComponent, private val reduced: () -> Boolean = { AetherSwingTheme.reducedMotion }) {
    var value = 0f; private set
    private var from = 0f
    private var target = 0f
    private var started = 0L
    private val timer = Timer(16) { tick(System.nanoTime()) }.apply { isCoalesce = true }
    val running get() = timer.isRunning
    init { owner.addHierarchyListener { if (it.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L && !owner.isShowing) stop() } }
    fun to(next: Float) {
        val bounded = next.coerceIn(0f, 1f)
        if (target == bounded && (running || value == bounded)) return
        target = bounded
        if (reduced() || !owner.isShowing) { stop(); return }
        from = value; started = System.nanoTime(); timer.start()
    }
    internal fun tick(now: Long) {
        val t = ((now - started) / 140_000_000f).coerceIn(0f, 1f)
        val ease = t * t * (3 - 2 * t)
        value = from + (target - from) * ease; owner.repaint()
        if (t == 1f) timer.stop()
    }
    fun stop() { timer.stop(); value = target; owner.repaint() }
    companion object {
        fun blend(from: Color, to: Color, fraction: Float): Color {
            val t = fraction.coerceIn(0f, 1f)
            fun channel(a: Int, b: Int) = (a + (b - a) * t).toInt().coerceIn(0,255)
            return Color(channel(from.red,to.red),channel(from.green,to.green),channel(from.blue,to.blue),channel(from.alpha,to.alpha))
        }
    }
}
