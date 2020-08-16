package org.spectral.asm

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes.ASM8
import org.objectweb.asm.tree.ClassNode

class Class(val group: ClassGroup, private val node: ClassNode) : ClassVisitor(ASM8) { }