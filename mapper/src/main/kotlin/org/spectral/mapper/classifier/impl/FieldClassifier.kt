package org.spectral.mapper.classifier.impl

import org.objectweb.asm.Opcodes.*
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
        addClassifier(accessFlags, 4)
        addClassifier(types, 10)
        addClassifier(readReferences, 6)
        addClassifier(writeReferences, 6)
        addClassifier(initValue, 7)
        addClassifier(overrides, 10)
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

    /**
     * Access Flags
     */
    private val accessFlags = classifier("access flags") { a, b ->
        val mask = ACC_PUBLIC or ACC_PROTECTED or ACC_PRIVATE or ACC_FINAL or ACC_VOLATILE or ACC_TRANSIENT or ACC_SYNTHETIC
        val resultA = a.access and mask
        val resultB = b.access and mask

        return@classifier (1 - Integer.bitCount(resultA pow resultB) / 6).toDouble()
    }

    /**
     * Types
     */
    private val types = classifier("types") { a, b ->
        return@classifier if(ClassifierUtil.isTypesPotentiallyEqual(a, b)) 1.0 else 0.0
    }

    /**
     * Read References
     */
    private val readReferences = classifier("read references") { a, b ->
        return@classifier ClassifierUtil.compareMethodSets(a.readRefs, b.readRefs)
    }

    /**
     * Write References
     */
    private val writeReferences = classifier("write references") { a, b ->
        return@classifier ClassifierUtil.compareMethodSets(a.writeRefs, b.writeRefs)
    }

    /**
     * Overrides
     */
    private val overrides = classifier("overrides") { a, b ->
        val overridesA = a.overrides
        val overridesB = b.overrides

        return@classifier ClassifierUtil.compareFieldSets(overridesA, overridesB)
    }

    /**
     * Initialized Value
     */
    private val initValue = classifier("init value") { a, b ->
        val valueA = a.value
        val valueB = b.value

        if(valueA == null && valueB == null) return@classifier 1.0
        if(valueA == null || valueB == null) return@classifier 0.0

        return@classifier if(valueA == valueB) 1.0 else 0.0
    }

    private infix fun Int.pow(value: Int): Int {
        return this.toDouble().pow(value.toDouble()).toInt()
    }
}