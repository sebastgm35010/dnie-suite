package pe.gob.reniec.dnie.bridge

/** Exclusive protocol policy shared by session adapters and presentation. */
enum class BridgeMode(val windowsPace: Boolean, val translatePace: Boolean) {
    TRANSLATED_ANDROID_PACE(false, true),
    LOGICAL_ANDROID_PACE(false, false),
    DIRECT_WINDOWS_PACE(true, false)
}
