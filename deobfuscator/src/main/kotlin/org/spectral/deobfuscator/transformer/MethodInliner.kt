package org.spectral.deobfuscator.transformer

import com.google.common.collect.HashMultimap
import com.google.common.collect.Iterables
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.spectral.asm.ClassGroup
import org.spectral.asm.isStatic
import org.spectral.asm.owner
import org.spectral.asm.resolveMethod
import org.spectral.deobfuscator.Transformer
import org.tinylog.kotlin.Logger

/**
 * Static methods which are only invoked one time by another method
 * get moved into that class as a private method.
 */
class MethodInliner : Transformer {

    private val inRefs = HashMultimap.create<MethodNode, MethodNode>()
    private val outRefs = HashMultimap.create<MethodNode, MethodNode>()

    private var counter = 0

    override fun transform(group: ClassGroup) {
        /*
         * Build the method references.
         */
        this.buildMethodReferences(group)

        val singleInvokedMethods = inRefs.keys().filter { inRefs[it].size == 1 }.filter { it.isStatic }
        singleInvokedMethods.forEach {
            val invoker = inRefs[it].first()
            val toCls = invoker.owner
            val method = it

            println("Invoker: ${inRefs[it].first().owner.name}.${inRefs[it].first().name} Method: ${it.owner.name}.${it.name}")
            method.moveAndMakePrivate(invoker, toCls)
        }
    }

    private fun buildMethodReferences(group: ClassGroup) {
        Logger.info("Building method reference graph...")

        group.flatMap { it.methods }.forEach { m ->
            val it = m.instructions.iterator()
            while(it.hasNext()) {
                val insn = it.next()
                if(insn is MethodInsnNode) {
                    val toInterface = (insn.itf || insn.opcode == INVOKEINTERFACE)

                    val owner = group[insn.owner] ?: continue
                    val dst = owner.resolveMethod(insn.name, insn.desc, toInterface) ?: continue

                    inRefs[dst].add(m)
                    outRefs[m].add(dst)
                }
            }
        }
    }

    private fun MethodNode.moveAndMakePrivate(invoker: MethodNode, cls: ClassNode) {
        val exceptions = Iterables.toArray(this.exceptions, String::class.java)
        val copy = MethodNode(this.access, this.name, this.desc, this.signature, exceptions)
        copy.access = copy.access and (ACC_PUBLIC and ACC_STATIC) or ACC_PRIVATE
        copy.maxLocals = copy.maxLocals - 1
        this.accept(copy)

        //this.owner.methods.remove(this)

        cls.methods.add(copy)

    }

    private class MethodInlineVisitor(private val method: MethodNode, private val newClass: ClassNode, parent: MethodVisitor) : MethodVisitor(ASM8, parent) {
        override fun visitMethodInsn(
            opcode: Int,
            owner: String?,
            name: String?,
            desc: String?,
            isInterface: Boolean
        ) {
            if(owner == method.owner.name && name == method.name && desc == method.desc) {
                super.visitMethodInsn(
                    opcode,
                    newClass.name,
                    name,
                    desc,
                    isInterface
                )
            } else {
                super.visitMethodInsn(opcode, owner, name, desc, isInterface)
            }
        }
    }
}