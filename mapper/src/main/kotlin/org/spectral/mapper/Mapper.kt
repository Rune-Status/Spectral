package org.spectral.mapper

import org.spectral.asm.ClassEnvironment
import java.io.File

class Mapper {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val env = ClassEnvironment.init(File("runescape-client-190.jar"), File("gamepack-deob-clean.jar"))
            println()
        }
    }
}