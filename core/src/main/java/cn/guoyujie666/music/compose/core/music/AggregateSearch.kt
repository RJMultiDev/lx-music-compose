package cn.guoyujie666.music.compose.core.music

import cn.guoyujie666.music.compose.core.music.sources.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AggregateSearch @Inject constructor(
    private val kwSource: KwSource,
    private val kgSource: KgSource,
    private val txSource: TxSource,
    private val wySource: WySource,
    private val mgSource: MgSource,
    private val sourceRegistry: SourceRegistry
) {
    init {
        // Register built-in sources so SourceRegistry has a complete view
        allSources.forEach { sourceRegistry.register(it) }
    }

    fun getSource(id: String): MusicSource? = when (id) {
        "kw" -> kwSource; "kg" -> kgSource; "tx" -> txSource; "wy" -> wySource; "mg" -> mgSource
        else -> null
    }

    val allSources: List<MusicSource> get() = listOf(kwSource, kgSource, txSource, wySource, mgSource)

    suspend fun searchAll(keyword: String, page: Int = 1, limit: Int = 30): List<SearchResult> = coroutineScope {
        allSources.filter { it.isEnabled }.map { src ->
            async { src.searchMusic(keyword, page, limit).getOrElse { SearchResult() } }
        }.map { it.await() }
    }

    suspend fun searchOne(sourceId: String, keyword: String, page: Int = 1, limit: Int = 30): SearchResult {
        return getSource(sourceId)?.searchMusic(keyword, page, limit)?.getOrElse { SearchResult() } ?: SearchResult()
    }

    suspend fun searchSongListAll(keyword: String, page: Int = 1): List<SongListItem> = coroutineScope {
        allSources.filter { it.isEnabled }.map { src ->
            async { src.searchSongList(keyword, page).getOrElse { emptyList() } }
        }.flatMap { it.await() }
    }

    suspend fun searchSongListOne(sourceId: String, keyword: String, page: Int = 1): List<SongListItem> {
        return getSource(sourceId)?.searchSongList(keyword, page)?.getOrElse { emptyList() } ?: emptyList()
    }

    suspend fun hotSearchAll(): Map<String, List<String>> = coroutineScope {
        allSources.filter { it.isEnabled }.map { src ->
            async { src.sourceId to src.hotSearch().getOrElse { emptyList() } }
        }.map { it.await() }.toMap()
    }

    suspend fun hotSearchOne(sourceId: String): List<String> {
        return getSource(sourceId)?.hotSearch()?.getOrElse { emptyList() } ?: emptyList()
    }
}
