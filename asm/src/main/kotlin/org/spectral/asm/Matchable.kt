package org.spectral.asm

/**
 * Represents an object which can be matched to another
 * object of the same generic type.
 *
 * @param T
 * @property match T?
 */
abstract class Matchable<T> {

    abstract val name: String

    /**
     * The matched object.
     */
    var match: T? = null

    /**
     * Whether this type has a match.
     *
     * @return Boolean
     */
    fun hasMatch(): Boolean = match != null


}