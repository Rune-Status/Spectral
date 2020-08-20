package org.spectral.mapper.classifier

import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import org.objectweb.asm.tree.AbstractInsnNode.*
import org.spectral.asm.*
import org.spectral.mapper.RankResult
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Holds utility methods for comparing how similar elements are
 * that are matchable.
 *
 * This class is almost the entire basis and is the lowest foundation on how
 * elements of Jar's get matched together properly.
 */
object ClassifierUtil {

    /**
     * Gets whether two give [Class] objects are potential
     * match candidates.
     *
     * @param a Class
     * @param b Class
     * @return Boolean
     */
    fun isPotentiallyEqual(a: Class, b: Class): Boolean {
        if(a == b) return true
        if(a.match != null) return a.match == b
        if(b.match != null) return b.match == a
        if(a.real != b.real) return false
        if(!checkNameMatch(a.name, b.name)) return false

        return true
    }

    /**
     * Gets whether two given [Method] objects are potential
     * match candidates.
     *
     * @param a Method
     * @param b Method
     * @return Boolean
     */
    fun isPotentiallyEqual(a: Method, b: Method): Boolean {
        if(a == b) return true
        if(a.match != null) return a.match == b
        if(b.match != null) return b.match == a
        if(!a.isStatic && !b.isStatic) {
            if(!isPotentiallyEqual(a.owner, b.owner)) return false
        }
        if(!checkNameMatch(a.name, b.name)) return false

        return true
    }

    /**
     * Gets whether two given [Field] objects are potential match
     * candidates.
     *
     * @param a Field
     * @param b Field
     * @return Boolean
     */
    fun isPotentiallyEqual(a: Field, b: Field): Boolean {
        if(a == b) return true
        if(a.match != null) return a.match == b
        if(b.match != null) return b.match == a
        if(!a.isStatic && !b.isStatic) {
            if(!isPotentiallyEqual(a.owner, b.owner)) return false
        }
        if(!checkNameMatch(a.name, b.name)) return false

        return true
    }

    /**
     * Gets whether two given [Field] object ASM [Type] objects are potential
     * match candidates.
     *
     * @param a Field
     * @param b Field
     * @return Boolean
     */
    fun isTypesPotentiallyEqual(a: Field, b: Field): Boolean {
        val typeClassA = a.group[a.type.className]
        val typeClassB = b.group[b.type.className]

        return isPotentiallyEqual(typeClassA, typeClassB)
    }

    /**
     * Gets whether two give return [Type] objects from two [Method] objects are
     * potentially match candidates.
     *
     * @param a Method
     * @param b Method
     * @return Boolean
     */
    fun isReturnTypesPotentiallyEqual(a: Method, b: Method): Boolean {
        val returnClassA = a.group[a.returnType.className]
        val returnClassB = b.group[b.returnType.className]

        return isPotentiallyEqual(
            returnClassA,
            returnClassB
        )
    }

    /**
     * Gets whether two given argument [Type] lists from two [Method] objects
     * are all potential match candidates.
     *
     * @param a Method
     * @param b Method
     * @return Boolean
     */
    fun isArgTypesPotentiallyEqual(a: Method, b: Method): Boolean {
        val argTypesA = a.argumentTypes.mapNotNull { a.group[it.className] }
        val argTypesB = b.argumentTypes.mapNotNull { b.group[it.className] }

        for(i in argTypesA.indices) {
            if(i >= argTypesB.size) return false
            val argA = argTypesA[i]
            val argB = argTypesB[i]
            if(!isPotentiallyEqual(argA, argB)) return false
        }

        return true
    }

    /**
     * Checks whether the names match if both are not obfuscated.
     *
     * @param a
     * @param b
     * @return Boolean
     */
    fun checkNameMatch(a: String, b: String): Boolean {
        return if(isObfuscatedName(a) && isObfuscatedName(
                b
            )
        ) { // Both are obfuscated names
            true
        } else if(isObfuscatedName(a) != isObfuscatedName(
                b
            )
        ) { // Only one name is obfuscated
            false
        } else { // Neither are obfuscated names
            a == b
        }
    }

    /**
     * Gets whether a string is an obfuscated name.
     *
     * @param name String
     * @return Boolean
     */
    fun isObfuscatedName(name: String): Boolean {
        return (name.length <= 2 || (name.length == 3 && name.startsWith("aa")))
                || (name.startsWith("class") || name.startsWith("method") || name.startsWith("field"))
    }

