package org.spectral.deobfuscator

import org.spectral.asm.ext.ClassGroupExt

/**
 * Represents a bytecode transformer step.
 */
interface Transformer {

    fun transform(group: ClassGroupExt)

}