package org.spectral.mapping

import java.lang.StringBuilder

/**
 * A Field Mapping
 *
 * @property ownerName String
 * @property name String
 * @property desc String
 * @property obfName String
 * @property obfDesc String
 * @constructor
 */
class FieldMapping(val ownerName: String, val name: String, val desc: String, val obfName: String, val obfDesc: String) {

    override fun toString(): String {
        val ret = StringBuilder()
        ret.append("\tFIELD $name $obfName $desc $obfDesc\n")
        return ret.toString()
    }
}