package org.spectral.mapper

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.file
import kotlinx.coroutines.*
import me.tongfei.progressbar.*
import org.spectral.asm.*
import org.spectral.common.coroutine.*
import org.spectral.mapper.classifier.*
import org.tinylog.kotlin.Logger
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Collectors
import kotlin.math.sqrt

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
class Mapper(val env: ClassEnvironment) {

    /**
     * Initialize the mapper.
     */
    fun init() {
        Logger.info("Analyzing class environment...")

        /*
         * Initialize the classifiers.
         */
        ClassClassifier.init()
        MethodClassifier.init()
    }

    /**
     * Run a iterable set process action in parallel using all
     * available runtime threads.
     *
     * @param set Set<T>
     * @param action Function1<T, Unit>
     */
    fun <T> runParallel(set: Set<T>, progress: ProgressBar, action: (T) -> Unit) {
        runBlocking {
            val tasks = ArrayDeque<Deferred<Unit>>()

            set.forEach {
                GlobalScope.async {
                    progress.step()
                    action(it)
                }.apply { tasks.push(this) }
            }

            tasks.forEach {
                it.await()
            }
        }
    }

    /**
     * Match all classes, methods, and fields.
     */
    fun run(progress: ProgressBar) {
        /*
         * Initially match any classes we can.
         * If we were successful, match classes again as we may be able to
         *  match some hierarchy members in the second pass.
         */
        if(matchClasses(ClassifierLevel.INITIAL, progress)) {
            matchClasses(ClassifierLevel.INITIAL, progress)
        }

        /*
         * Match each classifier level recursively.
         */
        matchLevel(ClassifierLevel.INITIAL, progress)
        matchLevel(ClassifierLevel.SECONDARY, progress)
        matchLevel(ClassifierLevel.TERTIARY, progress)
        matchLevel(ClassifierLevel.EXTRA,progress)

        /*
         * Print out the matching results.
         */
        val totalClasses = env.groupA.classes.filter { it.real }.size
        val matchedClasses = env.groupA.classes.filter { it.real && it.hasMatch() }.size

        val totalMethods = env.groupA.classes.filter { it.real }.flatMap { it.methods }.filter { it.real }.size
        val matchedMethods = env.groupA.classes.filter { it.real }.flatMap { it.methods }.filter { it.real && it.hasMatch() }.size

        val totalFields = env.groupA.classes.filter { it.real }.flatMap { it.fields }.filter { it.real }.size
        val matchedFields = env.groupA.classes.filter { it.real }.flatMap { it.fields }.filter { it.real && it.hasMatch() }.size

        println("==========================================================")
        println("Classes: \t $matchedClasses / $totalClasses (${(matchedClasses.toDouble() / totalClasses.toDouble()) * 100.0}%)")
        println("Methods: \t $matchedMethods / $totalMethods (${(matchedMethods.toDouble() / totalMethods.toDouble()) * 100.0}%)")
        println("Fields: \t $matchedFields / $totalFields (${(matchedFields.toDouble() / totalFields.toDouble()) * 100.0}%)")
        println("==========================================================")
    }

    /**
     * Matches classes, methods, and fields in order and continues to do so until no
     * new matches are made for any.
     */
    private fun matchLevel(level: ClassifierLevel, progress: ProgressBar) {

        var matchedAny: Boolean
        var matchedClassesBefore = true

        do {
            /*
             * Attempt to match normal methods.
             */
            matchedAny = matchMethods(level, progress)

            /*
             * If no methods where matched, break out of the loop.
             */
            if(!matchedAny && !matchedClassesBefore) {
                break
            }

            /*
             * Match any classes which we can now from the matched methods from
             * the last cycle.
             */
            matchedClassesBefore = matchClasses(level, progress)
            matchedAny = matchedAny or matchedClassesBefore

        } while(matchedAny)
    }

