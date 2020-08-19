package org.spectral.mapper

interface Classifier<T> {

    val name: String

    var weight: Double

    fun getScore(a: T, b: T): Double
}