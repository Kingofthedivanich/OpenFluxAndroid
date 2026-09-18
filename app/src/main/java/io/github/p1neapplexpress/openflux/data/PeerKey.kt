package io.github.p1neapplexpress.openflux.data

import java.util.Base64

/** The exit node's Noise static public key, as printed at its startup (--peer-key). */
object PeerKey {

    /** OpenFlux's X25519 public keys are exactly 32 bytes (transport/noise_keys.go). */
    private const val KEY_BYTES = 32

    fun normalize(raw: String): String = raw.trim()

    fun isValid(raw: String): Boolean {
        val decoded = runCatching {
            Base64.getDecoder().decode(normalize(raw))
        }.getOrNull() ?: return false
        return decoded.size == KEY_BYTES
    }
}
