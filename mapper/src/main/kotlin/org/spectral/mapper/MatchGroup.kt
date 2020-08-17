package org.spectral.mapper

import com.google.common.collect.HashMultimap
import org.objectweb.asm.tree.MethodNode
import org.spectral.asm.ClassGroup

/**
 * Represents a collection of [Match] objects which
 * point to a single node entry.
 */
class MatchGroup(val groupA: ClassGroup, val groupB: ClassGroup) {

    /**
     * The backing storage of matches.
     * Using guava multimaps.
     *
     * AKA. This is basically just: Map<Any, Set<Match>>
     */
    var matches = HashMultimap.create<Any, Match>()

    /**
     * Whether this match group's cardinal methods have executed.
     */
    var executed = false

    /**
     * The matching similarity score of the cardinal methods.
     */
    var score = 0

    /**
     * When the match group is being used as a [Match], the A method
     * being matched.
     */
    var cardinalMethodA: MethodNode? = null

    /**
     * When the match group is being used as a [Match], the B method
     * being matched.
     */
    var cardinalMethodB: MethodNode? = null

    /**
     * Gets or creates a new [Match].
     *
     * @param from Any
     * @param to Any
     * @return Match
     */
    fun getOrCreate(from: Any, to: Any): Match {
        matches[from].forEach { match ->
            if(match.to == to) return match
        }

        /*
         * If a match is not found, create a new one.
         * And return it.
         */
        val match = Match(from, to)
        matches.put(from, match)

        return match
    }

    /**
     * Sets two nodes to be matched together manually.
     *
     * @param from Any
     * @param to Any
     * @return Match
     */
    fun match(from: Any, to: Any): Match {
        val match = this.getOrCreate(from, to)
        match.count++

        return match
    }

    /**
     * Gets the highest weighted match for the [from] node.
     *
     * @param from Any
     * @return Any?
     */
    fun highest(from: Any): Any? {
        var highest: Match? = null

        /*
         * Loop through all the matches.
         * Calculates the best result from the score, and match counter.
         */
        matches[from].forEach { match ->
            if(highest == null || match.count > highest!!.count) {
                highest = match
            }
            else if(match.count == highest!!.count && from.toString() > highest!!.to.toString()) {
                highest = match
            }
        }

        return if(highest != null) highest!!.to else null
    }

    /**
     * Merges this group with another [MatchGroup] object.
     *
     * @param other MatchGroup
     */
    fun merge(other: MatchGroup) {
        other.matches.entries().forEach { entries ->
            val from = entries.key
            val match = entries.value

            val mergedMatch = this.getOrCreate(from, match.to)
            mergedMatch.merge(match)
        }
    }

    /**
     * Gets the highest match given a source node.
     *
     * @param from Any
     * @return Any?
     */
    operator fun get(from: Any): Any? = highest(from)

    /**
     * Converts this object to a [Map] object.
     *
     * @return Map<Any, Any?>
     */
    fun toMap(): Map<Any, Any?> {
        val map = hashMapOf<Any, Any?>()

        matches.keySet().forEach { from ->
            map[from] = highest(from)
        }

        return map
    }

    /**
     * Converts this object to a [Collection] object.
     *
     * @param from Any
     * @return Collection<Match>
     */
    fun toCollection(from: Any): Collection<Match> {
        return matches[from]
    }

    /**
     * For each key in [matches], the highest [Match] is kept but the
     * rest are reduced out (removed) from the list.
     *
     * After this method is called, each key entry should only have one
     * match object.
     */
    fun reduce() {
        /*
         * The comparator used for reduction sorting.
         */
        val comparator = compareByDescending<Match> { it.score }.thenByDescending { it.count }.thenByDescending { it.toString() }

        val sortedMatches = matches.values().sortedWith(comparator)

        /*
         * The new reduced map.
         * This will be what overwrites the [matches] field.
         */
        val reduced = HashMultimap.create<Any, Match>()

        /*
         * A simple fast way to check what was reduced out.
         */
        val reversed = hashMapOf<Any, Any>()

        sortedMatches.forEach { match ->
            if(reduced.containsKey(match.from)) {
                return@forEach
            }

            if(reversed.containsKey(match.to)) {
                return@forEach
            }

            reduced.put(match.from, match)
            reversed[match.to] = match.from
        }

        matches = reduced
    }
}