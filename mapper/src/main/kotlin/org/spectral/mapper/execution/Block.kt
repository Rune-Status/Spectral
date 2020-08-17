package org.spectral.mapper.execution

import org.objectweb.asm.tree.AbstractInsnNode

/**
 * Represents a control flow frame of a method.
 */
class Block {

    var startIndex = 0

    var endIndex = 0

    val instructions = mutableListOf<AbstractInsnNode>()

    var next: Block? = null

    var prev: Block? = null

    var trunk: Block? = null

    val origin: Block get() {
        var cur = this
        var last = prev
        while(last != null) {
            cur = last
            last = cur.prev
        }

        return cur
    }

    val branches = mutableListOf<Block>()
}