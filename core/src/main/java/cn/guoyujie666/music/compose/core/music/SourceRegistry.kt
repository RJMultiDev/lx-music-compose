package cn.guoyujie666.music.compose.core.music

import cn.guoyujie666.music.compose.core.model.KnownSources
import cn.guoyujie666.music.compose.core.model.OnlineSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registry of all available music sources.
 *
 * Ported from src/utils/musicSdk/api-source-info.ts.
 * Sources can be built-in (kw, kg, tx, wy, mg) or user-defined (QuickJS-backed).
 */

data class SourceInfo(
    val id: OnlineSource,
    val name: String,
    val isEnabled: Boolean = true,
    val isUserSource: Boolean = false,
    val supportedActions: List<String> = emptyList()  // "musicUrl" | "lyric" | "pic"
)

@Singleton
class SourceRegistry @Inject constructor() {
    private val sources = mutableMapOf<OnlineSource, MusicSource>()
    private val sourceInfoList = mutableListOf<SourceInfo>()

    fun register(source: MusicSource) {
        sources[source.sourceId] = source
        sourceInfoList.add(
            SourceInfo(
                id = source.sourceId,
                name = source.sourceName,
                isEnabled = source.isEnabled,
                supportedActions = if (source is UserApiSource) {
                    source.apiInfo.sources?.values?.firstOrNull()?.actions ?: emptyList()
                } else {
                    listOf("musicUrl", "lyric", "pic")
                }
            )
        )
    }

    fun unregister(sourceId: OnlineSource) {
        sources.remove(sourceId)
        sourceInfoList.removeAll { it.id == sourceId }
    }

    fun getSource(sourceId: OnlineSource): MusicSource? = sources[sourceId]

    fun getAllSources(): List<MusicSource> = sources.values.toList()

    fun getAllSourceInfos(): List<SourceInfo> = sourceInfoList.toList()

    fun getEnabledSources(): List<MusicSource> =
        sources.values.filter { it.isEnabled }

    fun getEnabledOnlineSources(): List<OnlineSource> =
        sources.values.filter { it.isEnabled && it.sourceId != KnownSources.LOCAL }
            .map { it.sourceId }

    /** Find a source by name (case-insensitive partial match) */
    fun findSourceByName(name: String): List<MusicSource> =
        sources.values.filter { it.sourceName.contains(name, ignoreCase = true) }

    val isEmpty: Boolean get() = sources.isEmpty()
    val count: Int get() = sources.size
}
