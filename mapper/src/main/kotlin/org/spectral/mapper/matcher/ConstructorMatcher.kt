package org.spectral.mapper.matcher

import com.google.common.collect.LinkedHashMultimap
import org.objectweb.asm.tree.MethodNode
import org.spectral.asm.ClassGroup
import org.spectral.asm.Method
import org.spectral.mapper.util.CompareUtil

/**
 * Gets a result list of potential constructor matches.
 */
class ConstructorMatcher {

    val results = LinkedHashMultimap.create<Method, Method>()

    private val ClassGroup.constructors: List<Method> get() {
        return this.flatMap { it.methods }
            .filter { it.isConstructor }
    }

    private fun Method.getPotentialMatches(group: ClassGroup): List<Method> {
        return group.constructors.filter { CompareUtil.isPotentialMatch(this, it) }
    }

    /**
     * Builds a result list of the potential constructor matches.
     *
     * @param groupA MutableList<ClassNode>
     * @param groupB MutableList<ClassNode>
     */
    fun run(groupA: ClassGroup, groupB: ClassGroup) {
        groupA.constructors.forEach { constructorA ->
            results.putAll(constructorA, constructorA.getPotentialMatches(groupB))
        }
    }
}