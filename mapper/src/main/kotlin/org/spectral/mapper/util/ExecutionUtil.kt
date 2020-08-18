package org.spectral.mapper.util

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*
import org.spectral.asm.ClassGroup
import org.spectral.asm.Method
import org.spectral.asm.desc
import org.spectral.asm.name
import org.spectral.mapper.MatchGroup

/**
 * A collection of execution utility methods.
 */
object ExecutionUtil {

    /**
     * Gets whether a [MethodInsnNode] is of a matchable class.
     *
     * @param group MutableList<ClassNode>
     * @param insn MethodInsnNode
     * @return Boolean
     */
    fun isMatchable(group: ClassGroup, insn: MethodInsnNode): Boolean {
        val method = group[insn.owner]?.methods?.firstOrNull { it.name == insn.name && it.desc == insn.desc }
            ?: return false

        val clsName = method.owner.name

        if(clsName.startsWith("java/lang/reflect") || clsName.startsWith("java/io") || clsName.startsWith("java/util/")) {
            return true
        }

        if(clsName.startsWith("java/") || clsName.startsWith("netscape/") || clsName.startsWith("javax/")) {
            return false
        }

        return true
    }

    /**
     * Gets whether a give instruction can be inlined.
     *
     * @param group MutableList<ClassNode>
     * @param insn AbstractInsnNode
     * @return Boolean
     */
    fun isInlineable(group: ClassGroup, insn: AbstractInsnNode): Boolean {
        if(insn.opcode != Opcodes.INVOKESTATIC) return false
        if(insn !is MethodInsnNode) return false

        val method = group[insn.owner]?.methods?.firstOrNull { it.name == insn.name && it.desc == insn.desc }
            ?: return false

        return group[method.owner.name] != null
    }

    /**
     * Gets whether [a] and [b] instructions are the same
     *
     * @param methodA MethodNode
     * @param methodB MethodNode
     * @param a AbstractInsnNode
     * @param b AbstractInsnNode
     * @return Boolean
     */
    fun isSame(matches: MatchGroup, methodA: Method, methodB: Method, a: AbstractInsnNode, b: AbstractInsnNode): Boolean {
        if(a::class != b::class) return false

        /*
         * If the instructions are [IntInsnNode] instances
         */
        if(a is IntInsnNode && b is IntInsnNode) {
            return a.operand == b.operand
        }

        /*
         * If the instructions are [VarInsnNode] instances
         */
        if(a is VarInsnNode && b is VarInsnNode) {
            return a.`var` == b.`var`
        }

        /*
         * If the instructions are [LdcInsnNode] instances
         */
        if(a is LdcInsnNode && b is LdcInsnNode) {
            return a.cst == b.cst
        }

        /*
         * If the instructions are [IincInsnNode] instances
         */
        if(a is IincInsnNode && b is IincInsnNode) {
            return (a.incr == b.incr)
        }

        /*
         * If the instructions are [JumpInsnNode] instances
         */
        if(a is JumpInsnNode && b is JumpInsnNode) {
            return true
        }

        if(a is LookupSwitchInsnNode && b is LookupSwitchInsnNode) {
            return a.keys.size == b.keys.size
        }

        if(a is TableSwitchInsnNode && b is TableSwitchInsnNode) {
            return a.labels.size == b.labels.size
        }

        if(a is MultiANewArrayInsnNode && b is MultiANewArrayInsnNode) {
            return a.dims == b.dims
        }

        /*
         * If the instructions are [TypeInsnNode] instances
         */
        if(a is TypeInsnNode && b is TypeInsnNode) {
            val clsA = methodA.group[a.desc]
            val clsB = methodB.group[b.desc]

            var match = false

            if(clsA != null && clsB != null) {
                match = CompareUtil.isPotentialMatch(clsA, clsB)
            }

            /*
             * If the classes are matches, we can
             * go ahead and add them to the matches
             */
            if(match) {
                matches.match(clsA!!, clsB!!)
            }

            return match
        }

        /*
         * If the instructions are [MethodInsnNode] instances
         */
        if(a is MethodInsnNode && b is MethodInsnNode) {
            val mthA = methodA.group[a.owner]?.methods?.firstOrNull { it.name == a.name && it.desc == a.desc }
            val mthB = methodB.group[b.owner]?.methods?.firstOrNull { it.name == b.name && it.desc == b.desc }

            var match = false

            if(mthA != null && mthB != null) {
                match = CompareUtil.isPotentialMatch(mthA, mthB)
            }

            /*
             * If the methods invoked were a potential match,
             * we can go ahead and add them to the matches.
             */
            if(match) {
                matches.match(mthA!!, mthB!!)
            }

            return match
        }

        /*
         * If the instructions are [FieldInsnNode] instances
         */
        if(a is FieldInsnNode && b is FieldInsnNode) {
            val fieldA = methodA.group[a.owner]?.fields?.firstOrNull { it.name == a.name && it.desc == a.desc }
            val fieldB = methodB.group[b.owner]?.fields?.firstOrNull { it.name == b.name && it.desc == b.desc }

            var match = false

            if(fieldA != null && fieldB != null) {
                match = CompareUtil.isPotentialMatch(fieldA, fieldB)
            }

            /*
             * If the fields where a potential match,
             * we can go ahead and add them to the matches.
             */
            if(match) {
                val m = matches.match(fieldA!!, fieldB!!)
                m.score += ScoreUtil.calculateScore(fieldA, fieldB)
            }

            return match
        }

        if(a.opcode == b.opcode) {
            return true
        }

        return false
    }
}