    /**
     * Match [Class] objects
     * @return Boolean
     */
    fun matchClasses(level: ClassifierLevel, progress: ProgressBar): Boolean {

        /*
         * The mapped classes.
         */
        val classes = env.groupA.classes
            .filter { it.real }
            .filter { !it.hasMatch() }
            .toSet()

        /*
         * The unmapped classes to compare to.
         */
        val cmpClasses = env.groupB.classes
            .filter { it.real }
            .filter { !it.hasMatch() }
            .toSet()

        progress.maxHint(progress.current + classes.size.toLong())

        val maxScore = ClassClassifier.getMaxScore(level)

        val matches = hashMapOf<Class, Class>()

        /*
         * Run the matching process
         */
        runParallel(classes, progress) { cls ->
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

    fun matchMethods(level: ClassifierLevel, progress: ProgressBar): Boolean {
        val totalUnmatched = AtomicInteger()

        val matches = classify(level, { it.methods.filter { !it.isStatic }.toTypedArray() }, MethodClassifier, totalUnmatched, progress)

        matches.forEach { (t, u) ->
            match(t, u)
        }

        return matches.isNotEmpty()
    }

    fun matchStaticMethods(level: ClassifierLevel, progress: ProgressBar): Boolean {
        val totalUnmatched = AtomicInteger()

        val matches = classify(level, { it.methods.filter { it.isStatic }.toTypedArray() }, MethodClassifier, totalUnmatched, progress)

        matches.forEach { (t, u) ->
            match(t, u)
        }

        return matches.isNotEmpty()
    }

    inline fun <reified T : Matchable<T>> classify(
        level: ClassifierLevel,
        noinline elements: (Class) -> Array<T>,
        classifier: AbstractClassifier<T>,
        totalUnmatched: AtomicInteger,
        progress: ProgressBar
    ): Map<T, T> {
        val classes = env.groupA.classes
            .filter { it.real && elements(it).isNotEmpty() }
            .filter { elements(it).any { !it.hasMatch() } }
            .toSet()

        val dsts = mutableListOf<T>()

        /*
         * build the matchables
         */
        env.groupB.classes.forEach { cls ->
            dsts.addAll(elements(cls))
        }

        if(classes.isEmpty()) return Collections.emptyMap()

        progress.maxHint(progress.current + classes.size.toLong())

        val ret = hashMapOf<T, T>()

        val maxScore = classifier.getMaxScore(level)
        val maxMismatch = maxScore - sqrt(ABSOLUTE_MATCHING_THRESHOLD * (1 - RELATIVE_MATCHING_THRESHOLD)) * maxScore

        runParallel(classes, progress) { cls ->
            var unmatched = 0

            elements(cls).forEach { member ->
                if(member.hasMatch()) return@forEach

                val ranking = classifier.rank(member, dsts.toTypedArray(), level, maxMismatch)

                if(foundMatch(ranking, maxScore)) {
                    val match = ranking[0].subject
                    ret[member] = match
                } else {
                    unmatched++
                }
            }

            if(unmatched > 0) totalUnmatched.addAndGet(unmatched)
        }

        resolveConflicts(ret)

        return ret
    }

    /**
     * Matches two [Class] objects together.
     *
     * @param a Class
     * @param b Class
     */
    fun match(a: Class, b: Class) {
        if(a.match == b) return

        Logger.info("CLASS [$a] -> [$b]")

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

        Logger.info("METHOD [${a.owner}.${a.name}] -> [${b.owner}.${b.name}]")

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

        Logger.info("FIELD [${a.owner}.${a.name}] -> [${b.owner}.${b.name}]")

        a.match = b
        b.match = a
    }

    /**
     * Resolves any conflicting matches by removing them from the match
     * queue.
     *
     * @param matches MutableMap<T, T>
     */
    fun <T> resolveConflicts(matches: MutableMap<T, T>) {
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
    fun foundMatch(ranking: List<RankResult<*>>, maxScore: Double): Boolean {
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
    fun getScore(a: Double, b: Double): Double {
        val ret = a / b
        return ret * ret
    }

    companion object {

        const val ABSOLUTE_MATCHING_THRESHOLD = 0.5
        const val RELATIVE_MATCHING_THRESHOLD = 0.05

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
                 * Progress bar
                 */
                val progress = ProgressBarBuilder()
                    .setTaskName("Analyzing")
                    .setStyle(ProgressBarStyle.UNICODE_BLOCK)
                    .setUpdateIntervalMillis(100)
                    .build()

                /*
                 * Match all.
                 */
                try {
                    mapper.run(progress)
                } catch(e : Exception) {
                    e.printStackTrace()
                    progress.close()
                } finally {
                    progress.close()
                }
            }

        }.main(args)
    }
}