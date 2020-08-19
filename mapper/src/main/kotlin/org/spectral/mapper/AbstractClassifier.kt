package org.spectral.mapper

import kotlin.math.sqrt

abstract class AbstractClassifier<T> {

    val classifiers = mutableListOf<Classifier<T>>()

    /**
     * The maximum score a classifier result can possibly have.
     */
    val maxScore: Double get() {
        var total = 0.0
        classifiers.forEach { total += it.weight }
        return total
    }

    /**
     * This is the highest score that a match is considered to be a mismatch.
     */
    val maxMismatch: Double get() {
        return sqrt(Mapper.ABSOLUTE_MATCHING_THRESHOLD * (1 - Mapper.RELATIVE_MATCHING_THRESHOLD)) * maxScore
    }

    abstract fun init()

    abstract fun rank(src: T, dsts: Array<T>): List<RankResult<T>>

    internal fun addClassifier(classifier: Classifier<T>, weight: Int) {
        classifier.weight = weight.toDouble()
        classifiers.add(classifier)
    }

    fun classifier(name: String, getScore: (a: T, b: T) -> Double): Classifier<T> {
        return object : Classifier<T> {
            override val name = name
            override var weight = 0.0
            override fun getScore(a: T, b: T): Double {
                return getScore(a, b)
            }
        }
    }

}