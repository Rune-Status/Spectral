package org.spectral.mapper.classifier

import org.objectweb.asm.Opcodes.*
import org.spectral.asm.Class
import org.spectral.asm.Method
import org.spectral.mapper.RankResult
import kotlin.math.pow

/**
 * Responsible for calculating similarity scores between [Method] objects.
 */
object MethodClassifier : AbstractClassifier<Method>() {

    /**
     * Initialize and register the classifiers
     */
    override fun init() {
        addClassifier(methodTypeCheck, 10)
        addClassifier(accessFlags, 4)
        addClassifier(argumentTypes, 10)
    }

    /**
     * Calculates and outputs a rank result list for comparing
     * a source method to an array of possible matching candidates.
     *
     * @param src Method
     * @param dsts Array<Method>
     * @param level ClassifierLevel
     * @return List<RankResult<Method>>
     */
    override fun rank(src: Method, dsts: Array<Method>, level: ClassifierLevel, maxMismatch: Double): List<RankResult<Method>> {
        return ClassifierUtil.rank(src, dsts, getClassifiers(level), ClassifierUtil::isPotentiallyEqual, maxMismatch)
    }

    /**
     * Method Type Check
     */
    private val methodTypeCheck = classifier("method type check") { a, b ->
        val mask = ACC_STATIC or ACC_ABSTRACT or ACC_NATIVE
        val resultA = a.access and mask
        val resultB = b.access and mask

        return@classifier (1 - Integer.bitCount(resultA pow resultB) / 3).toDouble()
    }

    /**
     * Access Flags
     */
    private val accessFlags = classifier("access flags") { a, b ->
        val mask = ACC_PUBLIC or ACC_PROTECTED or ACC_PRIVATE or ACC_FINAL or ACC_FINAL or ACC_SYNCHRONIZED or ACC_BRIDGE or ACC_VARARGS or ACC_STRICT or ACC_SYNTHETIC
        val resultA = a.access and mask
        val resultB = b.access and mask

        return@classifier (1 - Integer.bitCount(resultA pow resultB) / 8).toDouble()
    }

    /**
     * Argument Types
     */
    private val argumentTypes = classifier("argument types") { a, b ->
        val argTypesA = getArgumentTypes(a)
        val argTypesB = getArgumentTypes(b)
        return@classifier ClassifierUtil.compareClassSets(argTypesA.toHashSet(), argTypesB.toHashSet())
    }

    private fun getArgumentTypes(method: Method): Array<Class> {
        val argTypes = method.argumentTypes
        val ret = mutableListOf<Class>()

        argTypes.forEach { arg ->
            ret.add(method.group[arg.className])
        }

        return ret.toTypedArray()
    }

    private infix fun Int.pow(value: Int): Int {
        return this.toDouble().pow(value.toDouble()).toInt()
    }
}