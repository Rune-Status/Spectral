package org.spectral.deobfuscator

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.file
import me.tongfei.progressbar.ProgressBar
import me.tongfei.progressbar.ProgressBarBuilder
import org.spectral.asm.ClassGroup
import org.spectral.deobfuscator.transformer.*
import org.spectral.deobfuscator.transformer.rename.NameGenerator
import org.spectral.deobfuscator.transformer.controlflow.ControlFlowFixer
import org.spectral.deobfuscator.transformer.euclidean.MultiplierRemover
import org.tinylog.kotlin.Logger
import java.io.File

/**
 * Represents the Spectral OSRS gamepack deobfuscator object.
 *
 * @constructor
 */
class Deobfuscator {

    lateinit var group: ClassGroup

    /**
     * The transformers in order to run.
     */
    val transformers = mutableListOf<Transformer>()

    /**
     * Register the transformer instances.
     */
    init {
        register(TryCatchRemover())
        register(OpaquePredicateCheckRemover())
        register(GotoRemover())
        register(ControlFlowFixer())
        register(DeadCodeRemover())
        register(MultiplierRemover())
        register(FieldInliner())
        register(OpaquePredicateArgRemover())
        register(UnusedMethodRemover())
        register(UnusedFieldRemover())
        register(ErrorConstructorRemover())
        register(FieldSorter())
        register(MethodSorter())
        register(NameGenerator())
    }

    private fun register(transformer: Transformer) {
        transformers.add(transformer)
    }

    fun loadInputJar(inputFile: File) {
        Logger.info("Loading input JAR file: '${inputFile.path}' classes.")
        group = ClassGroup.fromJar(inputFile)
        Logger.info("Successfully loaded ${group.size} classes.")
    }

    /**
     * Runs the deobfuscation with a [Unit] consumer call back.
     *
     * @param consumer Function0<Unit>
     */
    fun run(consumer: () -> Unit) {
        /*
         * Apply each transformer
         */
        Logger.info("Preparing to run bytecode transformers.")

        transformers.forEach { transformer ->
            consumer()
            Logger.info("Running bytecode transformer '${transformer::class.simpleName}'...")
            transformer.transform(group)
        }
    }

    /**
     * Runs the deobfuscation with an empty consumer [Unit] callback.
     */
    fun run() = this.run {}

    companion object {
        @JvmStatic
        fun main(args: Array<String>) = object : CliktCommand(
            name = "Deobfuscator",
            help = "Deobfuscates a Jagex OSRS gamepack to enable it to be decompiled and reverse-engineered.",
            printHelpOnEmptyArgs = true,
            invokeWithoutSubcommand = true
        ) {

            private val inputFile by argument(name = "input file", help = "The obfuscated input JAR file.").file(mustExist = true, canBeDir = false)
            private val outputFile by argument(name = "output file", help = "The output JAR file to export to.").file(mustExist = false, canBeDir = false)

            private inline fun <R> ProgressBar.run(block: (ProgressBar) -> R): R {
                var exception: Throwable? = null
                try {
                    return block(this)
                } catch(e : Throwable) {
                    exception = e
                    throw e
                } finally {
                    when (exception) {
                        null -> close()
                        else -> {
                            try {
                                close()
                            } catch (closeException: Throwable) {}
                        }
                    }
                }
            }

            /**
             * Run the deobfuscator with progress bar call back when executing from
             * the command line.
             */
            override fun run() {
                Logger.info("Preparing deobfuscator...")

                val deobfuscator = Deobfuscator()
                deobfuscator.loadInputJar(inputFile)

                /*
                 * Create progress bar
                 */
                val progress = ProgressBarBuilder()
                    .setInitialMax(deobfuscator.transformers.size.toLong() + 1L)
                    .setTaskName("Deobfuscating")
                    .build()

                progress.run { p ->
                    deobfuscator.run {
                        p.step()
                    }

                    /*
                      * Export to the outputFile
                     */
                    p.step()

                    deobfuscator.group.toJar(outputFile)

                    return
                }
            }
        }.main(args)
    }
}