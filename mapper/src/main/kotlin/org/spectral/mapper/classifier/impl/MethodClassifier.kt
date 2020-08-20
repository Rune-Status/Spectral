package org.spectral.mapper.classifier.impl

import org.objectweb.asm.Opcodes.*
import org.spectral.asm.Class
import org.spectral.asm.FeatureExtractor
import org.spectral.asm.Method
import org.spectral.mapper.RankResult
import org.spectral.mapper.classifier.AbstractClassifier
import org.spectral.mapper.classifier.ClassifierLevel
import org.spectral.mapper.classifier.ClassifierUtil
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
        addClassifier(returnType, 5)
        addClassifier(classReferences, 3)
        addClassifier(stringConstants, 5)
        addClassifier(numericConstants, 5)
        addClassifier(overrides, 10)
        addClassifier(inReferences, 6)
        addClassifier(outReferences, 6)
        addClassifier(fieldReads, 5)
        addClassifier(fieldWrites, 5)
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
        return ClassifierUtil.rank(
            src,
            dsts,
            getClassifiers(level),
            ClassifierUtil::isPotentiallyEqual,
            maxMismatch
        )
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
        return@classifier ClassifierUtil.compareClassSets(
            argTypesA.toHashSet(),
            argTypesB.toHashSet()
        )
    }

    /**
     * Return Type
     */
    private val returnType = classifier("return type") { a, b ->
        val returnTypeA = a.group[a.type.returnType.className]
        val returnTypeB = b.group[b.type.returnType.className]

        return@classifier if(ClassifierUtil.isPotentiallyEqual(
                returnTypeA,
                returnTypeB
            )
        ) 1.0 else 0.0
    }

    /**
     * Class References
     */
    private val classReferences = classifier("class references") { a, b ->
        return@classifier ClassifierUtil.compareClassSets(
            a.classRefs,
            b.classRefs
        )
    }

    /**
     * String Constants
     */
    private val stringConstants = classifier("string constants") { a, b ->
        val stringsA = hashSetOf<String>()
        val stringsB = hashSetOf<String>()

        FeatureExtractor.extractStrings(a.instructions.iterator(), stringsA)
        FeatureExtractor.extractStrings(b.instructions.iterator(), stringsB)

        return@classifier ClassifierUtil.compareSets(
            stringsA,
            stringsB
        )
    }

    /**
     * Numeric Constants
     */
    private val numericConstants = classifier("numeric constants") { a, b ->
        val intsA = hashSetOf<Int>()
        val intsB = hashSetOf<Int>()
        val longsA = hashSetOf<Long>()
        val longsB = hashSetOf<Long>()
        val floatsA = hashSetOf<Float>()
        val floatsB = hashSetOf<Float>()
        val doublesA = hashSetOf<Double>()
        val doublesB = hashSetOf<Double>()

        FeatureExtractor.extractNumbers(a.instructions.iterator(), intsA, longsA, floatsA, doublesA)
        FeatureExtractor.extractNumbers(b.instructions.iterator(), intsB, longsB, floatsB, doublesB)

        return@classifier (ClassifierUtil.compareSets(intsA, intsB)
                + ClassifierUtil.compareSets(longsA, longsB)
                + ClassifierUtil.compareSets(floatsA, floatsB)
                + ClassifierUtil.compareSets(doublesA, doublesB)) / 4
    }

    /**
     * Overrides
     */
    private val overrides = classifier("overrides") { a, b ->
        return@classifier ClassifierUtil.compareMethodSets(a.overrides, b.overrides)
    }

    /**
     * In References
     */
    private val inReferences = classifier("in references") { a, b ->
        return@classifier ClassifierUtil.compareMethodSets(a.refsIn, b.refsIn)
    }

    /**
     * Out References
     */
    private val outReferences = classifier("out references") { a, b ->
        return@classifier ClassifierUtil.compareMethodSets(a.refsOut, b.refsOut)
    }

    /**
     * Field Reads
     */
    private val fieldReads = classifier("field reads") { a, b ->
        return@classifier ClassifierUtil.compareFieldSets(a.fieldReadRefs, b.fieldReadRefs)
    }

    /**
     * Field Writes
     */
    private val fieldWrites = classifier("field writes") { a, b ->
        return@classifier ClassifierUtil.compareFieldSets(a.fieldWriteRefs, b.fieldWriteRefs)
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