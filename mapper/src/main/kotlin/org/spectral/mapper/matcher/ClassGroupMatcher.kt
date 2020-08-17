package org.spectral.mapper.matcher

import org.objectweb.asm.tree.ClassNode
import org.spectral.asm.Class
import org.spectral.asm.ClassGroup
import org.spectral.mapper.util.CompareUtil

/**
 * Responsible for matching all classes in two [ClassGroup] together based
 * on non-static method similarities.
 *
 * @property groupA ClassGroup
 * @property groupB ClassGroup
 */
class ClassGroupMatcher(val groupA: ClassGroup, val groupB: ClassGroup) {

    val results = hashMapOf<Class, Class>()

    /**
     * Iterates through every possible combination of class matches.
     * Checks to see if the pair have similar methods which are not static.
     */
    fun run() {
        groupA.forEach aLoop@ { clsA ->
            groupB.forEach bLoop@ { clsB ->
                if(!CompareUtil.isPotentialMatch(clsA, clsB)) return@bLoop

                val classMatcher = ClassMatcher(clsA, clsB)
                if(!classMatcher.isMatch) return@bLoop

                results[clsA] = clsB
            }
        }
    }
}