package org.spectral.asm

import org.objectweb.asm.Type
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.MethodNode
import org.spectral.asm.util.asm
import java.lang.reflect.Modifier

/**
 * Represents a method within a class.
 */
class Method private constructor(
    val group: ClassGroup,
    val owner: Class,
    val node: MethodNode,
    val real: Boolean
){

    /**
     * Create a real (known) method.
     *
     * @param group ClassGroup
     * @param owner Class
     * @param node MethodNode
     * @constructor
     */
    constructor(group: ClassGroup, owner: Class, node: MethodNode) : this(group, owner, node, true)

    /**
     * Create a non real (unknown) method.
     *
     * @param group ClassGroup
     * @param owner Class
     * @param name String
     * @param desc String
     * @constructor
     */
    constructor(group: ClassGroup, owner: Class, name: String, desc: String) : this(group, owner, MethodNode(), false) {
        this.name = name
        this.desc = desc
        this.access = 0
    }

    var name: String by asm(node::name)

    var desc: String by asm(node::desc)

    var access: Int by asm(node::access)

    val instructions: InsnList by asm(node::instructions)

    val type get() = Type.getMethodType(desc)

    val returnType get() = type.returnType

    val argumentTypes get() = type.argumentTypes.toList()

    val isStatic: Boolean get() = Modifier.isStatic(this.access)

    val isPrivate: Boolean get() = Modifier.isPrivate(this.access)

    val refsIn = hashSetOf<Method>()

    val refsOut = hashSetOf<Method>()

    val fieldWriteRefs = hashSetOf<Field>()

    val fieldReadRefs = hashSetOf<Field>()

    val classRefs = hashSetOf<Class>()

    val overrides = hashSetOf<Method>()

    override fun toString(): String {
        return "$owner.$name$desc"
    }
}