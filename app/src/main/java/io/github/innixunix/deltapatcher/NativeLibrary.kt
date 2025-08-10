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
            logCallback: LogCallback
        ): Int

        external fun decode(
            originalPath: String,
            outputPath: String,
            patchPath: String,
            logCallback: LogCallback
        ): Int

        external fun getDescription(patchPath: String): String
    }

    interface LogCallback {
        fun onLogUpdate(message: String)
    }
}