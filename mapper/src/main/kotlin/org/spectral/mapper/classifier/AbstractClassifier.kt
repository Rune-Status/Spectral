package org.spectral.mapper.classifier

import org.spectral.mapper.Mapper
import org.spectral.mapper.RankResult
import kotlin.math.sqrt

abstract class AbstractClassifier<T> {

    private val classifiers = mutableListOf<Classifier<T>>()

    /**
     * The maximum score a classifier result can possibly have.
     */
    fun getMaxScore(level: ClassifierLevel): Double {
        var total = 0.0
        classifiers.filter { level in it.levels }.forEach { total += it.weight }
        return total
    }

    /**
     * This is the highest score that a match is considered to be a mismatch.
     */
    fun getMaxMismatch(level: ClassifierLevel): Double {
        return sqrt(Mapper.ABSOLUTE_MATCHING_THRESHOLD * (1 - Mapper.RELATIVE_MATCHING_THRESHOLD)) * getMaxScore(level)
    }

    abstract fun init()

    abstract fun rank(src: T, dsts: Array<T>, level: ClassifierLevel, maxMismatch: Double = getMaxMismatch(level)): List<RankResult<T>>

    fun getClassifiers(level: ClassifierLevel): List<Classifier<T>> {
        return classifiers.filter { level in it.levels }
    }

    internal fun addClassifier(classifier: Classifier<T>, weight: Int, vararg levels: ClassifierLevel) {
        classifier.levels.addAll(levels)
        classifier.weight = weight.toDouble()
        classifiers.add(classifier)
    }

    fun classifier(name: String, getScore: (a: T, b: T) -> Double): Classifier<T> {
        return object : Classifier<T> {
            override val name = name
            override var weight = 0.0
            override val levels = mutableListOf(ClassifierLevel.INITIAL)
            override fun getScore(a: T, b: T): Double {
                return getScore(a, b)
            }
        }
    }

}