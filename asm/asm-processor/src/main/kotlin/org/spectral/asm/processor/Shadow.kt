package org.spectral.asm.processor

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.SOURCE)
annotation class Shadow(val newName: String = "none")