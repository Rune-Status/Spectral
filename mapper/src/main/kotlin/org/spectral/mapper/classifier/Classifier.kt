package org.spectral.mapper.classifier

interface Classifier<T> {

    val levels: MutableList<ClassifierLevel>

    val name: String

    var weight: Double

    fun getScore(a: T, b: T): Double
}