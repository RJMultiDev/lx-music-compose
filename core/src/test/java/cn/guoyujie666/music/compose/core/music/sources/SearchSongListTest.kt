package cn.guoyujie666.music.compose.core.music.sources

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/** Integration tests: each hits the real platform songlist-search API. */
class SearchSongListTest {

    private fun check(sourceName: String, result: Result<List<cn.guoyujie666.music.compose.core.music.SongListItem>>) {
        val list = result.getOrThrow()
        assertTrue("$sourceName: expected non-empty songlist results", list.isNotEmpty())
        val first = list.first()
        assertTrue("$sourceName: id should not be empty", first.id.isNotEmpty())
        assertTrue("$sourceName: name should not be empty", first.name.isNotEmpty())
        assertTrue("$sourceName: source should be set", first.source.isNotEmpty())
    }

    @Test
    fun kw_searchSongList() = runBlocking {
        check("kw", KwSource().searchSongList("周杰伦", 1))
    }

    @Test
    fun kg_searchSongList() = runBlocking {
        val result = KgSource().searchSongList("周杰伦", 1)
        check("kg", result)
        assertTrue("kg: id should be prefixed with id_", result.getOrThrow().first().id.startsWith("id_"))
    }

    @Test
    fun tx_searchSongList() = runBlocking {
        check("tx", TxSource().searchSongList("林俊杰", 1))
    }

    @Test
    fun wy_searchSongList() = runBlocking {
        check("wy", WySource().searchSongList("周杰伦", 1))
    }

    @Test
    fun mg_searchSongList() = runBlocking {
        check("mg", MgSource().searchSongList("周杰伦", 1))
    }
}
