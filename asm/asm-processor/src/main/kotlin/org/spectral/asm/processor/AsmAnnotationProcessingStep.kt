package org.spectral.asm.processor

import com.google.auto.common.BasicAnnotationProcessor
import com.google.common.collect.SetMultimap
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.PropertySpec
import java.io.File
import java.io.IOException
import javax.annotation.processing.Messager
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.util.Elements
import javax.tools.Diagnostic

class AsmAnnotationProcessingStep(
    val elements: Elements,
    val messager: Messager,
    val outputDir: File
) : BasicAnnotationProcessor.ProcessingStep {

    private val factoryClasses = LinkedHashMap<String, AsmAnnotatedFieldFactory>()
    private val generatedPropertySpecs = mutableSetOf<PropertySpec>()

    override fun annotations(): Set<Class<out Annotation>> = setOf(Shadow::class.java)

    override fun process(elementsByAnnotation: SetMultimap<Class<out Annotation>, Element>): Set<Element> {
        try {
            elementsByAnnotation[Shadow::class.java].forEach { annotatedElement ->
                if(annotatedElement.kind !== ElementKind.FIELD) {
                    throw ProcessingException(annotatedElement, "Only fields can be annotated with @Shadow")
                }

                val factory = AsmAnnotatedFieldFactory(annotatedElement)
                factoryClasses[annotatedElement.simpleName.toString()] = factory
            }

            factoryClasses.forEach { (_, u) ->
                generatedPropertySpecs.add(u.generateCode(outputDir))
            }
            factoryClasses.clear()

            /*
             * Build the file.
             */
            val fileSpec = FileSpec.builder("org.spectral.asm", "AsmGeneratedExt")
            generatedPropertySpecs.forEach { fileSpec.addProperty(it) }
            fileSpec.build().writeTo(outputDir)

        } catch (e : ProcessingException) {
            error(e.element, e.message ?: "")
        } catch( e : IOException) {
            error(null, e.message ?: "")
        }
        catch(e : Exception) {
            e.printStackTrace()
        }

        return emptySet()
    }

    private fun error(e: Element?, msg: String) {
        messager.printMessage(Diagnostic.Kind.ERROR, msg, e)
    }
}