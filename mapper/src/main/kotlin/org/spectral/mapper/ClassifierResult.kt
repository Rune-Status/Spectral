package org.spectral.mapper

data class ClassifierResult<T>(val classifier: Classifier<T>, val score: Double)