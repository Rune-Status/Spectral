package org.spectral.mapper

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import org.spectral.asm.*
import org.spectral.mapper.matcher.*
import org.spectral.mapper.util.CompareUtil
import org.spectral.mapping.ClassMapping
import org.spectral.mapping.FieldMapping
import org.spectral.mapping.Mappings
import org.spectral.mapping.MethodMapping
import org.tinylog.kotlin.Logger

/**
 * Responsible for generating mapping between [groupA] -> [groupB] based
 * on multiple similarity comparisons.
 *
 * @property groupA ClassGroup
 * @property groupB ClassGroup
 * @constructor
 */
class Mapper(val groupA: ClassGroup, val groupB: ClassGroup) {

    /**
     * The global matches storage.
     */
    val matches = MatchGroup(groupA, groupB)

    /**
     * Runs the mapper.
     */
    fun run() {
        Logger.info("Matching static methods...")

        /*
         * Match the static methods
         */
        matches.merge(matchStaticMethods())

        /*
         * Match normal methods
         */
        matches.merge(matchMethods())

        /*
         * Reduce the matches to the highest candidates only.
         */
        matches.reduce()

        /*
         * Iterate through and match any methods
         * which were added through invocations
         */
        while(matchUnexecutedMethods())

        /*
         * Match classes
         */
        matchClasses()

        /*
         * Match the remaining methods.
         *
         * This is being done in a second pass since now
         * we can use some of the matched class information to
         * derive some method matches.
         */
        matchClassMethods()

        /*
         * Match the class constructors
         */
        matches.merge(matchConstructors())

        /*
         * Perform the final match group reduction.
         */
        matches.reduce()

        /*
         * Get the matching statistics and log them.
         */
        val classCount = groupA.size
        val methodCount = groupA.flatMap { it.methods }.size
        val fieldCount = groupA.flatMap { it.fields }.size

        val matchedClassCount = matches.matches.keySet().filter { it is Class }.size
        val matchedMethodCount = matches.matches.keySet().filter { it is Method }.size
        val matchedFieldCount = matches.matches.keySet().filter { it is Field }.size

        Logger.info("Mapper completed successfully. Below are the result statistics.")
        println("------------------------------")
        println("Classes: $matchedClassCount / $classCount (${(matchedClassCount.toDouble() / classCount.toDouble()) * 100.00}%)")
        println("Methods: $matchedMethodCount / $methodCount (${(matchedMethodCount.toDouble() / methodCount.toDouble()) * 100.00}%)")
        println("Fields: $matchedFieldCount / $fieldCount (${(matchedFieldCount.toDouble() / fieldCount.toDouble()) * 100.00}%)")
    }

    /**
     * Matches all of the static methods
     * @return MatchGroup
     */
    private fun matchStaticMethods(): MatchGroup {
        /*
         * Run the [StaticMethodMatcher].
         * This generates a list of [Method]s which are potential
         * candidates for being a match.
         */
        val staticMethodMatcher = StaticMethodMatcher()
        staticMethodMatcher.run(groupA, groupB)

        /*
         * A list of the multiple generated [MatchGroup] objects.
         */
        val matches = mutableListOf<MatchGroup>()

        /*
         * Loop through the potential match results and score them using
         * similarity comparator
         */
        staticMethodMatcher.results.keySet().forEach { from ->
            val methods = staticMethodMatcher.results[from]

            /*
             * For the given method match pair, execute their
             * instructions in-memory and record / analyze how they interact
             * with the JVM stack.
             */
            val executionMatcher = ExecutionMatcher(from, methods)
            val executionResults = executionMatcher.run() ?: return@forEach

            val match = executionResults.match(executionResults.cardinalMethodA!!, executionResults.cardinalMethodB!!)
            match.executed = true
            match.score = executionResults.score

            Logger.info("Matched STATIC method [${executionResults.cardinalMethodA!!}] -> [${executionResults.cardinalMethodB!!}]")

            matches.add(executionResults)
        }

        val results = MatchGroup(groupA, groupB)
        matches.forEach { results.merge(it) }

        return results
    }

    private fun matchMethods(): MatchGroup {
        /*
         * Run the [MethodMatcher]
         * This generates a map of all the potential match combinations
         * for every method in each [ClassGroup] object.
         */
        val methodMatcher = MethodMatcher()
        methodMatcher.run(groupA, groupB)

        val matches = mutableListOf<MatchGroup>()

        /*
         * Iterate through all of the possible match combinations.
         */
        methodMatcher.results.keySet().forEach { from ->
            val methods = methodMatcher.results[from]

            /*
             * For each possible match combination for the [from] method,
             * execute each pair in parallel and compare their
             * JVM stack operation similarity.
             */
            val executionMatcher = ExecutionMatcher(from, methods)
            val executionResults = executionMatcher.run() ?: return@forEach

            val match = executionResults.match(executionResults.cardinalMethodA!!, executionResults.cardinalMethodB!!)
            match.score = executionResults.score
            match.executed = true

            Logger.info("Matched MEMBER method [${executionResults.cardinalMethodA!!}] -> [${executionResults.cardinalMethodB!!}]")

            matches.add(executionResults)
        }

        val results = MatchGroup(groupA, groupB)
        matches.forEach { results.merge(it) }

        return results
    }


