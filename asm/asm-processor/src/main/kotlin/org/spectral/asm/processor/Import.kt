package org.spectral.asm.processor

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.SOURCE)
annotation class Import(val name: String = "none", val immutable: Boolean = false)