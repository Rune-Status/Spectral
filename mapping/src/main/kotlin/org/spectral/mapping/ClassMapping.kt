package org.spectral.mapping

/**
 * Represents a Class name mapping between obfuscated and readable.
 *
 * @property name The readable name of the class
 * @property obfName The obfuscated name of the class
 * @constructor
 */
class ClassMapping(val name: String, val obfName: String) {

    /**
     * The contained method mappings in this class.
     */
    val methods = mutableListOf<MethodMapping>()

    /**
     * The contained field mappings in this class.
     */
    val fields = mutableListOf<FieldMapping>()

    override fun toString(): String {
        val ret = StringBuilder()
        ret.append("CLASS $name $obfName\n")

        fields.forEach { ret.append(it.toString()) }
        methods.forEach { ret.append(it.toString()) }

        return ret.toString()
    }
}