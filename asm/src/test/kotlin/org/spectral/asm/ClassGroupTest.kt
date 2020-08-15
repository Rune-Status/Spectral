package org.spectral.asm

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import java.io.File

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClassGroupTest {

    @Test
    fun `fromJar() test - no file exists`() {
        val jarFile = File("rufdsafdfnescape-client.jar")
        assertThrows<NoSuchFileException> {
            ClassGroup.fromJar(jarFile)
        }
    }
}