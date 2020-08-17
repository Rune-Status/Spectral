package org.spectral.mapper.util

import org.objectweb.asm.Type
import org.spectral.asm.*

/**
 * Contains utility methods for comparing classes, methods, and fields
 * and determining if a pair could potentially be a match.
 */
object CompareUtil {

    /**
     * Gets whether a pair of [Class] objects are potential
     * candidates for a match.
     *
     * @param a Class
     * @param b Class
     * @return Boolean
     */
    fun isPotentialMatch(a: Class, b: Class): Boolean {
        if(a.parent != null && b.parent != null) {
            if(!isPotentialMatch(a.parent!!, b.parent!!)) return false
        }
        else {
            if(a.parentName != b.parentName) return false
        }

        if(a.interfaces.isNotEmpty() && b.interfaces.isNotEmpty() && a.interfaces.size == b.interfaces.size) {
            var foundMatch = false
            a.interfaces.forEach { ia ->
                b.interfaces.forEach { ib ->
                    if(isPotentialMatch(ia, ib)) {
                        foundMatch = true
                    }
                }
            }

            if(!foundMatch) return false
        }

        return true
    }

    /**
     * Gets whether a pair of [Method] objects are potential
     * candidates for a match.
     *
     * @param a Method
     * @param b Method
     * @return Boolean
     */
    fun isPotentialMatch(a: Method, b: Method): Boolean {
        if(a.isStatic != b.isStatic) return false
        if(a.isPrivate != b.isPrivate) return false
        if(a.isConstructor != b.isConstructor) return false
        if(a.isInitializer != b.isInitializer) return false
        if(!isNameObfuscated(a.name) && !isNameObfuscated(b.name)) {
            if(a.name != b.name) return false
        }
        if(!a.isStatic && !b.isStatic) {
            if(!isPotentialMatch(a.owner, b.owner)) return false
        }
        if(!isPotentialMethodTypeMatch(a.type, b.type)) return false

        return true
    }

    /**
     * Gets whether a pair of [Field] objects are potential candidates
     * for a match.
     *
     * @param a Field
     * @param b Field
     * @return Boolean
     */
    fun isPotentialMatch(a: Field, b: Field): Boolean {
        if(a.isStatic != b.isStatic) return false
        if(a.isPrivate != b.isPrivate) return false
        if(!a.isStatic && !b.isStatic) {
            if(!isPotentialMatch(a.owner, b.owner)) return false
        }
        if(!isPotentialTypeMatch(a.type, b.type)) return false

        return true
    }

    /**
     * Gets whether a pair of ASM method [Type] objects are potential
     * candidates for a match.
     *
     * @param a Type
     * @param b Type
     * @return Boolean
     */
    fun isPotentialMethodTypeMatch(a: Type, b: Type): Boolean {
        if(a.argumentTypes.size != b.argumentTypes.size) return false
        if(!isPotentialTypeMatch(a.returnType, b.returnType)) return false

        for(i in a.argumentTypes.indices) {
            if(i >= b.argumentTypes.size) return false

            val argA = a.argumentTypes[i]
            val argB = b.argumentTypes[i]

            if(!isPotentialTypeMatch(argA, argB)) return false
        }

        return true
    }

    /**
     * Gets whether a generic ASM [Type] object pair are potential
     * candidates for a match.
     *
     * @param a Type
     * @param b Type
     * @return Boolean
     */
    fun isPotentialTypeMatch(a: Type, b: Type): Boolean {
        if(a.sort != b.sort) return false
        if(a.isPrimitive && b.isPrimitive) {
            return a == b
        }

        return true
    }

    /**
     * Gets whether a name is an obfuscated name or not.
     *
     * @param name String
     * @return Boolean
     */
    fun isNameObfuscated(name: String): Boolean {
        if(name.length <= 2 || (name.length == 3 && name.startsWith("aa"))) return true
        if(name.startsWith("class") || name.startsWith("method") || name.startsWith("field")) return true
        return false
    }

    /**
     * Whether a class name belongs to the JVM std library.
     *
     * @param name String
     * @return Boolean
     */
    fun isJvmClass(name: String): Boolean = name.startsWith("java/")

    /**
     * Whether a given ASM [Type] is a primitive data type.
     */
    val Type.isPrimitive: Boolean get() {
        return when(this.sort) {
            Type.BOOLEAN, Type.BYTE, Type.SHORT, Type.CHAR, Type.INT,
            Type.VOID, Type.LONG, Type.FLOAT, Type.DOUBLE -> true
            else -> false
        }
    }
}