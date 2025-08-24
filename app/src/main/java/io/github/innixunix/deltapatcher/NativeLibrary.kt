package io.github.innixunix.deltapatcher

class NativeLibrary {
    companion object {
        init {
            System.loadLibrary("deltapatcher")
        }

        external fun encode(
            originalPath: String,
            modifiedPath: String,
            outputPath: String,
            description: String,
            logCallback: LogCallback,
            useChecksum : Boolean,
            compressionLevel : Int,
            secondaryCompression : Int,
            srcWindowSize : Int,
            progressCallback: ProgressCallback? = null,
            ): Int

        external fun decode(
            originalPath: String,
            outputPath: String,
            patchPath: String,
            useChecksum : Boolean,
            logCallback: LogCallback,
            progressCallback: ProgressCallback? = null
        ): Int

        external fun getDescription(patchPath: String): String
    }

    interface LogCallback {
        fun onLogUpdate(message: String)
    }
    
    interface ProgressCallback {
        fun onProgressUpdate(progress: Float, message: String)
    }
}