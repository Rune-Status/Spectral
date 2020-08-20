package org.spectral.mapper.classifier

data class ClassifierResult<T>(val classifier: Classifier<T>, val score: Double)