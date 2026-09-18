package io.github.p1neapplexpress.openflux.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerKeyTest {

    // 32 random bytes, base64 -- what OpenFlux prints as --exit-key-file's public key.
    private val valid32Bytes = "RVphg4JOCYHpz04FAKokhKoHsbdxyRItuyI75yuCN1E="

    @Test
    fun `needs exactly 32 bytes after base64 decode`() {
        assertTrue(PeerKey.isValid(valid32Bytes))
        assertFalse(PeerKey.isValid("4TRm308pxhJlZk3xLbmJQ0fUgQLAX4mZ53bHaKdbAA==")) // 31 bytes
        assertFalse(PeerKey.isValid(""))
    }

    @Test
    fun `rejects non-base64 input`() {
        assertFalse(PeerKey.isValid("not base64 at all!!"))
    }

    @Test
    fun `tolerates surrounding whitespace`() {
        assertTrue(PeerKey.isValid(" $valid32Bytes\n"))
    }

    @Test
    fun `normalize trims surrounding whitespace only`() {
        assertEquals("a b c", PeerKey.normalize("\t a b c \r\n"))
    }
}
