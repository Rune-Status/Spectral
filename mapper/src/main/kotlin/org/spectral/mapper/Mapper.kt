package org.spectral.mapper

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.file
import me.tongfei.progressbar.ProgressBar
import org.spectral.asm.Class
import org.spectral.asm.ClassEnvironment
import org.spectral.asm.Method
import org.tinylog.kotlin.Logger
import java.io.File
import java.util.*
import java.util.stream.Collectors
import kotlin.math.max

/**
 * The Spectral Mapper Object.
 *
 * This class is responsible for matching up obfuscation names between OSRS game revisions by
 * comparing node traits and analyzing execution patterns on the JVM stack which it does in memory using
 * an ASM bytecode interpreter.
 *
 * @property env The [ClassEnvironment] which has the class group objects loaded.
 * @constructor
 */
class Mapper(private val env: ClassEnvironment) {

    /**
     * Initialize the mapper.
     */
    fun init() {
        Logger.info("Initializing mapper...")

        /*
         * Initialize the classifiers.
         */
        ClassClassifier.init()
    }

    /**
     * Match all classes, methods, and fields.
     */
    fun matchAll() {
        Logger.info("Attempting to match all elements...")

        /*
         * Initially match any classes we can.
         * If we were successful, match classes again as we may be able to
         *  match some hierarchy members in the second pass.
         */
        if(matchClasses()) {
            matchClasses()
        }


    }

    /**
     * Match [Class] objects
     * @return Boolean
     */
    fun matchClasses(): Boolean {
        Logger.info("Matching classes...")
        /*
         * The mapped classes.
         */
        val classes = env.groupA.classes.stream()
            .filter { !it.hasMatch() }
            .collect(Collectors.toSet())

        /*
         * The unmapped classes to compare to.
         */
        val cmpClasses = env.groupB.classes.stream()
            .filter { !it.hasMatch() }
            .collect(Collectors.toSet())

        val maxScore = ClassClassifier.maxScore
        val maxMismatch = ClassClassifier.maxMismatch

        val matches = hashMapOf<Class, Class>()

        /*
         * Run the matching process
         */
        classes.forEach { cls ->
            val ranking = ClassClassifier.rank(cls, cmpClasses.toTypedArray())

            if(foundMatch(ranking, maxScore)) {
                val match = ranking[0].subject
                matches[cls] = match
            }
        }

        /*
         * Resolve any conflicting matches.
         */
        resolveConflicts(matches)

        /*
         * Apply matches
         */
        matches.forEach { (a, b) ->
            match(a, b)
        }

        return matches.isNotEmpty()
    }

    /**
     * Matches classes, methods, and fields in order and continues to do so until no
     * new matches are made for any.
     */
    private fun matchAllRecursively() {

    }

    /**
     * Matches two [Class] objects together.
     *
     * @param a Class
     * @param b Class
     */
    fun match(a: Class, b: Class) {
        if(a.match == b) return

        Logger.info("Mapped CLASS \t [$a] -> [$b]")

        /*
         * Set the class matches to each other.
         */
        a.match = b
        b.match = a

        /*
         * Match methods which are not obfuscated or have been
         * matched via parent / children
         */
        for(src in a.methods) {
            if(!ClassifierUtil.isObfuscatedName(src.name)) {
                val dst = b.getMethod(src.name, src.desc)

                if(dst != null && !ClassifierUtil.isObfuscatedName(dst.name)) {
                    match(src, dst)
                    continue
                }
            }


        }
    }

    /**
     * Match two [Method] objects together.
     *
     * @param a Method
     * @param b Method
     */
    fun match(a: Method, b: Method) {
        if(a.match == b) return

        Logger.info("Mapped METHOD \t [${a.owner}.${a.name}] -> [${b.owner}.${b.name}]")

        a.match = b
        b.match = a
    }

    /**
     * Resolves any conflicting matches by removing them from the match
     * queue.
     *
     * @param matches MutableMap<T, T>
     */
    private fun <T> resolveConflicts(matches: MutableMap<T, T>) {
        val matched = mutableSetOf<T>()
        val conflicts = mutableSetOf<T>()

        matches.values.forEach { cls ->
            if(!matched.add(cls)) {
                conflicts.add(cls)
            }
        }

        if(!conflicts.isEmpty()) {
            matches.values.removeAll(conflicts)
        }
    }

    /**
     * Gets whether a match was found given a list of classifier rank results.
     *
     * @param ranking List<RankResult<*>>
     * @param maxScore Double
     * @return Boolean
     */
    private fun foundMatch(ranking: List<RankResult<*>>, maxScore: Double): Boolean {
        if(ranking.isEmpty()) return false

        val score = getScore(ranking[0].score, maxScore)
        if(score < ABSOLUTE_MATCHING_THRESHOLD) return false

        return if(ranking.size == 1) {
            true
        } else {
            val nextScore = getScore(ranking[1].score , maxScore)
            nextScore < score * (1 - RELATIVE_MATCHING_THRESHOLD)
        }
    }

    /**
     * Calculates the score in a scale
     *
     * @param a Double
     * @param b Double
     * @return Double
     */
    private fun getScore(a: Double, b: Double): Double {
        val ret = a / b
        return ret * ret
    }

    companion object {

        const val ABSOLUTE_MATCHING_THRESHOLD = 0.0
        const val RELATIVE_MATCHING_THRESHOLD = 0.0

        @JvmStatic
        fun main(args: Array<String>) = object : CliktCommand(
            name = "Mapper",
            help = "Generates obfuscation mappings between OSRS revision updates.",
            printHelpOnEmptyArgs = true,
            invokeWithoutSubcommand = true
        ) {

            private val mappedJarFile by argument(name = "mapped jar", help = "Path to the mapped / renamed JAR file").file(mustExist = true, canBeDir = false)
            private val targetJarFile by argument(name = "unmapped deob jar", help = "Path to the un-renamed deob JAR file").file(mustExist = true, canBeDir = false)


            /**
             * Command logic.
             */
            override fun run() {
                /*
                 * Build the class environment.
                 */
                val environment = ClassEnvironment.init(mappedJarFile, targetJarFile)

                /*
                 * Create the mapper instance.
                 */
                val mapper = Mapper(environment)
                mapper.init()

                /*
                 * Match all.
                 */
                mapper.matchAll()
            }

        }.main(args)
    }
}