    ////////////////////////////////////////////////////////////////////////////////////////////
    // SCORING UTILITY METHODS
    //
    // These methods are all designed to score similarity between matchable elements.
    //
    // The method scoring is based on a "sigmoid" function meaning their outputs are all between
    // -1.0 and 1.0 with a sigmoid curve=0 at 0.0
    ////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Compares and scores two counts / integers.
     *
     * @param a Int
     * @param b Int
     * @return Double
     */
    fun compareCounts(a: Int, b: Int): Double {
        val delta = abs(a - b)
        if(delta == 0) return 1.0

        return (1 - delta / max(a, b)).toDouble()
    }

    /**
     * Compares two mutable sets of elements with a matching type.
     *
     * @param a MutableSet<T>
     * @param b MutableSet<T>
     * @return Double
     */
    fun <T> compareSets(a: MutableSet<T>, b: MutableSet<T>): Double {
        val copyB = mutableSetOf<T>().apply { this.addAll(b) }

        val oldSize = b.size
        copyB.removeAll(a)

        val matched = oldSize - b.size
        val total = a.size - matched + oldSize

        return if(total == 0) 1.0 else (matched / total).toDouble()
    }

    /**
     * Compares two sets of [Class] objects.
     *
     * @param setA MutableSet<Class>
     * @param setB MutableSet<Class>
     * @return Double
     */
    fun compareClassSets(setA: MutableSet<Class>, setB: MutableSet<Class>): Double {
        return compareMatchableSets(
            setA,
            setB,
            ClassifierUtil::isPotentiallyEqual
        )
    }

    /**
     * Compares two sets of [Method] objects.
     *
     * @param setA MutableSet<Method>
     * @param setB MutableSet<Method>
     * @return Double
     */
    fun compareMethodSets(setA: MutableSet<Method>, setB: MutableSet<Method>): Double {
        return compareMatchableSets(
            setA,
            setB,
            ClassifierUtil::isPotentiallyEqual
        )
    }

    /**
     * Compares two sets of [Field] objects.
     *
     * @param setA MutableSet<Field>
     * @param setB MutableSet<Field>
     * @return Double
     */
    fun compareFieldSets(setA: MutableSet<Field>, setB: MutableSet<Field>): Double {
        return compareMatchableSets(
            setA,
            setB,
            ClassifierUtil::isPotentiallyEqual
        )
    }

    /**
     * Compares two sets both of a [Matchable] type.
     *
     * @param a MutableSet<T>
     * @param b MutableSet<T>
     * @param predicate Function2<T, T, Boolean>
     * @return Double
     */
    private fun <T : Matchable<T>> compareMatchableSets(sa: MutableSet<T>, sb: MutableSet<T>, predicate: (T, T) -> Boolean): Double {
        if(sa.isEmpty() || sb.isEmpty()) {
            return if(sa.isEmpty() && sb.isEmpty()) 1.0 else 0.0
        }

        val setA = mutableSetOf<T>().apply { this.addAll(sa) }
        val setB = mutableSetOf<T>().apply { this.addAll(sb) }

        val total = setA.size + setB.size
        var unmatched = 0

        val itA = setA.iterator()
        while(itA.hasNext()) {
            val a = itA.next()

            if(setB.remove(a)) {
                itA.remove()
            } else if(a.match != null) {
                if(!setB.remove(a.match!!)) {
                    unmatched++
                }

                itA.remove()
            } else if(!isObfuscatedName(a.name)) {
                unmatched++
                itA.remove()
            }
        }

        val itB = setB.iterator()
        while(itB.hasNext()) {
            val b = itB.next()

            if(!isObfuscatedName(b.name)) {
                unmatched++
                itB.remove()
            }
        }

        val itC = setA.iterator()
        while(itC.hasNext()) {
            val a = itC.next()

            var found = false

            for(b in setB) {
                if(predicate(a, b)) {
                    found = true
                    break
                }
            }

            if(!found) {
                unmatched++
                itC.remove()
            }
        }

        for(b in setB) {
            var found = false

            for(a in setA) {
                if(predicate(a, b)) {
                    found = true
                    break
                }
            }

            if(!found) {
                unmatched++
            }
        }

        return ((total - unmatched) / total).toDouble()
    }

