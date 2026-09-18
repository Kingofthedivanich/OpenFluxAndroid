package io.github.p1neapplexpress.openflux.data

import kotlinx.serialization.Serializable

@Serializable
data class Tunnel(
    val id: Long,
    val name: String,
    val transportType: String,
    val transportConnPayload: List<String>,
    /** Exit node's Noise static public key (--peer-key), base64; null means unencrypted. */
    val peerKey: String? = null,
    /** Optional PSK on top of [peerKey] (--psk-file); ignored when peerKey is null. */
    val encryptionKey: String? = null,
)
