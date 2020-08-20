package org.spectral.mapper.classifier

enum class ClassifierLevel(val id: Int) {

    INITIAL(0),

    SECONDARY(1),

    TERTIARY(2),

    EXTRA(3);

    companion object {

        val ALL = values()
    }
}