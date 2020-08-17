package org.spectral.asm

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Type
import org.objectweb.asm.tree.FieldNode
import org.spectral.asm.processor.Import
import java.lang.reflect.Modifier

/**
 * Represents an ASM [FieldNode] or a field which is apart of a class
 *
 * @property group ClassGroup
 * @property owner Class
 * @property node FieldNode
 * @constructor
 */
class Field(val group: ClassGroup, val owner: Class, internal val node: FieldNode) {

    /**
     * The name of the field.
     */
    @Import
    private val name: String? = null

    /**
     * The type descriptor of the field.
     */
    @Import
    private val desc: String? = null

    /**
     * The access bit-packed opcodes
     */
    @Import
    private val access: Int = -1

    /**
     * The ASM [Type] of this field
     */
    val type = Type.getType(node.desc)

    /**
     * A list of [Method] objects which read the value from
     * this field object.
     */
    val reads = mutableListOf<Method>()

    /**
     * A list of [Method] objects which write or change the value
     * of this field object.
     */
    val writes = mutableListOf<Method>()

    /**
     * Whether the field is static.
     */
    val isStatic: Boolean get() = Modifier.isStatic(node.access)

    /**
     * Whether the field is private.
     */
    val isPrivate: Boolean get() = Modifier.isPrivate(node.access)

    /**
     * Makes the given [classVisitor] visit this field's ASM [node]
     * object.
     *
     * @param classVisitor ClassVisitor
     */
    fun accept(classVisitor: ClassVisitor) {
        node.accept(classVisitor)
    }

    override fun toString(): String = "$owner.${node.name}"
}