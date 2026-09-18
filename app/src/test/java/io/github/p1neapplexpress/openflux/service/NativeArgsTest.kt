package io.github.p1neapplexpress.openflux.service

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeArgsTest {

    private val socks = "127.0.0.1:4000"

    @Test
    fun `no peer key opts into plaintext and keeps the rest of the payload`() {
        val payload = listOf("--client", "--transport", "yandex", "--url", "https://d", "--debug")
        assertEquals(
            listOf(
                "--role", "client", "--inbound", "socks5", "--socks5", socks, "--allow-plaintext",
                "--transport", "yandex", "--url", "https://d", "--debug",
            ),
            NativeArgs.build(payload, socks, pskFile = null, peerKey = null),
        )
    }

    @Test
    fun `peer key alone enables encryption without a psk`() {
        val payload = listOf("--transport", "yandex", "--url", "https://d")
        assertEquals(
            listOf(
                "--role", "client", "--inbound", "socks5", "--socks5", socks, "--peer-key", "abc123",
                "--transport", "yandex", "--url", "https://d",
            ),
            NativeArgs.build(payload, socks, pskFile = null, peerKey = "abc123"),
        )
    }

    @Test
    fun `peer key with a psk file sets both`() {
        val payload = listOf("--transport", "yandex", "--url", "https://d")
        assertEquals(
            listOf(
                "--role", "client", "--inbound", "socks5", "--socks5", socks,
                "--peer-key", "abc123", "--psk-file", "/data/psk",
                "--transport", "yandex", "--url", "https://d",
            ),
            NativeArgs.build(payload, socks, pskFile = "/data/psk", peerKey = "abc123"),
        )
    }

    @Test
    fun `flags owned by the app are replaced`() {
        val payload = listOf(
            "--role=exit", "--socks5", ":1080", "--peer-key=stale", "--psk-file=/sdcard/stale",
            "-i", "tun", "--transport", "mailru", "--url", "a/b",
        )
        assertEquals(
            listOf(
                "--role", "client", "--inbound", "socks5", "--socks5", socks,
                "--peer-key", "fresh", "--psk-file", "/data/psk", "--transport", "mailru", "--url", "a/b",
            ),
            NativeArgs.build(payload, socks, pskFile = "/data/psk", peerKey = "fresh"),
        )
    }

    @Test
    fun `url values containing equals signs survive`() {
        val payload = listOf("--transport", "cupsonline", "--url=https://x/?rooms=abc==")
        assertEquals(
            listOf(
                "--role", "client", "--inbound", "socks5", "--socks5", socks, "--allow-plaintext",
                "--transport", "cupsonline", "--url=https://x/?rooms=abc==",
            ),
            NativeArgs.build(payload, socks, pskFile = null, peerKey = null),
        )
    }

    @Test
    fun `redact hides the max token`() {
        assertEquals(
            listOf("--maxToken", "***", "--maxUid", "1"),
            NativeArgs.redact(listOf("--maxToken", "secret", "--maxUid", "1")),
        )
        assertEquals(listOf("--maxToken=***"), NativeArgs.redact(listOf("--maxToken=secret")))
    }
}
