package org.spectral.mapper.matcher

import com.google.common.collect.LinkedHashMultimap
import org.spectral.asm.ClassGroup
import org.spectral.asm.Method
import org.spectral.mapper.util.CompareUtil

class StaticMethodMatcher {

    val results = LinkedHashMultimap.create<Method, Method>()

    private val ClassGroup.staticMethods: List<Method> get() = this.flatMap { it.methods }
        .filter { it.isStatic }
        .filter { !it.isConstructor && !it.isInitializer }

    private fun Method.getPotentialMatches(group: ClassGroup): List<Method> {
        return group.staticMethods.filter { CompareUtil.isPotentialMatch(this, it) }
    }

    fun run(groupA: ClassGroup, groupB: ClassGroup) {
        groupA.staticMethods.forEach { methodA ->
            results.putAll(methodA, methodA.getPotentialMatches(groupB))
        }
    }
}