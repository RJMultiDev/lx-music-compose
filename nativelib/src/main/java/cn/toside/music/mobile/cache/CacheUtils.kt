package cn.toside.music.mobile.cache

import android.content.Context
import java.io.File

/**
 * Kotlin wrapper replacing the RN CacheModule.
 * Provides app cache size calculation and cache clearing.
 */
object CacheUtils {

    /**
     * Get total app cache size in bytes as a string.
     */
    fun getAppCacheSize(context: Context): Long {
        var fileSize = 0L
        val cacheDir = context.cacheDir
        fileSize += Utils.getDirSize(cacheDir)
        if (Utils.isMethodsCompat(android.os.Build.VERSION_CODES.FROYO)) {
            val externalCacheDir = Utils.getExternalCacheDir(context)
            fileSize += Utils.getDirSize(externalCacheDir)
        }
        return fileSize
    }

    /**
     * Clear all app cache.
     */
    fun clearAppCache(context: Context) {
        // Delete webview databases
        context.deleteDatabase("webview.db")
        context.deleteDatabase("webview.db-shm")
        context.deleteDatabase("webview.db-wal")
        context.deleteDatabase("webviewCache.db")
        context.deleteDatabase("webviewCache.db-shm")
        context.deleteDatabase("webviewCache.db-wal")

        // Clear cache folders
        Utils.clearCacheFolder(context.cacheDir, System.currentTimeMillis())
        if (Utils.isMethodsCompat(android.os.Build.VERSION_CODES.FROYO)) {
            Utils.clearCacheFolder(Utils.getExternalCacheDir(context), System.currentTimeMillis())
        }
    }
}