    /**
     * Executes and ranks [src] -> array[dsts] for each classifier total weighted score.
     *
     * @param src T
     * @param dsts Array<T>
     * @param classifiers Collection<Classifier<T>>
     * @param predicate Function2<T, T, Boolean>
     * @param maxMismatch Double
     * @return List<RankResult<T>>
     */
    fun <T : Matchable<T>> rank(src: T, dsts: Array<T>, classifiers: Collection<Classifier<T>>, predicate: (T, T) -> Boolean, maxMismatch: Double): List<RankResult<T>> {
        val ret = mutableListOf<RankResult<T>>()

        for(dst in dsts) {
            val result = rank(
                src,
                dst,
                classifiers,
                predicate,
                maxMismatch
            )
            if(result != null) {
                ret.add(result)
            }
        }

        return ret.sortedByDescending { it.score }
    }

    private fun <T : Matchable<T>> rank(src: T, dst: T, classifiers: Collection<Classifier<T>>, predicate: (T, T) -> Boolean, maxMismatch: Double): RankResult<T>? {
        if(!predicate(src, dst)) return null

        var score = 0.0
        var mismatch = 0.0
        val results = mutableListOf<ClassifierResult<T>>()

        for(classifier in classifiers) {
            val cScore = classifier.getScore(src, dst)
            val weight = classifier.weight
            val weightedScore = cScore * weight

            mismatch += weight - weightedScore
            if(mismatch >= maxMismatch) return null

            score += weightedScore
            results.add(ClassifierResult(classifier, score))
        }

        return RankResult(dst, score, results)
    }

    /**
     * Compares the instructions of two [Method] objects.
     *
     * Returns a score based on their similarity on how they interact with
     * the JVM stack.
     *
     * @param a Method
     * @param b Method
     * @return Double
     */
    fun compareInsns(a: Method, b: Method): Double {
        if(!a.real || !b.real) return 1.0

        val insnsA = a.instructions
        val insnsB = b.instructions

        return compareLists(
            insnsA, insnsB,
            InsnList::get, InsnList::size,
            { ia, ib -> compareInsns(ia, ib, insnsA, insnsB, { insns: InsnList, insn: AbstractInsnNode -> insns.indexOf(insn) }, a.group, b.group) }
        )
    }

    /**
     * Compares two given instruction objects.
     *
     * Returns a score based on their similarity on how they interact with
     * the JVM stack.
     *
     * @param listA List<AbstractInsnNode>
     * @param listB List<AbstractInsnNode>
     * @param groupA ClassGroup
     * @param groupB ClassGroup
     * @return Double
     */
    fun compareInsns(listA: List<AbstractInsnNode>, listB: List<AbstractInsnNode>, groupA: ClassGroup, groupB: ClassGroup): Double {
        return compareLists(
            listA, listB,
            List<AbstractInsnNode>::get, List<AbstractInsnNode>::size,
            { ia, ib -> compareInsns(ia, ib, listA, listB, { insns: List<AbstractInsnNode>, insn: AbstractInsnNode -> insns.indexOf(insn) }, groupA, groupB) }
        )
    }

    /**
     * Compares two [Method] objects together by their given identifier attributes.
     *
     * @param ownerA String
     * @param nameA String
     * @param descA String
     * @param toIfA Boolean
     * @param groupA ClassGroup
     * @param ownerB String
     * @param nameB String
     * @param descB String
     * @param toIfB Boolean
     * @param groupB ClassGroup
     * @return Boolean
     */
    private fun compareMethods(
        ownerA: String,
        nameA: String,
        descA: String,
        toIfA: Boolean,
        groupA: ClassGroup,
        ownerB: String,
        nameB: String,
        descB: String,
        toIfB: Boolean,
        groupB: ClassGroup
    ): Boolean {
        val clsA = groupA[ownerA]
        val clsB = groupB[ownerB]
        return compareMethods(clsA, nameA, descA, toIfA, clsB, nameB, descB, toIfB)
    }

