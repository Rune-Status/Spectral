package org.spectral.mapper.classifier

import org.objectweb.asm.Type
import org.spectral.asm.Class
import org.spectral.asm.Field
import org.spectral.asm.Matchable
import org.spectral.asm.Method
import org.spectral.mapper.RankResult
import java.util.stream.Collectors
import kotlin.math.abs
import kotlin.math.max

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
            //if(mismatch > maxMismatch) return null

            score += weightedScore
            results.add(ClassifierResult(classifier, score))
        }

        return RankResult(dst, score, results)
    }
}