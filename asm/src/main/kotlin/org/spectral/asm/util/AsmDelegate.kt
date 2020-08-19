package org.spectral.asm.util

import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty

fun <T> asm(field: KMutableProperty0<T>): AsmDelegate<T> = AsmDelegate(field) {}

fun <T> asm(field: KMutableProperty0<T>, setter: (T) -> Unit): AsmDelegate<T> = AsmDelegate(field, setter)

class AsmDelegate<T>(val delegate: KMutableProperty0<T>, val setter: (T) -> Unit) {

    operator fun getValue(ref: Any?, property: KProperty<*>): T {
        return delegate.get()
    }

    operator fun setValue(ref: Any?, property: KProperty<*>, value: T) {
        delegate.set(value)
        setter(value)
    }

}
