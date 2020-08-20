package org.spectral.mapper.classifier.impl

import org.objectweb.asm.Opcodes.ACC_STATIC
import org.spectral.asm.Field
import org.spectral.mapper.RankResult
import org.spectral.mapper.classifier.AbstractClassifier
import org.spectral.mapper.classifier.ClassifierLevel
import org.spectral.mapper.classifier.ClassifierUtil
import kotlin.math.pow

/**
 * Responsible for classifying and calculating similarity scores
 * for [Field] objects.
 */
object FieldClassifier : AbstractClassifier<Field>() {

    /**
     * Initialize and register classifiers
     */
    override fun init() {
        addClassifier(fieldTypeCheck, 10)
    }

    /**
     * Calculate the classification result for [src] -> each [Field] in [dsts]
     */
    override fun rank(src: Field, dsts: Array<Field>, level: ClassifierLevel, maxMismatch: Double): List<RankResult<Field>> {
        return ClassifierUtil.rank(src, dsts, getClassifiers(level), ClassifierUtil::isPotentiallyEqual, maxMismatch)
    }

    /**
     * Field type check.
     */
    private val fieldTypeCheck = classifier("field type check") { a, b ->
        val mask = ACC_STATIC
        val resultA = a.access and mask
        val resultB = b.access and mask

        return@classifier (1 - Integer.bitCount(resultA pow resultB)).toDouble()
    }

    private infix fun Int.pow(value: Int): Int {
        return this.toDouble().pow(value.toDouble()).toInt()
    }
}