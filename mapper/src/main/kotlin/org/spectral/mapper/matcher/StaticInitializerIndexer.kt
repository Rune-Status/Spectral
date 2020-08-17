package org.spectral.mapper.matcher

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.FieldNode
import org.spectral.asm.*

/**
 * Indexes and stores static fields in a method.
 * This is used to properly match classes.
 */
class StaticInitializerIndexer(private val group: ClassGroup) {

    /**
     * The static fields which have been initialized.
     */
    private val fields = hashSetOf<Field>()

    fun index() {
        group.forEach { c ->
            val m = c.methods.firstOrNull { it.name == "<clinit>" } ?: return@forEach
            val insns = m.instructions

            for(i in insns) {
                if(i.type != Opcodes.PUTSTATIC) continue

                val putstatic = i as FieldInsnNode
                if(group[putstatic.owner]?.fields?.firstOrNull { it.name == putstatic.name && it.desc == putstatic.desc } == null) {
                    continue
                }

                fields.add(group[putstatic.owner]!!.fields.first { it.name == putstatic.name && it.desc == putstatic.desc })
            }
        }
    }

    /**
     * Gets whether a given [FieldNode] is indexed.
     *
     * @param field FieldNode
     * @return Boolean
     */
    fun isIndexed(field: Field): Boolean = fields.contains(field)
}