    /**
     * Compares two [Method] objects together by their given identifier attributes.
     *
     * @param ownerA Class
     * @param nameA String
     * @param descA String
     * @param toIfA Boolean
     * @param ownerB Class
     * @param nameB String
     * @param descB String
     * @param toIfB Boolean
     * @return Boolean
     */
    private fun compareMethods(
        ownerA: Class,
        nameA: String,
        descA: String,
        toIfA: Boolean,
        ownerB: Class,
        nameB: String,
        descB: String,
        toIfB: Boolean
    ): Boolean {
        val methodA = ownerA.resolveMethod(nameA, descA, toIfA)
        val methodB = ownerB.resolveMethod(nameB, descB, toIfB)

        if (methodA == null && methodB == null) return true
        if (methodA == null || methodB == null) return false

        return isPotentiallyEqual(methodA, methodB)
    }

    /**
     * Determines if two given instructions are doing the same thing
     * on the JVM stack.
     *
     * NOTE : This method is probably the most important method of the mapper.
     * It is the basis of how this entire thing works at all.
     *
     * @param insnA AbstractInsnNode
     * @param insnB AbstractInsnNode
     * @param listA T
     * @param listB T
     * @param position Function2<T, AbstractInsnNode, Int>
     * @param methodA Method
     * @param methodB Method
     * @return Boolean
     */
    private fun <T> compareInsns(
        insnA: AbstractInsnNode,
        insnB: AbstractInsnNode,
        listA: T,
        listB: T,
        position: (T, AbstractInsnNode) -> Int,
        groupA: ClassGroup,
        groupB: ClassGroup
    ): Boolean {
        if(insnA.opcode != insnB.opcode) return false

        /*
         * Switch through the different case type
         * for instruction A.
         */
        when(insnA.type) {

            INT_INSN -> {
                val a = insnA as IntInsnNode
                val b = insnB as IntInsnNode

                return a.operand == b.operand
            }

            VAR_INSN -> {
                val a = insnA as VarInsnNode
                val b = insnB as VarInsnNode

                    /*
                     * Future Feature.
                     *
                     * Here, we will be doing local variable and argument
                     * matching.
                     */
            }

            TYPE_INSN -> {
                val a = insnA as TypeInsnNode
                val b = insnB as TypeInsnNode

                val clsA = groupA[a.desc]
                val clsB = groupB[b.desc]

                return isPotentiallyEqual(clsA, clsB)
            }

            FIELD_INSN -> {
                val a = insnA as FieldInsnNode
                val b = insnB as FieldInsnNode

                val clsA = groupA[a.owner]
                val clsB = groupB[b.owner]

                val fieldA = clsA.resolveField(a.name, a.desc)
                val fieldB = clsB.resolveField(b.name, b.desc)

                if(fieldA == null && fieldB == null) return true
                if(fieldA == null || fieldB == null) return false

                return isPotentiallyEqual(fieldA, fieldB)
            }

            METHOD_INSN -> {
                val a = insnA as MethodInsnNode
                val b = insnB as MethodInsnNode

                return compareMethods(
                    a.owner, a.name, a.desc, a.isCallToInterface, groupA,
                    b.owner, b.name, b.desc, b.isCallToInterface, groupB
                )
            }

            INVOKE_DYNAMIC_INSN -> {
                val a = insnA as InvokeDynamicInsnNode
                val b = insnB as InvokeDynamicInsnNode

                if(a.bsm != b.bsm) return false

                if(a.bsm.isJavaLambda) {
                    val implA = a.bsmArgs[1] as Handle
                    val implB = b.bsmArgs[1] as Handle

                    if(implA.tag != implB.tag) return false

                    when(implA.tag) {
                        /*
                         * Check for known Java impl tags.
                         */
                        H_INVOKEVIRTUAL, H_INVOKESTATIC, H_INVOKESPECIAL, H_NEWINVOKESPECIAL, H_INVOKEINTERFACE -> {
                            return compareMethods(
                                implA.owner, implA.name, implA.desc, implA.isInterface, groupA,
                                implB.owner, implB.name, implB.desc, implB.isInterface, groupB
                            )
                        }
                    }
                }
            }

            /*
             * Control Flow Jump Instructions
             */
            JUMP_INSN -> {
                val a = insnA as JumpInsnNode
                val b = insnB as JumpInsnNode

                /*
                 * Since we have no primitive data to match
                 * jump instructions on,
                 *
                 * Solution is just to see if the jumps match up or down or adjacent
                 * to the current control flow block.
                 */
                return Integer.signum(position(listA, a.label) - position(listA, a)) == Integer.signum(position(listB, b.label) - position(listB, b))
            }

            LDC_INSN -> {
                val a = insnA as LdcInsnNode
                val b = insnB as LdcInsnNode

                if(a.cst::class != b.cst::class) return false

                if(a.cst::class == Type::class) {
                    val typeA = a.cst as Type
                    val typeB = b.cst as Type

                    if(typeA.sort != typeB.sort) return false

                    when(typeA.sort) {
                        Type.ARRAY, Type.OBJECT -> {
                            val clsA = groupA[typeA.className]
                            val clsB = groupB[typeB.className]

                            return isPotentiallyEqual(clsA, clsB)
                        }
                    }
                } else {
                    return a.cst == b.cst
                }
            }

            IINC_INSN -> {
                val a = insnA as IincInsnNode
                val b = insnB as IincInsnNode

                if(a.incr != b.incr) return false

                /*
                 * Implement local variable support
                 * Match the loaded local var from the stack.
                 */
            }

            TABLESWITCH_INSN -> {
                val a = insnA as TableSwitchInsnNode
                val b = insnB as TableSwitchInsnNode

                return (a.min == b.min && a.max == b.max)
            }

            LOOKUPSWITCH_INSN -> {
                val a = insnA as LookupSwitchInsnNode
                val b = insnB as LookupSwitchInsnNode

                return a.keys == b.keys
            }

            MULTIANEWARRAY_INSN -> {
                val a = insnA as MultiANewArrayInsnNode
                val b = insnB as MultiANewArrayInsnNode

                if(a.dims != b.dims) return false

                val clsA = groupA[a.desc]
                val clsB = groupB[b.desc]

                return isPotentiallyEqual(clsA, clsB)
            }

            /*
             * TO-DO List
             *
             * - Implement FRAME instruction support
             * - Implement LINE instruction support
             */
        }

        return true
    }

