package cn.guoyujie666.music.compose

import android.content.Intent
import android.net.Uri

/**
 * Handles deep link intents (lxmusic:// scheme, file opens).
 *
 * Ported from src/core/init/deeplink/.
 *
 * Supported schemes:
 * - lxmusic://search?keyword=xxx → navigate to search with keyword
 * - lxmusic://play?source=kw&id=xxx → play specific song
 * - File intents: .js, .json, .lxmc, audio files
 */
object DeepLinkHandler {

    sealed class DeepLinkAction {
        data class Search(val keyword: String) : DeepLinkAction()
        data class Play(val source: String, val id: String) : DeepLinkAction()
        data class ImportFile(val path: String, val type: String) : DeepLinkAction()
        data class OpenAudioFile(val path: String) : DeepLinkAction()
        data object Unknown : DeepLinkAction()
    }

    fun parseIntent(intent: Intent): DeepLinkAction {
        // Handle lxmusic:// scheme
        if (intent.action == Intent.ACTION_VIEW) {
            val uri = intent.data ?: return DeepLinkAction.Unknown

            when (uri.scheme) {
                "lxmusic" -> return parseLxMusicUri(uri)
                "file", "content" -> return parseFileUri(uri, intent.type)
            }
        }

        return DeepLinkAction.Unknown
    }

    private fun parseLxMusicUri(uri: Uri): DeepLinkAction {
        return when (uri.host) {
            "search" -> {
                val keyword = uri.getQueryParameter("keyword") ?: ""
                DeepLinkAction.Search(keyword)
            }
            "play" -> {
                val source = uri.getQueryParameter("source") ?: ""
                val id = uri.getQueryParameter("id") ?: ""
                DeepLinkAction.Play(source, id)
            }
            else -> DeepLinkAction.Unknown
        }
    }

    private fun parseFileUri(uri: Uri, mimeType: String?): DeepLinkAction {
        val path = uri.path ?: uri.toString()

        return when {
            // Audio files
            mimeType?.startsWith("audio/") == true ||
                path.let { p ->
                    AudioExtensions.any { ext -> p.lowercase().endsWith(".$ext") }
                } -> DeepLinkAction.OpenAudioFile(path)

            // Script/data files
            path.endsWith(".js", ignoreCase = true) ->
                DeepLinkAction.ImportFile(path, "script")
            path.endsWith(".json", ignoreCase = true) ||
                path.endsWith(".lxmc", ignoreCase = true) ->
                DeepLinkAction.ImportFile(path, "data")

            else -> DeepLinkAction.Unknown
        }
    }

    private val AudioExtensions = listOf(
        "mp3", "flac", "wav", "ape", "ogg", "m4a", "aac", "wma", "opus"
    )
}
