package org.spectral.asm

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes.ASM8
import org.objectweb.asm.tree.ClassNode
import org.spectral.asm.processor.Shadow

/**
 * Represents an ASM [ClassNode] or class which
 * is apart of a class group.
 *
 * @property group The class group this class belongs in.
 * @property node The internal [ClassNode] this object extends.
 * @constructor
 */
open class Class(val group: ClassGroup, internal val node: ClassNode) : ClassVisitor(ASM8) {

    /**
     * The name of the class.
     */
    @Shadow
    private val name: String? = null


}