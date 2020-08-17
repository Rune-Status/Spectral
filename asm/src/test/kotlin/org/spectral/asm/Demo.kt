package org.spectral.asm

import org.junit.jupiter.api.Test
import java.io.File

class Demo {

    @Test
    fun loadJar() {

        val jarFile = File("../gamepack-deob-190.jar")
        val group = ClassGroup.fromJar(jarFile)
        println("lfkdsj")
    }
}