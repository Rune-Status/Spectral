package org.spectral.asm.processor

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import java.io.File
import javax.lang.model.element.Element

class AsmAnnotatedFieldFactory(private val element: Element) {

    @Suppress("DEPRECATION")
    fun generateCode(outputDir: File): PropertySpec {

        val packageName = "org.spectral.asm"
        val className = element.enclosingElement.simpleName.toString()
        val fieldName = element.simpleName.toString()
        val annotation = element.getAnnotation(Import::class.java)

        val cls = ClassName(packageName, className)

        val delegateName = if(annotation.name != "none") {
            annotation.name
        } else {
            fieldName
        }

        val property = PropertySpec.builder(fieldName, element.asType().asTypeName().correctType())
            .receiver(cls)
            .mutable(!annotation.immutable)
            .getter(FunSpec.getterBuilder()
                .addStatement("return node.${delegateName}!!")
                .build())
            .setter(FunSpec.setterBuilder()
                .addParameter("value", element.asType().asTypeName().correctType())
                .addStatement("node.${delegateName} = value")
                .build())
            .build()

        return property
    }

    private fun TypeName.correctType() = when {
        this.toString().startsWith("java.lang.String") -> String::class.asTypeName()
        this.toString().startsWith("java.util.List") -> ClassName("kotlin.collections", "List")
            .parameterizedBy(Class.forName(this.toString().substring(this.toString().indexOf("<") + 1, this.toString().indexOf(">"))).kotlin.asTypeName())
        else -> this
    }
}