    /**
     * Compares two generic collection objects and returns a similarity score.
     *
     * @param listA T
     * @param listB T
     * @param elementRetriever Function2<T, Int, U>
     * @param sizeRetriever Function1<T, Int>
     * @param predicate Function2<U, U, Boolean>
     * @return Double
     */
    private fun <T, U> compareLists(
        listA: T,
        listB: T,
        elementRetriever: (T, Int) -> U,
        sizeRetriever: (T) -> Int,
        predicate: (U, U) -> Boolean
    ): Double {
        val sizeA = sizeRetriever(listA)
        val sizeB = sizeRetriever(listB)

        if(sizeA == 0 && sizeB == 0) return 1.0
        if(sizeA == 0 || sizeB == 0) return 0.0

        if(sizeA == sizeB) {
            var match = true

            for(i in 0 until sizeA) {
                if(!predicate(elementRetriever(listA, i), elementRetriever(listB, i))) {
                    match = false
                    break
                }
            }

            if(match) return 1.0
        }

        /*
         * Match the list elements by iterating over them using
         * the Levenshtein distance formula.
         *
         * https://en.wikipedia.org/wiki/Levenshtein_distance#Iterative_with_two_matrix_rows
         */

        val v0 = IntArray(sizeB + 1)
        val v1 = IntArray(sizeB + 1)

        for(i in v0.indices) {
            v0[i] = i
        }

        for(i in 0 until sizeA) {
            v1[0] = i + 1

            for(j in 0 until sizeB) {
                val cost = if(predicate(elementRetriever(listA, i), elementRetriever(listB, j))) 0 else 1
                v1[j + 1] = min(min(v1[j] + 1, v0[j + 1] + 1), v0[j] + cost)
            }

            for(j in v0.indices) {
                v0[j] = v1[j]
            }
        }

        val distance = v1[sizeB]
        val upperBound = max(sizeA, sizeB)

        return (1 - distance / upperBound).toDouble()
    }

    /**
     * Gets whether an invocation instruction is calling an interface reference.
     */
    val MethodInsnNode.isCallToInterface: Boolean get() {
        return this.itf
    }

    /**
     * Gets whether an invoke dynamic instruction is a Java 8 Lambda
     */
    val Handle.isJavaLambda: Boolean get() {
        return (this.tag == Opcodes.H_INVOKESTATIC && this.owner == "java/lang/invoke/LambdaMetafactory" && (this.name == "metafactory" && this.desc == "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;"
                || this.name == "altMetafactory" && this.desc == "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;")
                && !this.isInterface)
    }
}