package org.spectral.mapper.matcher

import com.google.common.collect.LinkedHashMultimap
import org.spectral.asm.ClassGroup
import org.spectral.asm.Method
import org.spectral.mapper.util.CompareUtil

/**
 * Generates a list of [Method] objects which are all potential
 * matches.
 *
 * @property results The result match multi map.
 */
class MethodMatcher {

    val results = LinkedHashMultimap.create<Method, Method>()

    private val ClassGroup.methods: List<Method> get() = this.flatMap { it.methods }
        .filter { !it.isStatic }
        .filter { !it.isConstructor }

    private fun Method.getPotentialMatches(group: ClassGroup): List<Method> {
        return group.methods.filter { CompareUtil.isPotentialMatch(this, it) }
    }

    /**
     * Run and generate the results in the [results] multimap.
     * @param groupA ClassGroup
     * @param groupB ClassGroup
     */
    fun run(groupA: ClassGroup, groupB: ClassGroup) {
        groupA.methods.forEach { methodA ->
            results.putAll(methodA, methodA.getPotentialMatches(groupB))
        }
    }
}