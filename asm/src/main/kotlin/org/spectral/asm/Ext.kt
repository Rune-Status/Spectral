package org.spectral.asm

inline fun <T> whileDo(initialValue: T?, nextValue: (T?) -> T?, condition: (T?) -> Boolean, body: (T?) -> Unit) {
    var value = nextValue(initialValue)
    while(condition(value)) {
        body(value)
        value = nextValue(value)
    }
}