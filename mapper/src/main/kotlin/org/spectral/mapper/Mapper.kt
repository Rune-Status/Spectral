package org.spectral.mapper

import org.spectral.asm.ClassGroup
import org.spectral.mapper.matcher.StaticMethodMatcher
import org.spectral.mapper.util.ScoreUtil
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
        Logger.info("Preparing to start mapper.")

        /*
         * Match the static methods
         */
        matches.merge(matchStaticMethods())

        println()
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

            methods.forEach { to ->
                val result = MatchGroup(from.group, to.group)
                val match = result.match(from, to)

                /*
                 * Calculate the basic similarity score.
                 */
                match.score = ScoreUtil.calculateScore(this.matches, from, to)

                matches.add(result)
            }
        }

        val results = MatchGroup(groupA, groupB)
        matches.forEach { results.merge(it) }

        return results
    }
}