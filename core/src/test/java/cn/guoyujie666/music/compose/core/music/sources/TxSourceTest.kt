package cn.guoyujie666.music.compose.core.music.sources

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TxSourceTest {
    private val source = TxSource()

    private fun zzcSign(data: String): String {
        val m = TxSource::class.java.getDeclaredMethod("zzcSign", String::class.java)
        m.isAccessible = true
        return m.invoke(source, data) as String
    }

    /**
     * Expected values come from the RN implementation
     * (src/utils/musicSdk/tx/utils/crypto.js) which is accepted by the live server.
     * Guards the intentional out-of-range index 40 behavior (JS undefined -> ''):
     * part1 must be 7 chars, not throw StringIndexOutOfBoundsException.
     */
    @Test
    fun zzcSign_matchesRnImplementation() {
        assertEquals("zzcfa14dde89n1iwax0l5rimr0qwjexceiov4daaee8d6", zzcSign("hello"))
        assertEquals("zzc7746109xq501htmv4ipz7c8owlxfpihjm1fd695c7", zzcSign("""{"a":1}"""))
        assertEquals("zzc49f0875iejcysg4vwa3gb3rwwb4zvqfdu80185cf1", zzcSign("周杰伦"))
    }

    /** Integration test: hits the real QQ Music search API. */
    @Test
    fun searchMusic_returnsSongs() = runBlocking {
        val result = source.searchMusic("陈奕迅", 1, 5).getOrThrow()
        assertTrue("expected non-empty search result", result.list.isNotEmpty())
        assertTrue("expected total > 0", result.total > 0)
        val first = result.list.first()
        assertTrue("id should be prefixed with tx_", first.id.startsWith("tx_"))
        assertTrue("name should not be empty", first.name.isNotEmpty())
        assertTrue("should have at least one quality",
            (first.meta as cn.guoyujie666.music.compose.core.model.MusicInfoMetaOnline).qualitys.isNotEmpty())
    }

    /** Keywords containing JSON special chars must not break the payload. */
    @Test
    fun searchMusic_escapesSpecialChars() = runBlocking {
        // Must not throw due to invalid JSON; empty result list is acceptable.
        val result = source.searchMusic("""a"b\c""", 1, 3)
        assertTrue("request should not fail: ${result.exceptionOrNull()}", result.isSuccess)
    }
}
