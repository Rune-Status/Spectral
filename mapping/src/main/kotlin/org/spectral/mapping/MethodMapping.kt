package org.spectral.mapping

import java.lang.StringBuilder

/**
 * A Method mapping
 *
 * @property ownerName String
 * @property name String
 * @property desc String
 * @property obfName String
 * @property obfDesc String
 * @constructor
 */
class MethodMapping(val ownerName: String, val name: String, val desc: String, val obfName: String, val obfDesc: String) {

    override fun toString(): String {
        val ret = StringBuilder()
        ret.append("\tMETHOD $name $obfName $desc $obfDesc\n")
        return ret.toString()
    }
}