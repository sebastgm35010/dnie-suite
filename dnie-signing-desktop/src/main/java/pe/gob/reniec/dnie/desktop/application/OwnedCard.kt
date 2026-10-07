package pe.gob.reniec.dnie.desktop.application

import java.util.concurrent.atomic.AtomicBoolean
import pe.gob.reniec.dnie.core.nfc.DnieTransceiverInterface

/** The application owns the injected connection, including failures before signing. */
internal class OwnedCard(private val delegate: DnieTransceiverInterface) : DnieTransceiverInterface by delegate {
    private val closed = AtomicBoolean(false)
    override fun close() { if (closed.compareAndSet(false, true)) delegate.close() }
}
