package org.spectral.deobfuscator.util

import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode
import org.spectral.asm.group
import org.spectral.asm.owner
import java.lang.reflect.Modifier

/*
 * Contains helpful utility methods for
 * dealing with some ASM base logic.
 *
 * Some of these methods might be best implemented into the :asm module library.
 */

val String.isObfuscatedName: Boolean get() {
    return (this.length <= 2 || (this.length == 3 && (this.startsWith("aa"))))
}