package io.github.innixunix.deltapatcher

import android.content.Context
import android.widget.Toast

class FileUtil {
    companion object {
        fun clearCache(context: Context) {
            try {
                val cacheDir = context.cacheDir
                if (cacheDir.exists()) {
                    cacheDir.listFiles()?.forEach { file ->
                        try {
                            if (file.isFile) {
                                file.delete()
                            } else if (file.isDirectory) {
                                file.deleteRecursively()
                            }
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                }
                
                Toast.makeText(context, "Delta Patcher is clearing cache", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                // ignore
            }
        }
    }
}
