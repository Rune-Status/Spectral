package org.spectral.asm

import org.jgrapht.traverse.DepthFirstIterator
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.spectral.asm.processor.Import

/**
 * Represents an ASM [ClassNode] or class which
 * is apart of a class group.
 *
 * @property group The class group this class belongs in.
 * @property node The internal [ClassNode] this object extends.
 * @constructor
 */
open class Class(val group: ClassGroup, val node: ClassNode) {

    /**
     * The name of the class.
     */
    @Import(name = "name")
    private val name: String? = null

    /**
     * The name of the class which this class extends.
     */
    @Import(name = "superName")
    private val parentName: String? = null

    /**
     * The access bit-packed opcodes
     */
    @Import
    private val access: Int = -1

    /**
     * A list of class names which this class implements.
     */
    @Import(name = "interfaces")
    private val interfaceNames: List<String>? = null

    /**
     * The ASM [Type] of this class.
     */
    val type = Type.getObjectType(node.name)

    /**
     * The parent [Class] which this class extends if it is
     * apart of the same class group object.
     */
    var parent: Class? = null
        internal set

    /**
     * The [Class] objects which extend this class.
     */
    val children = mutableListOf<Class>()

    /**
     * The [Class]s that this class implements.
     */
    val interfaces = mutableListOf<Class>()

    /**
     * The [Class]s that implement this class as an interface.
     */
    val implementers = mutableListOf<Class>()

    /**
     * The Methods contained in this class object.
     */
    val methods = node.methods.map { Method(group, this, it) }

    /**
     * The fields contained in this class object.
     */
    val fields = node.fields.map { Field(group, this, it) }

    /**
     * The hierarchy classes for this class object.
     *
     * This model contains a list of [Class] objects which
     * this object has access to via inheritance.
     *
     * This information is deprived the the class group hierarchy graph
     * and the edges between this object's vertex in the graph.
     */
    val hierarchy = mutableListOf<Class>()

    /**
     * Gets a method inside the class given a name and descriptor of
     * the method.
     *
     * @param name The name of the method
     * @param desc The type descriptor of the method
     * @return Method?
     */
    fun getMethod(name: String, desc: String): Method? {
        return this.methods.firstOrNull { it.name == name && it.desc == desc }
    }

    /**
     * Gets a field inside the class given a name and descriptor of a field.
     *
     * @param name String
     * @param desc String
     * @return Field?
     */
    fun getField(name: String, desc: String): Field? {
        return this.fields.firstOrNull { it.node.name == name && it.node.desc == desc }
    }

    /**
     * Resolves a method from the current class or from
     * the hierarchy tree.
     *
     * @param name String
     * @param desc String
     * @return Method?
     */
    fun resolveMethod(name: String, desc: String): Method? {
        this.hierarchy.forEach { c ->
            val ret = getMethod(name, desc)
            if(ret != null) return ret
        }

        return null
    }

    /**
     * Resolves a field from the current class or from
     * the hierarchy tree.
     *
     * @param name String
     * @param desc String
     * @return Field?
     */
    fun resolveField(name: String, desc: String): Field? {
        this.hierarchy.forEach { c ->
            val ret = getField(name, desc)
            if(ret != null) return ret
        }

        return null
    }

    /**
     * Makes the given [classVisitor] visit the [ClassNode]
     * object that is embedded in this object.
     *
     * @param classVisitor ClassVisitor
     */
    fun accept(classVisitor: ClassVisitor) {
        this.node.accept(classVisitor)
    }

    /**
     * Post processing of data models in this class.
     */
    internal fun process() {
        /*
         * Build this class's hierarchy model
         */
        val hierarchyIterator = DepthFirstIterator(group.hierarchyGraph, this)

        while(hierarchyIterator.hasNext()) {
            this.hierarchy.add(hierarchyIterator.next())
        }

        /*
         * Process each method.
         */
        methods.forEach { it.process() }
    }

    override fun toString(): String = node.name
}