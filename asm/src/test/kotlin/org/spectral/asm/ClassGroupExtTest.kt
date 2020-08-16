package org.spectral.asm

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.spectral.asm.ext.ClassGroupExt
import java.io.File

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClassGroupExtTest {

    @Test
    fun `fromJar() test - no file exists`() {
        val jarFile = File("rufdsafdfnescape-client.jar")
        assertThrows<NoSuchFileException> {
            ClassGroupExt.fromJar(jarFile)
        }
    }
}