package org.spectral.mapper.matcher

import com.google.common.collect.ImmutableMultiset
import com.google.common.collect.Multiset
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode
import org.spectral.asm.Class
import org.spectral.asm.Field
import org.spectral.asm.Method
import org.spectral.asm.name
import org.spectral.mapper.util.CompareUtil

/**
 * Matches two [ClassNode] objects based on non-static method
 * similarities.
 *
 * @property clsA ClassNode
 * @property clsB ClassNode
 * @constructor
 */
class ClassMatcher(val clsA: Class, val clsB: Class) {

    /**
     * The potential fields which are candidates for matching classes by
     * matching these field types.
     */
    private val Class.fieldMatchCandidates: Multiset<Field>
        get() {
        val t = this.fields
            .filter { !it.isStatic }

        return ImmutableMultiset.copyOf(t)
    }

    /**
     * The potential methods which are candidates for matching classes by
     * matching these method types.
     */
    private val Class.methodMatchCandidates: Multiset<Method>
        get() {
        val t = this.methods
            .filter { !it.isStatic }
            .filter { !it.isConstructor }

        return ImmutableMultiset.copyOf(t)
    }

    /**
     * Gets whether [clsA] -> [clsB] is a match.
     */
    val isMatch: Boolean get() {
        val fieldCandidatesA = clsA.fieldMatchCandidates
        val fieldCandidatesB = clsB.fieldMatchCandidates

        fieldCandidatesA.forEachIndexed { i, fieldA ->
            if(i >= fieldCandidatesB.size) return false
            if(!CompareUtil.isPotentialMatch(fieldA, fieldCandidatesB.elementAt(i))) return false
        }

        val methodCandidatesA = clsA.methodMatchCandidates
        val methodCandidatesB = clsB.methodMatchCandidates

        methodCandidatesA.forEachIndexed { i, methodA ->
            if(i >= methodCandidatesB.size) return false
            if(!CompareUtil.isPotentialMatch(methodA, methodCandidatesB.elementAt(i))) return false
        }

        return true
    }
}