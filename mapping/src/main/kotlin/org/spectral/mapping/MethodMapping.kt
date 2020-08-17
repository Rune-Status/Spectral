package org.spectral.mapping

/**
 * Represents a method name mappings between obfuscated and readable.
 *
 * @property name The readable name of the method
 * @property desc The readable descriptor of the method
 * @property owner The readable parent name of this method.
 * @property obfName The obfuscated name of the method
 * @property obfDesc The obfuscated descriptor of the method
 * @property obfOwner The parent name this method belongs to
 * @constructor
 */
class MethodMapping(val name: String, val desc: String, val owner: String, val obfName: String, val obfDesc: String, val obfOwner: String) {

    override fun toString(): String {
        val ret = StringBuilder()

        ret.append("\tMETHOD $name:$desc $obfName:$obfDesc $owner $obfOwner")

        ret.append("\n")

        return ret.toString()
    }
}