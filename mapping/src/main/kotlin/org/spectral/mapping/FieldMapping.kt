package org.spectral.mapping

import java.lang.StringBuilder

/**
 * The field name mapping between obfuscated and readable.
 *
 * @property name The readable name of the field
 * @property desc The readable descriptor of the field
 * @property owner The readable parent name of this field.
 * @property obfName The obfuscated name of the field
 * @property obfDesc The obfuscated descriptor of the field
 * @property obfOwner The obfuscated parent name this field belongs in
 * @constructor
 */
class FieldMapping(val name: String, val desc: String, val owner: String, val obfName: String, val obfDesc: String, val obfOwner: String) {

    override fun toString(): String {
        val ret = StringBuilder()

        ret.append("\tFIELD $name:$desc $obfName:$obfDesc $owner $obfOwner")

        ret.append("\n")

        return ret.toString()
    }
}