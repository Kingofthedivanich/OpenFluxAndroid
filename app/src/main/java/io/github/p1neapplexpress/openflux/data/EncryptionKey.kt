package io.github.p1neapplexpress.openflux.data

/** Optional PSK passed to OpenFlux via `--psk-file`, alongside a [PeerKey]. */
object EncryptionKey {

    /** OpenFlux rejects shorter secrets (transport/noise_keys.go: DerivePSK). */
    const val MIN_BYTES = 16

    /** OpenFlux reads the key file through strings.TrimSpace. */
    fun normalize(raw: String): String = raw.trim()

    fun isValid(raw: String): Boolean =
        normalize(raw).toByteArray(Charsets.UTF_8).size >= MIN_BYTES
}
