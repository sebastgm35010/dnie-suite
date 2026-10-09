package pe.gob.reniec.dnie.ui.bridge

/** Presentation state comes from transport callbacks, never from human-readable log text. */
enum class BridgeSessionStage {
    IDLE, PREPARING, WAITING_CARD, CONNECTING_PC, OPENING_CARD, ACTIVE, STOPPING;

    val visible get() = this != IDLE
    val pcConnected get() = this == WAITING_CARD || this == OPENING_CARD || this == ACTIVE
    val cardReady get() = this == ACTIVE
}
