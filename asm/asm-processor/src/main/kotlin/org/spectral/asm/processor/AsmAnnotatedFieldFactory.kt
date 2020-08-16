package org.spectral.asm.processor

import com.squareup.kotlinpoet.*
import java.io.File
import javax.lang.model.element.Element

class AsmAnnotatedFieldFactory(private val element: Element) {

    @Suppress("DEPRECATION")
    fun generateCode(outputDir: File) {

        val packageName = "org.spectral.asm"
        val className = element.enclosingElement.simpleName.toString()
        val fieldName = element.simpleName.toString()
        val annotation = element.getAnnotation(Shadow::class.java)

        val cls = ClassName(packageName, className)

        val propertyName = if(annotation.newName != "none") {
            annotation.newName
        } else {
            fieldName
        }

        val property = PropertySpec.builder(propertyName, element.asType().asTypeName().correctStringType())
            .receiver(cls)
            .mutable(true)
            .getter(FunSpec.getterBuilder()
                .addStatement("return node.${fieldName}")
                .build())
            .setter(FunSpec.setterBuilder()
                .addParameter("value", element.asType().asTypeName().correctStringType())
                .addStatement("node.${fieldName} = value")
                .build())
            .build()

        /*
         * Build the file.
         */
        FileSpec.builder(packageName, "${className}$${fieldName}Ext")
            .addProperty(property)
            .build()
            .writeTo(outputDir)
    }

    private fun TypeName.correctStringType() =
        if(this.toString() == "java.lang.String") ClassName("kotlin", "String") else this
}