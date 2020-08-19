package org.spectral.mapper

import org.objectweb.asm.Opcodes.*
import org.spectral.asm.Class
import kotlin.math.pow

/**
 * Responsible for running classifiers on [Class] objects and calculating
 * a similarity match score.
 */
object ClassClassifier : AbstractClassifier<Class>() {

    /**
     * Initialize / register the classifiers.
     */
    override fun init() {
        addClassifier(classTypeCheck, 20)
    }

    override fun rank(src: Class, dsts: Array<Class>): List<RankResult<Class>> {
        return ClassifierUtil.rank(src, dsts, classifiers, ClassifierUtil::isPotentiallyEqual, this.maxMismatch)
    }

    private val classTypeCheck = classifier("class type check") { a, b ->
        val mask = ACC_ENUM or ACC_INTERFACE or ACC_ANNOTATION or ACC_ABSTRACT
        val resultA = a.access and mask
        val resultB = b.access and mask

        return@classifier (1 - Integer.bitCount(resultA pow resultB) / 4).toDouble()
    }

    private infix fun Int.pow(value: Int): Int {
        return this.toDouble().pow(value.toDouble()).toInt()
    }
}