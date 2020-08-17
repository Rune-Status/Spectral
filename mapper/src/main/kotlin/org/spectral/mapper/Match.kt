package org.spectral.mapper

import kotlin.math.max

/**
 * Represents an node entry potential match.
 */
class Match(val from: Any, val to: Any) {

    /**
     * The number of times this has been matched with other
     * entry nodes.
     */
    var count = 0

    /**
     * Whether this match nodes have been simulated
     * and executed for similarity calculations.
     */
    var executed = false

    /**
     * The match execution / simulation score
     */
    var score = 0

    /**
     * Combines another [Match] with this instance.
     * The highest traits get saved.
     *
     * @param other Match
     */
    fun merge(other: Match) {
        count += other.count
        executed = executed or other.executed
        score = max(score, other.score)
    }
}