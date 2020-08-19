package org.spectral.asm

import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.objectweb.asm.tree.ClassNode

class ClassTest {

    private val group = mockk<ClassGroup>()
    private val node = mockk<ClassNode>()

    @Test
    fun `create real class`() {
        val cls = Class(group, node)
        assert(cls.real)
    }

    @Test
    fun `create non real class`()  {
        val cls = Class(group, "test")
        assert(!cls.real)
    }

    @Test
    fun `asm node delegation`() {
        every { group[any()] } answers { mockk() }

        val cls = Class(group, "test")

        cls.name = "changed"
        assert(cls.node.name == "changed")
    }
}