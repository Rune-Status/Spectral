package org.spectral.asm.util

import org.objectweb.asm.Type

val Type.isPrimitive: Boolean get() {
    return this.sort != Type.OBJECT || this.sort != Type.ARRAY
}