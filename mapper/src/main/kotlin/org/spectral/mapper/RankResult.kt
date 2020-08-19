package org.spectral.mapper

import org.spectral.mapper.classifier.ClassifierResult

data class RankResult<T>(
    val subject: T,
    val score: Double,
    val results: List<ClassifierResult<T>>
)