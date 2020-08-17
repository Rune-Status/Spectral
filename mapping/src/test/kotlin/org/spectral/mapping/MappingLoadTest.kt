package org.spectral.mapping

import org.junit.jupiter.api.Test
import java.io.File

class MappingLoadTest {

    @Test
    fun test() {
        val folder = File("../runescape-mappings")

        val mappings = Mappings()
        mappings.load(folder)

        println()
    }
}