package org.spectral.asm

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import org.spectral.asm.processor.Import
import java.lang.reflect.Modifier

/**
 * Represents an ASM [Method] or a method which
 * is apart of a [Class] object.
 *
 * @property group The [ClassGroup] this method belongs in.
 * @property owner The [Class] that this method belongs in.
 * @property node The internal [MethodNode] that this object is an extension of.
 * @constructor
 */
class Method(val group: ClassGroup, val owner: Class, val node: MethodNode) {

    /**
     * The name of the method.
     */
    @Import
    private val name: String? = null

    /**
     * The descriptor of the method.
     */
    @Import
    private val desc: String? = null

    /**
     * The access bit-packed opcodes.
     */
    @Import
    private val access: Int = -1

    /**
     * The ASM instructions this method contains.
     */
    @Import
    private val instructions: InsnList? = null

    /**
     * The ASM [AnnotationNode]s that are visible on the method.
     */
    @Import(name = "visibleAnnotations")
    private val annotations: MutableList<AnnotationNode>? = null

    /**
     * The ASM [Type] of this method.
     */
    val type = Type.getMethodType(node.desc)

    /**
     * The return ASM [Type] of this method.
     */
    val returnType = type.returnType

    /**
     * A list of ASM [Type]s for each argument in this method.
     */
    val argumentTypes = type.argumentTypes.asList()

    /**
     * The [Method]s that invoke this object.
     */
    val references = mutableListOf<Method>()

    /**
     * The [Method]s that this method invokes.
     */
    val invokes = mutableListOf<Method>()

    /**
     * The [Field]s that this method reads from.
     */
    val fieldReads = mutableListOf<Field>()

    /**
     * The [Field]s that this method writes to.
     */
    val fieldWrites = mutableListOf<Field>()

    /**
     * A list of methods that this method overrides in the hierarchy.
     */
    val overrides = mutableListOf<Method>()

    /**
     * Gets whether the method is static.
     */
    val isStatic: Boolean get() = Modifier.isStatic(node.access)

    /**
     * Whether the method is private
     */
    val isPrivate: Boolean get() = Modifier.isPrivate(node.access)

    /**
     * Gets whether the method is a class initializer.
     */
    val isInitializer: Boolean get() = (name == "<clinit>")

    /**
     * Gets whether the method is a constructor.
     */
    val isConstructor: Boolean get() = (name == "<init>")

    /**
     * Makes the given [methodVisitor] visit the ASM [node] attached
     * to this class.
     *
     * @param methodVisitor MethodVisitor
     */
    fun accept(methodVisitor: MethodVisitor) {
        node.accept(methodVisitor)
    }

    /**
     * Processes and builds the references for this
     * object.
     */
    internal fun process() {
        /*
         * Build method overrides model
         */
        owner.hierarchy.forEach { c ->
            val m = c.getMethod(node.name, node.desc)
            if(m != null) {
                overrides.add(m)
            }
        }

        /*
         * Process the method instructions.
         */
        val it = node.instructions.iterator()
        loop@ while(it.hasNext()) {
            when(val insn = it.next()) {
                /*
                 * Method Invoke Instruction
                 */
                is MethodInsnNode -> {
                    val method = group[insn.owner]?.resolveMethod(insn.name, insn.desc) ?: continue@loop
                    this.invokes.add(method)
                    method.references.add(this)
                }

                /*
                 * Field Invoke Instruction
                 */
                is FieldInsnNode -> {
                    val field = group[insn.owner]?.resolveField(insn.name, insn.desc) ?: continue@loop

                    /*
                     * Get whether this is a read or write.
                     */
                    if(insn.opcode == GETFIELD || insn.opcode == GETSTATIC) {
                        fieldReads.add(field)
                        field.reads.add(this)
                    }

                    if(insn.opcode == PUTFIELD || insn.opcode == PUTSTATIC) {
                        fieldWrites.add(field)
                        field.writes.add(this)
                    }
                }
            }
        }
    }

    override fun toString(): String = "$owner.${node.name}${node.desc}"
}