package org.spectral.asm

import org.objectweb.asm.Opcodes.ASM8
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.spectral.asm.util.asm
import java.util.stream.Collectors

/**
 * Represents a java class.
 *
 * @property group ClassGroup
 * @property node ClassNode
 * @property real Boolean
 * @constructor
 */
class Class private constructor(
    val group: ClassGroup,
    val node: ClassNode,
    val real: Boolean
) : Matchable<Class>() {

    /**
     * Creates a real known [Class] object.
     *
     * @param group ClassGroup
     * @param node ClassNode
     * @constructor
     */
    constructor(group: ClassGroup, node: ClassNode) : this(group, node, true)

    /**
     * Creates a non-real unknown [Class] object.
     *
     * @param group ClassGroup
     * @param name String
     * @constructor
     */
    constructor(group: ClassGroup, name: String) : this(group, ClassNode(ASM8), false) {
        this.name = name
        this.parentName = "java/lang/Object"
        this.match = this
    }

    var name: String by asm(node::name)

    var parentName: String by asm(node::superName)

    var access: Int by asm(node::access)

    val interfaceNames: List<String> by asm(node::interfaces)

    val type get() = Type.getObjectType(name)

    lateinit var methods: MutableSet<Method>

    lateinit var fields: MutableSet<Field>

    lateinit var parent: Class

    val children = hashSetOf<Class>()

    val interfaces = hashSetOf<Class>()

    val implementers = hashSetOf<Class>()

    val hierarchy = hashSetOf<Class>()

    val strings = hashSetOf<String>()

    val methodTypeRefs = hashSetOf<Method>()

    val fieldTypeRefs = hashSetOf<Field>()

    /**
     * Gets a method in the current class given the name
     * and descriptor of the method.
     *
     * If no method exists, a non-real method is created.
     *
     * @param name String
     * @param desc String
     * @return Method
     */
    fun getMethod(name: String, desc: String): Method {
        var method = methods.firstOrNull { it.name == name && it.desc == desc }
        if(method == null) {
            method = Method(group, this, name, desc)
            methods.add(method)
        }

        return method
    }

    fun getField(name: String, desc: String): Field {
        var field = fields.firstOrNull { it.name == name && it.desc == desc }
        if(field == null) {
            field = Field(group, this, name, desc)
            fields.add(field)
        }

        return field
    }

    fun resolveMethod(name: String, desc: String, toInterface: Boolean): Method? {
        return null
    }

    override fun toString(): String {
        return name
    }
}