package org.spectral.deobfuscator

import org.spectral.asm.ClassGroup

/**
 * Represents a bytecode transformer step.
 */
interface Transformer {

    fun transform(group: ClassGroup)

}