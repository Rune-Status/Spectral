package org.spectral.asm

import org.jgrapht.graph.DefaultEdge
import org.jgrapht.graph.DirectedMultigraph
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.util.jar.JarFile

/**
 * Represents a group of classes from a class path.
 *
 * @constructor
 */
class ClassGroup() : MutableList<Class> by mutableListOf() {

    /**
     * The class group hierarchy graph data model.
     *
     * This data model has each class as a vertex and a edge between all the classes
     * which it extends or implements.
     */
    val hierarchyGraph = DirectedMultigraph<Class, DefaultEdge>(DefaultEdge::class.java)

    /**
     * Creates a class group with added [ClassNode] objects
     *
     * @param nodes The nodes to add
     * @constructor
     */
    private constructor(nodes: Collection<ClassNode>) : this() {
        nodes.forEach {
            val cls = Class(this, it)
            this.add(cls)
        }

        this.process()
    }

    /**
     * Processes anything required for the class group
     * object after nodes have been added.
     */
    fun process() {
        /*
         * Build the class parent and interface hierarchy
         */
        this.forEach { c ->
            c.parent = this[c.node.superName]
            if(c.parent != null) {
                c.parent!!.children.add(c)
            }

            c.node.interfaces.forEach { i ->
                if(this[i] != null) {
                    c.interfaces.add(this[i]!!)
                    this[i]!!.implementers.add(c)
                }
            }
        }

        /*
         * Build the class hierarchy graph.
         */
        this.forEach { c ->
            hierarchyGraph.addVertex(c)
        }

        /*
         * Add the hierarchy graph edges.
         */
        this.forEach { c ->
            if(c.parent != null) {
                hierarchyGraph.addEdge(c, c.parent)
            }

            c.interfaces.forEach { i ->
                hierarchyGraph.addEdge(c, i)
            }
        }

        /*
         * Process each class.
         */
        this.forEach { it.process() }

    }

    operator fun get(name: String): Class? = this.firstOrNull { it.node.name == name }

    companion object {
        /**
         * Create a [ClassGroup] object from the classes within a JAR file
         *
         * @param file The jar file to load.
         * @return The [ClassGroup] object with the jar entries loaded.
         */
        fun fromJar(file: File): ClassGroup {
            val nodes = mutableListOf<ClassNode>()

            JarFile(file).use { jar ->
                jar.entries().asSequence()
                    .filter { it.name.endsWith(".class") }
                    .forEach {
                        val node = ClassNode()
                        val reader = ClassReader(jar.getInputStream(it))
                        reader.accept(node, ClassReader.SKIP_FRAMES)
                        nodes.add(node)
                    }
            }

            return ClassGroup(nodes)
        }
    }
}