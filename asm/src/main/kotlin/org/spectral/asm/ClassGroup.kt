package org.spectral.asm

import org.jgrapht.graph.*
import org.objectweb.asm.tree.ClassNode
import java.util.stream.Collectors

/**
 * Represents a collection of [Class] objects from a
 * single class path.
 *
 * @property classes Collection<ClassNode>
 * @constructor
 */
class ClassGroup internal constructor(
    val env: ClassEnvironment?,
    nodes: Collection<ClassNode>
) {

    /**
     * Creates an empty class group without any environment.
     *
     * @constructor
     */
    constructor() : this(null, mutableListOf())

    /**
     * Creates an empty class group with a specified environment.
     *
     * @param env ClassEnvironment
     * @constructor
     */
    constructor(env: ClassEnvironment) : this(env, mutableListOf())

    /**
     * The feature extractor instance.
     */
    private val extractor = FeatureExtractor(this)

    /**
     * The hierarchy graph for the class group.
     */
    internal val hierarchyGraph = DefaultDirectedGraph<Class, DefaultEdge>(DefaultEdge::class.java)

    /**
     * The list of [Class] contained in this group.
     */
    val classes = nodes.map { Class(this, it) }.toMutableSet()

    /**
     * Initializes the class group.
     */
    fun init() {
        classes.filter { it.real }.forEach { hierarchyGraph.addVertex(it) }

        extractor.process()
    }

    /**
     * Adds a class to the group.
     *
     * @param element Class
     * @return Boolean
     */
    fun add(element: Class): Boolean {
        return classes.add(element)
    }

    /**
     * Execution a [Unit] for each element in the group.
     *
     * @param action Function1<Class, Unit>
     */
    fun forEach(action: (Class) -> Unit) {
        classes.stream().collect(Collectors.toSet()).forEach { action(it) }
    }

    /**
     * Gets a [Class] from the group for a given name.
     * If no class in the [ClassGroup] exists with the given name,
     * a non real entry is created.
     *
     * If the environment is present, the created class is added as a shared class.
     *
     * @param name String
     * @return Class?
     */
    operator fun get(name: String): Class {
        var cls: Class? = classes.firstOrNull { it.name == name }

        if(cls == null) {
            cls = Class(this, name)
            env?.addSharedClass(cls) ?: this.add(cls)
        }

        return cls
    }

    fun find(name: String): Class? = classes.firstOrNull { it.name == name }
}