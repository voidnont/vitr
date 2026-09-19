package com.bloodvitr.vitr.save

import java.io.File

enum class SaveInputOwnership {
    Engine,
    Caller
}

object VitrSaveEnginePolicy {
    fun cleanupFiles(
        input: File,
        output: File,
        ownership: SaveInputOwnership
    ): List<File> = buildList {
        if (ownership == SaveInputOwnership.Engine) {
            add(input)
        }
        add(output)
    }
}
