package org.spectral.mapper.classifier

import org.objectweb.asm.Opcodes.*
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
    override fun rank(src: Method, dsts: Array<Method>, level: ClassifierLevel): List<RankResult<Method>> {
        return ClassifierUtil.rank(src, dsts, getClassifiers(level), ClassifierUtil::isPotentiallyEqual, getMaxMismatch(level))
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

    private infix fun Int.pow(value: Int): Int {
        return this.toDouble().pow(value.toDouble()).toInt()
    }
}