    private fun matchUnexecutedMethods(): Boolean {
        var matched = false
        matches.toMap().keys.forEach { from ->
            val m = matches.matches[from].iterator().next()

            if(m.executed || m.from !is Method) {
                return@forEach
            }

            val methodA = m.from
            val methodB = m.from

            val execution = ExecutionMatcher.execute(methodA, methodB)
            m.executed = true
            matched = true
            matches.merge(execution)
        }

        return matched
    }

    /**
     * Matches classes based on static field initialized similarities.
     */
    private fun matchClasses() {

        /*
         * Attempt to match any class we can
         * based off how the fields are statically initialized within the class.
         */

        val indexerA = StaticInitializerIndexer(groupA)
        indexerA.index()

        val indexerB = StaticInitializerIndexer(groupB)
        indexerB.index()

        var map = matches.toMap()
        for(from in map.keys) {
            val to = map[from]
            mapClass(indexerA, indexerB, from, to!!)
        }

        map = matches.toMap()
        matches.reduce()

        /*
         * Iterate through all other possible class
         * matches and match any which have very similar non-static
         * methods.
         *
         * This is done by comparing the method return type id's and argument type id's.
         * Same for fields.
         */

        val classGroupMatcher = ClassGroupMatcher(groupA, groupB)
        classGroupMatcher.run()

        /*
         * Loop through all the classes to find out
         * if they were already matched based on static field initialized indexes.
         */
        for(clsA in groupA) {
            /*
             * If the class in [groupA] has NOT already been matched.
             * Check to see what the best match is from the method similarity
             * [ClassGroupMatcher] object is.
             */
            if(!map.containsKey(clsA)) {
                val other = classGroupMatcher.results[clsA]

                /*
                 * If there is no match in the [ClassGroupMatcher],
                 * We conclude there was not enough information given to properly match
                 * the class between class groups.
                 *
                 * This can sometimes happen to some classes which hold ONLY static methods
                 * and these get moved around every revision re-obfuscation.
                 */
                if(other == null) {
                    Logger.info("Unable to match class [${clsA}]")
                }
                else {
                    /*
                     * We found a match in the [ClassGroupMatcher]
                     */
                    Logger.info("Matched class [${clsA}] -> [${other}]")

                    val classMatch = matches.getOrCreate(clsA, other)
                    classMatch.count++
                }
            }
        }
    }

    /**
     * Matches two classes together based on the field index similarities.
     *
     * @param indexerA StaticInitializerIndexer
     * @param indexerB StaticInitializerIndexer
     * @param a Any
     * @param b Any
     */
    private fun mapClass(indexerA: StaticInitializerIndexer, indexerB: StaticInitializerIndexer, a: Any, b: Any) {
        val clsA: Class
        val clsB: Class

        /*
         * If we are attempting to match two [ClassNode]s by comparing field owners
         * which have been statically initialized.
         */
        if(a is Field || b is Field) {
            val fieldA = a as Field
            val fieldB = b as Field

            if(indexerA.isIndexed(fieldA) && indexerB.isIndexed(fieldB)) {
                Logger.info("Matched class [${fieldA.owner}] -> [${fieldB.owner}]")
            }
            else if(fieldA.isStatic || fieldB.isStatic) {
                return
            }

            clsA = fieldA.owner
            clsB = fieldB.owner
        }
        /*
         * If we are attempting to match [ClassNodes]s by comparing two methods
         * which have been statically called or initialized as types.
         */
        else if(a is Method || b is Method) {
            val methodA = a as Method
            val methodB = b as Method

            if(methodA.isStatic || methodB.isStatic) {
                return
            }

            clsA = methodA.owner
            clsB = methodB.owner
        }
        else {
            return
        }

        val m = matches.getOrCreate(clsA, clsB)
        m.count++
    }

    /**
     * Matches methods which are a member of classes
     * which have already been successfully matched.
     */
    private fun matchClassMethods() {
        /*
         * Second pass for mapping methods.
         * This pass, we use the matching classes to derive missing
         * method relationships.
         *
         * This will get methods which may of had their arguments or signatures changed
         * but still do roughly the same thing.
         */

        for(clsA in groupA) {
            val clsB = matches[clsA] as Class? ?: continue

            /*
             * Get a collection of member methods
             * for both [clsA] and [clsB].
             */
            val methodsA = clsA.methods
                .filter { !it.isStatic }
                .filter { it.name != "<init>" }

            val methodsB = clsB.methods
                .filter { !it.isStatic }
                .filter { it.name != "<init>" }

            /*
             * Loop through each method in parallel
             * and check if its already been matched.
             *
             * If not, check if they are similar and their
             * owner [ClassNode] classes are matched.
             *
             * If so, score them based on running the matching pair of
             * methods through the [ExecutionMatcher]
             */
            for(methodA in methodsA) {
                if(matches[methodA] != null) continue

                val possibleMatches = methodsB.filter { CompareUtil.isPotentialMatch(methodA, it) }

                /*
                 * Run the possible matching methods
                 * through the [ExecutionMatcher] to get the
                 * best matching result.
                 */
                val executionMatcher = ExecutionMatcher(methodA, possibleMatches)
                val executionResults = executionMatcher.run() ?: continue

                executionResults.match(executionResults.cardinalMethodA!!, executionResults.cardinalMethodB!!)
                Logger.info("Matched method [${executionResults.cardinalMethodA!!}] -> [${executionResults.cardinalMethodB!!}]")

                /*
                 * Merge the execution scored resulting methods into the
                 * global match group.
                 */
                matches.merge(executionResults)
            }
        }
    }

