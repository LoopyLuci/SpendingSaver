package com.spendingsaver.link

/**
 * JNI bridge to `libss_link.so` (spendingsaver-desktop/link, the Transferd overlay). Every call takes and
 * returns strings so no native types cross the boundary; replies are `{"ok":..,"result"|"error":..}`.
 *
 * The library is built by `scripts/build-link-android.ps1` and only exists for arm64 builds. When
 * it is missing, [available] is false and the app simply offers LAN sync only.
 */
object NativeLink {
    val available: Boolean = try {
        System.loadLibrary("ss_link")
        true
    } catch (_: UnsatisfiedLinkError) {
        false
    }

    @JvmStatic external fun nativeStart(stateDir: String, name: String, relaysCsv: String): String
    @JvmStatic external fun nativeCall(op: String, argsJson: String): String
    @JvmStatic external fun nativeStop()
}
