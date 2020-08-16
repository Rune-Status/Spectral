package org.spectral.asm.processor

import com.squareup.kotlinpoet.*
import java.io.File
import javax.lang.model.element.Element

class AsmAnnotatedFieldFactory(private val element: Element) {

    @Suppress("DEPRECATION")
    fun generateCode(outputDir: File): PropertySpec {

        val packageName = "org.spectral.asm"
        val className = element.enclosingElement.simpleName.toString()
        val fieldName = element.simpleName.toString()
        val annotation = element.getAnnotation(Shadow::class.java)

        val cls = ClassName(packageName, className)

        val delegateName = if(annotation.name != "none") {
            annotation.name
        } else {
            fieldName
        }

        val property = PropertySpec.builder(fieldName, element.asType().asTypeName().correctStringType())
            .receiver(cls)
            .mutable(!annotation.immutable)
            .getter(FunSpec.getterBuilder()
                .addStatement("return node.${delegateName}")
                .build())
            .setter(FunSpec.setterBuilder()
                .addParameter("value", element.asType().asTypeName().correctStringType())
                .addStatement("node.${delegateName} = value")
                .build())
            .build()

        return property
    }

    private fun TypeName.correctStringType() =
        if(this.toString() == "java.lang.String") ClassName("kotlin", "String") else this
}