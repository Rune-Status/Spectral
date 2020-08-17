package org.spectral.asm.processor

import com.google.auto.common.BasicAnnotationProcessor
import com.google.auto.service.AutoService
import java.io.File
import javax.annotation.processing.Processor
import javax.lang.model.SourceVersion

@AutoService(Processor::class)
class AsmAnnotationProcessor : BasicAnnotationProcessor() {

    override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()

    override fun getSupportedOptions(): Set<String> = setOf(KAPT_KOTLIN_GENERATED_OPTION_NAME)

    override fun initSteps(): Iterable<ProcessingStep> {

        val outputDir = processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME]?.let { File(it) }
            ?: throw IllegalArgumentException("No output directory given.")

        return listOf(AsmAnnotationProcessingStep(
            elements = processingEnv.elementUtils,
            messager = processingEnv.messager,
            outputDir = outputDir
        ))
    }

    companion object {
        private const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"
    }
}