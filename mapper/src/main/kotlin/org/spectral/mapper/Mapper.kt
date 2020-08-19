package org.spectral.mapper

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.file
import org.spectral.asm.Class
import org.spectral.asm.ClassEnvironment
import org.spectral.asm.Field
import org.spectral.asm.Method
import org.spectral.common.coroutine.*
import org.spectral.mapper.classifier.ClassClassifier
import org.spectral.mapper.classifier.ClassifierLevel
import org.spectral.mapper.classifier.ClassifierUtil
import org.tinylog.kotlin.Logger
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.stream.Collectors

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
     * Run a iterable set process action in parallel using all
     * available runtime threads.
     *
     * @param set Set<T>
     * @param action Function1<T, Unit>
     */
    private fun <T> runParallel(set: Set<T>, action: (T) -> Unit) {
        val availableThreads = Runtime.getRuntime().availableProcessors()

        runBlocking(CommonPool) {
            val threadPool = newFixedThreadPoolContext(availableThreads, "mapper-thread")
            val taskQueue = ArrayDeque<CompletableFuture<Unit>>()

            /*
             * Build the task queue
             */
            set.stream().collect(Collectors.toSet()).forEach {
                val future = future(threadPool) {
                    action(it)
                }

                taskQueue.push(future)
            }

            /*
             * Run and await for each job from the queue to complete.
             */
            taskQueue.forEach { it.await() }
        }
    }

    /**
     * Match all classes, methods, and fields.
     */
    fun matchAll() {
        /*
         * Initially match any classes we can.
         * If we were successful, match classes again as we may be able to
         *  match some hierarchy members in the second pass.
         */
        if(matchClasses(ClassifierLevel.INITIAL)) {
            matchClasses(ClassifierLevel.INITIAL)
        }
    }

    /**
     * Match [Class] objects
     * @return Boolean
     */
    fun matchClasses(level: ClassifierLevel): Boolean {
        /*
         * The mapped classes.
         */
        val classes = env.groupA.classes.stream()
            .filter { it.real }
            .filter { !it.hasMatch() }
            .collect(Collectors.toSet())

        /*
         * The unmapped classes to compare to.
         */
        val cmpClasses = env.groupB.classes.stream()
            .filter { it.real }
            .filter { !it.hasMatch() }
            .collect(Collectors.toSet())

        val maxScore = ClassClassifier.getMaxScore(level)

        val matches = hashMapOf<Class, Class>()

        /*
         * Run the matching process
         */
        runParallel(classes) { cls ->
            val ranking = ClassClassifier.rank(cls, cmpClasses.toTypedArray(), level)

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

        Logger.info("\t\t CLASS [$a] -> [$b]")

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

            /*
             * Match hierarchy members
             */
            val matchedSrc = src.overrides.firstOrNull { it.name == src.name && it.desc == src.desc } ?: continue
            val dstHierarchyMembers = matchedSrc.match?.overrides ?: hashSetOf()
            if(dstHierarchyMembers.isEmpty()) continue

            for(dst in b.methods) {
                if(dstHierarchyMembers.contains(dst)) {
                    match(src, dst)
                    break
                }
            }
        }

        /*
         * Match fields together if they are not obfuscated.
         */
        for(src in a.fields) {
            if(!ClassifierUtil.isObfuscatedName(src.name)) {
                val dst = b.getField(src.name, src.desc)

                if(dst != null && !ClassifierUtil.isObfuscatedName(dst.name)) {
                    match(src, dst)
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
    fun match(a: Method, b: Method, matchHierarchy: Boolean = true) {
        if(a.match == b) return

        Logger.info("\t\t METHOD [${a.owner}.${a.name}] -> [${b.owner}.${b.name}]")

        a.match = b
        b.match = a

        if(matchHierarchy) {
            val srcHierarchyMembers = a.overrides
            if(srcHierarchyMembers.isEmpty()) return

            var dstHierarchyMembers: Set<Method>? = null

            for(src in srcHierarchyMembers) {
                if(src.hasMatch() || !src.owner.hasMatch() || !src.owner.real) continue

                if(dstHierarchyMembers == null) dstHierarchyMembers = b.overrides

                for(dst in src.owner.match!!.methods) {
                    if(dstHierarchyMembers.contains(dst)) {
                        match(src, dst, false)
                        break
                    }
                }
            }
        }
    }

    fun match(a: Field, b: Field) {
        if(a.match == b) return

        Logger.info("\t\t FIELD [${a.owner}.${a.name}] -> [${b.owner}.${b.name}]")

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
                Logger.info("Building class environment...")
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