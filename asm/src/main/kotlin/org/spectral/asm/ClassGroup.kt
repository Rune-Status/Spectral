package org.spectral.asm

import org.objectweb.asm.tree.ClassNode

/**
 * Represents a group of classes from a class path.
 *
 * @constructor
 */
class ClassGroup() : MutableList<Class> by mutableListOf() {

    /**
     * Creates a class group with added [ClassNode] objects
     *
     * @param nodes The nodes to add
     * @constructor
     */
    private constructor(nodes: Collection<ClassNode>) {

    }
}