    /**
     * Matches method constructors between class groups.
     * Constructor methods have a name of '<init>' in bytecode.
     */
    private fun matchConstructors(): MatchGroup {
        val constructorMatcher = ConstructorMatcher()
        constructorMatcher.run(groupA, groupB)

        val matches = mutableListOf<MatchGroup>()

        /*
         * Run each of the constructor matching pairs through the
         * [ExecutionMatcher] to get the best result.
         */
        constructorMatcher.results.keySet().forEach { from ->
            val constructors = constructorMatcher.results[from]

            val executionMatcher = ExecutionMatcher(from, constructors)
            val executionResults = executionMatcher.run() ?: return@forEach

            executionResults.match(executionResults.cardinalMethodA!!, executionResults.cardinalMethodB!!)

            Logger.info("Matched constructor [${executionResults.cardinalMethodA!!}] -> [${executionResults.cardinalMethodB!!}]")
            matches.add(executionResults)
        }

        val results = MatchGroup(groupA, groupB)
        matches.forEach { results.merge(it) }

        return results
    }

    companion object {
        /**
         * CLI Command entrance to the mapper.
         *
         * @param args Array<String>
         */
        @JvmStatic
        fun main(args: Array<String>) = object : CliktCommand(
            name = "Spectral Mapper",
            help = "Generates mappings between name obfuscations of JAR/classpaths.",
            printHelpOnEmptyArgs = true,
            invokeWithoutSubcommand = true
        ) {

            private val inputJarFile by argument(name = "input file", help = "The mapped/renamed jar file path").file(mustExist = true, canBeDir = false)
            private val targetJarFile by argument("target file", help = "The un-mapped, new jar file path").file(mustExist = true, canBeDir = false)

            private val exportFlag by option("-e", "--export", help = "The folder path to export mappings to.")
                .file(mustExist = false, canBeDir = true)

            override fun run() {
                Logger.info("Preparing to run mapper.")

                val groupA = ClassGroup.fromJar(inputJarFile)
                val groupB = ClassGroup.fromJar(targetJarFile)

                val mapper = Mapper(groupA, groupB)
                mapper.run()

                /*
                 * If the export flag is specified,
                 * build and export the methods to a provided directory.
                 */
                if(exportFlag != null) {
                    Logger.info("Building mappings from mapper results.")

                    if(!exportFlag!!.exists()) {
                        exportFlag!!.mkdirs()
                    }

                    val mappings = Mappings()
                    mappings.load(mapper.matches)

                    mappings.export(exportFlag!!)
                }
            }

        }.main(args)

        /**
         * Initializes the mappings from a [MatchGroup]
         *
         * @param matches MatchGroup
         */
        fun Mappings.load(matches: MatchGroup) {
            /*
             * Get the classes first.
             */
            matches.groupA.forEach {
                val clsA = it
                val clsB = matches[clsA] as Class?

                val classMapping = ClassMapping(clsA.name, if(clsB == null) "?" else clsB.name)

                /*
                 * Get the fields that are parented to [clsA]
                 */
                matches.matches.keySet().filterIsInstance<Field>().filter { it.owner == clsA }.forEach {
                    val fieldA = it
                    val fieldB = matches[fieldA] as Field? ?: throw NullPointerException("No match found for field key: '${fieldA}'.")

                    val fieldMapping = FieldMapping(fieldA.name, fieldA.desc, fieldA.owner.name, fieldB.name, fieldB.desc, fieldB.owner.name)
                    classMapping.fields.add(fieldMapping)
                }

                /*
                 * Get the methods that are parented to [clsA]
                 */
                matches.matches.keySet().filterIsInstance<Method>().filter { it.owner == clsA }.forEach {
                    val methodA = it
                    val methodB = matches[methodA] as Method? ?: throw NullPointerException("No match found for method key: '${methodA}'.")

                    val methodMapping = MethodMapping(methodA.name, methodA.desc, methodA.owner.name, methodB.name, methodB.desc, methodB.owner.name)
                    classMapping.methods.add(methodMapping)
                }

                /*
                 * Add the class mapping to the [classes] list.
                 */
                classes.add(classMapping)
            }
        }
    }
}