package org.spectral.mapper.classifier.impl

import org.objectweb.asm.Opcodes.*
import org.spectral.asm.Class
import org.spectral.asm.FeatureExtractor
import org.spectral.asm.Field
import org.spectral.asm.Method
import org.spectral.mapper.Mapper
import org.spectral.mapper.RankResult
import org.spectral.mapper.classifier.AbstractClassifier
import org.spectral.mapper.classifier.ClassifierLevel
import org.spectral.mapper.classifier.ClassifierUtil
import kotlin.math.max
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
        addClassifier(hierarchyDepth, 1)
        addClassifier(parentClass, 4)
        addClassifier(childClasses, 3)
        addClassifier(interfaces, 3)
        addClassifier(implementers, 2)
        addClassifier(methodCount, 3)
        addClassifier(fieldCount, 3)
        addClassifier(hierarchySiblings, 2)
        addClassifier(similarMethods, 10)
        addClassifier(outReferences, 6)
        addClassifier(inReferences, 6)
        addClassifier(stringConstants, 8)
        addClassifier(numericConstants, 6)
        addClassifier(methodOutReferences, 5, ClassifierLevel.SECONDARY, ClassifierLevel.TERTIARY, ClassifierLevel.EXTRA)
        addClassifier(methodInReferences, 6, ClassifierLevel.SECONDARY, ClassifierLevel.TERTIARY, ClassifierLevel.EXTRA)
        addClassifier(fieldReadReferences, 5, ClassifierLevel.SECONDARY, ClassifierLevel.TERTIARY, ClassifierLevel.EXTRA)
        addClassifier(fieldWriteReferences, 5, ClassifierLevel.SECONDARY, ClassifierLevel.TERTIARY, ClassifierLevel.EXTRA)
        addClassifier(membersFull, 10, ClassifierLevel.TERTIARY, ClassifierLevel.EXTRA)
    }

    override fun rank(src: Class, dsts: Array<Class>, level: ClassifierLevel, maxMismatch: Double): List<RankResult<Class>> {
        return ClassifierUtil.rank(src, dsts, getClassifiers(level), ClassifierUtil::isPotentiallyEqual, maxMismatch)
    }

    /**
     * Class Types
     */
    private val classTypeCheck = classifier("class type check") { a, b ->
        val mask = ACC_ENUM or ACC_INTERFACE or ACC_ANNOTATION or ACC_ABSTRACT
        val resultA = a.access and mask
        val resultB = b.access and mask

        return@classifier (1 - Integer.bitCount(resultA pow resultB) / 4).toDouble()
    }

    /**
     * Hierarchy Depth
     */
    private val hierarchyDepth = classifier("hierarchy depth") { a, b ->
        return@classifier ClassifierUtil.compareCounts(
            a.hierarchy.size,
            b.hierarchy.size
        )
    }

    /**
     * Hierarchy Siblings
     */
    private val hierarchySiblings = classifier("hierarchy siblings") { a, b ->
        return@classifier ClassifierUtil.compareCounts(
            a.parent!!.children.size,
            b.parent!!.children.size
        )
    }

    /**
     * Parent Class
     */
    private val parentClass = classifier("parent class") { a, b ->
        if(a.parent == null && b.parent == null) return@classifier 1.0
        if(a.parent == null || b.parent == null) return@classifier 0.0

        return@classifier if(ClassifierUtil.isPotentiallyEqual(
                a.parent!!,
                b.parent!!
            )
        ) 1.0 else 0.0
    }

    /**
     * Child Classes
     */
    private val childClasses = classifier("child classes") { a, b ->
        return@classifier ClassifierUtil.compareClassSets(
            a.children,
            b.children
        )
    }

    /**
     * Interfaces
     */
    private val interfaces = classifier("interfaces") { a, b ->
        return@classifier ClassifierUtil.compareClassSets(
            a.interfaces,
            b.interfaces
        )
    }

    /**
     * Implementers
     */
    private val implementers = classifier("implementers") { a, b ->
        return@classifier ClassifierUtil.compareClassSets(
            a.implementers,
            b.implementers
        )
    }

    /**
     * Method Count
     */
    private val methodCount = classifier("method count") { a, b ->
        return@classifier ClassifierUtil.compareCounts(
            a.methods.size,
            b.methods.size
        )
    }

    /**
     * Field Count
     */
    private val fieldCount = classifier("field count") { a, b ->
        return@classifier ClassifierUtil.compareCounts(
            a.fields.size,
            b.fields.size
        )
    }

    /**
     * Similar Methods
     */
    private val similarMethods = classifier("similar methods") { a, b ->
        if(a.methods.isEmpty() && b.methods.isEmpty()) return@classifier 1.0
        if(a.methods.isEmpty() || b.methods.isEmpty()) return@classifier 0.0

        val methodsB = hashSetOf<Method>().apply { this.addAll(b.methods) }
        var totalScore = 0.0
        var bestMatch: Method? = null
        var bestScore = 0.0

        a.methods.forEach loopA@ { methodA ->
            methodsB.forEach loopB@ { methodB ->
                if(!ClassifierUtil.isPotentiallyEqual(
                        methodA,
                        methodB
                    )
                ) return@loopA
                if(!ClassifierUtil.isReturnTypesPotentiallyEqual(
                        methodA,
                        methodB
                    )
                ) return@loopA
                if(!ClassifierUtil.isArgTypesPotentiallyEqual(
                        methodA,
                        methodB
                    )
                ) return@loopB

                val score: Double = if(methodA.real || methodB.real) {
                    if(methodA.real && methodB.real) 1.0 else 0.0
                } else {
                    ClassifierUtil.compareCounts(
                        methodA.instructions.size(),
                        methodB.instructions.size()
                    )
                }

                if(score > bestScore) {
                    bestScore = score
                    bestMatch = methodB
                }
            }

            if(bestMatch != null) {
                totalScore += bestScore
                methodsB.remove(bestMatch!!)
            }
        }

        return@classifier totalScore / max(a.methods.size, b.methods.size)
    }

    /**
     * String Constants
     */
    private val stringConstants = classifier("string constants") { a, b ->
        return@classifier ClassifierUtil.compareSets(
            a.strings,
            b.strings
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

        a.extractNumbers(intsA, longsA, floatsA, doublesA)
        b.extractNumbers(intsB, longsB, floatsB, doublesB)

        return@classifier (ClassifierUtil.compareSets(intsA, intsB)
                + ClassifierUtil.compareSets(longsA, longsB)
                + ClassifierUtil.compareSets(floatsA, floatsB)
                + ClassifierUtil.compareSets(doublesA, doublesB)) / 4
    }

    /**
     * Out References
     */
    private val outReferences = classifier("out references") { a, b ->
        val refsA = a.outRefs
        val refsB = b.outRefs

        return@classifier ClassifierUtil.compareClassSets(
            refsA,
            refsB
        )
    }

    /**
     * In References
     */
    private val inReferences = classifier("in references") { a, b ->
        val refsA = a.inRefs
        val refsB = b.inRefs

        return@classifier ClassifierUtil.compareClassSets(
            refsA,
            refsB
        )
    }

    /**
     * Method Out References
     */
    private val methodOutReferences = classifier("method out references") { a, b ->
        val refsA = a.methodOutRefs
        val refsB = b.methodOutRefs

        return@classifier ClassifierUtil.compareMethodSets(
            refsA,
            refsB
        )
    }

    /**
     * Method In References
     */
    private val methodInReferences = classifier("method in references") { a, b ->
        val refsA = a.methodInRefs
        val refsB = b.methodInRefs

        return@classifier ClassifierUtil.compareMethodSets(
            refsA,
            refsB
        )
    }

    /**
     * Field Read References
     */
    private val fieldReadReferences = classifier("field read references") { a, b ->
        val refsA = a.fieldReadRefs
        val refsB = b.fieldReadRefs

        return@classifier ClassifierUtil.compareFieldSets(refsA, refsB)
    }

    /**
     * Field Write References
     */
    private val fieldWriteReferences = classifier("field write references") { a, b ->
        val refsA = a.fieldWriteRefs
        val refsB = b.fieldWriteRefs

        return@classifier ClassifierUtil.compareFieldSets(refsA, refsB)
    }

    /**
     * Members Full
     */
    private val membersFull = classifier("members full") { a, b ->
        val level = ClassifierLevel.TERTIARY
        var match = 0.0

        /*
         * Match method members.
         */
        if(a.methods.isNotEmpty() && b.methods.isNotEmpty()) {
            val maxScore = MethodClassifier.getMaxScore(level)

            a.methods.filter { it.real && !it.isStatic }.forEach { methodA ->
                val ranking = MethodClassifier.rank(methodA, b.methods.filter { it.real && !it.isStatic }.toTypedArray(), level, Double.POSITIVE_INFINITY)
                if(Mapper.foundMatch(ranking, maxScore)) match += Mapper.getScore(ranking[0].score, maxScore)
            }
        }

        val methodCount = max(a.methods.size, b.methods.size)

        if(methodCount == 0) {
            return@classifier 1.0
        } else {
            return@classifier match / methodCount
        }
    }

    private val Class.outRefs: MutableSet<Class> get() {
        val ret = hashSetOf<Class>()
        this.methods.forEach { ret.addAll(it.classRefs) }
        this.fields.forEach { it.group.find(it.type.className)?.apply { ret.add(this) } }
        return ret
    }

    private val Class.inRefs: MutableSet<Class> get() {
        val ret = hashSetOf<Class>()
        this.methodTypeRefs.forEach { ret.add(it.owner) }
        this.fieldTypeRefs.forEach { ret.add(it.owner) }
        return ret
    }

    private val Class.methodOutRefs: MutableSet<Method> get() {
        val ret = hashSetOf<Method>()
        this.methods.forEach { ret.addAll(it.refsOut) }
        return ret
    }

    private val Class.methodInRefs: MutableSet<Method> get() {
        val ret = hashSetOf<Method>()
        this.methods.forEach { ret.addAll(it.refsIn) }
        return ret
    }

    private val Class.fieldReadRefs: MutableSet<Field> get() {
        val ret = hashSetOf<Field>()
        this.methods.forEach { ret.addAll(it.fieldReadRefs) }
        return ret
    }

    private val Class.fieldWriteRefs: MutableSet<Field> get() {
        val ret = hashSetOf<Field>()
        this.methods.forEach { ret.addAll(it.fieldWriteRefs) }
        return ret
    }

    private fun Class.extractNumbers(ints: MutableSet<Int>, longs: MutableSet<Long>, floats: MutableSet<Float>, doubles: MutableSet<Double>) {
        this.methods.forEach {
            if(!it.real) return@forEach
            FeatureExtractor.extractNumbers(it.instructions.iterator(), ints, longs, floats, doubles)
        }

        this.fields.forEach {
            if(!it.real) return@forEach
            if(it.value == null) return@forEach
            FeatureExtractor.handleNumberValue(it.value, ints,longs, floats, doubles)
        }
    }

    private infix fun Int.pow(value: Int): Int {
        return this.toDouble().pow(value.toDouble()).toInt()
    }
}