package org.spectral.mapper.util

import org.spectral.asm.Class
import org.spectral.asm.Field
import org.spectral.asm.Matchable
import org.spectral.asm.Method

/**
 * Holds utility methods for comparing how similar elements are
 * that are matchable.
 *
 * This class is almost the entire basis and is the lowest foundation on how
 * elements of Jar's get matched together properly.
 */
object ClassifierUtil {

    /**
     * Gets whether two give [Class] objects are potential
     * match candidates.
     *
     * @param a Class
     * @param b Class
     * @return Boolean
     */
    fun isPotentiallyEqual(a: Class, b: Class): Boolean {
        if(a == b) return true
        if(a.match != null) return a.match == b
        if(b.match != null) return b.match == a
        if(a.real != b.real) return false
        if(!checkNameMatch(a.name, b.name)) return false

        return true
    }

    /**
     * Gets whether two given [Method] objects are potential
     * match candidates.
     *
     * @param a Method
     * @param b Method
     * @return Boolean
     */
    fun isPotentiallyEqual(a: Method, b: Method): Boolean {
        if(a == b) return true
        if(a.match != null) return a.match == b
        if(b.match != null) return b.match == a
        if(!isPotentiallyEqual(a.owner, b.owner)) return false
        if(!checkNameMatch(a.name, b.name)) return false

        return true
    }

    /**
     * Gets whether two given [Field] objects are potential match
     * candidates.
     *
     * @param a Field
     * @param b Field
     * @return Boolean
     */
    fun isPotentiallyEqual(a: Field, b: Field): Boolean {
        if(a == b) return true
        if(a.match != null) return a.match == b
        if(b.match != null) return b.match == a
        if(!isPotentiallyEqual(a.owner, b.owner)) return false
        if(!checkNameMatch(a.name, b.name)) return false

        return true
    }

    /**
     * Checks whether the names match if both are not obfuscated.
     *
     * @param a
     * @param b
     * @return Boolean
     */
    fun checkNameMatch(a: String, b: String): Boolean {
        return if(isObfuscatedName(a) && isObfuscatedName(b)) { // Both are obfuscated names
            true
        } else if(isObfuscatedName(a) != isObfuscatedName(b)) { // Only one name is obfuscated
            false
        } else { // Neither are obfuscated names
            a == b
        }
    }

    /**
     * Gets whether a string is an obfuscated name.
     *
     * @param name String
     * @return Boolean
     */
    fun isObfuscatedName(name: String): Boolean {
        return (name.length <= 2 || (name.length == 3 && name.startsWith("aa")))
                || (name.startsWith("class") || name.startsWith("method") || name.startsWith("field"))
    }
}