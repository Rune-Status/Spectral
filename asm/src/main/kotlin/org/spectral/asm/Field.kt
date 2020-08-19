package org.spectral.asm

import org.objectweb.asm.Type
import org.objectweb.asm.tree.FieldNode
import org.spectral.asm.util.asm
import java.lang.reflect.Modifier

/**
 * Represents a field that is apart of a class.
 */
class Field private constructor(
    val group: ClassGroup,
    val owner: Class,
    val node: FieldNode,
    val read: Boolean
) : Matchable<Field>() {

    /**
     * Creates a real (known) field.
     *
     * @param group ClassGroup
     * @param owner Class
     * @param node FieldNode
     * @constructor
     */
    constructor(group: ClassGroup, owner: Class, node: FieldNode) : this(group, owner, node, true) {
        owner.fieldTypeRefs.add(this)
    }

    /**
     * Creates a non real (unknown) field.
     *
     * @param group ClassGroup
     * @param owner Class
     * @param name String
     * @param desc String
     * @constructor
     */
    constructor(group: ClassGroup, owner: Class, name: String, desc: String) : this(group, owner, DEFAULT_FIELD_NODE, false) {
        this.name = name
        this.desc = desc
        this.match = this
        owner.fieldTypeRefs.add(this)
    }

    override var name by asm(node::name)

    var desc by asm(node::desc)

    var access by asm(node::access)

    var value by asm(node::value)

    val type get() = Type.getType(desc)

    val isStatic: Boolean get() = Modifier.isStatic(this.access)

    val isPrivate: Boolean get() = Modifier.isPrivate(this.access)

    val readRefs = hashSetOf<Method>()

    val writeRefs = hashSetOf<Method>()

    val overrides = hashSetOf<Field>()

    override fun toString(): String {
        return "$owner.$name"
    }

    companion object {
        /**
         * An empty default field node instance.
         */
        private val DEFAULT_FIELD_NODE get() = FieldNode(0, "", "", null, null)
    }
}